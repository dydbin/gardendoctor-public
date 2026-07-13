package com.project.farming.domain.user.controller;

import com.project.farming.domain.user.dto.*;
import com.project.farming.domain.user.service.AuthService;
import com.project.farming.domain.user.service.PasswordResetService;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.jwtToken.JwtToken;
import com.project.farming.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "인증 (Auth)", description = "회원가입, 로그인, 토큰 관리 등 사용자 인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다. 이메일은 고유해야 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검증 실패 또는 이메일 중복)")
    })
    @PostMapping("/register")
    public ResponseEntity<CommonResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("회원가입 요청 수신");
        try {
            authService.registerUser(request.getEmail(), request.getPassword(), request.getNickname());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    CommonResponse.success("회원가입이 성공적으로 완료되었습니다.", request.getEmail())
            );
        } catch (IllegalArgumentException ex) {
            log.warn("회원가입 실패: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(
                    CommonResponse.error(ex.getMessage(), "BAD_REQUEST")
            );
        }
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 JWT 토큰을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검증 실패)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (이메일 또는 비밀번호 불일치)")
    })
    @PostMapping("/login")
    public ResponseEntity<CommonResponse<JwtToken>> login(@Valid @RequestBody LoginRequest request) {
        JwtToken tokens = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(CommonResponse.success("로그인 성공", tokens));
    }

    @Operation(summary = "FCM 토큰 갱신", description = "로그인된 사용자의 FCM 기기 토큰을 갱신합니다. 앱 시작 시 또는 토큰 갱신 시 호출해야 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "FCM 토큰 갱신 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping("/fcm-token")
    public ResponseEntity<CommonResponse<Void>> updateFcmToken(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody FcmTokenUpdateRequest request) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        authService.updateFcmToken(userId, request.getFcmToken());

        return ResponseEntity.ok(CommonResponse.success("FCM 토큰이 성공적으로 갱신되었습니다."));
    }


    @Operation(summary = "로그아웃", description = "현재 사용자의 Access Token을 블랙리스트에 추가하고 Refresh Token을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효한 토큰 없음)"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout(
            HttpServletRequest request,
            @Parameter(description = "인증된 사용자 ID", example = "1")
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("로그아웃 실패: Authorization 헤더가 없거나 'Bearer ' 형식으로 시작하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    CommonResponse.error("로그아웃 실패: 유효한 Access Token이 필요합니다.", "UNAUTHORIZED")
            );
        }
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        String accessToken = header.substring(7);
        Long userId = customUserDetails.getUser().getUserId();

        authService.logout(accessToken, userId);

        return ResponseEntity.ok(CommonResponse.success("로그아웃 성공"));
    }

    @Operation(summary = "토큰 재발급", description = "만료된 Access Token 대신 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (리프레시 토큰 누락 등 유효성 검증 실패)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않거나 블랙리스트에 있는 리프레시 토큰)")
    })
    @PostMapping("/token/refresh")
    public ResponseEntity<CommonResponse<JwtToken>> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        JwtToken newTokens = authService.refreshTokens(request.getRefreshToken());
        return ResponseEntity.ok(CommonResponse.success("토큰 재발급 성공", newTokens));
    }

    @Operation(summary = "현재 사용자 정보 조회", description = "인증된 사용자의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "사용자 정보 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "jwtAuth")
    @GetMapping("/user/me")
    public ResponseEntity<CommonResponse<UserMyPageResponse>> getCurrentUser(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        UserMyPageResponse response = authService.getMyPageInfo(userId);

        return ResponseEntity.ok(CommonResponse.success("사용자 정보 조회 성공", response));
    }


    @Operation(summary = "회원 탈퇴", description = "로그인한 사용자의 계정을 삭제합니다. (하드 삭제)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "회원 탈퇴 성공 (No Content)"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "jwtAuth")
    @DeleteMapping("/me")
    public ResponseEntity<CommonResponse<Void>> deleteMyPage(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        authService.deleteMyPageInfo(userId);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "닉네임 변경", description = "로그인한 사용자의 닉네임을 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "닉네임 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검증 실패)"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping("/me/nickname")
    public ResponseEntity<CommonResponse<UserMyPageResponse>> updateNickname(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody NicknameUpdateRequest request) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        UserMyPageResponse updated = authService.updateNickname(userId, request.getNewNickname());
        return ResponseEntity.ok(CommonResponse.success("닉네임 변경 성공", updated));
    }

    // 이미지 변경
    @Operation(summary = "프로필 이미지 업로드 및 변경", description = "사용자가 업로드한 이미지 파일로 프로필 이미지를 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "프로필 이미지 변경 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping(value = "/me/profile-image/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<UserMyPageResponse>> updateProfileImageByUpload(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestPart("image") MultipartFile imageFile
    ) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        UserMyPageResponse updated = authService.updateProfileImage(userId, imageFile);
        return ResponseEntity.ok(CommonResponse.success("프로필 이미지 변경 성공", updated));
    }

    // 프로필 이미지 삭제 (기본 이미지로 되돌리기)
    @Operation(summary = "프로필 이미지 삭제", description = "로그인한 사용자의 프로필 이미지를 기본 이미지로 되돌립니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "프로필 이미지 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "jwtAuth")
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<CommonResponse<UserMyPageResponse>> deleteProfileImage(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        UserMyPageResponse updated = authService.deleteProfileImage(userId);
        return ResponseEntity.ok(CommonResponse.success("프로필 이미지 삭제 성공", updated));
    }

    @Operation(summary = "비밀번호 재설정 요청", description = "계정 존재 여부를 노출하지 않고 일회용 비밀번호 재설정 링크 발송을 접수합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "비밀번호 재설정 요청 접수"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "429", description = "요청 횟수 제한 초과")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<CommonResponse<Void>> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                CommonResponse.success("계정이 존재하면 비밀번호 재설정 안내가 발송됩니다.")
        );
    }

    @Operation(summary = "비밀번호 재설정 확정", description = "메일로 받은 일회용 토큰을 소비하고 새 비밀번호를 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "비밀번호 재설정 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않거나 만료된 토큰"),
            @ApiResponse(responseCode = "503", description = "비밀번호 재설정 저장소 일시 장애")
    })
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<CommonResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 변경 (로그인 상태)", description = "로그인된 사용자가 현재 비밀번호를 확인 후 새 비밀번호로 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping("/change-password")
    public ResponseEntity<CommonResponse<Void>> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody PasswordChangeRequest request) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = customUserDetails.getUser().getUserId();
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());

        return ResponseEntity.ok(
                CommonResponse.success("비밀번호가 성공적으로 변경되었습니다.")
        );
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }

}

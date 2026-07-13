package com.project.farming.domain.notification.controller;

import com.project.farming.domain.notification.outbox.FcmOutboxAdminService;
import com.project.farming.domain.notification.outbox.FcmOutboxAdminFilter;
import com.project.farming.domain.notification.outbox.FcmOutboxBulkRetryRequest;
import com.project.farming.domain.notification.outbox.FcmOutboxResponse;
import com.project.farming.domain.notification.outbox.FcmOutboxSourceType;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/admin/fcm-outbox")
@Tag(name = "FCM Outbox Admin", description = "FCM outbox 운영 관리 API")
@SecurityRequirement(name = "jwtAuth")
public class FcmOutboxAdminController {

    private final FcmOutboxAdminService fcmOutboxAdminService;

    @Operation(
            summary = "실패 FCM outbox 목록 조회",
            description = "전송 실패 상태의 FCM outbox를 조회합니다. FCM 토큰은 마스킹되어 반환됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실패 outbox 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @GetMapping("/failed")
    public ResponseEntity<CommonResponse<Page<FcmOutboxResponse>>> getFailedOutboxes(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(description = "outbox source type 필터", example = "NOTIFICATION")
            @RequestParam(required = false) FcmOutboxSourceType sourceType,
            @Parameter(description = "source id 필터", example = "100")
            @RequestParam(required = false) Long sourceId,
            @Parameter(description = "사용자 id 필터", example = "1")
            @RequestParam(required = false) Long userId,
            @ParameterObject
            @Parameter(description = "페이징 정보 (기본: page=0, size=20, sort=updatedAt,desc)")
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        requireAdmin(customUserDetails);
        Page<FcmOutboxResponse> failedOutboxes = fcmOutboxAdminService.getFailedOutboxes(
                new FcmOutboxAdminFilter(sourceType, sourceId, userId),
                pageable);
        return ResponseEntity.ok(CommonResponse.success("실패 FCM outbox 목록 조회 성공", failedOutboxes));
    }

    @Operation(
            summary = "실패 FCM outbox 재시도 요청",
            description = "실패 상태의 FCM outbox를 대기 상태로 되돌려 worker가 다시 처리할 수 있게 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "재시도 요청 접수"),
            @ApiResponse(responseCode = "400", description = "실패 상태가 아닌 outbox"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "outbox를 찾을 수 없음")
    })
    @PatchMapping("/failed/{fcmOutboxId}/retry")
    public ResponseEntity<CommonResponse<Void>> retryFailedOutbox(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(description = "재시도할 FCM outbox ID", example = "10") @PathVariable Long fcmOutboxId) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        requireAdmin(customUserDetails);
        fcmOutboxAdminService.retryFailedOutbox(fcmOutboxId, customUserDetails.getUser().getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success("FCM outbox 재시도 요청이 접수되었습니다."));
    }

    @Operation(
            summary = "선택한 실패 FCM outbox 일괄 재시도 요청",
            description = "명시적으로 선택한 실패 상태의 FCM outbox 목록을 대기 상태로 되돌립니다. 필터 전체 재시도는 지원하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "일괄 재시도 요청 접수"),
            @ApiResponse(responseCode = "400", description = "잘못된 ID 목록 또는 실패 상태가 아닌 outbox"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "outbox를 찾을 수 없음")
    })
    @PatchMapping("/failed/retry")
    public ResponseEntity<CommonResponse<Integer>> retryFailedOutboxes(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Valid @RequestBody FcmOutboxBulkRetryRequest request) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        requireAdmin(customUserDetails);
        int retriedCount = fcmOutboxAdminService.retryFailedOutboxes(
                request.fcmOutboxIds(),
                customUserDetails.getUser().getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success("선택한 FCM outbox 재시도 요청이 접수되었습니다.", retriedCount));
    }

    private void requireAdmin(CustomUserDetails customUserDetails) {
        if (customUserDetails.getUser().getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("관리자만 FCM outbox를 관리할 수 있습니다.");
        }
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }
}

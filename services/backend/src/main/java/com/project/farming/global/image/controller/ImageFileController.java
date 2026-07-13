package com.project.farming.global.image.controller;

import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.image.dto.ImageUploadResponse;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "이미지 (Image)", description = "이미지 업로드, 삭제 등 이미지 파일 관리 API")
public class ImageFileController {

    private final ImageFileService imageFileService;

    @Operation(
            summary = "사용자 소유 이미지 업로드 (레거시)",
            description = "인증된 사용자의 USER 이미지 파일만 저장하는 호환 API입니다. 실제 프로필 변경은 /auth/me/profile-image/upload를 사용합니다.",
            deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "이미지 업로드 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 없음, 지원하지 않는 파일 형식 등)"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "본인 프로필 이미지가 아닌 업로드 요청")
    })
    @SecurityRequirement(name = "jwtAuth")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<ImageUploadResponse>> uploadImage(
            @Parameter(description = "업로드할 이미지 파일", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "이미지 도메인 유형. 공용 API에서는 USER만 허용됩니다.", required = true)
            @RequestParam("domainType") ImageDomainType domainType,
            @Parameter(description = "인증된 사용자의 ID", required = true)
            @RequestParam("domainId") Long domainId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        ImageUploadResponse response = imageFileService.uploadImageResponseForUser(
                file, domainType, domainId, customUserDetails.getUser().getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success("이미지 업로드 성공", response));
    }

    @Operation(
            summary = "사용자 소유 이미지 삭제 (레거시)",
            description = "인증된 사용자가 소유한 USER 이미지 파일만 삭제하는 호환 API입니다. 실제 프로필 삭제는 /auth/me/profile-image를 사용합니다.",
            deprecated = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "이미지 삭제 성공"),
            @ApiResponse(responseCode = "404", description = "이미지가 없거나 현재 사용자가 접근할 수 없는 이미지 ID"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "jwtAuth")
    @DeleteMapping("/{imageFileId}")
    public ResponseEntity<CommonResponse<Void>> deleteImage(
            @Parameter(description = "삭제할 이미지 파일의 ID", required = true)
            @PathVariable Long imageFileId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        imageFileService.deleteImageForUser(imageFileId, customUserDetails.getUser().getUserId());
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }
}

package com.project.farming.domain.analysis.controller;


import com.project.farming.domain.analysis.dto.PhotoAnalysisSidebarResponse;
import com.project.farming.domain.analysis.service.PhotoAnalysisService;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "PhotoAnalysis API", description = "사진 분석 API")
@RestController
@RequestMapping("/api/photo-analysis")
@RequiredArgsConstructor
public class PhotoAnalysisController {

    private final PhotoAnalysisService photoAnalysisService;

    @SecurityRequirement(name = "jwtAuth")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "사진 분석 요청", description = "JWT 인증된 사용자로부터 이미지 파일을 받아 AI 분석 후 DB에 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "사진 분석 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<CommonResponse<PhotoAnalysisSidebarResponse>> analyzePhoto(

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Parameter(description = "업로드할 이미지 파일 (분석 대상)", required = true)
            @RequestPart("file") MultipartFile file) {

        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();

        PhotoAnalysisSidebarResponse response = photoAnalysisService.analyzePhotoAndSave(userId, file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success("사진 분석 성공", response));
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }
}

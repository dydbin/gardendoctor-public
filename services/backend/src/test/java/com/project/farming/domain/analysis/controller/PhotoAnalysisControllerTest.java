package com.project.farming.domain.analysis.controller;

import com.project.farming.domain.analysis.dto.PhotoAnalysisSidebarResponse;
import com.project.farming.domain.analysis.service.PhotoAnalysisService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoAnalysisControllerTest {

    @Mock
    private PhotoAnalysisService photoAnalysisService;

    private PhotoAnalysisController photoAnalysisController;

    @BeforeEach
    void setUp() {
        photoAnalysisController = new PhotoAnalysisController(photoAnalysisService);
    }

    @Test
    void analyzePhotoShouldWrapCreatedResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        MockMultipartFile file = new MockMultipartFile("file", "plant.jpg", "image/jpeg", "image".getBytes());
        PhotoAnalysisSidebarResponse analysis = PhotoAnalysisSidebarResponse.builder()
                .photoAnalysisId(10L)
                .createdDate("2026-07-10")
                .detectedDisease("정상")
                .analysisSummary("정상입니다.")
                .solution("유지")
                .imageUrl("https://example.com/plant.jpg")
                .build();
        when(photoAnalysisService.analyzePhotoAndSave(1L, file)).thenReturn(analysis);

        ResponseEntity<CommonResponse<PhotoAnalysisSidebarResponse>> response =
                photoAnalysisController.analyzePhoto(userDetails, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("사진 분석 성공");
        assertThat(response.getBody().getData()).isSameAs(analysis);
    }

    @Test
    void missingPrincipalShouldReturnCommonUnauthorizedBody() {
        MockMultipartFile file = new MockMultipartFile("file", "plant.jpg", "image/jpeg", "image".getBytes());

        ResponseEntity<CommonResponse<PhotoAnalysisSidebarResponse>> response =
                photoAnalysisController.analyzePhoto(null, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(photoAnalysisService);
    }

    private CustomUserDetails userDetails() {
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .password("password")
                .nickname("user")
                .role(UserRole.USER)
                .subscriptionStatus("ACTIVE")
                .build();
        return new CustomUserDetails(user);
    }
}

package com.project.farming.global.image.controller;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.image.dto.ImageUploadResponse;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.service.ImageFileService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageFileControllerTest {

    @Mock
    private ImageFileService imageFileService;

    private ImageFileController imageFileController;

    @BeforeEach
    void setUp() {
        imageFileController = new ImageFileController(imageFileService);
    }

    @Test
    void uploadImageShouldWrapCreatedResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", "image".getBytes());
        ImageUploadResponse uploadResponse = ImageUploadResponse.builder()
                .imageFileId(10L)
                .imageUrl("https://example.com/plant.jpg")
                .message("이미지 업로드 성공")
                .build();
        when(imageFileService.uploadImageResponseForUser(file, ImageDomainType.USER, 1L, 1L))
                .thenReturn(uploadResponse);

        ResponseEntity<CommonResponse<ImageUploadResponse>> response =
                imageFileController.uploadImage(file, ImageDomainType.USER, 1L, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("이미지 업로드 성공");
        assertThat(response.getBody().getData()).isSameAs(uploadResponse);
    }

    @Test
    void deleteImageShouldKeepNoContentSuccessResponse() {
        CustomUserDetails userDetails = userDetails();

        ResponseEntity<CommonResponse<Void>> response = imageFileController.deleteImage(10L, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(imageFileService).deleteImageForUser(10L, 1L);
    }

    @Test
    void missingPrincipalShouldReturnCommonUnauthorizedBody() {
        MockMultipartFile file = new MockMultipartFile("file", "plant.jpg", "image/jpeg", "image".getBytes());

        ResponseEntity<CommonResponse<ImageUploadResponse>> response =
                imageFileController.uploadImage(file, ImageDomainType.PLANT, 20L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(imageFileService);
    }

    @Test
    void uploadImageShouldRejectNonUserDomainAtServiceBoundary() {
        CustomUserDetails userDetails = userDetails();
        MockMultipartFile file = new MockMultipartFile("file", "diary.jpg", "image/jpeg", "image".getBytes());

        when(imageFileService.uploadImageResponseForUser(file, ImageDomainType.DIARY, 20L, 1L))
                .thenThrow(new AccessDeniedException("본인 프로필 이미지만 업로드할 수 있습니다."));

        assertThatThrownBy(() -> imageFileController.uploadImage(
                file, ImageDomainType.DIARY, 20L, userDetails))
                .isInstanceOf(AccessDeniedException.class);

        verify(imageFileService).uploadImageResponseForUser(file, ImageDomainType.DIARY, 20L, 1L);
    }

    @Test
    void uploadImageShouldRejectAnotherUsersProfileAtServiceBoundary() {
        CustomUserDetails userDetails = userDetails();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", "image".getBytes());

        when(imageFileService.uploadImageResponseForUser(file, ImageDomainType.USER, 2L, 1L))
                .thenThrow(new AccessDeniedException("본인 프로필 이미지만 업로드할 수 있습니다."));

        assertThatThrownBy(() -> imageFileController.uploadImage(
                file, ImageDomainType.USER, 2L, userDetails))
                .isInstanceOf(AccessDeniedException.class);

        verify(imageFileService).uploadImageResponseForUser(file, ImageDomainType.USER, 2L, 1L);
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

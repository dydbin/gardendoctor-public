package com.project.farming.domain.user.controller;

import com.project.farming.domain.user.dto.LoginRequest;
import com.project.farming.domain.user.dto.NicknameUpdateRequest;
import com.project.farming.domain.user.dto.PasswordResetConfirmRequest;
import com.project.farming.domain.user.dto.PasswordResetRequest;
import com.project.farming.domain.user.dto.TokenRefreshRequest;
import com.project.farming.domain.user.dto.UserMyPageResponse;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.service.AuthService;
import com.project.farming.domain.user.service.PasswordResetService;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.jwtToken.JwtToken;
import com.project.farming.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService, passwordResetService);
    }

    @Test
    void loginShouldWrapJwtTokenInCommonResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");
        JwtToken tokens = jwtToken();
        when(authService.login(request.getEmail(), request.getPassword())).thenReturn(tokens);

        ResponseEntity<CommonResponse<JwtToken>> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("로그인 성공");
        assertThat(response.getBody().getData()).isSameAs(tokens);
    }

    @Test
    void refreshTokenShouldWrapJwtTokenInCommonResponse() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken("old-refresh-token");
        JwtToken tokens = jwtToken();
        when(authService.refreshTokens("old-refresh-token")).thenReturn(tokens);

        ResponseEntity<CommonResponse<JwtToken>> response = authController.refreshToken(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("토큰 재발급 성공");
        assertThat(response.getBody().getData()).isSameAs(tokens);
    }

    @Test
    void getCurrentUserShouldWrapMyPageResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        UserMyPageResponse myPage = myPageResponse("user");
        when(authService.getMyPageInfo(1L)).thenReturn(myPage);

        ResponseEntity<CommonResponse<UserMyPageResponse>> response = authController.getCurrentUser(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("사용자 정보 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(myPage);
    }

    @Test
    void updateNicknameShouldWrapMyPageResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        NicknameUpdateRequest request = new NicknameUpdateRequest();
        request.setNewNickname("new-user");
        UserMyPageResponse myPage = myPageResponse("new-user");
        when(authService.updateNickname(1L, "new-user")).thenReturn(myPage);

        ResponseEntity<CommonResponse<UserMyPageResponse>> response =
                authController.updateNickname(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("닉네임 변경 성공");
        assertThat(response.getBody().getData()).isSameAs(myPage);
    }

    @Test
    void profileImageChangesShouldWrapMyPageResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        MockMultipartFile image = new MockMultipartFile("image", "profile.jpg", "image/jpeg", "image".getBytes());
        UserMyPageResponse myPage = myPageResponse("user");
        when(authService.updateProfileImage(1L, image)).thenReturn(myPage);
        when(authService.deleteProfileImage(1L)).thenReturn(myPage);

        ResponseEntity<CommonResponse<UserMyPageResponse>> uploaded =
                authController.updateProfileImageByUpload(userDetails, image);
        ResponseEntity<CommonResponse<UserMyPageResponse>> deleted =
                authController.deleteProfileImage(userDetails);

        assertThat(uploaded.getBody()).isNotNull();
        assertThat(uploaded.getBody().getData()).isSameAs(myPage);
        assertThat(deleted.getBody()).isNotNull();
        assertThat(deleted.getBody().getData()).isSameAs(myPage);
    }

    @Test
    void deleteMyPageShouldKeepNoContentSuccessResponse() {
        CustomUserDetails userDetails = userDetails();

        ResponseEntity<CommonResponse<Void>> response = authController.deleteMyPage(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(authService).deleteMyPageInfo(1L);
    }

    @Test
    void logoutShouldReturnUnauthorizedWhenPrincipalMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");

        ResponseEntity<CommonResponse<Void>> response = authController.logout(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(authService);
    }

    @Test
    void forgotPasswordShouldReturnSameAcceptedEnvelope() {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail("user@example.com");

        ResponseEntity<CommonResponse<Void>> response = authController.forgotPassword(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("계정이 존재하면 비밀번호 재설정 안내가 발송됩니다.");
        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    void confirmPasswordResetShouldConsumeTokenAndReturnNoContent() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setToken("raw-reset-token");
        request.setNewPassword("New-password1!");

        ResponseEntity<CommonResponse<Void>> response = authController.confirmPasswordReset(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(passwordResetService).confirmPasswordReset("raw-reset-token", "New-password1!");
    }

    private JwtToken jwtToken() {
        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
    }

    private UserMyPageResponse myPageResponse(String nickname) {
        return UserMyPageResponse.builder()
                .userId(1L)
                .email("user@example.com")
                .nickname(nickname)
                .profileImageUrl("https://example.com/profile.jpg")
                .oauthProvider("LOCAL")
                .role("USER")
                .subscriptionStatus("ACTIVE")
                .build();
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

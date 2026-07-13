package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.InvalidRefreshTokenException;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.jwtToken.JwtBlacklistService;
import com.project.farming.global.jwtToken.JwtToken;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceRefreshTokenTest {

    private static final Long USER_ID = 1L;
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    @Mock
    private ImageFileRepository imageFileRepository;

    @Mock
    private ImageFileService imageFileService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                jwtTokenProvider,
                jwtBlacklistService,
                authenticationManager,
                passwordEncoder,
                refreshTokenSessionService,
                imageFileRepository,
                imageFileService,
                loginAttemptService
        );
    }

    @Test
    void refreshTokenRotationUsesCompareAndSwapAndDoesNotLogRawToken(CapturedOutput output) {
        User user = user();
        givenValidRefreshRequest(user);
        when(refreshTokenSessionService.rotate(
                eq(USER_ID),
                eq(OLD_REFRESH_TOKEN),
                eq(NEW_REFRESH_TOKEN),
                any(Instant.class)
        )).thenReturn(1);
        when(jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(OLD_REFRESH_TOKEN)).thenReturn(60_000L);

        JwtToken result = authService.refreshTokens(OLD_REFRESH_TOKEN);

        assertThat(result.getAccessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(result.getRefreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
        verify(refreshTokenSessionService).rotate(
                eq(USER_ID),
                eq(OLD_REFRESH_TOKEN),
                eq(NEW_REFRESH_TOKEN),
                any(Instant.class)
        );
        verify(jwtBlacklistService).blacklistToken(OLD_REFRESH_TOKEN, 60_000L);
        assertThat(output).doesNotContain(OLD_REFRESH_TOKEN);
    }

    @Test
    void reusedOldRefreshTokenFailsWhenCompareAndSwapUpdatesNoRows() {
        User user = user();
        givenValidRefreshRequest(user);
        when(refreshTokenSessionService.rotate(
                eq(USER_ID),
                eq(OLD_REFRESH_TOKEN),
                eq(NEW_REFRESH_TOKEN),
                any(Instant.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> authService.refreshTokens(OLD_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("재로그인이 필요합니다");

        verify(jwtBlacklistService, never()).blacklistToken(eq(OLD_REFRESH_TOKEN), anyLong());
    }

    @Test
    void invalidRefreshTokenFailsAsUnauthorizedDomainException() {
        when(jwtTokenProvider.validateRefreshToken(OLD_REFRESH_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshTokens(OLD_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("유효하지 않은 리프레시 토큰");

        verify(refreshTokenSessionService, never()).rotate(anyLong(), any(), any(), any());
        verify(jwtBlacklistService, never()).blacklistToken(eq(OLD_REFRESH_TOKEN), anyLong());
    }

    @Test
    void refreshTokenIssuedBeforePasswordChangeShouldRevokeStoredSession() {
        User user = user();
        when(jwtTokenProvider.validateRefreshToken(OLD_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromRefreshToken(OLD_REFRESH_TOKEN)).thenReturn(USER_ID);
        when(jwtBlacklistService.isBlacklisted(OLD_REFRESH_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.matchesRefreshCredentialVersion(OLD_REFRESH_TOKEN, 0L)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshTokens(OLD_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("비밀번호 변경");

        verify(refreshTokenSessionService).revoke(USER_ID);
        verify(refreshTokenSessionService, never()).rotate(anyLong(), any(), any(), any());
    }

    private void givenValidRefreshRequest(User user) {
        when(jwtTokenProvider.validateRefreshToken(OLD_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromRefreshToken(OLD_REFRESH_TOKEN)).thenReturn(USER_ID);
        when(jwtBlacklistService.isBlacklisted(OLD_REFRESH_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.matchesRefreshCredentialVersion(OLD_REFRESH_TOKEN, 0L)).thenReturn(true);
        when(jwtTokenProvider.generateToken(USER_ID, 0L)).thenReturn(NEW_ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(USER_ID, 0L)).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(NEW_REFRESH_TOKEN)).thenReturn(1_209_600_000L);
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .email("refresh@example.com")
                .password("encoded")
                .nickname("refresh-user")
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
    }
}

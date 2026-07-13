package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceLoginTest {

    private static final Long USER_ID = 1L;

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
    void successfulLoginShouldClearLoginFailureCounter() {
        String email = "login-success@example.com";
        String password = "raw-secret-password";
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(email)));
        when(jwtTokenProvider.generateToken(USER_ID, 0L)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID, 0L)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshExpirationRemainingTimeMillis("refresh-token")).thenReturn(60_000L);

        JwtToken result = authService.login(email, password);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(loginAttemptService).assertAllowed(email);
        verify(loginAttemptService).recordSuccess(email);
        verify(loginAttemptService, never()).recordFailure(email);
        verify(refreshTokenSessionService).store(eq(USER_ID), eq("refresh-token"), any(Instant.class));
    }

    @Test
    void failedLoginShouldRemainAuthenticationFailureAndNotLogCredentials(CapturedOutput output) {
        String email = "login-failure@example.com";
        String password = "raw-secret-password";
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(email, password))
                .isInstanceOf(AuthenticationException.class)
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 잘못되었습니다.");

        verify(loginAttemptService).assertAllowed(email);
        verify(loginAttemptService).recordFailure(email);
        verify(loginAttemptService, never()).recordSuccess(email);
        verify(refreshTokenSessionService, never()).store(any(), any(), any());
        assertThat(output)
                .doesNotContain(email)
                .doesNotContain(password);
    }

    private User user(String email) {
        return User.builder()
                .userId(USER_ID)
                .email(email)
                .password("encoded-password")
                .nickname("login-user")
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
    }
}

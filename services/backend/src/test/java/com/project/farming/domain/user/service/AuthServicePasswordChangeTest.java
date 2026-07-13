package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.jwtToken.JwtBlacklistService;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordChangeTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtBlacklistService jwtBlacklistService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenSessionService refreshTokenSessionService;
    @Mock private ImageFileRepository imageFileRepository;
    @Mock private ImageFileService imageFileService;
    @Mock private LoginAttemptService loginAttemptService;

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
    void passwordChangeShouldAdvanceCredentialVersionAndDeleteRefreshSession() {
        User user = User.builder()
                .userId(7L)
                .email("change-password@example.com")
                .password("old-hash")
                .nickname("change-user")
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old-password1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("New-password1!")).thenReturn("new-hash");

        authService.changePassword(7L, "Old-password1!", "New-password1!");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getCredentialVersion()).isEqualTo(1L);
        verify(refreshTokenSessionService).revoke(7L);
    }
}

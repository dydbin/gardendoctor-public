package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.InvalidPasswordResetTokenException;
import com.project.farming.global.jwtToken.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenStore tokenStore;
    @Mock
    private PasswordResetMailDispatcher mailDispatcher;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository,
                passwordEncoder,
                refreshTokenRepository,
                tokenStore,
                mailDispatcher
        );
    }

    @Test
    void unknownEmailShouldUseSameSuccessfulRequestPathWithoutIssuingToken() {
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestPasswordReset(email)).doesNotThrowAnyException();

        verify(tokenStore).assertRequestAllowed(email);
        verify(tokenStore, never()).issue(org.mockito.ArgumentMatchers.anyLong());
        verifyNoInteractions(mailDispatcher);
    }

    @Test
    void requestShouldSendTokenWithoutChangingCurrentPassword() {
        User user = user();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenStore.issue(7L)).thenReturn("raw-reset-token");

        passwordResetService.requestPasswordReset(user.getEmail());

        assertThat(user.getPassword()).isEqualTo("old-password-hash");
        verify(tokenStore).assertRequestAllowed(user.getEmail());
        verify(mailDispatcher).dispatch(user.getEmail(), "raw-reset-token", 7L);
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void knownEmailShouldQueueMailWithoutWaitingForSmtp() {
        User user = user();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenStore.issue(7L)).thenReturn("raw-reset-token");

        passwordResetService.requestPasswordReset(user.getEmail());

        verify(mailDispatcher).dispatch(user.getEmail(), "raw-reset-token", 7L);
    }

    @Test
    void validTokenShouldBeConsumedOnceAndRevokeRefreshSession() {
        User user = user();
        when(tokenStore.consume("raw-reset-token")).thenReturn(Optional.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("New-password1!")).thenReturn("new-password-hash");

        passwordResetService.confirmPasswordReset("raw-reset-token", "New-password1!");

        assertThat(user.getPassword()).isEqualTo("new-password-hash");
        assertThat(user.getCredentialVersion()).isEqualTo(1L);
        verify(refreshTokenRepository).deleteByUserId(7L);
    }

    @Test
    void invalidOrConsumedTokenShouldNotReadOrMutateUser() {
        when(tokenStore.consume("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset("invalid-token", "New-password1!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenRepository);
    }

    private User user() {
        return User.builder()
                .userId(7L)
                .email("user@example.com")
                .password("old-password-hash")
                .nickname("user")
                .build();
    }
}

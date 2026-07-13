package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.PasswordResetToken;
import com.project.farming.domain.user.repository.PasswordResetTokenRepository;
import com.project.farming.global.exception.PasswordResetRateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenStore = new PasswordResetTokenStore(redisTemplate, tokenRepository);
        ReflectionTestUtils.setField(tokenStore, "tokenTtlSeconds", 900L);
        ReflectionTestUtils.setField(tokenStore, "requestCooldownSeconds", 60L);
    }

    @Test
    void requestLimitKeyShouldContainEmailFingerprintInsteadOfRawEmail() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(true);

        tokenStore.assertRequestAllowed("User@Example.com");

        verify(valueOperations).setIfAbsent(
                eq("auth:password-reset:request:" + CredentialFingerprint.email("user@example.com")),
                eq("1"),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void repeatedRequestShouldBeRateLimitedForKnownAndUnknownEmailsAlike() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(false);

        assertThatThrownBy(() -> tokenStore.assertRequestAllowed("any@example.com"))
                .isInstanceOf(PasswordResetRateLimitExceededException.class);
    }

    @Test
    void issuedTokenShouldBeStoredOnlyByFingerprintAndConsumedOnce() {
        String rawToken = tokenStore.issue(7L);

        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(40);
        org.mockito.ArgumentCaptor<PasswordResetToken> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).deleteByUserId(7L);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenFingerprint())
                .isEqualTo(CredentialFingerprint.sha256(rawToken));
        assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(7L);

        PasswordResetToken stored = PasswordResetToken.issue(
                CredentialFingerprint.sha256(rawToken),
                7L,
                LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now()
        );
        when(tokenRepository.findConsumableForUpdate(
                eq(CredentialFingerprint.sha256(rawToken)), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stored))
                .thenReturn(Optional.empty());
        Optional<Long> first = tokenStore.consume(rawToken);
        Optional<Long> second = tokenStore.consume(rawToken);

        assertThat(first).contains(7L);
        assertThat(second).isEmpty();
    }
}

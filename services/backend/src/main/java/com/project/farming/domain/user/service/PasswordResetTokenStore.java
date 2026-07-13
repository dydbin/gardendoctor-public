package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.PasswordResetToken;
import com.project.farming.domain.user.repository.PasswordResetTokenRepository;
import com.project.farming.global.exception.PasswordResetRateLimitExceededException;
import com.project.farming.global.exception.PasswordResetUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenStore {

    private static final String REQUEST_KEY_PREFIX = "auth:password-reset:request:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final PasswordResetTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.password-reset.token-ttl-seconds:900}")
    private long tokenTtlSeconds;

    @Value("${app.auth.password-reset.request-cooldown-seconds:60}")
    private long requestCooldownSeconds;

    public void assertRequestAllowed(String email) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    REQUEST_KEY_PREFIX + CredentialFingerprint.email(email),
                    "1",
                    Duration.ofSeconds(Math.max(1, requestCooldownSeconds))
            );
            if (!Boolean.TRUE.equals(acquired)) {
                throw new PasswordResetRateLimitExceededException("비밀번호 재설정 요청이 반복되었습니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (PasswordResetRateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PasswordResetUnavailableException("비밀번호 재설정 기능을 일시적으로 사용할 수 없습니다.", ex);
        }
    }

    @Transactional
    public String issue(Long userId) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenFingerprint = CredentialFingerprint.sha256(rawToken);
        try {
            LocalDateTime now = LocalDateTime.now();
            tokenRepository.deleteByUserId(userId);
            tokenRepository.save(PasswordResetToken.issue(
                    tokenFingerprint,
                    userId,
                    now.plusSeconds(Math.max(1, tokenTtlSeconds)),
                    now
            ));
            return rawToken;
        } catch (RuntimeException ex) {
            throw new PasswordResetUnavailableException("비밀번호 재설정 기능을 일시적으로 사용할 수 없습니다.", ex);
        }
    }

    @Transactional
    public Optional<Long> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        try {
            String tokenFingerprint = CredentialFingerprint.sha256(rawToken);
            LocalDateTime now = LocalDateTime.now();
            return tokenRepository.findConsumableForUpdate(tokenFingerprint, now)
                    .map(token -> {
                        token.markConsumed(now);
                        return token.getUserId();
                    });
        } catch (RuntimeException ex) {
            throw new PasswordResetUnavailableException("비밀번호 재설정 기능을 일시적으로 사용할 수 없습니다.", ex);
        }
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        try {
            tokenRepository.deleteByTokenFingerprint(CredentialFingerprint.sha256(rawToken));
        } catch (RuntimeException ignored) {
            // The token has a short TTL; failed cleanup is reported by the caller and expires naturally.
        }
    }
}

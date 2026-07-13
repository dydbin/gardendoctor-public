package com.project.farming.domain.user.service;

import com.project.farming.global.exception.PasswordResetRateLimitExceededException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@TestPropertySource(properties = {
        "app.auth.password-reset.token-ttl-seconds=30",
        "app.auth.password-reset.request-cooldown-seconds=30",
        "app.auth.password-reset.confirm-url=http://localhost/reset-password"
})
class PasswordResetTokenStoreIntegrationTest {

    @Autowired
    private PasswordResetTokenStore tokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void redisShouldRateLimitAllEmailsAndConsumeOnlyHashedTokenOnce() {
        Long userId = 9_000_000L + System.nanoTime() % 100_000L;
        String email = "password-reset-" + userId + "@example.com";
        String requestKey = "auth:password-reset:request:" + CredentialFingerprint.email(email);
        String rawToken = null;

        try {
            tokenStore.assertRequestAllowed(email);
            assertThatThrownBy(() -> tokenStore.assertRequestAllowed(email))
                    .isInstanceOf(PasswordResetRateLimitExceededException.class);

            rawToken = tokenStore.issue(userId);
            assertThat(redisTemplate.hasKey(rawToken)).isFalse();
            assertThat(redisTemplate.hasKey(
                    "auth:password-reset:token:" + CredentialFingerprint.sha256(rawToken))).isFalse();
            assertThat(tokenStore.consume(rawToken)).contains(userId);
            assertThat(tokenStore.consume(rawToken)).isEmpty();
        } finally {
            redisTemplate.delete(requestKey);
            if (rawToken != null) {
                tokenStore.revoke(rawToken);
            }
        }
    }
}

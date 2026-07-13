package com.project.farming.integration;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.jwtToken.JwtTokenFingerprint;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenRepository;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class RefreshTokenRotationIntegrationTest {

    private static final String EMAIL_PREFIX = "refresh-rotation-";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenSessionService refreshTokenSessionService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                DELETE token FROM refresh_token token
                JOIN users u ON u.user_id = token.user_pk
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void rawTokenIsNeverStoredAndConcurrentRotationHasExactlyOneWinner() throws Exception {
        User user = userRepository.saveAndFlush(user());
        String oldRawToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), 0L);
        String firstNewRawToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), 0L);
        String secondNewRawToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), 0L);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        refreshTokenSessionService.store(user.getUserId(), oldRawToken, expiresAt);

        String storedBeforeRotation = jdbcTemplate.queryForObject(
                "SELECT token_fingerprint FROM refresh_token WHERE user_pk = ?",
                String.class,
                user.getUserId());
        assertThat(storedBeforeRotation)
                .isEqualTo(JwtTokenFingerprint.sha256(oldRawToken))
                .hasSize(64)
                .doesNotContain(oldRawToken);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> rotateAfterStart(
                    start, user.getUserId(), oldRawToken, firstNewRawToken, expiresAt));
            Future<Integer> second = executor.submit(() -> rotateAfterStart(
                    start, user.getUserId(), oldRawToken, secondNewRawToken, expiresAt));
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        String storedAfterRotation = refreshTokenRepository.findByUserId(user.getUserId())
                .orElseThrow()
                .getTokenFingerprint();
        assertThat(storedAfterRotation).isIn(
                JwtTokenFingerprint.sha256(firstNewRawToken),
                JwtTokenFingerprint.sha256(secondNewRawToken));
        assertThat(storedAfterRotation)
                .doesNotContain(firstNewRawToken)
                .doesNotContain(secondNewRawToken);
    }

    private int rotateAfterStart(
            CountDownLatch start,
            Long userId,
            String oldRawToken,
            String newRawToken,
            Instant expiresAt) throws InterruptedException {
        start.await(3, TimeUnit.SECONDS);
        return refreshTokenSessionService.rotate(userId, oldRawToken, newRawToken, expiresAt);
    }

    private User user() {
        return User.builder()
                .email(EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .password("encoded")
                .nickname("refresh-rotation")
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
    }
}

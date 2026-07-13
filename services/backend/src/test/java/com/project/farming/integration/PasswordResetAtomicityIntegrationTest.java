package com.project.farming.integration;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.user.service.PasswordResetService;
import com.project.farming.domain.user.service.PasswordResetTokenStore;
import com.project.farming.global.exception.InvalidPasswordResetTokenException;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.JwtTokenFingerprint;
import com.project.farming.global.jwtToken.RefreshToken;
import com.project.farming.global.jwtToken.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class PasswordResetAtomicityIntegrationTest {

    private static final String EMAIL_PREFIX = "password-reset-atomicity-";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenStore tokenStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                DELETE token
                FROM password_reset_tokens token
                JOIN users u ON u.user_id = token.user_id
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE refresh_token
                FROM refresh_token
                JOIN users u ON u.user_id = refresh_token.user_pk
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void failedPasswordUpdateShouldRollbackTokenConsumptionAndAllowSafeRetry() {
        User user = saveUser("rollback");
        String rawToken = tokenStore.issue(user.getUserId());
        String oldAccessToken = jwtTokenProvider.generateToken(
                user.getUserId(), user.getCredentialVersion());
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .userId(user.getUserId())
                .tokenFingerprint(JwtTokenFingerprint.sha256("reset-refresh-" + UUID.randomUUID()))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
        when(passwordEncoder.encode("Too-long-password1!"))
                .thenReturn("x".repeat(300));

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(
                rawToken, "Too-long-password1!"))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        User rolledBack = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(rolledBack.getPassword()).isEqualTo("old-password-hash");
        assertThat(rolledBack.getCredentialVersion()).isZero();
        assertThat(refreshTokenRepository.findByUserId(user.getUserId())).isPresent();

        when(passwordEncoder.encode("Valid-password1!"))
                .thenReturn("new-password-hash");
        passwordResetService.confirmPasswordReset(rawToken, "Valid-password1!");

        entityManager.clear();
        User changed = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(changed.getPassword()).isEqualTo("new-password-hash");
        assertThat(changed.getCredentialVersion()).isEqualTo(1L);
        assertThat(refreshTokenRepository.findByUserId(user.getUserId())).isEmpty();
        assertThat(jwtTokenProvider.matchesAccessCredentialVersion(
                oldAccessToken, changed.getCredentialVersion())).isFalse();
        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(
                rawToken, "Valid-password1!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void concurrentConfirmationShouldAllowExactlyOnePasswordChange() throws Exception {
        User user = saveUser("concurrent");
        String rawToken = tokenStore.issue(user.getUserId());
        when(passwordEncoder.encode("Concurrent-password1!"))
                .thenReturn("concurrent-password-hash");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ConfirmationOutcome> first = executor.submit(() -> confirmAfterStart(
                    start, rawToken, "Concurrent-password1!"));
            Future<ConfirmationOutcome> second = executor.submit(() -> confirmAfterStart(
                    start, rawToken, "Concurrent-password1!"));
            start.countDown();

            List<ConfirmationOutcome> outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
            assertThat(outcomes).containsExactlyInAnyOrder(
                    ConfirmationOutcome.SUCCESS,
                    ConfirmationOutcome.INVALID_TOKEN
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        entityManager.clear();
        User changed = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(changed.getPassword()).isEqualTo("concurrent-password-hash");
        assertThat(changed.getCredentialVersion()).isEqualTo(1L);
    }

    private ConfirmationOutcome confirmAfterStart(
            CountDownLatch start,
            String rawToken,
            String password) throws InterruptedException {
        start.await(3, TimeUnit.SECONDS);
        try {
            passwordResetService.confirmPasswordReset(rawToken, password);
            return ConfirmationOutcome.SUCCESS;
        } catch (InvalidPasswordResetTokenException exception) {
            return ConfirmationOutcome.INVALID_TOKEN;
        }
    }

    private User saveUser(String suffix) {
        return userRepository.saveAndFlush(User.builder()
                .email(EMAIL_PREFIX + suffix + "-" + UUID.randomUUID() + "@example.com")
                .password("old-password-hash")
                .nickname("reset-" + suffix)
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build());
    }

    private enum ConfirmationOutcome {
        SUCCESS,
        INVALID_TOKEN
    }
}

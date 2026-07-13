package com.project.farming.integration;

import com.project.farming.domain.notification.outbox.FcmOutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
class FcmOutboxAtomicEnqueueIntegrationTest {

    private static final String EMAIL_PREFIX = "fcm-enqueue-it-";

    @Autowired
    private FcmOutboxService fcmOutboxService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long notificationId;

    @AfterEach
    void cleanup() {
        if (notificationId != null) {
            jdbcTemplate.update(
                    "DELETE FROM fcm_outbox WHERE source_type = 'NOTIFICATION' AND source_id = ?",
                    notificationId);
            jdbcTemplate.update("DELETE FROM notification WHERE notification_id = ?", notificationId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void concurrentNotificationEnqueueShouldCreateOneOutboxWithoutLoserException() throws Exception {
        userId = insertUser();
        notificationId = insertNotification(userId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> enqueueAfter(start));
            Future<Integer> second = executor.submit(() -> enqueueAfter(start));
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM fcm_outbox
                    WHERE source_type = 'NOTIFICATION'
                      AND source_id = ?
                      AND user_id = ?
                    """, Long.class, notificationId, userId)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int enqueueAfter(CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out before concurrent FCM enqueue");
        }
        return fcmOutboxService.enqueueNotification(
                notificationId,
                userId,
                "atomic-token",
                "동시 enqueue",
                "DB upsert로 한 행만 생성");
    }

    private Long insertUser() {
        String suffix = UUID.randomUUID().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (
                        email, password, nickname, role, fcm_token, subscription_status, credential_version
                    ) VALUES (?, '{noop}password', ?, 'USER', 'atomic-token', 'FREE', 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, EMAIL_PREFIX + suffix + "@example.test");
            statement.setString(2, "fcm-atomic");
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertNotification(Long ownerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notification (
                        user_id, event_key, title, message, is_read, created_at
                    ) VALUES (?, ?, '동시 enqueue', '원자 upsert', FALSE, CURRENT_TIMESTAMP(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, "fcm-atomic-enqueue:" + UUID.randomUUID());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}

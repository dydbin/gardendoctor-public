package com.project.farming.integration;

import com.project.farming.domain.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class NoticeFanoutIntegrationDiagnosticsTest {

    private static final int ADDED_RECIPIENT_COUNT = 500;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void noticeFanoutShouldUseBoundedStatementsInsteadOfOneInsertPerRecipient() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        batchInsertRecipients(suffix);
        long eligibleRecipients = eligibleRecipientCount();
        long noticeId = Long.MAX_VALUE - Math.floorMod(System.nanoTime(), 1_000_000_000L);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        long startedAt = System.nanoTime();

        notificationService.saveNotice(
                noticeId,
                "대량 공지 " + suffix,
                "수신자 수와 무관하게 bounded SQL로 생성되어야 하는 공지입니다.");
        entityManager.flush();

        long elapsedNanos = System.nanoTime() - startedAt;
        long preparedStatements = statistics.getPrepareStatementCount();
        long entityInsertCount = statistics.getEntityInsertCount();
        long insertedNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE notice_id = ?",
                Long.class,
                noticeId);

        System.out.printf(
                "Notice fan-out dataset[addedRecipients=%d,eligibleRecipients=%d], "
                        + "preparedStatements=%d,hibernateEntityInserts=%d,elapsed=%.3fms%n",
                ADDED_RECIPIENT_COUNT,
                eligibleRecipients,
                preparedStatements,
                entityInsertCount,
                elapsedNanos / 1_000_000.0);

        assertThat(insertedNotifications).isEqualTo(eligibleRecipients);
        assertThat(preparedStatements)
                .as("Notice fan-out statement count must not grow linearly with recipient count.")
                .isLessThanOrEqualTo(4);
    }

    private void batchInsertRecipients(String suffix) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO users (
                            email, password, nickname, oauth_provider, oauth_id,
                            role, fcm_token, subscription_status, credential_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        String identity = "fanout-" + suffix + "-" + index;
                        statement.setString(1, identity + "@example.test");
                        statement.setString(2, "encoded-password");
                        statement.setString(3, "fanout-" + index);
                        statement.setString(4, "LOCAL");
                        statement.setString(5, identity);
                        statement.setString(6, "USER");
                        statement.setString(7, "fanout-token-" + suffix + "-" + index);
                        statement.setString(8, "ACTIVE");
                    }

                    @Override
                    public int getBatchSize() {
                        return ADDED_RECIPIENT_COUNT;
                    }
                });
    }

    private long eligibleRecipientCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE fcm_token IS NOT NULL
                  AND TRIM(fcm_token) <> ''
                  AND subscription_status <> 'WITHDRAWN'
                """, Long.class);
    }
}

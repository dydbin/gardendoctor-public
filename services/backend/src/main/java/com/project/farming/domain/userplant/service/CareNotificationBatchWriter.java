package com.project.farming.domain.userplant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CareNotificationBatchWriter {

    private static final String INSERT_NOTIFICATION_SQL = """
            INSERT INTO notification (
                user_id, event_key, title, message, is_read, created_at
            ) VALUES (?, ?, ?, ?, FALSE, ?)
            ON DUPLICATE KEY UPDATE notification_id = notification_id
            """;

    private static final String SELECT_ELIGIBLE_USER_IDS_SQL = """
            SELECT user_id
            FROM users
            WHERE user_id IN (:userIds)
              AND subscription_status <> 'WITHDRAWN'
              AND fcm_token IS NOT NULL
              AND TRIM(fcm_token) <> ''
            ORDER BY user_id
            FOR SHARE
            """;

    private static final String INSERT_OUTBOX_SQL = """
            INSERT INTO fcm_outbox (
                source_type, source_id, user_id, target_token, title, body,
                status, attempt_count, next_retry_at, created_at, updated_at
            )
            SELECT
                'NOTIFICATION', n.notification_id, n.user_id, u.fcm_token, n.title, n.message,
                'PENDING', 0, :now, :now, :now
            FROM notification n
            JOIN users u ON u.user_id = n.user_id
            LEFT JOIN fcm_outbox existing
              ON existing.source_type = 'NOTIFICATION'
             AND existing.source_id = n.notification_id
             AND existing.user_id = n.user_id
            WHERE n.event_key IN (:eventKeys)
              AND u.subscription_status <> 'WITHDRAWN'
              AND u.fcm_token IS NOT NULL
              AND TRIM(u.fcm_token) <> ''
              AND existing.fcm_outbox_id IS NULL
            ON DUPLICATE KEY UPDATE source_id = VALUES(source_id)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Transactional
    public int write(List<CareNotificationPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return 0;
        }

        Set<Long> eligibleUserIds = new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(
                SELECT_ELIGIBLE_USER_IDS_SQL,
                new MapSqlParameterSource("userIds", payloads.stream()
                        .map(CareNotificationPayload::userId)
                        .toList()),
                Long.class
        ));
        List<CareNotificationPayload> eligiblePayloads = payloads.stream()
                .filter(payload -> eligibleUserIds.contains(payload.userId()))
                .toList();
        if (eligiblePayloads.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        Timestamp createdAt = Timestamp.valueOf(now);
        jdbcTemplate.batchUpdate(
                INSERT_NOTIFICATION_SQL,
                eligiblePayloads,
                eligiblePayloads.size(),
                (statement, payload) -> {
                    statement.setLong(1, payload.userId());
                    statement.setString(2, payload.eventKey());
                    statement.setString(3, payload.title());
                    statement.setString(4, payload.message());
                    statement.setTimestamp(5, createdAt);
                }
        );

        List<String> eventKeys = eligiblePayloads.stream()
                .map(CareNotificationPayload::eventKey)
                .toList();
        return namedParameterJdbcTemplate.update(
                INSERT_OUTBOX_SQL,
                new MapSqlParameterSource()
                        .addValue("eventKeys", eventKeys)
                        .addValue("now", createdAt)
        );
    }
}

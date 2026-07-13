package com.project.farming.domain.notification.outbox;

import com.project.farming.domain.notification.entity.Notice;
import com.project.farming.domain.notification.repository.NoticeRepository;
import com.project.farming.global.fcm.FcmBatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmOutboxBatchStore {

    private static final int MAX_ERROR_LENGTH = 1000;

    private static final String SELECT_DUE_IDS_SQL = """
            SELECT fcm_outbox_id
            FROM fcm_outbox
            WHERE status = 'PENDING'
              AND next_retry_at <= ?
            ORDER BY next_retry_at ASC, fcm_outbox_id ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String SELECT_CANDIDATES_SQL = """
            SELECT
                o.fcm_outbox_id,
                o.source_type,
                o.source_id,
                o.notice_id,
                o.user_id,
                o.target_token,
                o.title,
                o.body,
                o.attempt_count,
                u.subscription_status,
                u.fcm_token AS current_fcm_token,
                CASE
                    WHEN o.source_type = 'NOTICE' THEN notice.notice_id
                    WHEN o.source_type = 'NOTIFICATION' THEN n.notification_id
                END AS referenced_source_id,
                CASE
                    WHEN n.event_key IS NOT NULL THEN n.event_key
                    WHEN o.source_type = 'NOTICE' THEN CONCAT('notice:', o.source_id, ':user:', o.user_id)
                    ELSE CONCAT('notification:', o.source_id)
                END AS event_id
            FROM fcm_outbox o
            LEFT JOIN users u ON u.user_id = o.user_id
            LEFT JOIN notices notice
              ON o.source_type = 'NOTICE'
             AND notice.notice_id = o.source_id
            LEFT JOIN notification n
              ON o.source_type = 'NOTIFICATION'
             AND n.notification_id = o.source_id
            WHERE o.fcm_outbox_id IN (:ids)
            ORDER BY o.fcm_outbox_id ASC
            """;

    private static final String CLAIM_SQL = """
            UPDATE fcm_outbox
            SET status = 'PROCESSING',
                target_token = ?,
                locked_at = ?,
                updated_at = ?
            WHERE fcm_outbox_id = ?
              AND status = 'PENDING'
            """;

    private static final String CANCEL_SQL = """
            UPDATE fcm_outbox
            SET status = 'CANCELLED',
                locked_at = NULL,
                last_error = ?,
                updated_at = ?
            WHERE fcm_outbox_id = ?
              AND status = 'PENDING'
            """;

    private static final String COMPLETE_SQL = """
            UPDATE fcm_outbox
            SET status = ?,
                attempt_count = ?,
                next_retry_at = ?,
                locked_at = NULL,
                sent_at = ?,
                last_error = ?,
                updated_at = ?
            WHERE fcm_outbox_id = ?
              AND status = 'PROCESSING'
              AND locked_at = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final FcmOutboxRepository fcmOutboxRepository;
    private final NoticeRepository noticeRepository;

    @Transactional
    public int requeueExpiredProcessingJobs(long lockTimeoutMinutes) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        return fcmOutboxRepository.requeueExpiredProcessingJobs(
                FcmOutboxStatus.PROCESSING,
                FcmOutboxStatus.PENDING,
                now.minusMinutes(lockTimeoutMinutes),
                now,
                "Processing lock expired"
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<FcmOutboxDispatch> claimDueBatch(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(500, requestedBatchSize));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        List<Long> dueIds = jdbcTemplate.queryForList(
                SELECT_DUE_IDS_SQL,
                Long.class,
                Timestamp.valueOf(now),
                batchSize
        );
        if (dueIds.isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = namedParameterJdbcTemplate.query(
                SELECT_CANDIDATES_SQL,
                new MapSqlParameterSource("ids", dueIds),
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getLong("fcm_outbox_id"),
                        FcmOutboxSourceType.valueOf(resultSet.getString("source_type")),
                        resultSet.getLong("source_id"),
                        nullableLong(resultSet, "notice_id"),
                        nullableLong(resultSet, "user_id"),
                        resultSet.getString("target_token"),
                        resultSet.getString("title"),
                        resultSet.getString("body"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getString("subscription_status"),
                        resultSet.getString("current_fcm_token"),
                        nullableLong(resultSet, "referenced_source_id"),
                        resultSet.getString("event_id")
                )
        );

        List<Candidate> valid = new ArrayList<>(candidates.size());
        List<Candidate> cancelled = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.hasEligibleRecipient()) {
                cancelled.add(candidate);
            } else {
                valid.add(candidate);
            }
        }

        Timestamp claimedAt = Timestamp.valueOf(now);
        if (!cancelled.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    CANCEL_SQL,
                    cancelled,
                    cancelled.size(),
                    (statement, candidate) -> {
                        statement.setString(1, "Recipient is no longer eligible for push delivery");
                        statement.setTimestamp(2, claimedAt);
                        statement.setLong(3, candidate.fcmOutboxId());
                    }
            );
        }
        if (valid.isEmpty()) {
            return List.of();
        }

        int[][] claimResults = jdbcTemplate.batchUpdate(
                CLAIM_SQL,
                valid,
                valid.size(),
                (statement, candidate) -> {
                    statement.setString(1, candidate.deliveryToken());
                    statement.setTimestamp(2, claimedAt);
                    statement.setTimestamp(3, claimedAt);
                    statement.setLong(4, candidate.fcmOutboxId());
                }
        );
        assertNoSkippedUpdate(claimResults, "claim");

        return valid.stream()
                .map(candidate -> new FcmOutboxDispatch(
                        candidate.fcmOutboxId(),
                        candidate.sourceType(),
                        candidate.sourceId(),
                        candidate.noticeId(),
                        candidate.userId(),
                        candidate.deliveryToken(),
                        candidate.title(),
                        candidate.body(),
                        candidate.attemptCount(),
                        candidate.eventId(),
                        now
                ))
                .toList();
    }

    @Transactional
    public void completeBatch(
            List<FcmOutboxDispatch> dispatches,
            List<FcmBatchResult> results,
            int requestedMaxAttempts) {
        if (dispatches.isEmpty()) {
            return;
        }

        int maxAttempts = Math.max(1, requestedMaxAttempts);
        Map<Long, FcmBatchResult> resultById = new LinkedHashMap<>();
        for (FcmBatchResult result : results) {
            resultById.put(result.correlationId(), result);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Completion> completions = new ArrayList<>(dispatches.size());
        for (FcmOutboxDispatch dispatch : dispatches) {
            FcmBatchResult result = resultById.get(dispatch.fcmOutboxId());
            if (result == null) {
                result = FcmBatchResult.failure(
                        dispatch.fcmOutboxId(),
                        false,
                        "FCM batch returned no result for the claimed outbox"
                );
            }
            completions.add(toCompletion(dispatch, result, maxAttempts, now));
        }

        Timestamp updatedAt = Timestamp.valueOf(now);
        int[][] updateResults = jdbcTemplate.batchUpdate(
                COMPLETE_SQL,
                completions,
                completions.size(),
                (statement, completion) -> {
                    statement.setString(1, completion.status().name());
                    statement.setInt(2, completion.attemptCount());
                    statement.setTimestamp(3, Timestamp.valueOf(completion.nextRetryAt()));
                    statement.setTimestamp(4, completion.sentAt() == null
                            ? null
                            : Timestamp.valueOf(completion.sentAt()));
                    statement.setString(5, truncate(completion.errorMessage()));
                    statement.setTimestamp(6, updatedAt);
                    statement.setLong(7, completion.fcmOutboxId());
                    statement.setTimestamp(8, Timestamp.valueOf(completion.claimedAt()));
                }
        );
        int staleCompletions = countSkippedUpdates(updateResults);
        if (staleCompletions > 0) {
            log.warn("Ignored {} stale FCM outbox completion result(s).", staleCompletions);
        }
        markCompletedNotices(dispatches);
    }

    private Completion toCompletion(
            FcmOutboxDispatch dispatch,
            FcmBatchResult result,
            int maxAttempts,
            LocalDateTime now) {
        if (result.successful()) {
            return new Completion(
                    dispatch.fcmOutboxId(),
                    FcmOutboxStatus.SENT,
                    dispatch.attemptCount(),
                    now,
                    now,
                    null,
                    dispatch.claimedAt()
            );
        }

        int nextAttemptCount = dispatch.attemptCount() + 1;
        boolean exhausted = nextAttemptCount >= maxAttempts;
        FcmOutboxStatus status = result.permanentFailure() || exhausted
                ? FcmOutboxStatus.FAILED
                : FcmOutboxStatus.PENDING;
        LocalDateTime nextRetryAt = status == FcmOutboxStatus.PENDING
                ? nextRetryTime(nextAttemptCount, now)
                : now;
        return new Completion(
                dispatch.fcmOutboxId(),
                status,
                nextAttemptCount,
                nextRetryAt,
                null,
                result.errorMessage(),
                dispatch.claimedAt()
        );
    }

    private LocalDateTime nextRetryTime(int attemptCount, LocalDateTime now) {
        long delayMinutes = Math.min(60L, 1L << Math.min(attemptCount - 1, 5));
        return now.plusMinutes(delayMinutes);
    }

    private void markCompletedNotices(List<FcmOutboxDispatch> dispatches) {
        Set<Long> noticeIds = new LinkedHashSet<>();
        for (FcmOutboxDispatch dispatch : dispatches) {
            if (dispatch.noticeId() != null) {
                noticeIds.add(dispatch.noticeId());
            }
        }
        for (Long noticeId : noticeIds) {
            if (!fcmOutboxRepository.existsByNoticeIdAndStatusNot(noticeId, FcmOutboxStatus.SENT)) {
                noticeRepository.findById(noticeId).ifPresent(this::markNoticeAsSent);
            }
        }
    }

    private void markNoticeAsSent(Notice notice) {
        if (!notice.isSent()) {
            notice.markAsSent();
            noticeRepository.save(notice);
        }
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void assertNoSkippedUpdate(int[][] results, String operation) {
        for (int[] batch : results) {
            for (int result : batch) {
                if (result == 0) {
                    throw new IllegalStateException("FCM outbox batch " + operation + " skipped a locked row");
                }
            }
        }
    }

    private static int countSkippedUpdates(int[][] results) {
        int skipped = 0;
        for (int[] batch : results) {
            for (int result : batch) {
                if (result == 0) {
                    skipped++;
                }
            }
        }
        return skipped;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private record Candidate(
            Long fcmOutboxId,
            FcmOutboxSourceType sourceType,
            Long sourceId,
            Long noticeId,
            Long userId,
            String targetToken,
            String title,
            String body,
            int attemptCount,
            String subscriptionStatus,
            String currentFcmToken,
            Long referencedSourceId,
            String eventId
    ) {
        boolean hasEligibleRecipient() {
            return userId != null
                    && referencedSourceId != null
                    && subscriptionStatus != null
                    && !"WITHDRAWN".equals(subscriptionStatus)
                    && currentFcmToken != null
                    && !currentFcmToken.isBlank();
        }

        String deliveryToken() {
            return currentFcmToken;
        }
    }

    private record Completion(
            Long fcmOutboxId,
            FcmOutboxStatus status,
            int attemptCount,
            LocalDateTime nextRetryAt,
            LocalDateTime sentAt,
            String errorMessage,
            LocalDateTime claimedAt
    ) {
    }
}

package com.project.farming.integration;

import com.project.farming.domain.notification.repository.NotificationRepository;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class DiaryNotificationQueryIndexIntegrationDiagnosticsTest {

    private static final int OTHER_ROW_COUNT = 50_000;
    private static final int TARGET_ROW_COUNT = 30;
    private static final int LATENCY_WARMUP_ITERATIONS = 5;
    private static final int LATENCY_SAMPLE_ITERATIONS = 25;
    private static final LocalDate TARGET_RANGE_START = LocalDate.of(2026, 1, 8);
    private static final LocalDate TARGET_RANGE_END = LocalDate.of(2026, 1, 17);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void diaryAndNotificationQueriesShouldUseCompositeIndexesMatchingPredicates() {
        SeedUsers users = seedUsers();
        String suffix = "query-index-" + System.nanoTime();
        batchInsertDiaries(suffix, users.targetUserId(), users.otherUserId());
        batchInsertNotifications(suffix, users.targetUserId(), users.otherUserId());

        assertThat(indexColumns("diaries", "idx_diary_user_created"))
                .containsExactly("user_id", "created_at");
        assertThat(indexColumns("diaries", "idx_diary_user_date"))
                .containsExactly("user_id", "diary_date");
        assertThat(indexColumns("diary_user_plant", "idx_diary_user_plant_user_plant_diary"))
                .containsExactly("user_plant_id", "diary_id");
        assertThat(indexColumns("notification", "idx_notification_user_created"))
                .containsExactly("user_id", "created_at");
        assertThat(indexColumns("notification", "idx_notification_user_read"))
                .containsExactly("user_id", "is_read");

        QueryMeasurement diaryLatest = measureListQuery(
                "diary latest",
                noIndexHint("diaries",
                        "idx_user_diary", "idx_diary_user_created", "idx_diary_user_date"),
                "FORCE INDEX (idx_diary_user_created)",
                """
                        SELECT diary_id, user_id, title, content, diary_date, created_at, updated_at
                        FROM diaries %s
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        """,
                TARGET_ROW_COUNT,
                users.targetUserId());
        QueryMeasurement diaryRange = measureListQuery(
                "diary date range",
                noIndexHint("diaries",
                        "idx_user_diary", "idx_diary_user_created", "idx_diary_user_date"),
                "FORCE INDEX (idx_diary_user_date)",
                """
                        SELECT diary_id, user_id, title, content, diary_date, created_at, updated_at
                        FROM diaries %s
                        WHERE user_id = ?
                          AND diary_date BETWEEN ? AND ?
                        ORDER BY diary_date ASC
                        """,
                10,
                users.targetUserId(),
                Date.valueOf(TARGET_RANGE_START),
                Date.valueOf(TARGET_RANGE_END));
        QueryMeasurement notificationLatest = measureListQuery(
                "notification latest",
                noIndexHint("notification",
                        "idx_user_notification", "idx_notification_user_created", "idx_notification_user_read"),
                "FORCE INDEX (idx_notification_user_created)",
                """
                        SELECT notification_id, title, message, is_read, created_at
                        FROM notification %s
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        """,
                TARGET_ROW_COUNT,
                users.targetUserId());
        QueryMeasurement notificationUnread = measureCountQuery(
                "notification unread count",
                noIndexHint("notification",
                        "idx_user_notification", "idx_notification_user_created", "idx_notification_user_read"),
                "FORCE INDEX (idx_notification_user_read)",
                """
                        SELECT COUNT(*)
                        FROM notification %s
                        WHERE user_id = ?
                          AND is_read = false
                        """,
                TARGET_ROW_COUNT / 2,
                users.targetUserId());

        System.out.printf(
                "Diary/Notification index measurement: totalRowsPerTable=%d, " +
                        "diaryLatest[%s], diaryRange[%s], notificationLatest[%s], notificationUnread[%s]%n",
                OTHER_ROW_COUNT + TARGET_ROW_COUNT,
                diaryLatest.summary(),
                diaryRange.summary(),
                notificationLatest.summary(),
                notificationUnread.summary()
        );

        assertImproved(diaryLatest);
        assertImproved(diaryRange);
        assertImproved(notificationLatest);
        assertImproved(notificationUnread);
    }

    @Test
    void notificationMarkAsReadShouldUseIdempotentConditionalUpdate() {
        SeedUsers users = seedUsers();
        String suffix = "mark-read-" + System.nanoTime();
        long unreadNotificationId = insertNotification(users.targetUserId(), suffix + "-unread", false);
        long alreadyReadNotificationId = insertNotification(users.targetUserId(), suffix + "-already-read", true);
        long otherUserUnreadNotificationId = insertNotification(users.otherUserId(), suffix + "-other-user", false);

        int firstUpdate = notificationRepository.markAsReadIfUnreadAndOwned(
                unreadNotificationId,
                users.targetUserId()
        );
        int repeatedUpdate = notificationRepository.markAsReadIfUnreadAndOwned(
                unreadNotificationId,
                users.targetUserId()
        );
        int alreadyReadUpdate = notificationRepository.markAsReadIfUnreadAndOwned(
                alreadyReadNotificationId,
                users.targetUserId()
        );
        int wrongOwnerUpdate = notificationRepository.markAsReadIfUnreadAndOwned(
                otherUserUnreadNotificationId,
                users.targetUserId()
        );

        System.out.printf(
                "Notification mark-as-read conditional update: firstUnread=%d, repeated=%d, " +
                        "alreadyRead=%d, wrongOwner=%d%n",
                firstUpdate,
                repeatedUpdate,
                alreadyReadUpdate,
                wrongOwnerUpdate
        );

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(repeatedUpdate).isZero();
        assertThat(alreadyReadUpdate).isZero();
        assertThat(wrongOwnerUpdate).isZero();
        assertThat(isRead(unreadNotificationId)).isTrue();
        assertThat(isRead(alreadyReadNotificationId)).isTrue();
        assertThat(isRead(otherUserUnreadNotificationId)).isFalse();
    }

    private SeedUsers seedUsers() {
        String suffix = "query-index-" + System.nanoTime();
        User targetUser = user("target-" + suffix);
        User otherUser = user("other-" + suffix);
        entityManager.persist(targetUser);
        entityManager.persist(otherUser);
        entityManager.flush();
        return new SeedUsers(targetUser.getUserId(), otherUser.getUserId());
    }

    private User user(String suffix) {
        return User.builder()
                .email(suffix + "@example.com")
                .password("encoded-password")
                .nickname(suffix.substring(0, Math.min(30, suffix.length())))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
    }

    private void batchInsertDiaries(String suffix, Long targetUserId, Long otherUserId) {
        LocalDateTime baseCreatedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        jdbcTemplate.batchUpdate("""
                        INSERT INTO diaries (
                            version,
                            user_id,
                            title,
                            content,
                            diary_date,
                            created_at,
                            updated_at,
                            watered,
                            pruned,
                            fertilized
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        boolean targetRow = i < TARGET_ROW_COUNT;
                        Long userId = targetRow ? targetUserId : otherUserId;
                        int rowNumber = targetRow ? i : i - TARGET_ROW_COUNT;
                        LocalDate diaryDate = targetRow
                                ? LocalDate.of(2026, 1, 1).plusDays(rowNumber)
                                : LocalDate.of(2025, 1, 1).plusDays(rowNumber % 365);
                        LocalDateTime createdAt = targetRow
                                ? baseCreatedAt.plusMinutes(rowNumber)
                                : baseCreatedAt.minusDays(1).minusSeconds(rowNumber);

                        ps.setLong(1, 0L);
                        ps.setLong(2, userId);
                        ps.setString(3, suffix + "-diary-" + i);
                        ps.setString(4, "diagnostic");
                        ps.setDate(5, Date.valueOf(diaryDate));
                        ps.setTimestamp(6, Timestamp.valueOf(createdAt));
                        ps.setTimestamp(7, Timestamp.valueOf(createdAt));
                        ps.setBoolean(8, false);
                        ps.setBoolean(9, false);
                        ps.setBoolean(10, false);
                    }

                    @Override
                    public int getBatchSize() {
                        return OTHER_ROW_COUNT + TARGET_ROW_COUNT;
                    }
                });
    }

    private void batchInsertNotifications(String suffix, Long targetUserId, Long otherUserId) {
        LocalDateTime baseCreatedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        jdbcTemplate.batchUpdate("""
                        INSERT INTO notification (
                            user_id,
                            title,
                            message,
                            is_read,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        boolean targetRow = i < TARGET_ROW_COUNT;
                        Long userId = targetRow ? targetUserId : otherUserId;
                        int rowNumber = targetRow ? i : i - TARGET_ROW_COUNT;
                        LocalDateTime createdAt = targetRow
                                ? baseCreatedAt.plusMinutes(rowNumber)
                                : baseCreatedAt.minusDays(1).minusSeconds(rowNumber);

                        ps.setLong(1, userId);
                        ps.setString(2, suffix + "-notification-" + i);
                        ps.setString(3, "diagnostic");
                        ps.setBoolean(4, !targetRow || rowNumber % 2 == 0);
                        ps.setTimestamp(5, Timestamp.valueOf(createdAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return OTHER_ROW_COUNT + TARGET_ROW_COUNT;
                    }
                });
    }

    private long insertNotification(Long userId, String title, boolean isRead) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            INSERT INTO notification (
                                user_id,
                                title,
                                message,
                                is_read,
                                created_at
                            )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, "diagnostic");
            ps.setBoolean(4, isRead);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0)));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        assertThat(key).as("Inserted notification id should be returned.").isNotNull();
        return key.longValue();
    }

    private boolean isRead(long notificationId) {
        Boolean isRead = jdbcTemplate.queryForObject("""
                        SELECT is_read
                        FROM notification
                        WHERE notification_id = ?
                        """,
                Boolean.class,
                notificationId);
        return Boolean.TRUE.equals(isRead);
    }

    private QueryMeasurement measureListQuery(
            String label,
            String noIndexHint,
            String forceIndexHint,
            String sqlTemplate,
            int expectedRows,
            Object... parameters) {

        ExplainRow noIndexPlan = explain(sqlTemplate.formatted(noIndexHint), parameters);
        ExplainRow forcedIndexPlan = explain(sqlTemplate.formatted(forceIndexHint), parameters);
        LatencyStats noIndexLatency = measureListLatency(sqlTemplate.formatted(noIndexHint), expectedRows, parameters);
        LatencyStats forcedIndexLatency = measureListLatency(sqlTemplate.formatted(forceIndexHint), expectedRows, parameters);
        return new QueryMeasurement(label, noIndexPlan, forcedIndexPlan, noIndexLatency, forcedIndexLatency);
    }

    private QueryMeasurement measureCountQuery(
            String label,
            String noIndexHint,
            String forceIndexHint,
            String sqlTemplate,
            long expectedCount,
            Object... parameters) {

        ExplainRow noIndexPlan = explain(sqlTemplate.formatted(noIndexHint), parameters);
        ExplainRow forcedIndexPlan = explain(sqlTemplate.formatted(forceIndexHint), parameters);
        LatencyStats noIndexLatency = measureCountLatency(sqlTemplate.formatted(noIndexHint), expectedCount, parameters);
        LatencyStats forcedIndexLatency = measureCountLatency(sqlTemplate.formatted(forceIndexHint), expectedCount, parameters);
        return new QueryMeasurement(label, noIndexPlan, forcedIndexPlan, noIndexLatency, forcedIndexLatency);
    }

    private ExplainRow explain(String sql, Object... parameters) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("EXPLAIN " + sql, parameters);
        return rows.stream()
                .map(this::toExplainRow)
                .findFirst()
                .orElseThrow(() -> new AssertionError("EXPLAIN row was not found: " + rows));
    }

    private LatencyStats measureListLatency(String sql, int expectedRows, Object... parameters) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            jdbcTemplate.queryForList(sql, parameters);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
            long finishedAt = System.nanoTime();
            assertThat(rows).hasSize(expectedRows);
            elapsedNanos.add(finishedAt - startedAt);
        }
        return LatencyStats.from(elapsedNanos);
    }

    private LatencyStats measureCountLatency(String sql, long expectedCount, Object... parameters) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            jdbcTemplate.queryForObject(sql, Long.class, parameters);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters);
            long finishedAt = System.nanoTime();
            assertThat(count).isEqualTo(expectedCount);
            elapsedNanos.add(finishedAt - startedAt);
        }
        return LatencyStats.from(elapsedNanos);
    }

    private void assertImproved(QueryMeasurement measurement) {
        assertThat(measurement.forcedIndexPlan().keyName())
                .as("%s should use the forced composite index.", measurement.label())
                .startsWith("idx_");
        assertThat(measurement.forcedIndexPlan().accessType())
                .as("%s should use index lookup/range access.", measurement.label())
                .isIn("ref", "range");
        assertThat(measurement.forcedIndexPlan().rowsEstimate())
                .as("%s should expose a positive InnoDB estimate for diagnostics.", measurement.label())
                .isPositive();
        assertThat(measurement.forcedIndexLatency().p95Nanos())
                .as("%s should have lower local Docker p95 latency with the composite index.", measurement.label())
                .isLessThan(measurement.noIndexLatency().p95Nanos());
    }

    private String noIndexHint(String tableName, String... candidateIndexNames) {
        List<String> existingIndexes = List.of(candidateIndexNames).stream()
                .filter(indexName -> indexExists(tableName, indexName))
                .toList();

        assertThat(existingIndexes)
                .as("The no-index baseline for %s should ignore at least one relevant index.", tableName)
                .isNotEmpty();
        return "IGNORE INDEX (" + String.join(", ", existingIndexes) + ")";
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        """,
                Integer.class,
                tableName,
                indexName);
        return count != null && count > 0;
    }

    private List<String> indexColumns(String tableName, String indexName) {
        return jdbcTemplate.queryForList("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        ORDER BY seq_in_index
                        """,
                String.class,
                tableName,
                indexName);
    }

    private ExplainRow toExplainRow(Map<String, Object> row) {
        return new ExplainRow(
                value(row, "table"),
                value(row, "type"),
                value(row, "key"),
                number(row.get("rows"))
        );
    }

    private String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private record SeedUsers(Long targetUserId, Long otherUserId) {
    }

    private record ExplainRow(
            String tableName,
            String accessType,
            String keyName,
            long rowsEstimate
    ) {
    }

    private record QueryMeasurement(
            String label,
            ExplainRow noIndexPlan,
            ExplainRow forcedIndexPlan,
            LatencyStats noIndexLatency,
            LatencyStats forcedIndexLatency
    ) {
        String summary() {
            return ("ignoreIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                    "forceIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                    "p95Improvement=%.2fx").formatted(
                    noIndexPlan.accessType(),
                    noIndexPlan.keyName(),
                    noIndexPlan.rowsEstimate(),
                    noIndexLatency.p50Millis(),
                    noIndexLatency.p95Millis(),
                    noIndexLatency.maxMillis(),
                    forcedIndexPlan.accessType(),
                    forcedIndexPlan.keyName(),
                    forcedIndexPlan.rowsEstimate(),
                    forcedIndexLatency.p50Millis(),
                    forcedIndexLatency.p95Millis(),
                    forcedIndexLatency.maxMillis(),
                    noIndexLatency.p95Millis() / forcedIndexLatency.p95Millis());
        }
    }

    private record LatencyStats(
            long p50Nanos,
            long p95Nanos,
            long maxNanos
    ) {
        static LatencyStats from(List<Long> elapsedNanos) {
            List<Long> sorted = elapsedNanos.stream()
                    .sorted()
                    .toList();
            return new LatencyStats(
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    sorted.get(sorted.size() - 1)
            );
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        double p50Millis() {
            return nanosToMillis(p50Nanos);
        }

        double p95Millis() {
            return nanosToMillis(p95Nanos);
        }

        double maxMillis() {
            return nanosToMillis(maxNanos);
        }

        private double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}

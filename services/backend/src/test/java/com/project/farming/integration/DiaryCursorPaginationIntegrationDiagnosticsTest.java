package com.project.farming.integration;

import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.service.DiaryService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.pagination.CreatedAtIdCursor;
import com.project.farming.global.response.CursorPageResponse;
import jakarta.persistence.EntityManager;
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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class DiaryCursorPaginationIntegrationDiagnosticsTest {

    private static final int TARGET_DIARY_COUNT = 100_000;
    private static final int OTHER_DIARY_COUNT = 20_000;
    private static final int PAGE_SIZE = 20;
    private static final int DEEP_OFFSET = 80_000;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int SAMPLE_ITERATIONS = 25;
    private static final int SEED_BATCH_SIZE = 2_000;
    private static final int SAME_TIMESTAMP_GROUP_SIZE = 4;
    private static final LocalDateTime BASE_CREATED_AT = LocalDateTime.of(2026, 7, 10, 12, 0);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DiaryService diaryService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void keysetCursorShouldRemainStableAndAvoidDeepOffsetScan() {
        User targetUser = persistUser("cursor-target");
        User otherUser = persistUser("cursor-other");
        batchInsertDiaries(targetUser.getUserId(), otherUser.getUserId());

        List<Long> baselineTopForty = latestDiaryIds(targetUser.getUserId(), PAGE_SIZE * 2);
        CursorPageResponse<DiaryResponse> firstPage = diaryService.getDiaryFeedByUser(
                targetUser, null, PAGE_SIZE);
        assertThat(firstPage.content()).extracting(DiaryResponse::getDiaryId)
                .containsExactlyElementsOf(baselineTopForty.subList(0, PAGE_SIZE));
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        insertDiary(targetUser.getUserId(), "concurrent-newest", BASE_CREATED_AT.plusDays(1));
        CursorPageResponse<DiaryResponse> secondPage = diaryService.getDiaryFeedByUser(
                targetUser, firstPage.nextCursor(), PAGE_SIZE);

        List<Long> firstPageIds = firstPage.content().stream().map(DiaryResponse::getDiaryId).toList();
        List<Long> secondPageIds = secondPage.content().stream().map(DiaryResponse::getDiaryId).toList();
        assertThat(secondPageIds).containsExactlyElementsOf(baselineTopForty.subList(PAGE_SIZE, PAGE_SIZE * 2));
        assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);

        CreatedAtIdCursor deepCursor = deepCursor(targetUser.getUserId());
        String offsetSql = """
                SELECT diary_id, created_at
                FROM diaries FORCE INDEX (idx_diary_user_created)
                WHERE user_id = ?
                ORDER BY created_at DESC, diary_id DESC
                LIMIT 20 OFFSET 80000
                """;
        String keysetSql = """
                SELECT diary_id, created_at
                FROM diaries FORCE INDEX (idx_diary_user_created)
                WHERE user_id = ?
                  AND (
                       created_at < ?
                       OR (created_at = ? AND diary_id < ?)
                  )
                ORDER BY created_at DESC, diary_id DESC
                LIMIT 20
                """;
        String tupleKeysetSql = """
                SELECT diary_id, created_at
                FROM diaries FORCE INDEX (idx_diary_user_created)
                WHERE user_id = ?
                  AND (created_at, diary_id) < (?, ?)
                ORDER BY created_at DESC, diary_id DESC
                LIMIT 20
                """;

        QueryMeasurement offset = measure(offsetSql, targetUser.getUserId());
        QueryMeasurement tupleKeyset = measure(
                tupleKeysetSql,
                targetUser.getUserId(),
                Timestamp.valueOf(deepCursor.createdAt()),
                deepCursor.id());
        QueryMeasurement keyset = measure(
                keysetSql,
                targetUser.getUserId(),
                Timestamp.valueOf(deepCursor.createdAt()),
                Timestamp.valueOf(deepCursor.createdAt()),
                deepCursor.id());

        DatabaseEnvironment environment = databaseEnvironment();
        System.out.printf(
                "Diary pagination dataset[target=%d,other=%d,duplicateTimestampGroup=%d,pageSize=%d,depth=%d], "
                        + "mysql[%s], offset[%s], tupleKeyset[%s], expandedKeyset[%s], p95Improvement=%.2fx%n",
                TARGET_DIARY_COUNT,
                OTHER_DIARY_COUNT,
                SAME_TIMESTAMP_GROUP_SIZE,
                PAGE_SIZE,
                DEEP_OFFSET,
                environment,
                offset.summary(),
                tupleKeyset.summary(),
                keyset.summary(),
                (double) offset.latency().p95Nanos() / keyset.latency().p95Nanos());
        System.out.printf(
                "Offset EXPLAIN ANALYZE:%n%s%nTuple Keyset EXPLAIN ANALYZE:%n%s%nExpanded Keyset EXPLAIN ANALYZE:%n%s%n",
                offset.explainAnalyze(), tupleKeyset.explainAnalyze(), keyset.explainAnalyze());

        assertThat(tupleKeyset.ids()).containsExactlyElementsOf(offset.ids());
        assertThat(keyset.ids()).containsExactlyElementsOf(offset.ids());
        assertThat(keyset.handlerReads())
                .as("An optimizer-visible created_at range should beat the row-constructor baseline on MySQL 8.4.")
                .isLessThan(tupleKeyset.handlerReads());
        assertThat(keyset.handlerReads())
                .as("Keyset should not walk and discard every row before the deep page.")
                .isLessThan(offset.handlerReads());
        assertThat(keyset.latency().p95Nanos())
                .as("Keyset should have lower local Docker p95 at the same deep position.")
                .isLessThan(offset.latency().p95Nanos());

    }

    private User persistUser(String label) {
        String suffix = label + "-" + Long.toUnsignedString(System.nanoTime(), 36);
        User user = User.builder()
                .email(suffix + "@example.test")
                .password("encoded-password")
                .nickname(suffix.substring(0, Math.min(40, suffix.length())))
                .oauthProvider("LOCAL")
                .oauthId(suffix)
                .role(UserRole.USER)
                .subscriptionStatus("ACTIVE")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private void batchInsertDiaries(Long targetUserId, Long otherUserId) {
        int totalRows = TARGET_DIARY_COUNT + OTHER_DIARY_COUNT;
        for (int batchStart = 0; batchStart < totalRows; batchStart += SEED_BATCH_SIZE) {
            int firstRow = batchStart;
            int rowsInBatch = Math.min(SEED_BATCH_SIZE, totalRows - batchStart);
            jdbcTemplate.batchUpdate("""
                            INSERT INTO diaries (
                                user_id, title, content, diary_date, created_at, updated_at,
                                watered, pruned, fertilized, version
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement statement, int index) throws SQLException {
                            int globalIndex = firstRow + index;
                            boolean target = globalIndex < TARGET_DIARY_COUNT;
                            int sequence = target ? globalIndex : globalIndex - TARGET_DIARY_COUNT;
                            LocalDateTime createdAt = BASE_CREATED_AT.minusSeconds(
                                    sequence / SAME_TIMESTAMP_GROUP_SIZE);
                            statement.setLong(1, target ? targetUserId : otherUserId);
                            statement.setString(2, (target ? "target-" : "other-") + sequence);
                            statement.setString(3, "deterministic large-dataset diary");
                            statement.setDate(4, Date.valueOf(createdAt.toLocalDate()));
                            statement.setTimestamp(5, Timestamp.valueOf(createdAt));
                            statement.setTimestamp(6, Timestamp.valueOf(createdAt));
                            statement.setBoolean(7, sequence % 3 == 0);
                            statement.setBoolean(8, sequence % 5 == 0);
                            statement.setBoolean(9, sequence % 7 == 0);
                            statement.setLong(10, 0L);
                        }

                        @Override
                        public int getBatchSize() {
                            return rowsInBatch;
                        }
                    });
        }
    }

    private void insertDiary(Long userId, String title, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO diaries (
                            user_id, title, content, diary_date, created_at, updated_at,
                            watered, pruned, fertilized, version
                        ) VALUES (?, ?, ?, ?, ?, ?, false, false, false, 0)
                        """,
                userId,
                title,
                "concurrent insert",
                Date.valueOf(createdAt.toLocalDate()),
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt));
    }

    private List<Long> latestDiaryIds(Long userId, int limit) {
        return jdbcTemplate.queryForList("""
                        SELECT diary_id
                        FROM diaries FORCE INDEX (idx_diary_user_created)
                        WHERE user_id = ?
                        ORDER BY created_at DESC, diary_id DESC
                        LIMIT ?
                        """,
                Long.class,
                userId,
                limit);
    }

    private CreatedAtIdCursor deepCursor(Long userId) {
        return jdbcTemplate.queryForObject("""
                        SELECT created_at, diary_id
                        FROM diaries FORCE INDEX (idx_diary_user_created)
                        WHERE user_id = ?
                        ORDER BY created_at DESC, diary_id DESC
                        LIMIT 1 OFFSET ?
                        """,
                (resultSet, rowNumber) -> new CreatedAtIdCursor(
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        resultSet.getLong("diary_id")),
                userId,
                DEEP_OFFSET - 1);
    }

    private QueryMeasurement measure(String sql, Object... parameters) {
        String explainAnalyze = explainAnalyze(sql, parameters);
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            queryIds(sql, parameters);
        }

        List<Long> elapsedNanos = new ArrayList<>(SAMPLE_ITERATIONS);
        for (int index = 0; index < SAMPLE_ITERATIONS; index++) {
            long startedAt = System.nanoTime();
            List<Long> ids = queryIds(sql, parameters);
            long finishedAt = System.nanoTime();
            assertThat(ids).hasSize(PAGE_SIZE);
            elapsedNanos.add(finishedAt - startedAt);
        }

        long handlerReadsBefore = handlerReads();
        List<Long> ids = queryIds(sql, parameters);
        long handlerReads = handlerReads() - handlerReadsBefore;
        return new QueryMeasurement(
                ids,
                LatencyStats.from(elapsedNanos),
                handlerReads,
                explainAnalyze);
    }

    private List<Long> queryIds(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getLong("diary_id"), parameters);
    }

    private String explainAnalyze(String sql, Object... parameters) {
        return jdbcTemplate.query(
                "EXPLAIN ANALYZE " + sql,
                resultSet -> {
                    StringBuilder plan = new StringBuilder();
                    while (resultSet.next()) {
                        if (!plan.isEmpty()) {
                            plan.append(System.lineSeparator());
                        }
                        plan.append(resultSet.getString(1));
                    }
                    return plan.toString();
                },
                parameters);
    }

    private long handlerReads() {
        return jdbcTemplate.query("""
                        SHOW SESSION STATUS
                        WHERE Variable_name IN (
                            'Handler_read_first', 'Handler_read_key', 'Handler_read_next',
                            'Handler_read_prev', 'Handler_read_rnd', 'Handler_read_rnd_next'
                        )
                        """,
                resultSet -> {
                    long total = 0;
                    while (resultSet.next()) {
                        total += resultSet.getLong("Value");
                    }
                    return total;
                });
    }

    private DatabaseEnvironment databaseEnvironment() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT VERSION() AS version,
                       @@default_storage_engine AS engine,
                       @@transaction_isolation AS isolation_level,
                       @@innodb_buffer_pool_size AS buffer_pool_size
                """);
        return new DatabaseEnvironment(
                String.valueOf(row.get("version")),
                String.valueOf(row.get("engine")),
                String.valueOf(row.get("isolation_level")),
                ((Number) row.get("buffer_pool_size")).longValue());
    }

    private record QueryMeasurement(
            List<Long> ids,
            LatencyStats latency,
            long handlerReads,
            String explainAnalyze) {

        String summary() {
            return "handlerReads=%d,p50=%.3fms,p95=%.3fms,max=%.3fms"
                    .formatted(
                            handlerReads,
                            latency.p50Nanos() / 1_000_000.0,
                            latency.p95Nanos() / 1_000_000.0,
                            latency.maxNanos() / 1_000_000.0);
        }
    }

    private record LatencyStats(long p50Nanos, long p95Nanos, long maxNanos) {

        static LatencyStats from(List<Long> elapsedNanos) {
            long[] sorted = elapsedNanos.stream().mapToLong(Long::longValue).sorted().toArray();
            return new LatencyStats(
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    Arrays.stream(sorted).max().orElseThrow());
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1);
            return sorted[index];
        }
    }

    private record DatabaseEnvironment(
            String version,
            String engine,
            String isolationLevel,
            long bufferPoolBytes) {
    }
}

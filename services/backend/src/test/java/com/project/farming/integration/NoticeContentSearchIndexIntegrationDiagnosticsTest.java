package com.project.farming.integration;

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
import java.sql.Types;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class NoticeContentSearchIndexIntegrationDiagnosticsTest {

    private static final int NOISE_NOTICE_COUNT = 30_000;
    private static final int TARGET_NOTICE_COUNT = 40;
    private static final int PAGE_SIZE = 20;
    private static final int LATENCY_WARMUP_ITERATIONS = 5;
    private static final int LATENCY_SAMPLE_ITERATIONS = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void noticeContentPrefixSearchShouldUseContentIndexOnLargeSeed() {
        String targetPrefix = "notice-content-hit-" + System.nanoTime();
        seedNoticeRows(targetPrefix);

        List<String> indexColumns = indexColumns("notices", "idx_notice_content");
        long targetRows = countTargetRows(targetPrefix);
        ExplainRow noContentIndexPlan = explainNoticeSearch("IGNORE INDEX (idx_notice_content)", targetPrefix);
        ExplainRow contentIndexPlan = explainNoticeSearch("FORCE INDEX (idx_notice_content)", targetPrefix);
        LatencyStats noContentIndexLatency = measureNoticeSearchLatency(
                "IGNORE INDEX (idx_notice_content)",
                targetPrefix
        );
        LatencyStats contentIndexLatency = measureNoticeSearchLatency(
                "FORCE INDEX (idx_notice_content)",
                targetPrefix
        );

        System.out.printf(
                "Notice content prefix index measurement: targetRows=%d, " +
                        "ignoreContentIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                        "forceContentIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                        "p95Improvement=%.2fx%n",
                targetRows,
                noContentIndexPlan.accessType(),
                noContentIndexPlan.keyName(),
                noContentIndexPlan.rowsEstimate(),
                noContentIndexLatency.p50Millis(),
                noContentIndexLatency.p95Millis(),
                noContentIndexLatency.maxMillis(),
                contentIndexPlan.accessType(),
                contentIndexPlan.keyName(),
                contentIndexPlan.rowsEstimate(),
                contentIndexLatency.p50Millis(),
                contentIndexLatency.p95Millis(),
                contentIndexLatency.maxMillis(),
                noContentIndexLatency.p95Millis() / contentIndexLatency.p95Millis()
        );

        assertThat(indexColumns)
                .as("Notice content prefix search needs a B-Tree index on notices.content.")
                .containsExactly("content");
        assertThat(targetRows).isEqualTo(TARGET_NOTICE_COUNT);
        assertThat(noContentIndexPlan.keyName())
                .as("The baseline should not use idx_notice_content.")
                .doesNotContain("idx_notice_content");
        assertThat(contentIndexPlan.keyName())
                .as("The optimized plan should use idx_notice_content for prefix range access.")
                .contains("idx_notice_content");
        assertThat(contentIndexPlan.accessType())
                .as("The optimized plan should be range/index style access, not a full scan.")
                .isIn("range", "index");
        assertThat(contentIndexLatency.p95Nanos())
                .as("The indexed prefix search should have lower local Docker p95 latency.")
                .isLessThan(noContentIndexLatency.p95Nanos());
    }

    private void seedNoticeRows(String targetPrefix) {
        LocalDateTime now = LocalDateTime.now();
        batchInsertNoiseNotices(targetPrefix, now);
        batchInsertTargetNotices(targetPrefix, now);
    }

    private void batchInsertNoiseNotices(String targetPrefix, LocalDateTime now) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO notices (
                            title,
                            content,
                            is_sent,
                            sent_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        bindNotice(
                                ps,
                                "notice-noise-title-" + targetPrefix + "-" + i,
                                "noise-content-" + i + "-" + targetPrefix,
                                now
                        );
                    }

                    @Override
                    public int getBatchSize() {
                        return NOISE_NOTICE_COUNT;
                    }
                });
    }

    private void batchInsertTargetNotices(String targetPrefix, LocalDateTime now) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO notices (
                            title,
                            content,
                            is_sent,
                            sent_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        bindNotice(
                                ps,
                                "notice-hit-title-" + targetPrefix + "-" + i,
                                targetPrefix + "-body-" + i,
                                now
                        );
                    }

                    @Override
                    public int getBatchSize() {
                        return TARGET_NOTICE_COUNT;
                    }
                });
    }

    private void bindNotice(
            PreparedStatement ps,
            String title,
            String content,
            LocalDateTime now
    ) throws SQLException {
        Timestamp timestamp = Timestamp.valueOf(now);
        ps.setString(1, title);
        ps.setString(2, content);
        ps.setBoolean(3, false);
        ps.setNull(4, Types.TIMESTAMP);
        ps.setTimestamp(5, timestamp);
        ps.setTimestamp(6, timestamp);
    }

    private long countTargetRows(String targetPrefix) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM notices
                        WHERE content LIKE ?
                        """,
                Long.class,
                targetPrefix + "%");
        return count == null ? 0L : count;
    }

    private ExplainRow explainNoticeSearch(String indexHint, String targetPrefix) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        EXPLAIN
                        SELECT notice_id, title, content, is_sent, sent_at, created_at, updated_at
                        FROM notices %s
                        WHERE content LIKE ?
                        ORDER BY notice_id ASC
                        LIMIT %d
                        """.formatted(indexHint, PAGE_SIZE),
                targetPrefix + "%");

        return rows.stream()
                .map(this::toExplainRow)
                .filter(row -> row.tableName().equals("notices"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("notices EXPLAIN row was not found: " + rows));
    }

    private LatencyStats measureNoticeSearchLatency(String indexHint, String targetPrefix) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            executeNoticeSearch(indexHint, targetPrefix);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            List<Map<String, Object>> rows = executeNoticeSearch(indexHint, targetPrefix);
            long finishedAt = System.nanoTime();
            assertThat(rows).hasSize(PAGE_SIZE);
            elapsedNanos.add(finishedAt - startedAt);
        }

        return LatencyStats.from(elapsedNanos);
    }

    private List<Map<String, Object>> executeNoticeSearch(String indexHint, String targetPrefix) {
        return jdbcTemplate.queryForList("""
                        SELECT notice_id, title, content, is_sent, sent_at, created_at, updated_at
                        FROM notices %s
                        WHERE content LIKE ?
                        ORDER BY notice_id ASC
                        LIMIT %d
                        """.formatted(indexHint, PAGE_SIZE),
                targetPrefix + "%");
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

    private record ExplainRow(
            String tableName,
            String accessType,
            String keyName,
            long rowsEstimate
    ) {
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
            return p50Nanos / 1_000_000.0;
        }

        double p95Millis() {
            return p95Nanos / 1_000_000.0;
        }

        double maxMillis() {
            return maxNanos / 1_000_000.0;
        }
    }
}

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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class NoticeNormalizationTradeoffIntegrationDiagnosticsTest {

    private static final int NOTICE_COUNT = 200;
    private static final int RECIPIENT_COUNT = 500;
    private static final int TOTAL_RECIPIENT_ROWS = NOTICE_COUNT * RECIPIENT_COUNT;
    private static final int FEED_USER_ID = 250;
    private static final int PAGE_SIZE = 20;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int SAMPLE_ITERATIONS = 25;
    private static final LocalDateTime BASE_CREATED_AT = LocalDateTime.of(2026, 7, 10, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void shouldQuantifyNormalizedRecipientAndDenormalizedSnapshotTradeoffs() {
        createTemporaryTables();
        try {
            seedNoticesAndRecipients();

            assertThat(rowCount("portfolio_notice_recipient_normalized")).isEqualTo(TOTAL_RECIPIENT_ROWS);
            assertThat(rowCount("portfolio_notification_snapshot")).isEqualTo(TOTAL_RECIPIENT_ROWS);

            String normalizedSql = """
                    SELECT recipient.notification_id,
                           notice.title,
                           notice.content AS message,
                           recipient.is_read,
                           recipient.created_at
                    FROM portfolio_notice_recipient_normalized recipient
                    FORCE INDEX (idx_portfolio_normalized_user_created)
                    JOIN portfolio_notice_source notice ON notice.notice_id = recipient.notice_id
                    WHERE recipient.user_id = ?
                    ORDER BY recipient.created_at DESC, recipient.notification_id DESC
                    LIMIT 20
                    """;
            String snapshotSql = """
                    SELECT notification_id, title, message, is_read, created_at
                    FROM portfolio_notification_snapshot
                    FORCE INDEX (idx_portfolio_snapshot_user_created)
                    WHERE user_id = ?
                    ORDER BY created_at DESC, notification_id DESC
                    LIMIT 20
                    """;

            QueryMeasurement normalized = measure(normalizedSql, FEED_USER_ID);
            QueryMeasurement snapshot = measure(snapshotSql, FEED_USER_ID);
            assertThat(normalized.rows()).isEqualTo(snapshot.rows()).isEqualTo(PAGE_SIZE);

            long normalizedTextBytes = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(OCTET_LENGTH(title) + OCTET_LENGTH(content)), 0)
                    FROM portfolio_notice_source
                    """, Long.class);
            long duplicatedSnapshotTextBytes = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(OCTET_LENGTH(title) + OCTET_LENGTH(message)), 0)
                    FROM portfolio_notification_snapshot
                    """, Long.class);

            String revisedContent = fixedText("revised-content-", 512);
            int normalizedUpdateRows = jdbcTemplate.update("""
                    UPDATE portfolio_notice_source
                    SET content = ?
                    WHERE notice_id = 100
                    """, revisedContent);
            int snapshotPropagationRows = jdbcTemplate.update("""
                    UPDATE portfolio_notification_snapshot
                    SET message = ?
                    WHERE notice_id = 100
                    """, revisedContent);

            double textDuplicationRatio = (double) duplicatedSnapshotTextBytes / normalizedTextBytes;
            System.out.printf(
                    "Notice normalization dataset[notices=%d,recipients=%d,totalRecipientRows=%d,pageSize=%d], "
                            + "normalized[%s,textBytes=%d,contentUpdateRows=%d], "
                            + "snapshot[%s,duplicatedTextBytes=%d,livePropagationRows=%d], "
                            + "textDuplication=%.2fx%n",
                    NOTICE_COUNT,
                    RECIPIENT_COUNT,
                    TOTAL_RECIPIENT_ROWS,
                    PAGE_SIZE,
                    normalized.summary(),
                    normalizedTextBytes,
                    normalizedUpdateRows,
                    snapshot.summary(),
                    duplicatedSnapshotTextBytes,
                    snapshotPropagationRows,
                    textDuplicationRatio);
            System.out.printf(
                    "Normalized EXPLAIN ANALYZE:%n%s%nSnapshot EXPLAIN ANALYZE:%n%s%n",
                    normalized.explainAnalyze(),
                    snapshot.explainAnalyze());

            assertThat(normalizedUpdateRows).isOne();
            assertThat(snapshotPropagationRows).isEqualTo(RECIPIENT_COUNT);
            assertThat(textDuplicationRatio).isEqualTo(RECIPIENT_COUNT);
            assertThat(snapshot.handlerReads())
                    .as("A self-contained snapshot feed should avoid Notice PK lookups per result row.")
                    .isLessThan(normalized.handlerReads());
        } finally {
            dropTemporaryTables();
        }
    }

    private void createTemporaryTables() {
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE portfolio_notice_source (
                    notice_id BIGINT NOT NULL PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    content TEXT NOT NULL,
                    created_at DATETIME(6) NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE portfolio_recipient_number (
                    user_id BIGINT NOT NULL PRIMARY KEY
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE portfolio_notice_recipient_normalized (
                    notification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    notice_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    is_read BOOLEAN NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    INDEX idx_portfolio_normalized_user_created (user_id, created_at)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE portfolio_notification_snapshot (
                    notification_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    notice_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    message TEXT NOT NULL,
                    is_read BOOLEAN NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    INDEX idx_portfolio_snapshot_user_created (user_id, created_at)
                ) ENGINE=InnoDB
                """);
    }

    private void seedNoticesAndRecipients() {
        jdbcTemplate.batchUpdate(
                "INSERT INTO portfolio_recipient_number (user_id) VALUES (?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        statement.setLong(1, index + 1L);
                    }

                    @Override
                    public int getBatchSize() {
                        return RECIPIENT_COUNT;
                    }
                });

        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO portfolio_notice_source (notice_id, title, content, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        long noticeId = index + 1L;
                        statement.setLong(1, noticeId);
                        statement.setString(2, fixedText("notice-title-" + noticeId + "-", 80));
                        statement.setString(3, fixedText("notice-content-" + noticeId + "-", 512));
                        statement.setTimestamp(4, Timestamp.valueOf(BASE_CREATED_AT.plusSeconds(noticeId)));
                    }

                    @Override
                    public int getBatchSize() {
                        return NOTICE_COUNT;
                    }
                });

        jdbcTemplate.update("""
                INSERT INTO portfolio_notice_recipient_normalized (notice_id, user_id, is_read, created_at)
                SELECT notice.notice_id,
                       recipient.user_id,
                       MOD(notice.notice_id + recipient.user_id, 3) = 0,
                       notice.created_at
                FROM portfolio_notice_source notice
                CROSS JOIN portfolio_recipient_number recipient
                ORDER BY notice.notice_id, recipient.user_id
                """);
        jdbcTemplate.update("""
                INSERT INTO portfolio_notification_snapshot (
                    notice_id, user_id, title, message, is_read, created_at
                )
                SELECT notice.notice_id,
                       recipient.user_id,
                       notice.title,
                       notice.content,
                       MOD(notice.notice_id + recipient.user_id, 3) = 0,
                       notice.created_at
                FROM portfolio_notice_source notice
                CROSS JOIN portfolio_recipient_number recipient
                ORDER BY notice.notice_id, recipient.user_id
                """);
    }

    private QueryMeasurement measure(String sql, Object... parameters) {
        String explainAnalyze = explainAnalyze(sql, parameters);
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            queryRows(sql, parameters);
        }

        List<Long> elapsedNanos = new ArrayList<>(SAMPLE_ITERATIONS);
        for (int index = 0; index < SAMPLE_ITERATIONS; index++) {
            long startedAt = System.nanoTime();
            int rows = queryRows(sql, parameters);
            long finishedAt = System.nanoTime();
            assertThat(rows).isEqualTo(PAGE_SIZE);
            elapsedNanos.add(finishedAt - startedAt);
        }

        long handlerReadsBefore = handlerReads();
        int rows = queryRows(sql, parameters);
        long handlerReads = handlerReads() - handlerReadsBefore;
        return new QueryMeasurement(rows, LatencyStats.from(elapsedNanos), handlerReads, explainAnalyze);
    }

    private int queryRows(String sql, Object... parameters) {
        return jdbcTemplate.queryForList(sql, parameters).size();
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

    private long rowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private String fixedText(String prefix, int length) {
        if (prefix.length() >= length) {
            return prefix.substring(0, length);
        }
        return prefix + "x".repeat(length - prefix.length());
    }

    private void dropTemporaryTables() {
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS portfolio_notification_snapshot");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS portfolio_notice_recipient_normalized");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS portfolio_recipient_number");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS portfolio_notice_source");
    }

    private record QueryMeasurement(
            int rows,
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
}

package com.project.farming.integration;

import com.project.farming.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class NoticeFanoutConcurrencyIntegrationDiagnosticsTest {

    private static final int ADDED_RECIPIENT_COUNT = 100;
    private static final int WRITERS_PER_ROUND = 8;
    private static final int ROUNDS = 10;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void concurrentNoticeFanoutShouldBeIdempotentWithoutDuplicateOrFailedWriters() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        long noticeIdBase = Long.MAX_VALUE - 10_000_000_000L - Math.floorMod(System.nanoTime(), 1_000_000L);
        ExecutorService executor = Executors.newFixedThreadPool(WRITERS_PER_ROUND);
        List<Long> noticeIds = new ArrayList<>();

        try {
            batchInsertRecipients(suffix);
            long eligibleRecipients = eligibleRecipientCount();
            List<AttemptResult> allAttempts = new ArrayList<>(ROUNDS * WRITERS_PER_ROUND);

            for (int round = 0; round < ROUNDS; round++) {
                long noticeId = noticeIdBase + round;
                noticeIds.add(noticeId);
                List<AttemptResult> roundAttempts = runConcurrentRound(executor, noticeId, round);
                allAttempts.addAll(roundAttempts);

                assertThat(roundAttempts).allSatisfy(result -> assertThat(result.failure()).isNull());
                assertThat(notificationCount(noticeId)).isEqualTo(eligibleRecipients);
                assertThat(duplicateRecipientCount(noticeId)).isZero();
            }

            LatencyStats latency = LatencyStats.from(
                    allAttempts.stream().map(AttemptResult::elapsedNanos).toList());
            long failedAttempts = allAttempts.stream().filter(result -> result.failure() != null).count();
            long totalRows = noticeIds.stream().mapToLong(this::notificationCount).sum();

            System.out.printf(
                    "Notice concurrent fan-out dataset[addedRecipients=%d,eligibleRecipients=%d,rounds=%d,writersPerRound=%d], "
                            + "attempts=%d,completedAttempts=%d,failedAttempts=%d,totalRows=%d,duplicates=0, "
                            + "latency[p50=%.3fms,p95=%.3fms,max=%.3fms]%n",
                    ADDED_RECIPIENT_COUNT,
                    eligibleRecipients,
                    ROUNDS,
                    WRITERS_PER_ROUND,
                    allAttempts.size(),
                    allAttempts.size() - failedAttempts,
                    failedAttempts,
                    totalRows,
                    latency.p50Nanos() / 1_000_000.0,
                    latency.p95Nanos() / 1_000_000.0,
                    latency.maxNanos() / 1_000_000.0);

            assertThat(failedAttempts).isZero();
            assertThat(totalRows).isEqualTo(eligibleRecipients * ROUNDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            deleteDiagnosticRows(noticeIds, suffix);
        }
    }

    private List<AttemptResult> runConcurrentRound(
            ExecutorService executor,
            long noticeId,
            int round) throws Exception {
        CountDownLatch ready = new CountDownLatch(WRITERS_PER_ROUND);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttemptResult>> futures = new ArrayList<>(WRITERS_PER_ROUND);

        for (int writer = 0; writer < WRITERS_PER_ROUND; writer++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                long startedAt = System.nanoTime();
                try {
                    notificationService.saveNotice(
                            noticeId,
                            "동시 공지 " + round,
                            "동일 noticeId에 대한 동시 fan-out은 한 번만 반영되어야 합니다.");
                    return new AttemptResult(System.nanoTime() - startedAt, null);
                } catch (Throwable failure) {
                    return new AttemptResult(System.nanoTime() - startedAt, failure);
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<AttemptResult> results = new ArrayList<>(WRITERS_PER_ROUND);
        for (Future<AttemptResult> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
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
                        String identity = "fanout-race-" + suffix + "-" + index;
                        statement.setString(1, identity + "@example.test");
                        statement.setString(2, "encoded-password");
                        statement.setString(3, "fanout-race-" + index);
                        statement.setString(4, "LOCAL");
                        statement.setString(5, identity);
                        statement.setString(6, "USER");
                        statement.setString(7, "fanout-race-token-" + suffix + "-" + index);
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

    private long notificationCount(long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE notice_id = ?",
                Long.class,
                noticeId);
    }

    private long duplicateRecipientCount(long noticeId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) - COUNT(DISTINCT user_id)
                FROM notification
                WHERE notice_id = ?
                """, Long.class, noticeId);
    }

    private void deleteDiagnosticRows(List<Long> noticeIds, String suffix) {
        for (Long noticeId : noticeIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE notice_id = ?", noticeId);
        }
        jdbcTemplate.update(
                "DELETE FROM users WHERE oauth_provider = 'LOCAL' AND oauth_id LIKE ?",
                "fanout-race-" + suffix + "-%");
    }

    private record AttemptResult(long elapsedNanos, Throwable failure) {
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

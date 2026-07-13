package com.project.farming.domain.userplant.service;

import com.zaxxer.hikari.HikariDataSource;
import com.project.farming.domain.notification.outbox.FcmOutboxProcessor;
import com.project.farming.domain.notification.service.NotificationService;
import com.project.farming.global.fcm.FcmBatchMessage;
import com.project.farming.global.fcm.FcmBatchResult;
import com.project.farming.global.fcm.FcmService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest(properties = "app.fcm.outbox.worker.enabled=false")
@ActiveProfiles({"test", "integration"})
class UserPlantCareBatchPerformanceIntegrationDiagnosticsTest {

    private static final int DATASET_SIZE = 100_000;
    private static final int COMPARISON_DATASET_SIZE = 5_000;
    private static final int PRODUCER_CHUNK_SIZE = 1_000;
    private static final int FCM_BATCH_SIZE = 500;
    private static final String EMAIL_PREFIX = "care-100k-diagnostic-";

    @Autowired
    private UserPlantCareJobService jobService;

    @Autowired
    private FcmOutboxProcessor processor;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CareNotificationBatchWriter batchWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HikariDataSource dataSource;

    @MockBean
    private FcmService fcmService;

    private String runPrefix;

    @BeforeEach
    void setUp() {
        cleanup();
        runPrefix = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 8) + "-";
        ReflectionTestUtils.setField(jobService, "batchSize", PRODUCER_CHUNK_SIZE);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void oneHundredThousandPersonalizedNotificationsShouldBeMaterializedAndDrainedInBatches() {
        LocalDate executionDate = LocalDate.of(2026, 7, 11);
        seedUsers(DATASET_SIZE);
        seedDueUserPlants(executionDate);
        long lowerUserId = queryUserId("MIN");
        long upperUserId = queryUserId("MAX");

        long producerStartedAt = System.nanoTime();
        UserPlantCareJobService.CareNotificationJobResult producerResult;
        int producerMaxActiveConnections;
        try (ConnectionUsageSampler sampler = new ConnectionUsageSampler(dataSource)) {
            producerResult = jobService.processTaskRows(
                    executionDate,
                    "daily",
                    false,
                    lowerUserId - 1,
                    upperUserId
            );
            producerMaxActiveConnections = sampler.maxActiveConnections();
        }
        long producerMillis = elapsedMillis(producerStartedAt);

        assertThat(producerResult.processedUsers()).isEqualTo(DATASET_SIZE);
        assertThat(producerResult.createdOutboxes()).isEqualTo(DATASET_SIZE);
        assertThat(producerResult.failedUsers()).isZero();
        assertThat(producerResult.batches()).isEqualTo(DATASET_SIZE / PRODUCER_CHUNK_SIZE);
        assertThat(producerResult.lastScannedUserId()).isEqualTo(upperUserId);
        assertThat(countNotifications()).isEqualTo(DATASET_SIZE);
        assertThat(countOutboxes()).isEqualTo(DATASET_SIZE);
        long backlogBeforeDrain = countOutboxesByStatus("PENDING");
        assertThat(backlogBeforeDrain).isEqualTo(DATASET_SIZE);

        int expectedLogicalDatabaseCalls = producerResult.batches() * 5;
        assertThat(expectedLogicalDatabaseCalls).isEqualTo(500);

        makeDiagnosticOutboxesFirstInQueue();
        when(fcmService.sendBatch(anyList())).thenAnswer(invocation -> {
            List<FcmBatchMessage> messages = invocation.getArgument(0);
            return messages.stream()
                    .map(message -> FcmBatchResult.success(message.correlationId()))
                    .toList();
        });

        long drainStartedAt = System.nanoTime();
        int drained = 0;
        int fcmBatches = 0;
        int drainMaxActiveConnections;
        try (ConnectionUsageSampler sampler = new ConnectionUsageSampler(dataSource)) {
            while (drained < DATASET_SIZE) {
                int processed = processor.processBatch(FCM_BATCH_SIZE, 5);
                assertThat(processed).isPositive();
                drained += processed;
                fcmBatches++;
            }
            drainMaxActiveConnections = sampler.maxActiveConnections();
        }
        long drainMillis = elapsedMillis(drainStartedAt);

        assertThat(drained).isEqualTo(DATASET_SIZE);
        assertThat(fcmBatches).isEqualTo(DATASET_SIZE / FCM_BATCH_SIZE);
        assertThat(countSentOutboxes()).isEqualTo(DATASET_SIZE);
        assertThat(countOutboxesByStatus("PENDING")).isZero();
        assertThat(Duration.ofMillis(producerMillis)).isLessThan(Duration.ofMinutes(2));
        assertThat(Duration.ofMillis(drainMillis)).isLessThan(Duration.ofMinutes(2));

        System.out.printf(
                "CARE_NOTIFICATION_100K producerMs=%d producerUsersPerSec=%.2f "
                        + "drainMs=%d drainRowsPerSec=%.2f producerBatches=%d fcmBatches=%d "
                        + "logicalDbCalls=%d producerMaxActiveConnections=%d "
                        + "drainMaxActiveConnections=%d backlogBeforeDrain=%d backlogAfterDrain=%d%n",
                producerMillis,
                perSecond(DATASET_SIZE, producerMillis),
                drainMillis,
                perSecond(DATASET_SIZE, drainMillis),
                producerResult.batches(),
                fcmBatches,
                expectedLogicalDatabaseCalls,
                producerMaxActiveConnections,
                drainMaxActiveConnections,
                backlogBeforeDrain,
                countOutboxesByStatus("PENDING")
        );
    }

    @Test
    void jdbcBatchShouldOutperformPerUserTransactionalNotificationCreation() {
        seedUsers(COMPARISON_DATASET_SIZE);
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM users WHERE email LIKE ? ORDER BY user_id",
                Long.class,
                runPrefix + "%"
        );

        long baselineStartedAt = System.nanoTime();
        for (Long userId : userIds) {
            notificationService.createAndSendNotification(
                    userId,
                    "[care-transaction-baseline]",
                    "개별 트랜잭션 기준 알림"
            );
        }
        long baselineMillis = elapsedMillis(baselineStartedAt);
        assertThat(countDeliveriesByTitle("[care-transaction-baseline]"))
                .isEqualTo(COMPARISON_DATASET_SIZE);

        deleteDeliveriesByTitle("[care-transaction-baseline]");
        List<CareNotificationPayload> payloads = userIds.stream()
                .map(userId -> new CareNotificationPayload(
                        userId,
                        "care-batch-comparison:" + runPrefix + userId,
                        "[care-jdbc-batch]",
                        "JDBC batch 기준 알림"
                ))
                .toList();

        long batchStartedAt = System.nanoTime();
        int created = 0;
        for (int start = 0; start < payloads.size(); start += PRODUCER_CHUNK_SIZE) {
            int end = Math.min(start + PRODUCER_CHUNK_SIZE, payloads.size());
            created += batchWriter.write(payloads.subList(start, end));
        }
        long batchMillis = elapsedMillis(batchStartedAt);

        assertThat(created).isEqualTo(COMPARISON_DATASET_SIZE);
        assertThat(countDeliveriesByTitle("[care-jdbc-batch]"))
                .isEqualTo(COMPARISON_DATASET_SIZE);
        assertThat(batchMillis).isLessThan(baselineMillis);

        System.out.printf(
                "CARE_NOTIFICATION_DB_COMPARISON rows=%d baselineMs=%d batchMs=%d improvement=%.2fx%n",
                COMPARISON_DATASET_SIZE,
                baselineMillis,
                batchMillis,
                baselineMillis / (double) Math.max(1L, batchMillis)
        );
    }

    private void seedUsers(int datasetSize) {
        List<Integer> indexes = IntStream.range(0, datasetSize).boxed().toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO users (
                    email, password, nickname, role, fcm_token, subscription_status, credential_version
                ) VALUES (?, '{noop}password', ?, 'USER', ?, 'FREE', 0)
                """,
                indexes,
                PRODUCER_CHUNK_SIZE,
                (statement, index) -> {
                    statement.setString(1, runPrefix + index + "@example.com");
                    statement.setString(2, "care100k-" + index);
                    statement.setString(3, "care-100k-token-" + index);
                }
        );
    }

    private void seedDueUserPlants(LocalDate executionDate) {
        jdbcTemplate.update("""
                INSERT INTO user_plants (
                    version, user_id, plant_id, plant_name, plant_nickname,
                    farm_id, planting_place, planted_date, notes,
                    is_notification_enabled,
                    water_interval_days, next_watering_date, watered,
                    prune_interval_days, next_pruning_date, pruned,
                    fertilize_interval_days, next_fertilizing_date, fertilized,
                    user_plant_image_file_id, created_at, updated_at, deleted
                )
                SELECT
                    0, u.user_id, 1, '몬스테라', CONCAT('care-', u.user_id),
                    1, '성능 진단', NOW(), NULL,
                    TRUE,
                    1, ?, FALSE,
                    365, ?, FALSE,
                    365, ?, FALSE,
                    1, NOW(), NOW(), FALSE
                FROM users u
                WHERE u.email LIKE ?
                """,
                executionDate.minusDays(1),
                executionDate.plusDays(365),
                executionDate.plusDays(365),
                runPrefix + "%"
        );
    }

    private void makeDiagnosticOutboxesFirstInQueue() {
        jdbcTemplate.update("""
                UPDATE fcm_outbox o
                JOIN notification n
                  ON o.source_type = 'NOTIFICATION'
                 AND n.notification_id = o.source_id
                JOIN users u ON u.user_id = n.user_id
                SET o.next_retry_at = '2000-01-01 00:00:00'
                WHERE u.email LIKE ?
                """, runPrefix + "%");
    }

    private long queryUserId(String aggregate) {
        String sql = "SELECT " + aggregate + "(user_id) FROM users WHERE email LIKE ?";
        return jdbcTemplate.queryForObject(sql, Long.class, runPrefix + "%");
    }

    private long countNotifications() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification n
                JOIN users u ON u.user_id = n.user_id
                WHERE u.email LIKE ?
                """, Long.class, runPrefix + "%");
    }

    private long countOutboxes() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                  AND o.source_type = 'NOTIFICATION'
                """, Long.class, runPrefix + "%");
    }

    private long countSentOutboxes() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                  AND o.source_type = 'NOTIFICATION'
                  AND o.status = 'SENT'
                """, Long.class, runPrefix + "%");
    }

    private long countOutboxesByStatus(String status) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                  AND o.source_type = 'NOTIFICATION'
                  AND o.status = ?
                """, Long.class, runPrefix + "%", status);
    }

    private long countDeliveriesByTitle(String title) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                  AND o.source_type = 'NOTIFICATION'
                  AND o.title = ?
                """, Long.class, runPrefix + "%", title);
    }

    private void deleteDeliveriesByTitle(String title) {
        jdbcTemplate.update("""
                DELETE o FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                  AND o.source_type = 'NOTIFICATION'
                  AND o.title = ?
                """, runPrefix + "%", title);
        jdbcTemplate.update("""
                DELETE n FROM notification n
                JOIN users u ON u.user_id = n.user_id
                WHERE u.email LIKE ?
                  AND n.title = ?
                """, runPrefix + "%", title);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private double perSecond(long rows, long millis) {
        return rows * 1000.0 / Math.max(1L, millis);
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE o FROM fcm_outbox o
                JOIN users u ON u.user_id = o.user_id
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE n FROM notification n
                JOIN users u ON u.user_id = n.user_id
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE up FROM user_plants up
                JOIN users u ON u.user_id = up.user_id
                WHERE u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    private static final class ConnectionUsageSampler implements AutoCloseable {

        private final HikariDataSource dataSource;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger maxActiveConnections = new AtomicInteger();
        private final Thread samplerThread;

        private ConnectionUsageSampler(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            samplerThread = new Thread(this::sample, "care-notification-connection-sampler");
            samplerThread.setDaemon(true);
            samplerThread.start();
        }

        private void sample() {
            while (running.get()) {
                maxActiveConnections.accumulateAndGet(
                        dataSource.getHikariPoolMXBean().getActiveConnections(),
                        Math::max
                );
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private int maxActiveConnections() {
            maxActiveConnections.accumulateAndGet(
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    Math::max
            );
            return maxActiveConnections.get();
        }

        @Override
        public void close() {
            running.set(false);
            samplerThread.interrupt();
            try {
                samplerThread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

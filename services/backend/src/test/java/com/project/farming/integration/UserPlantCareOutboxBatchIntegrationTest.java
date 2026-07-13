package com.project.farming.integration;

import com.project.farming.domain.notification.outbox.FcmOutbox;
import com.project.farming.domain.notification.outbox.FcmOutboxBatchStore;
import com.project.farming.domain.notification.outbox.FcmOutboxDispatch;
import com.project.farming.domain.notification.outbox.FcmOutboxProcessor;
import com.project.farming.domain.notification.outbox.FcmOutboxRepository;
import com.project.farming.domain.notification.outbox.FcmOutboxStatus;
import com.project.farming.domain.userplant.repository.UserPlantCareTaskRow;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import com.project.farming.domain.userplant.service.CareNotificationBatchWriter;
import com.project.farming.domain.userplant.service.CareNotificationPayload;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest(properties = "app.fcm.outbox.worker.enabled=false")
@ActiveProfiles({"test", "integration"})
class UserPlantCareOutboxBatchIntegrationTest {

    private static final String EMAIL_PREFIX = "care-pipeline-it-";
    private static final String TEST_TITLE = "[care-pipeline-integration]";

    @Autowired
    private CareNotificationBatchWriter batchWriter;

    @Autowired
    private FcmOutboxBatchStore batchStore;

    @Autowired
    private FcmOutboxProcessor processor;

    @Autowired
    private FcmOutboxRepository outboxRepository;

    @Autowired
    private UserPlantRepository userPlantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FcmService fcmService;

    @BeforeEach
    void cleanBefore() {
        cleanup();
    }

    @AfterEach
    void cleanAfter() {
        cleanup();
    }

    @Test
    void batchWriterShouldAtomicallyCreateOneNotificationAndOutboxPerEligibleEvent() {
        Long activeUserId = insertUser("active", "token-active", "FREE");
        Long withdrawnUserId = insertUser("withdrawn", null, "WITHDRAWN");
        String activeEventKey = eventKey("active");
        String withdrawnEventKey = eventKey("withdrawn");
        List<CareNotificationPayload> payloads = List.of(
                new CareNotificationPayload(
                        activeUserId, activeEventKey, TEST_TITLE, "몬스테라 물주기"),
                new CareNotificationPayload(
                        withdrawnUserId, withdrawnEventKey, TEST_TITLE, "선인장 영양제")
        );

        batchWriter.write(payloads);
        batchWriter.write(payloads);

        assertThat(count("SELECT COUNT(*) FROM notification WHERE event_key = ?", activeEventKey))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE event_key = ?", withdrawnEventKey))
                .isZero();
        assertThat(count("""
                SELECT COUNT(*)
                FROM fcm_outbox o
                JOIN notification n ON n.notification_id = o.source_id
                WHERE n.event_key = ?
                """, activeEventKey)).isEqualTo(1);
    }

    @Test
    void concurrentBatchWritersShouldCreateOneLogicalNotificationAndOutbox() throws Exception {
        Long userId = insertUser("writer-race", "token-writer-race", "FREE");
        String eventKey = eventKey("writer-race");
        List<CareNotificationPayload> payloads = List.of(new CareNotificationPayload(
                userId,
                eventKey,
                TEST_TITLE,
                "동시 writer 알림"
        ));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return batchWriter.write(payloads);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return batchWriter.write(payloads);
            });
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(count("SELECT COUNT(*) FROM notification WHERE event_key = ?", eventKey))
                    .isEqualTo(1);
            assertThat(count("""
                    SELECT COUNT(*)
                    FROM fcm_outbox o
                    JOIN notification n ON n.notification_id = o.source_id
                    WHERE n.event_key = ?
                    """, eventKey)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void repositoryShouldSelectEligibleUsersByKeysetAndReturnAllTheirDueTasks() {
        LocalDate executionDate = LocalDate.of(2026, 7, 11);
        Long activeUserId = insertUser("keyset-active", "token-keyset-active", "FREE");
        Long disabledUserId = insertUser("keyset-disabled", "token-keyset-disabled", "FREE");
        Long withdrawnUserId = insertUser("keyset-withdrawn", "token-keyset-withdrawn", "WITHDRAWN");
        insertUserPlant(activeUserId, "몬스테라", "잎이", true, executionDate.minusDays(1), executionDate.plusDays(10));
        insertUserPlant(activeUserId, "장미", "장미", true, executionDate.plusDays(10), executionDate.minusDays(1));
        insertUserPlant(disabledUserId, "선인장", "가시", false, executionDate.minusDays(1), executionDate.plusDays(10));
        insertUserPlant(withdrawnUserId, "토마토", "방울", true, executionDate.minusDays(1), executionDate.plusDays(10));

        List<Long> userIds = userPlantRepository.findDueCareUserIdsAfter(
                activeUserId - 1,
                withdrawnUserId,
                executionDate,
                PageRequest.of(0, 10)
        );
        List<UserPlantCareTaskRow> rows = userPlantRepository.findDueCareTaskRowsByUserIds(
                userIds,
                executionDate
        );

        assertThat(userIds).containsExactly(activeUserId);
        assertThat(rows)
                .hasSize(2)
                .extracting(UserPlantCareTaskRow::plantNickname)
                .containsExactly("잎이", "장미");
        assertThat(rows.get(0).wateringDue()).isTrue();
        assertThat(rows.get(1).pruningDue()).isTrue();
    }

    @Test
    void processorShouldUseLatestTokenCancelWithdrawnRecipientAndPersistPartialResults() {
        Long successUserId = insertUser("success", "token-success", "FREE");
        Long retryUserId = insertUser("retry", "token-retry-old", "FREE");
        Long failedUserId = insertUser("failed", "token-failed", "FREE");
        Long withdrawnUserId = insertUser("cancelled", "token-cancelled", "FREE");

        FcmOutbox success = saveOutbox(successUserId, 910_001L);
        FcmOutbox retry = saveOutbox(retryUserId, 910_002L);
        FcmOutbox failed = saveOutbox(failedUserId, 910_003L);
        FcmOutbox cancelled = saveOutbox(withdrawnUserId, 910_004L);
        jdbcTemplate.update(
                "UPDATE users SET fcm_token = ? WHERE user_id = ?",
                "token-retry-new",
                retryUserId
        );
        jdbcTemplate.update(
                "UPDATE users SET subscription_status = 'WITHDRAWN', fcm_token = NULL WHERE user_id = ?",
                withdrawnUserId
        );

        when(fcmService.sendBatch(anyList())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            List<FcmBatchMessage> messages = invocation.getArgument(0);
            assertThat(messages)
                    .extracting(FcmBatchMessage::targetToken)
                    .containsExactly("token-success", "token-retry-new", "token-failed")
                    .doesNotContain("token-cancelled", "token-retry-old");
            return List.of(
                    FcmBatchResult.success(success.getFcmOutboxId()),
                    FcmBatchResult.failure(retry.getFcmOutboxId(), false, "temporary outage"),
                    FcmBatchResult.failure(failed.getFcmOutboxId(), true, "unregistered")
            );
        });

        int processed = processor.processBatch(500, 5);

        assertThat(processed).isEqualTo(3);
        assertThat(status(success.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.SENT);
        assertThat(status(retry.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.PENDING);
        assertThat(status(failed.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.FAILED);
        assertThat(status(cancelled.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.CANCELLED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_token FROM fcm_outbox WHERE fcm_outbox_id = ?",
                String.class,
                retry.getFcmOutboxId()
        )).isEqualTo("token-retry-new");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM fcm_outbox WHERE fcm_outbox_id = ?",
                Integer.class,
                retry.getFcmOutboxId()
        )).isEqualTo(1);
    }

    @Test
    void concurrentWorkersShouldClaimDisjointOutboxRows() throws Exception {
        for (int index = 0; index < 20; index++) {
            Long userId = insertUser("concurrent-" + index, "token-concurrent-" + index, "FREE");
            saveOutbox(userId, 920_000L + index);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<FcmOutboxDispatch>> first = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return batchStore.claimDueBatch(10);
            });
            Future<List<FcmOutboxDispatch>> second = executor.submit(() -> {
                start.await(3, TimeUnit.SECONDS);
                return batchStore.claimDueBatch(10);
            });
            start.countDown();

            List<FcmOutboxDispatch> firstClaim = first.get(5, TimeUnit.SECONDS);
            List<FcmOutboxDispatch> secondClaim = second.get(5, TimeUnit.SECONDS);
            Set<Long> firstIds = ids(firstClaim);
            Set<Long> secondIds = ids(secondClaim);
            Set<Long> intersection = new HashSet<>(firstIds);
            intersection.retainAll(secondIds);
            List<FcmOutboxDispatch> remainingClaim = batchStore.claimDueBatch(20);
            Set<Long> allClaimedIds = new HashSet<>(firstIds);
            allClaimedIds.addAll(secondIds);
            allClaimedIds.addAll(ids(remainingClaim));

            assertThat(firstClaim.size() + secondClaim.size())
                    .as("SKIP LOCKED may return an empty claim under contention, but must make progress")
                    .isPositive();
            assertThat(intersection).isEmpty();
            assertThat(allClaimedIds).hasSize(20);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void staleWorkerCompletionShouldNotOverwriteReclaimedOutbox() {
        Long userId = insertUser("stale-worker", "token-stale-worker", "FREE");
        FcmOutbox outbox = saveOutbox(userId, 930_001L);

        FcmOutboxDispatch firstClaim = batchStore.claimDueBatch(1).get(0);
        jdbcTemplate.update(
                "UPDATE fcm_outbox SET locked_at = ? WHERE fcm_outbox_id = ?",
                LocalDateTime.now().minusMinutes(20),
                outbox.getFcmOutboxId()
        );
        assertThat(batchStore.requeueExpiredProcessingJobs(10)).isEqualTo(1);
        FcmOutboxDispatch secondClaim = batchStore.claimDueBatch(1).get(0);

        batchStore.completeBatch(
                List.of(firstClaim),
                List.of(FcmBatchResult.success(firstClaim.fcmOutboxId())),
                5
        );

        assertThat(status(outbox.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.PROCESSING);
        batchStore.completeBatch(
                List.of(secondClaim),
                List.of(FcmBatchResult.success(secondClaim.fcmOutboxId())),
                5
        );
        assertThat(status(outbox.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.SENT);
    }

    @Test
    void providerSuccessWithoutPersistedCompletionShouldBeRetriedWithSameEventId() {
        Long userId = insertUser("provider-db-gap", "token-provider-db-gap", "FREE");
        FcmOutbox outbox = saveOutbox(userId, 940_001L);
        AtomicInteger sends = new AtomicInteger();
        java.util.List<String> eventIds = new java.util.ArrayList<>();
        when(fcmService.sendBatch(anyList())).thenAnswer(invocation -> {
            List<FcmBatchMessage> messages = invocation.getArgument(0);
            eventIds.add(messages.get(0).eventId());
            if (sends.getAndIncrement() == 0) {
                jdbcTemplate.update("""
                        UPDATE fcm_outbox
                        SET status = 'PENDING', locked_at = NULL, next_retry_at = CURRENT_TIMESTAMP(6)
                        WHERE fcm_outbox_id = ?
                        """, outbox.getFcmOutboxId());
            }
            return List.of(FcmBatchResult.success(outbox.getFcmOutboxId()));
        });

        assertThat(processor.processBatch(1, 5)).isEqualTo(1);
        assertThat(status(outbox.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.PENDING);
        assertThat(processor.processBatch(1, 5)).isEqualTo(1);

        assertThat(status(outbox.getFcmOutboxId())).isEqualTo(FcmOutboxStatus.SENT);
        assertThat(sends).hasValue(2);
        assertThat(eventIds).hasSize(2).doesNotContainNull().allMatch(eventIds.get(0)::equals);
    }

    private Long insertUser(String suffix, String token, String subscriptionStatus) {
        String unique = suffix + "-" + UUID.randomUUID();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (
                        email, password, nickname, role, fcm_token, subscription_status, credential_version
                    ) VALUES (?, ?, ?, 'USER', ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, EMAIL_PREFIX + unique + "@example.com");
            statement.setString(2, "{noop}password");
            statement.setString(3, unique.substring(0, Math.min(50, unique.length())));
            statement.setString(4, token);
            statement.setString(5, subscriptionStatus);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private FcmOutbox saveOutbox(Long userId, Long sourceId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notification (
                        user_id, event_key, title, message, is_read, created_at
                    ) VALUES (?, ?, ?, '내용', FALSE, CURRENT_TIMESTAMP(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, "care-pipeline-integration:" + sourceId + ":" + UUID.randomUUID());
            statement.setString(3, TEST_TITLE);
            return statement;
        }, keyHolder);
        Long notificationId = keyHolder.getKey().longValue();
        return outboxRepository.saveAndFlush(FcmOutbox.notificationPush(
                notificationId,
                userId,
                "snapshot-token-" + userId,
                TEST_TITLE,
                "내용"
        ));
    }

    private void insertUserPlant(
            Long userId,
            String plantName,
            String plantNickname,
            boolean notificationEnabled,
            LocalDate nextWateringDate,
            LocalDate nextPruningDate) {
        jdbcTemplate.update("""
                INSERT INTO user_plants (
                    version, user_id, plant_id, plant_name, plant_nickname,
                    farm_id, planting_place, planted_date, notes,
                    is_notification_enabled,
                    water_interval_days, next_watering_date, watered,
                    prune_interval_days, next_pruning_date, pruned,
                    fertilize_interval_days, next_fertilizing_date, fertilized,
                    user_plant_image_file_id, created_at, updated_at, deleted
                ) VALUES (
                    0, ?, 1, ?, ?,
                    1, '통합 테스트', NOW(), NULL,
                    ?,
                    1, ?, FALSE,
                    1, ?, FALSE,
                    365, ?, FALSE,
                    1, NOW(), NOW(), FALSE
                )
                """,
                userId,
                plantName,
                plantNickname,
                notificationEnabled,
                nextWateringDate,
                nextPruningDate,
                nextWateringDate.plusDays(365)
        );
    }

    private String eventKey(String suffix) {
        return "care-pipeline-integration:" + suffix + ":" + UUID.randomUUID();
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private FcmOutboxStatus status(Long outboxId) {
        return FcmOutboxStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM fcm_outbox WHERE fcm_outbox_id = ?",
                String.class,
                outboxId
        ));
    }

    private Set<Long> ids(List<FcmOutboxDispatch> dispatches) {
        Set<Long> ids = new HashSet<>();
        dispatches.forEach(dispatch -> ids.add(dispatch.fcmOutboxId()));
        return ids;
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE o FROM fcm_outbox o
                LEFT JOIN users u ON u.user_id = o.user_id
                WHERE o.title = ? OR u.email LIKE ?
                """, TEST_TITLE, EMAIL_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE n FROM notification n
                LEFT JOIN users u ON u.user_id = n.user_id
                WHERE n.event_key LIKE 'care-pipeline-integration:%' OR u.email LIKE ?
                """, EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE up FROM user_plants up JOIN users u ON u.user_id = up.user_id WHERE u.email LIKE ?",
                EMAIL_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }
}

package com.project.farming.integration;

import com.project.farming.domain.notification.entity.Notice;
import com.project.farming.domain.notification.outbox.FcmOutboxRepository;
import com.project.farming.domain.notification.outbox.NoticeDeliveryInProgressException;
import com.project.farming.domain.notification.repository.NoticeRepository;
import com.project.farming.domain.notification.service.NoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class NoticeDeliveryConcurrencyIntegrationDiagnosticsTest {

    private static final String EMAIL_PREFIX = "notice-concurrency-diagnostic-";

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private FcmOutboxRepository fcmOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long noticeId;

    @AfterEach
    void cleanup() {
        if (noticeId != null) {
            jdbcTemplate.update("DELETE FROM fcm_outbox WHERE notice_id = ?", noticeId);
            jdbcTemplate.update("DELETE FROM notification WHERE notice_id = ?", noticeId);
            jdbcTemplate.update("DELETE FROM notices WHERE notice_id = ?", noticeId);
        }
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }

    @Test
    void deleteShouldWaitForClaimLockAndRejectProcessingNoticeDelivery() throws Exception {
        insertRecipient();
        Notice notice = noticeRepository.saveAndFlush(Notice.builder()
                .title("동시 삭제 진단")
                .content("발송 중 Notice는 삭제하지 않는다")
                .isSent(false)
                .sentAt(LocalDateTime.now())
                .build());
        noticeId = notice.getNoticeId();
        noticeService.sendNotice(noticeId);

        CountDownLatch workerLocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> worker = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var outboxes = fcmOutboxRepository.findByNoticeIdForUpdate(noticeId);
                assertThat(outboxes).isNotEmpty();
                jdbcTemplate.update(
                        "UPDATE fcm_outbox SET status = 'PROCESSING', locked_at = CURRENT_TIMESTAMP(6) "
                                + "WHERE notice_id = ?",
                        noticeId);
                workerLocked.countDown();
                await(releaseWorker);
            }));

            assertThat(workerLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> deletion = executor.submit(() -> {
                deleteStarted.countDown();
                noticeService.deleteNotice(noticeId);
            });
            assertThat(deleteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(deletion.isDone()).isFalse();

            releaseWorker.countDown();
            worker.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> deletion.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(NoticeDeliveryInProgressException.class);
            assertThat(noticeRepository.existsById(noticeId)).isTrue();
            assertThat(count("SELECT COUNT(*) FROM notification WHERE notice_id = ?", noticeId)).isPositive();
            assertThat(count("SELECT COUNT(*) FROM fcm_outbox WHERE notice_id = ?", noticeId)).isPositive();
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void sendAndDeleteShouldSerializeOnNoticeLockWithoutLeavingOrphans() throws Exception {
        insertRecipient();
        Notice notice = noticeRepository.saveAndFlush(Notice.builder()
                .title("발송 삭제 직렬화")
                .content("Notice 행 잠금으로 수명주기를 직렬화한다")
                .isSent(false)
                .sentAt(LocalDateTime.now())
                .build());
        noticeId = notice.getNoticeId();

        CountDownLatch senderLocked = new CountDownLatch(1);
        CountDownLatch releaseSender = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> sender = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                noticeRepository.findByIdForUpdate(noticeId).orElseThrow();
                senderLocked.countDown();
                await(releaseSender);
                noticeService.sendNotice(noticeId);
            }));

            assertThat(senderLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> deletion = executor.submit(() -> {
                deleteStarted.countDown();
                noticeService.deleteNotice(noticeId);
            });
            assertThat(deleteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(deletion.isDone()).isFalse();

            releaseSender.countDown();
            sender.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);

            assertThat(noticeRepository.existsById(noticeId)).isFalse();
            assertThat(count("SELECT COUNT(*) FROM notification WHERE notice_id = ?", noticeId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM fcm_outbox WHERE notice_id = ?", noticeId)).isZero();
        } finally {
            releaseSender.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void insertRecipient() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        jdbcTemplate.update("""
                INSERT INTO users (
                    email, password, nickname, role, fcm_token, subscription_status, credential_version
                ) VALUES (?, '{noop}password', ?, 'USER', ?, 'FREE', 0)
                """,
                EMAIL_PREFIX + suffix + "@example.test",
                "notice-lock",
                "notice-lock-token-" + suffix);
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0L : count;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating Notice delivery concurrency test");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Notice delivery concurrency test was interrupted", ex);
        }
    }
}

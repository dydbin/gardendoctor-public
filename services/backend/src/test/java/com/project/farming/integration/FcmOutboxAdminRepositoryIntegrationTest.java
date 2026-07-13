package com.project.farming.integration;

import com.project.farming.domain.notification.outbox.FcmOutbox;
import com.project.farming.domain.notification.outbox.FcmOutboxAdminFilter;
import com.project.farming.domain.notification.outbox.FcmOutboxAdminRow;
import com.project.farming.domain.notification.outbox.FcmOutboxAdminService;
import com.project.farming.domain.notification.outbox.FcmOutboxResponse;
import com.project.farming.domain.notification.outbox.FcmOutboxRepository;
import com.project.farming.domain.notification.outbox.FcmOutboxRetryAuditRepository;
import com.project.farming.domain.notification.outbox.FcmOutboxSourceType;
import com.project.farming.domain.notification.outbox.FcmOutboxStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class FcmOutboxAdminRepositoryIntegrationTest {

    @Autowired
    private FcmOutboxRepository fcmOutboxRepository;

    @Autowired
    private FcmOutboxAdminService fcmOutboxAdminService;

    @Autowired
    private FcmOutboxRetryAuditRepository fcmOutboxRetryAuditRepository;

    @Test
    @Transactional
    void failedOutboxAdminQueryRetryUpdateAndAuditInsertShouldRunAgainstMysql() {
        FcmOutbox saved = saveFailedNotificationOutbox(10_000L, 20_000L);

        Page<FcmOutboxAdminRow> failedRows = fcmOutboxRepository.findAdminRowsByStatus(
                FcmOutboxStatus.FAILED,
                PageRequest.of(0, 10)
        );

        assertThat(failedRows.getContent())
                .extracting(FcmOutboxAdminRow::fcmOutboxId)
                .contains(saved.getFcmOutboxId());

        fcmOutboxAdminService.retryFailedOutbox(saved.getFcmOutboxId(), 30_000L);

        FcmOutbox retried = fcmOutboxRepository.findById(saved.getFcmOutboxId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(FcmOutboxStatus.PENDING);
        assertThat(retried.getAttemptCount()).isZero();
        assertThat(retried.getLastError()).isNull();
        assertThat(retried.getSourceType()).isEqualTo(FcmOutboxSourceType.NOTIFICATION);

        assertThat(fcmOutboxRetryAuditRepository.findByFcmOutboxIdOrderByCreatedAtDesc(saved.getFcmOutboxId()))
                .singleElement()
                .satisfies(audit -> {
                    assertThat(audit.getAdminUserId()).isEqualTo(30_000L);
                    assertThat(audit.getPreviousStatus()).isEqualTo(FcmOutboxStatus.FAILED);
                    assertThat(audit.getResultStatus()).isEqualTo(FcmOutboxStatus.PENDING);
                    assertThat(audit.getReason()).isEqualTo("MANUAL_RETRY");
                });
    }

    @Test
    @Transactional
    void failedOutboxAdminFilterShouldRunAgainstMysql() {
        FcmOutbox matched = saveFailedNotificationOutbox(20_000L, 30_000L);
        saveFailedNotificationOutbox(20_001L, 30_001L);

        Page<FcmOutboxResponse> filteredRows = fcmOutboxAdminService.getFailedOutboxes(
                new FcmOutboxAdminFilter(FcmOutboxSourceType.NOTIFICATION, 20_000L, 30_000L),
                PageRequest.of(0, 10)
        );

        assertThat(filteredRows.getContent())
                .extracting(FcmOutboxResponse::fcmOutboxId)
                .contains(matched.getFcmOutboxId());
        assertThat(filteredRows.getContent())
                .allSatisfy(row -> {
                    assertThat(row.sourceType()).isEqualTo(FcmOutboxSourceType.NOTIFICATION);
                    assertThat(row.sourceId()).isEqualTo(20_000L);
                    assertThat(row.userId()).isEqualTo(30_000L);
                });
    }

    @Test
    void selectedBulkRetryShouldRollbackAllWhenAnySelectedRowIsInvalid() {
        long sourceIdBase = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1_000_000_000L;
        long userIdBase = sourceIdBase + 1_000_000_000L;
        FcmOutbox failedOne = saveFailedNotificationOutbox(sourceIdBase, userIdBase);
        FcmOutbox notFailed = fcmOutboxRepository.saveAndFlush(FcmOutbox.notificationPush(
                sourceIdBase + 1,
                userIdBase + 1,
                "token-" + UUID.randomUUID(),
                "제목",
                "내용"
        ));
        FcmOutbox failedTwo = saveFailedNotificationOutbox(sourceIdBase + 2, userIdBase + 2);

        try {
            assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutboxes(
                    List.of(failedOne.getFcmOutboxId(), notFailed.getFcmOutboxId(), failedTwo.getFcmOutboxId()),
                    50_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("실패 상태");

            assertThat(fcmOutboxRepository.findById(failedOne.getFcmOutboxId()).orElseThrow().getStatus())
                    .isEqualTo(FcmOutboxStatus.FAILED);
            assertThat(fcmOutboxRepository.findById(notFailed.getFcmOutboxId()).orElseThrow().getStatus())
                    .isEqualTo(FcmOutboxStatus.PENDING);
            assertThat(fcmOutboxRepository.findById(failedTwo.getFcmOutboxId()).orElseThrow().getStatus())
                    .isEqualTo(FcmOutboxStatus.FAILED);
            assertThat(fcmOutboxRetryAuditRepository.findByFcmOutboxIdOrderByCreatedAtDesc(failedOne.getFcmOutboxId()))
                    .isEmpty();
            assertThat(fcmOutboxRetryAuditRepository.findByFcmOutboxIdOrderByCreatedAtDesc(failedTwo.getFcmOutboxId()))
                    .isEmpty();
        } finally {
            fcmOutboxRepository.deleteAllByIdInBatch(List.of(
                    failedOne.getFcmOutboxId(),
                    notFailed.getFcmOutboxId(),
                    failedTwo.getFcmOutboxId()
            ));
        }
    }

    private FcmOutbox saveFailedNotificationOutbox(Long sourceId, Long userId) {
        FcmOutbox failedOutbox = FcmOutbox.notificationPush(
                sourceId,
                userId,
                "token-" + UUID.randomUUID(),
                "제목",
                "내용"
        );
        failedOutbox.markFailed("FCM send failed", LocalDateTime.now());
        return fcmOutboxRepository.saveAndFlush(failedOutbox);
    }
}

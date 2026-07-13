package com.project.farming.domain.notification.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.fcm.outbox.worker.enabled", havingValue = "true")
public class FcmOutboxWorker {

    private final FcmOutboxProcessor fcmOutboxProcessor;

    @Value("${app.fcm.outbox.worker.batch-size:500}")
    private int batchSize;

    @Value("${app.fcm.outbox.worker.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.fcm.outbox.worker.lock-timeout-minutes:10}")
    private long lockTimeoutMinutes;

    @Value("${app.fcm.outbox.worker.max-batches-per-run:20}")
    private int maxBatchesPerRun;

    @Scheduled(fixedDelayString = "${app.fcm.outbox.worker.fixed-delay-ms:1000}")
    public void processDueOutbox() {
        int requeued = fcmOutboxProcessor.requeueExpiredProcessingJobs(Math.max(1, lockTimeoutMinutes));
        if (requeued > 0) {
            log.warn("Requeued {} expired FCM outbox jobs.", requeued);
        }

        int safeBatchSize = Math.max(1, Math.min(500, batchSize));
        int batches = Math.max(1, maxBatchesPerRun);
        for (int index = 0; index < batches; index++) {
            int processed = fcmOutboxProcessor.processBatch(safeBatchSize, Math.max(1, maxAttempts));
            if (processed < safeBatchSize) {
                break;
            }
        }
    }
}

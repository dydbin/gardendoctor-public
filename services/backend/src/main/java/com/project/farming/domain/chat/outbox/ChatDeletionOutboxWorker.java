package com.project.farming.domain.chat.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.chat.deletion-worker.enabled", havingValue = "true")
public class ChatDeletionOutboxWorker {

    private final ChatDeletionOutboxRepository repository;
    private final ChatDeletionOutboxService outboxService;
    private final ChatDeletionOutboxProcessor processor;

    @Value("${app.chat.deletion-worker.batch-size:50}")
    private int batchSize;

    @Value("${app.chat.deletion-worker.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.chat.deletion-worker.lock-timeout-minutes:10}")
    private long lockTimeoutMinutes;

    @Scheduled(fixedDelayString = "${app.chat.deletion-worker.fixed-delay-ms:10000}")
    public void processDueDeletions() {
        int requeued = outboxService.requeueExpired(lockTimeoutMinutes);
        if (requeued > 0) {
            log.warn("Requeued {} expired chat deletion jobs", requeued);
        }

        List<Long> dueIds = repository.findDueIds(
                ChatDeletionOutboxStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, Math.max(1, batchSize))
        );
        dueIds.forEach(id -> processor.processOne(id, Math.max(1, maxAttempts)));
    }
}

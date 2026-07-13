package com.project.farming.domain.notification.outbox;

import com.project.farming.global.fcm.FcmBatchResult;
import com.project.farming.global.fcm.FcmSendException;
import com.project.farming.global.fcm.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Timer;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmOutboxProcessor {

    private final FcmOutboxBatchStore batchStore;
    private final FcmService fcmService;
    private final FcmOutboxMetrics metrics;

    public int requeueExpiredProcessingJobs(long lockTimeoutMinutes) {
        int requeued = batchStore.requeueExpiredProcessingJobs(Math.max(1, lockTimeoutMinutes));
        metrics.recordRequeued(requeued);
        return requeued;
    }

    public int processBatch(int batchSize, int maxAttempts) {
        List<FcmOutboxDispatch> dispatches = batchStore.claimDueBatch(batchSize);
        if (dispatches.isEmpty()) {
            return 0;
        }
        metrics.recordClaimed(dispatches.size());

        List<FcmBatchResult> results;
        Timer.Sample sendSample = metrics.startProviderSend();
        try {
            results = fcmService.sendBatch(
                    dispatches.stream()
                            .map(FcmOutboxDispatch::toBatchMessage)
                            .toList()
            );
        } catch (FcmSendException exception) {
            results = failedResults(dispatches, exception.isPermanentFailure(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Unexpected FCM batch failure: outboxCount={}", dispatches.size(), exception);
            results = failedResults(dispatches, false, "Unexpected FCM batch failure: " + exception.getMessage());
        }
        metrics.recordProviderResults(sendSample, results);

        try {
            batchStore.completeBatch(dispatches, results, Math.max(1, maxAttempts));
        } catch (RuntimeException exception) {
            metrics.recordPersistenceFailure(dispatches.size());
            throw exception;
        }
        return dispatches.size();
    }

    private List<FcmBatchResult> failedResults(
            List<FcmOutboxDispatch> dispatches,
            boolean permanentFailure,
            String errorMessage) {
        return dispatches.stream()
                .map(dispatch -> FcmBatchResult.failure(
                        dispatch.fcmOutboxId(),
                        permanentFailure,
                        errorMessage
                ))
                .toList();
    }
}

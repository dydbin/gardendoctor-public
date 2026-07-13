package com.project.farming.domain.notification.outbox;

import com.project.farming.global.fcm.FcmBatchResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FcmOutboxMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter claimedCounter;
    private final Counter requeuedCounter;
    private final Counter persistenceFailureCounter;
    private final Timer providerSendTimer;

    public FcmOutboxMetrics(MeterRegistry meterRegistry, FcmOutboxRepository repository) {
        this.meterRegistry = meterRegistry;
        this.claimedCounter = Counter.builder("gardendoctor.fcm.outbox.claimed")
                .description("Number of FCM outbox rows claimed for delivery")
                .register(meterRegistry);
        this.requeuedCounter = Counter.builder("gardendoctor.fcm.outbox.requeued")
                .description("Number of expired PROCESSING rows returned to PENDING")
                .register(meterRegistry);
        this.persistenceFailureCounter = Counter.builder("gardendoctor.fcm.outbox.persistence.failures")
                .description("Number of claimed rows whose provider result could not be persisted")
                .register(meterRegistry);
        this.providerSendTimer = Timer.builder("gardendoctor.fcm.outbox.provider.send")
                .description("FCM provider batch call duration")
                .publishPercentileHistogram()
                .register(meterRegistry);

        registerBacklogGauge(repository, FcmOutboxStatus.PENDING);
        registerBacklogGauge(repository, FcmOutboxStatus.PROCESSING);
        registerBacklogGauge(repository, FcmOutboxStatus.FAILED);
    }

    public void recordClaimed(int count) {
        claimedCounter.increment(count);
    }

    public void recordRequeued(int count) {
        requeuedCounter.increment(count);
    }

    public Timer.Sample startProviderSend() {
        return Timer.start(meterRegistry);
    }

    public void recordProviderResults(Timer.Sample sample, List<FcmBatchResult> results) {
        sample.stop(providerSendTimer);
        for (FcmBatchResult result : results) {
            String outcome = result.successful()
                    ? "accepted"
                    : result.permanentFailure() ? "permanent_failure" : "retryable_failure";
            Counter.builder("gardendoctor.fcm.outbox.provider.results")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordPersistenceFailure(int count) {
        persistenceFailureCounter.increment(count);
    }

    private void registerBacklogGauge(FcmOutboxRepository repository, FcmOutboxStatus status) {
        Gauge.builder(
                        "gardendoctor.fcm.outbox.backlog",
                        repository,
                        value -> value.countByStatus(status))
                .description("Current FCM outbox rows by status")
                .tag("status", status.name().toLowerCase())
                .register(meterRegistry);
    }
}

package com.project.farming.domain.notification.outbox;

import com.project.farming.global.fcm.FcmBatchMessage;
import com.project.farming.global.fcm.FcmBatchResult;
import com.project.farming.global.fcm.FcmSendException;
import com.project.farming.global.fcm.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmOutboxProcessorTest {

    @Mock
    private FcmOutboxBatchStore batchStore;

    @Mock
    private FcmService fcmService;

    @Mock
    private FcmOutboxRepository fcmOutboxRepository;

    private SimpleMeterRegistry meterRegistry;

    private FcmOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        processor = new FcmOutboxProcessor(
                batchStore,
                fcmService,
                new FcmOutboxMetrics(meterRegistry, fcmOutboxRepository));
    }

    @Test
    void batchProcessingShouldClaimSendAndCompleteInOrder() {
        List<FcmOutboxDispatch> dispatches = List.of(
                dispatch(10L, 100L, "event-100"),
                dispatch(11L, 101L, "event-101")
        );
        List<FcmBatchResult> results = List.of(
                FcmBatchResult.success(10L),
                FcmBatchResult.failure(11L, true, "unregistered")
        );
        when(batchStore.claimDueBatch(500)).thenReturn(dispatches);
        when(fcmService.sendBatch(anyList())).thenReturn(results);

        int processed = processor.processBatch(500, 5);

        assertThat(processed).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FcmBatchMessage>> messageCaptor = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(batchStore, fcmService);
        order.verify(batchStore).claimDueBatch(500);
        order.verify(fcmService).sendBatch(messageCaptor.capture());
        order.verify(batchStore).completeBatch(dispatches, results, 5);
        assertThat(messageCaptor.getValue())
                .extracting(FcmBatchMessage::correlationId)
                .containsExactly(10L, 11L);
        assertThat(messageCaptor.getValue())
                .extracting(FcmBatchMessage::eventId)
                .containsExactly("event-100", "event-101");
        assertThat(meterRegistry.counter("gardendoctor.fcm.outbox.claimed").count()).isEqualTo(2);
        assertThat(meterRegistry.counter(
                "gardendoctor.fcm.outbox.provider.results", "outcome", "accepted").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "gardendoctor.fcm.outbox.provider.results", "outcome", "permanent_failure").count()).isEqualTo(1);
    }

    @Test
    void wholeBatchFailureShouldReturnRetryableResultForEveryClaimedRow() {
        List<FcmOutboxDispatch> dispatches = List.of(
                dispatch(20L, 200L, "event-200"),
                dispatch(21L, 201L, "event-201")
        );
        when(batchStore.claimDueBatch(500)).thenReturn(dispatches);
        when(fcmService.sendBatch(anyList())).thenThrow(
                FcmSendException.retryable("temporary outage", new RuntimeException("timeout")));

        int processed = processor.processBatch(500, 5);

        assertThat(processed).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FcmBatchResult>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchStore).completeBatch(
                org.mockito.ArgumentMatchers.eq(dispatches),
                resultCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(5)
        );
        assertThat(resultCaptor.getValue())
                .allSatisfy(result -> {
                    assertThat(result.successful()).isFalse();
                    assertThat(result.permanentFailure()).isFalse();
                    assertThat(result.errorMessage()).contains("temporary outage");
                });
    }

    @Test
    void emptyClaimShouldSkipFirebaseAndCompletion() {
        when(batchStore.claimDueBatch(500)).thenReturn(List.of());

        int processed = processor.processBatch(500, 5);

        assertThat(processed).isZero();
        verify(fcmService, never()).sendBatch(anyList());
        verify(batchStore, never()).completeBatch(anyList(), anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void processorMustNotOpenTransactionAroundExternalFcmCall() throws Exception {
        assertThat(FcmOutboxProcessor.class
                .getMethod("processBatch", int.class, int.class)
                .getAnnotation(Transactional.class))
                .isNull();
    }

    @Test
    void providerSuccessFollowedByPersistenceFailureShouldBeObservableAndPropagated() {
        List<FcmOutboxDispatch> dispatches = List.of(dispatch(30L, 300L, "event-300"));
        List<FcmBatchResult> results = List.of(FcmBatchResult.success(30L));
        when(batchStore.claimDueBatch(500)).thenReturn(dispatches);
        when(fcmService.sendBatch(anyList())).thenReturn(results);
        doThrow(new IllegalStateException("database unavailable"))
                .when(batchStore).completeBatch(dispatches, results, 5);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processBatch(500, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(meterRegistry.counter("gardendoctor.fcm.outbox.persistence.failures").count())
                .isEqualTo(1);
    }

    private FcmOutboxDispatch dispatch(Long outboxId, Long userId, String eventId) {
        return new FcmOutboxDispatch(
                outboxId,
                FcmOutboxSourceType.NOTIFICATION,
                1_000L + outboxId,
                null,
                userId,
                "token-" + userId,
                "제목",
                "내용",
                0,
                eventId,
                LocalDateTime.of(2026, 7, 11, 1, 0)
        );
    }
}

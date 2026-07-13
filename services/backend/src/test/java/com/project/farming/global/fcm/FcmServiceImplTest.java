package com.project.farming.global.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmServiceImplTest {

    @Mock
    private FirebaseMessagingGateway firebaseMessagingGateway;

    @Mock
    private BatchResponse batchResponse;

    @Mock
    private SendResponse successResponse;

    @Mock
    private SendResponse failedResponse;

    @Mock
    private FirebaseMessagingException unregisteredException;

    private FcmServiceImpl fcmService;

    @BeforeEach
    void setUp() {
        fcmService = new FcmServiceImpl(firebaseMessagingGateway);
    }

    @Test
    void sendBatchShouldMapPerRecipientSuccessAndPermanentFailure() throws Exception {
        when(firebaseMessagingGateway.sendEach(anyList())).thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(List.of(successResponse, failedResponse));
        when(successResponse.isSuccessful()).thenReturn(true);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.getException()).thenReturn(unregisteredException);
        when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(unregisteredException.getMessage()).thenReturn("registration token is not registered");

        List<FcmBatchResult> results = fcmService.sendBatch(List.of(
                message(1L),
                message(2L)
        ));

        assertThat(results).containsExactly(
                FcmBatchResult.success(1L),
                FcmBatchResult.failure(
                        2L,
                        true,
                        "FCM send failed [UNREGISTERED]: registration token is not registered"
                )
        );
    }

    @Test
    void sendBatchShouldRejectMoreThanFirebaseLimit() throws Exception {
        List<FcmBatchMessage> messages = LongStream.rangeClosed(1, 501)
                .mapToObj(this::message)
                .toList();

        assertThatThrownBy(() -> fcmService.sendBatch(messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");
        verify(firebaseMessagingGateway, never()).sendEach(anyList());
    }

    private FcmBatchMessage message(long id) {
        return new FcmBatchMessage(id, "event-" + id, "token-" + id, "제목", "내용");
    }
}

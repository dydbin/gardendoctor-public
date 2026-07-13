package com.project.farming.domain.chat.outbox;

import com.project.farming.domain.chat.service.ChatAiClient;
import com.project.farming.global.ai.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatDeletionOutboxProcessorTest {

    @Mock
    private ChatDeletionOutboxService outboxService;
    @Mock
    private ChatAiClient chatAiClient;
    @InjectMocks
    private ChatDeletionOutboxProcessor processor;

    @Test
    void successfulRemoteDeleteShouldMarkOutboxSent() {
        when(outboxService.claim(1L)).thenReturn(Optional.of(101L));

        processor.processOne(1L, 5);

        verify(chatAiClient).deleteSession(101L);
        verify(outboxService).markSent(1L);
        verify(outboxService, never()).markRetryOrFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void failedRemoteDeleteShouldRemainRetryable() {
        when(outboxService.claim(1L)).thenReturn(Optional.of(101L));
        doThrow(new AiServiceUnavailableException("offline", new IllegalStateException()))
                .when(chatAiClient).deleteSession(101L);

        processor.processOne(1L, 5);

        verify(outboxService).markRetryOrFailed(1L, "offline", 5);
        verify(outboxService, never()).markSent(1L);
    }
}

package com.project.farming.domain.chat.outbox;

import com.project.farming.domain.chat.service.ChatAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatDeletionOutboxProcessor {

    private final ChatDeletionOutboxService outboxService;
    private final ChatAiClient chatAiClient;

    public void processOne(Long outboxId, int maxAttempts) {
        outboxService.claim(outboxId).ifPresent(pythonSessionId -> {
            try {
                chatAiClient.deleteSession(pythonSessionId);
                outboxService.markSent(outboxId);
            } catch (RuntimeException ex) {
                outboxService.markRetryOrFailed(outboxId, ex.getMessage(), maxAttempts);
            }
        });
    }
}

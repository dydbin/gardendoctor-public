package com.project.farming.integration;

import com.project.farming.domain.chat.outbox.ChatDeletionOutbox;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxProcessor;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxRepository;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxService;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxStatus;
import com.project.farming.domain.chat.service.ChatAiClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class ChatDeletionOutboxIntegrationTest {

    @Autowired
    private ChatDeletionOutboxRepository repository;

    @Autowired
    private ChatDeletionOutboxService outboxService;

    @Autowired
    private ChatDeletionOutboxProcessor processor;

    @MockBean
    private ChatAiClient chatAiClient;

    @Test
    void enqueueClaimAndRemoteDeleteShouldBeDurableAndIdempotent() {
        Long pythonSessionId = 8_000_000L + System.nanoTime() % 100_000L;
        Long outboxId = null;
        try {
            outboxService.enqueue(pythonSessionId);
            outboxService.enqueue(pythonSessionId);
            ChatDeletionOutbox pending = repository.findByPythonSessionId(pythonSessionId).orElseThrow();
            outboxId = pending.getChatDeletionOutboxId();

            assertThat(repository.findAll().stream()
                    .filter(row -> row.getPythonSessionId().equals(pythonSessionId)))
                    .hasSize(1);

            processor.processOne(outboxId, 5);
            ChatDeletionOutbox sent = repository.findById(outboxId).orElseThrow();

            assertThat(sent.getStatus()).isEqualTo(ChatDeletionOutboxStatus.SENT);
            assertThat(outboxService.claim(outboxId)).isEqualTo(Optional.empty());
            verify(chatAiClient).deleteSession(pythonSessionId);
        } finally {
            if (outboxId != null) {
                repository.deleteById(outboxId);
            }
        }
    }
}

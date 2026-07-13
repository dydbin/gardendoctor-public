package com.project.farming.domain.chat.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatDeletionOutboxService {

    private final ChatDeletionOutboxRepository repository;

    @Transactional
    public void enqueue(Long pythonSessionId) {
        if (!repository.existsByPythonSessionId(pythonSessionId)) {
            repository.save(ChatDeletionOutbox.pending(pythonSessionId, LocalDateTime.now()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> claim(Long outboxId) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = repository.claim(
                outboxId,
                ChatDeletionOutboxStatus.PENDING,
                ChatDeletionOutboxStatus.PROCESSING,
                now
        );
        return claimed == 1
                ? repository.findClaimedPythonSessionId(outboxId, ChatDeletionOutboxStatus.PROCESSING)
                : Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long outboxId) {
        repository.findById(outboxId).ifPresent(outbox -> outbox.markSent(LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryOrFailed(Long outboxId, String error, int maxAttempts) {
        repository.findById(outboxId)
                .ifPresent(outbox -> outbox.markRetryOrFailed(error, maxAttempts, LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requeueExpired(long lockTimeoutMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return repository.requeueExpired(
                ChatDeletionOutboxStatus.PROCESSING,
                ChatDeletionOutboxStatus.PENDING,
                now.minusMinutes(Math.max(1, lockTimeoutMinutes)),
                now,
                "Processing lock expired"
        );
    }
}

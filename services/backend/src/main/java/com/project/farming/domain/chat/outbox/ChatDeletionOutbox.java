package com.project.farming.domain.chat.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_deletion_outbox",
        indexes = @Index(name = "idx_chat_delete_due", columnList = "status,next_retry_at,chat_deletion_outbox_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_delete_python_session",
                columnNames = "python_session_id")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatDeletionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_deletion_outbox_id")
    private Long chatDeletionOutboxId;

    @Column(name = "python_session_id", nullable = false)
    private Long pythonSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatDeletionOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ChatDeletionOutbox pending(Long pythonSessionId, LocalDateTime now) {
        return ChatDeletionOutbox.builder()
                .pythonSessionId(pythonSessionId)
                .status(ChatDeletionOutboxStatus.PENDING)
                .attemptCount(0)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markSent(LocalDateTime now) {
        status = ChatDeletionOutboxStatus.SENT;
        lockedAt = null;
        lastError = null;
        updatedAt = now;
    }

    public void markRetryOrFailed(String error, int maxAttempts, LocalDateTime now) {
        attemptCount++;
        lockedAt = null;
        lastError = truncate(error);
        updatedAt = now;
        if (attemptCount >= maxAttempts) {
            status = ChatDeletionOutboxStatus.FAILED;
            nextRetryAt = now;
            return;
        }
        status = ChatDeletionOutboxStatus.PENDING;
        nextRetryAt = now.plusSeconds(Math.min(300, 1L << Math.min(8, attemptCount)));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}

package com.project.farming.domain.notification.outbox;

import java.time.LocalDateTime;

public record FcmOutboxAdminRow(
        Long fcmOutboxId,
        FcmOutboxSourceType sourceType,
        Long sourceId,
        Long noticeId,
        Long userId,
        String targetToken,
        String title,
        String body,
        FcmOutboxStatus status,
        int attemptCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.project.farming.domain.notification.outbox;

import java.time.LocalDateTime;

public record FcmOutboxResponse(
        Long fcmOutboxId,
        FcmOutboxSourceType sourceType,
        Long sourceId,
        Long noticeId,
        Long userId,
        String maskedTargetToken,
        String title,
        String body,
        FcmOutboxStatus status,
        int attemptCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FcmOutboxResponse from(FcmOutboxAdminRow row) {
        return new FcmOutboxResponse(
                row.fcmOutboxId(),
                row.sourceType(),
                row.sourceId(),
                row.noticeId(),
                row.userId(),
                maskToken(row.targetToken()),
                row.title(),
                row.body(),
                row.status(),
                row.attemptCount(),
                row.lastError(),
                row.nextRetryAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "****";
        }
        if (token.length() <= 10) {
            return "****";
        }
        return token.substring(0, 5) + "..." + token.substring(token.length() - 5);
    }
}

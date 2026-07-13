package com.project.farming.domain.notification.outbox;

import com.project.farming.global.fcm.FcmBatchMessage;

import java.time.LocalDateTime;

public record FcmOutboxDispatch(
        Long fcmOutboxId,
        FcmOutboxSourceType sourceType,
        Long sourceId,
        Long noticeId,
        Long userId,
        String targetToken,
        String title,
        String body,
        int attemptCount,
        String eventId,
        LocalDateTime claimedAt
) {

    public FcmBatchMessage toBatchMessage() {
        return new FcmBatchMessage(fcmOutboxId, eventId, targetToken, title, body);
    }
}

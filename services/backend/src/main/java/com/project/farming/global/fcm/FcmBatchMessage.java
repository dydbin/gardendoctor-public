package com.project.farming.global.fcm;

public record FcmBatchMessage(
        Long correlationId,
        String eventId,
        String targetToken,
        String title,
        String body
) {
}

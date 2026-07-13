package com.project.farming.domain.notification.outbox;

public record FcmOutboxAdminFilter(
        FcmOutboxSourceType sourceType,
        Long sourceId,
        Long userId
) {
    public static FcmOutboxAdminFilter empty() {
        return new FcmOutboxAdminFilter(null, null, null);
    }
}

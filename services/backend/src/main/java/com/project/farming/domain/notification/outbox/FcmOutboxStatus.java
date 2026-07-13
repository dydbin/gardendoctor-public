package com.project.farming.domain.notification.outbox;

public enum FcmOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    CANCELLED
}

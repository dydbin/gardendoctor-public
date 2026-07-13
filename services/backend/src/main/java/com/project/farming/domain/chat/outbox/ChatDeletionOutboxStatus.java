package com.project.farming.domain.chat.outbox;

public enum ChatDeletionOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}

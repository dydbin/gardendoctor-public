package com.project.farming.domain.notification.outbox;

public class NoticeDeliveryInProgressException extends RuntimeException {

    public NoticeDeliveryInProgressException(String message) {
        super(message);
    }
}

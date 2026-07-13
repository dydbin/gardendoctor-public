package com.project.farming.domain.notification.repository;

import java.time.LocalDateTime;

public record NotificationReadRow(
        Long notificationId,
        Long userId,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {
}

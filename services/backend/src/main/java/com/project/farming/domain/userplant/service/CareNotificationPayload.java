package com.project.farming.domain.userplant.service;

public record CareNotificationPayload(
        Long userId,
        String eventKey,
        String title,
        String message
) {
}

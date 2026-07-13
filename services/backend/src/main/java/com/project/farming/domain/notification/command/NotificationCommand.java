package com.project.farming.domain.notification.command;

import com.project.farming.domain.notification.entity.Notification;
import java.util.List;

public record NotificationCommand(
        List<Long> userIds,
        String title,
        String message
) {

    public static final int MAX_TARGET_USERS = 500;

    public NotificationCommand {
        if (userIds == null || userIds.isEmpty() || userIds.size() > MAX_TARGET_USERS) {
            throw new IllegalArgumentException("알림 대상 사용자는 1명 이상 500명 이하여야 합니다.");
        } else if (userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("알림 대상 사용자 ID는 모두 양수여야 합니다.");
        } else if (userIds.stream().distinct().count() != userIds.size()) {
            throw new IllegalArgumentException("알림 대상 사용자 ID는 중복될 수 없습니다.");
        } else {
            userIds = List.copyOf(userIds);
        }
        Notification.validateText(title, message);
    }
}

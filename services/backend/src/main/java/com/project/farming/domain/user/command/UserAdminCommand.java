package com.project.farming.domain.user.command;

public record UserAdminCommand(
        String email,
        String nickname,
        String oauthProvider,
        String oauthId,
        String role,
        String fcmToken,
        String subscriptionStatus
) {
}

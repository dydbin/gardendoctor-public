package com.project.farming.domain.user.repository;

import com.project.farming.domain.user.entity.UserRole;

public interface UserAdminResponseRow {
    Long getUserId();

    String getEmail();

    String getNickname();

    String getOauthProvider();

    String getOauthId();

    UserRole getRole();

    String getFcmToken();

    String getSubscriptionStatus();

    String getProfileImageUrl();
}

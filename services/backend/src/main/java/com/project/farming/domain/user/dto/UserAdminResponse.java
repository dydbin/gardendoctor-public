package com.project.farming.domain.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserAdminResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String oauthProvider;
    private String oauthId;
    private String role;
    private String fcmToken;
    private String subscriptionStatus;
    private String profileImageUrl;
}

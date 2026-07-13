package com.project.farming.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserMyPageResponse {

    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String oauthProvider;
    private String role;
    private String subscriptionStatus;
}

package com.project.farming.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FcmTokenUpdateRequest {

    @NotBlank(message = "FCM 토큰은 비어있을 수 없습니다.")
    private String fcmToken;
}
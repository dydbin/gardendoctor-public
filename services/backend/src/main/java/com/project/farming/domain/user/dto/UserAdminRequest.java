package com.project.farming.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAdminRequest {

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "유효한 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "닉네임은 필수 입력 값입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해주세요.")
    private String nickname;

    private String oauthProvider;
    private String oauthId;
    
    @NotBlank(message = "권한은 필수 입력 값입니다.")
    private String role;
    
    private String fcmToken;

    @NotBlank(message = "구독 상태는 필수 입력 값입니다.")
    private String subscriptionStatus;
}

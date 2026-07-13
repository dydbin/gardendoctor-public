package com.project.farming.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import com.project.farming.global.validation.PasswordPolicy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmRequest {

    @NotBlank(message = "비밀번호 재설정 토큰은 필수입니다.")
    private String token;

    @PasswordPolicy
    private String newPassword;
}

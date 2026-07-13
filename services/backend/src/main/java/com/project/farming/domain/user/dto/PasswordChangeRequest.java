package com.project.farming.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import com.project.farming.global.validation.PasswordPolicy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;

    @PasswordPolicy
    private String newPassword;
}

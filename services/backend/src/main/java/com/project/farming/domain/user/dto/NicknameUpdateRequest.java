package com.project.farming.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "닉네임 업데이트 요청")
public class NicknameUpdateRequest {
    @NotBlank(message = "닉네임은 필수 입력 사항입니다.")
    @Schema(description = "새로운 닉네임", example = "새닉네임")
    private String newNickname;
}
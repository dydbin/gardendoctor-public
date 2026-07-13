package com.project.farming.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileImageUpdateRequest {
    
    @NotNull(message = "새로운 프로필 이미지 파일 ID는 필수입니다.")
    private Long profileImageFileId;
    
}
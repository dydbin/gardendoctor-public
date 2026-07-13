package com.project.farming.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeRequest {

    @NotBlank(message = "공지사항 제목을 입력해주세요.")
    @Size(max = 100, message = "공지사항 제목은 100자를 초과할 수 없습니다.")
    private String title;

    @NotBlank(message = "공지사항 내용을 입력해주세요.")
    @Size(max = 500, message = "공지사항 내용은 500자를 초과할 수 없습니다.")
    private String content;
}

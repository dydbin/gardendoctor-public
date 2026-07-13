package com.project.farming.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ChatResponse {
    private String answer;
    private String question;
    private Long chatId; // 서버가 클라이언트에게 알려주는 세션 ID 역할.
}
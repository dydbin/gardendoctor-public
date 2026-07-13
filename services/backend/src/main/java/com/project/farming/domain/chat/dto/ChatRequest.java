// src/main/farming/domain/chat/dto/ChatRequest.java
package com.project.farming.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatRequest {
    @Positive(message = "채팅방 ID는 양수여야 합니다.")
    private Long chatId;

    @NotBlank(message = "질문은 필수입니다.")
    @Size(max = 1000, message = "질문은 1000자 이하여야 합니다.")
    private String query;
}

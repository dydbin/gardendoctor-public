package com.project.farming.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Builder
public class ChatRoomResponse {
    private Long chatId;             // Spring DB의 chat ID
    private Long pythonSessionId;    // Python 세션 ID
    private String query;            // 첫 번째 사용자 메시지

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("message_count")
    private Integer messageCount;
}
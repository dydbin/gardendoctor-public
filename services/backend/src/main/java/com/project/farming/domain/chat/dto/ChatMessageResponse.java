package com.project.farming.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private String role;
    private String query;
    private String timestamp;
}

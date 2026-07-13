package com.project.farming.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Python FastAPI 서버와 통신하기 위한 payload 클래스 모음
 */
public class PythonChatPayload {

    // 1. Python 서버로 요청을 보낼 때 사용할 payload
    @Data
    @AllArgsConstructor
    public static class PythonChatRequest {
        @JsonProperty("session_id")
        private Long sessionId; // session_id를 snake_case로 변환

        @JsonProperty("query")
        private String query;
    }

    // 2. Python 서버로부터 응답을 받을 때 사용할 payload (메인 응답 객체)
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true) // API 응답에 있지만 DTO에 없는 필드는 무시
    public static class PythonChatResponse {
        @JsonProperty("id")
        private Long id; // 세션 ID

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;

        @JsonProperty("total_messages")
        private Long totalMessages;

        @JsonProperty("messages")
        private List<PythonChatMessage> messages;
    }

    // 3. 응답에 포함된 개별 메시지 payload
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PythonChatMessage {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("role")
        private String role;

        @JsonProperty("query")
        private String query; // 메시지 내용

        @JsonProperty("timestamp")
        private String timestamp;
    }
}

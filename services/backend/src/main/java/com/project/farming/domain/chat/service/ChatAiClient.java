package com.project.farming.domain.chat.service;

import com.project.farming.domain.chat.dto.PythonChatPayload;
import com.project.farming.domain.chat.dto.PythonSessionPayload;
import com.project.farming.domain.chat.dto.PythonSessionQueryRequest;
import com.project.farming.global.ai.AiRemoteServiceException;
import com.project.farming.global.ai.AiWebClientExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class ChatAiClient {

    private final WebClient pythonWebClient;
    private final AiWebClientExecutor executor;

    public ChatAiClient(
            @Qualifier("pythonWebClient") WebClient pythonWebClient,
            AiWebClientExecutor executor) {
        this.pythonWebClient = pythonWebClient;
        this.executor = executor;
    }

    public PythonChatPayload.PythonChatResponse ask(Long pythonSessionId, String question) {
        PythonChatPayload.PythonChatRequest request =
                new PythonChatPayload.PythonChatRequest(pythonSessionId, question);
        return executor.execute(
                pythonWebClient.post()
                        .uri("/api/chat")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(PythonChatPayload.PythonChatResponse.class),
                "chat"
        );
    }

    public PythonChatPayload.PythonChatResponse getSession(
            Long pythonSessionId, long offset, int limit, String role) {
        return executor.execute(
                pythonWebClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder
                                    .path("/api/chat/sessions/{id}")
                                    .queryParam("offset", offset)
                                    .queryParam("limit", limit);
                            if (role != null) {
                                builder.queryParam("role", role);
                            }
                            return builder.build(pythonSessionId);
                        })
                        .retrieve()
                        .bodyToMono(PythonChatPayload.PythonChatResponse.class),
                "chat session"
        );
    }

    public List<PythonSessionPayload> getSessions(List<Long> authorizedSessionIds) {
        if (authorizedSessionIds.isEmpty()) {
            return List.of();
        }
        return executor.execute(
                pythonWebClient.post()
                        .uri("/api/chat/sessions/query")
                        .bodyValue(new PythonSessionQueryRequest(authorizedSessionIds))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<>() { }),
                "authorized chat sessions"
        );
    }

    public void deleteSession(Long pythonSessionId) {
        try {
            executor.execute(
                    pythonWebClient.delete()
                            .uri("/api/chat/sessions/{sessionId}", pythonSessionId)
                            .retrieve()
                            .toBodilessEntity(),
                    "delete chat session"
            );
        } catch (AiRemoteServiceException ex) {
            if (ex.getStatusCode() != 404) {
                throw ex;
            }
        }
    }
}

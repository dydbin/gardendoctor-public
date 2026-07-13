package com.project.farming.global.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class AiWebClientExecutor {

    @Value("${python.request-timeout-ms:15000}")
    private long requestTimeoutMillis;

    public <T> T execute(Mono<T> request, String operation) {
        return request
                .timeout(Duration.ofMillis(Math.max(1, requestTimeoutMillis)))
                .onErrorMap(TimeoutException.class,
                        ex -> new AiServiceTimeoutException("AI 서버 응답 시간이 초과되었습니다: " + operation, ex))
                .onErrorMap(WebClientRequestException.class,
                        ex -> new AiServiceUnavailableException("AI 서버에 연결할 수 없습니다: " + operation, ex))
                .onErrorMap(WebClientResponseException.class,
                        ex -> new AiRemoteServiceException(
                                "AI 서버가 오류를 반환했습니다: " + operation,
                                ex.getStatusCode().value(),
                                ex))
                .block();
    }
}

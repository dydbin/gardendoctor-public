package com.project.farming.domain.analysis.service;

import com.project.farming.domain.analysis.dto.AnalysisRequestPayload;
import com.project.farming.domain.analysis.dto.AnalysisResultPayload;
import com.project.farming.global.ai.AiWebClientExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PhotoAnalysisAiClient {

    private final WebClient pythonWebClient;
    private final AiWebClientExecutor executor;

    public PhotoAnalysisAiClient(
            @Qualifier("pythonWebClient") WebClient pythonWebClient,
            AiWebClientExecutor executor) {
        this.pythonWebClient = pythonWebClient;
        this.executor = executor;
    }

    public AnalysisResultPayload analyze(String imageUrl) {
        return executor.execute(
                pythonWebClient.post()
                        .uri("/diagnose-by-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AnalysisRequestPayload(imageUrl))
                        .retrieve()
                        .bodyToMono(AnalysisResultPayload.class),
                "photo analysis"
        );
    }
}

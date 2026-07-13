package com.project.farming.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${python.server.url}")
    private String pythonServerUrl;

    @Value("${python.connect-timeout-ms:2000}")
    private int connectTimeoutMillis;

    @Value("${python.response-timeout-ms:15000}")
    private long responseTimeoutMillis;

    @Bean("pythonWebClient")
    public WebClient pythonWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(1, connectTimeoutMillis))
                .responseTimeout(Duration.ofMillis(Math.max(1, responseTimeoutMillis)));

        return WebClient.builder()
                .baseUrl(pythonServerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

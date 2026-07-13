package com.project.farming.global.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWebClientExecutorTest {

    private AiWebClientExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AiWebClientExecutor();
        ReflectionTestUtils.setField(executor, "requestTimeoutMillis", 20L);
    }

    @Test
    void successfulResponseShouldReturnValue() {
        assertThat(executor.execute(Mono.just("ok"), "test")).isEqualTo("ok");
    }

    @Test
    void nonTerminatingResponseShouldBecomeGatewayTimeoutException() {
        assertThatThrownBy(() -> executor.execute(Mono.never(), "test"))
                .isInstanceOf(AiServiceTimeoutException.class);
    }
}

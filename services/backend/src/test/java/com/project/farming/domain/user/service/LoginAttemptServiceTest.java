package com.project.farming.domain.user.service;

import com.project.farming.global.exception.LoginRateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
        ReflectionTestUtils.setField(loginAttemptService, "enabled", true);
        ReflectionTestUtils.setField(loginAttemptService, "maxFailures", 5);
        ReflectionTestUtils.setField(loginAttemptService, "failureWindowSeconds", 300L);
        ReflectionTestUtils.setField(loginAttemptService, "lockDurationSeconds", 900L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void assertAllowedShouldBlockWhenFailureCountReachedLimit() {
        when(valueOperations.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> loginAttemptService.assertAllowed("User@Example.com"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .hasMessage("로그인 실패가 반복되어 잠시 후 다시 시도해주세요.");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .startsWith("auth:login:failure:")
                .doesNotContain("User@Example.com")
                .doesNotContain("user@example.com");
    }

    @Test
    void recordFailureShouldIncrementAndSetLockTtlWhenLimitIsReached() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        loginAttemptService.recordFailure("user@example.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        verify(redisTemplate).expire(keyCaptor.getValue(), Duration.ofSeconds(900));
    }

    @Test
    void recordSuccessShouldDeleteFingerprintKey() {
        loginAttemptService.recordSuccess("user@example.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("auth:login:failure:");
    }

    @Test
    void redisFailureShouldNotBlockAuthenticationFlow() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatNoException()
                .isThrownBy(() -> loginAttemptService.assertAllowed("user@example.com"));
    }
}

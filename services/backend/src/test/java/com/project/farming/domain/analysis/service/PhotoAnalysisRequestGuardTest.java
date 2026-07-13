package com.project.farming.domain.analysis.service;

import com.project.farming.global.exception.PhotoAnalysisRateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoAnalysisRequestGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PhotoAnalysisRequestGuard guard;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        guard = new PhotoAnalysisRequestGuard(redisTemplate);
        ReflectionTestUtils.setField(guard, "cooldownSeconds", 10L);
    }

    @Test
    void firstRequestShouldAcquireUserCooldown() {
        when(valueOperations.setIfAbsent("analysis:photo:cooldown:7", "1", Duration.ofSeconds(10)))
                .thenReturn(true);

        guard.acquire(7L);

        verify(valueOperations).setIfAbsent("analysis:photo:cooldown:7", "1", Duration.ofSeconds(10));
    }

    @Test
    void concurrentRequestShouldBeRejectedBeforeExternalWork() {
        when(valueOperations.setIfAbsent("analysis:photo:cooldown:7", "1", Duration.ofSeconds(10)))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.acquire(7L))
                .isInstanceOf(PhotoAnalysisRateLimitExceededException.class);
    }
}

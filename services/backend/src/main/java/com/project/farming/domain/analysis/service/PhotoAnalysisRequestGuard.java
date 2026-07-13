package com.project.farming.domain.analysis.service;

import com.project.farming.global.exception.PhotoAnalysisRateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhotoAnalysisRequestGuard {

    private static final String KEY_PREFIX = "analysis:photo:cooldown:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.photo-analysis.request-cooldown-seconds:10}")
    private long cooldownSeconds;

    public void acquire(Long userId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + userId,
                "1",
                Duration.ofSeconds(Math.max(1, cooldownSeconds))
        );
        if (!Boolean.TRUE.equals(acquired)) {
            throw new PhotoAnalysisRateLimitExceededException("연속 분석 요청은 잠시 후 다시 시도해주세요.");
        }
    }
}

package com.project.farming.domain.user.service;

import com.project.farming.global.exception.LoginRateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private static final String KEY_PREFIX = "auth:login:failure:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.auth.login-rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.auth.login-rate-limit.max-failures:5}")
    private int maxFailures;

    @Value("${app.auth.login-rate-limit.failure-window-seconds:900}")
    private long failureWindowSeconds;

    @Value("${app.auth.login-rate-limit.lock-duration-seconds:900}")
    private long lockDurationSeconds;

    public void assertAllowed(String email) {
        if (!enabled) {
            return;
        }
        try {
            String attempts = redisTemplate.opsForValue().get(key(email));
            if (attempts != null && Long.parseLong(attempts) >= maxFailures) {
                throw new LoginRateLimitExceededException("로그인 실패가 반복되어 잠시 후 다시 시도해주세요.");
            }
        } catch (LoginRateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("로그인 실패 횟수 조회 중 Redis 오류가 발생했습니다. 로그인 제한을 건너뜁니다: {}", ex.getMessage());
        }
    }

    public void recordFailure(String email) {
        if (!enabled) {
            return;
        }
        try {
            String key = key(email);
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts == null) {
                return;
            }
            long ttlSeconds = attempts >= maxFailures ? lockDurationSeconds : failureWindowSeconds;
            redisTemplate.expire(key, Duration.ofSeconds(Math.max(1, ttlSeconds)));
        } catch (RuntimeException ex) {
            log.warn("로그인 실패 횟수 기록 중 Redis 오류가 발생했습니다. 로그인 제한을 건너뜁니다: {}", ex.getMessage());
        }
    }

    public void recordSuccess(String email) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.delete(key(email));
        } catch (RuntimeException ex) {
            log.warn("로그인 성공 후 실패 횟수 초기화 중 Redis 오류가 발생했습니다: {}", ex.getMessage());
        }
    }

    private String key(String email) {
        return KEY_PREFIX + CredentialFingerprint.email(email);
    }
}

package com.project.farming.global.jwtToken;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class JwtBlacklistService {

    private static final String KEY_PREFIX = "auth:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * JWT 토큰을 Redis 블랙리스트에 추가합니다.
     * 토큰은 지정된 만료 시간(밀리초) 동안 유효하지 않게 됩니다.
     * @param token 블랙리스트에 추가할 JWT
     * @param expirationMillis 토큰이 블랙리스트에 유지될 시간 (밀리초)
     */
    public void blacklistToken(String token, long expirationMillis) {
        // 만료 시간이 0보다 큰 경우에만 Redis에 저장하여 불필요한 저장을 방지합니다.
        if (expirationMillis > 0) {
            redisTemplate.opsForValue().set(key(token), "blacklisted", expirationMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 특정 JWT가 블랙리스트에 있는지 확인합니다.
     * @param token 확인할 JWT
     * @return 블랙리스트에 있으면 true, 없으면 false
     */
    public boolean isBlacklisted(String token) {
        // Redis에 해당 키(토큰)가 존재하는지 확인합니다.
        // hasKey 메서드는 Boolean 객체를 반환할 수 있으므로, Boolean.TRUE.equals()를 사용하여 null 안전성을 확보합니다.
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    private String key(String token) {
        return KEY_PREFIX + JwtTokenFingerprint.sha256(token);
    }
}

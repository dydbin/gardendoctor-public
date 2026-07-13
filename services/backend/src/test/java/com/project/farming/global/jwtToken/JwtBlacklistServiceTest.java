package com.project.farming.global.jwtToken;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtBlacklistService jwtBlacklistService;

    @BeforeEach
    void setUp() {
        jwtBlacklistService = new JwtBlacklistService(redisTemplate);
    }

    @Test
    void redisKeyContainsOnlyNamespacedFingerprint() {
        String rawToken = "header.payload.signature";
        String expectedKey = "auth:jwt:blacklist:" + JwtTokenFingerprint.sha256(rawToken);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);

        jwtBlacklistService.blacklistToken(rawToken, 60_000L);

        verify(valueOperations).set(expectedKey, "blacklisted", 60_000L, TimeUnit.MILLISECONDS);
        assertThat(jwtBlacklistService.isBlacklisted(rawToken)).isTrue();
        verify(redisTemplate).hasKey(expectedKey);
        assertThat(expectedKey).doesNotContain(rawToken);
    }
}

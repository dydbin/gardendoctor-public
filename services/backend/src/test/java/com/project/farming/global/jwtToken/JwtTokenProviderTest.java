package com.project.farming.global.jwtToken;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "local-test-access-secret-key-local-test-access-123456";
    private static final String REFRESH_SECRET = "local-test-refresh-secret-key-local-test-refresh-123456";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "accessSecret", ACCESS_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshSecret", REFRESH_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", 1_209_600_000L);
        jwtTokenProvider.init();
    }

    @Test
    void accessTokenIsValidOnlyAsAccessToken() {
        String accessToken = jwtTokenProvider.generateToken(1L);

        assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
    }

    @Test
    void refreshTokenIsValidOnlyAsRefreshToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    void generatedTokensKeepUserIdSubject() {
        String accessToken = jwtTokenProvider.generateToken(42L);
        String refreshToken = jwtTokenProvider.generateRefreshToken(42L);

        assertThat(jwtTokenProvider.getUserIdFromAccessToken(accessToken)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(refreshToken)).isEqualTo(42L);
    }

    @Test
    void refreshTokensAreUniqueForRotationEvenWhenIssuedBackToBack() {
        String firstRefreshToken = jwtTokenProvider.generateRefreshToken(1L);
        String secondRefreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);
    }

    @Test
    void credentialVersionShouldInvalidateTokensIssuedBeforePasswordChange() {
        String accessToken = jwtTokenProvider.generateToken(7L, 3L);
        String refreshToken = jwtTokenProvider.generateRefreshToken(7L, 3L);

        assertThat(jwtTokenProvider.matchesAccessCredentialVersion(accessToken, 3L)).isTrue();
        assertThat(jwtTokenProvider.matchesRefreshCredentialVersion(refreshToken, 3L)).isTrue();
        assertThat(jwtTokenProvider.matchesAccessCredentialVersion(accessToken, 4L)).isFalse();
        assertThat(jwtTokenProvider.matchesRefreshCredentialVersion(refreshToken, 4L)).isFalse();
    }

    @Test
    void tokenTypeClaimCannotBypassTheDedicatedSigningKey() {
        String refreshClaimSignedWithAccessKey = token("refresh", ACCESS_SECRET);
        String accessClaimSignedWithRefreshKey = token("access", REFRESH_SECRET);

        assertThat(jwtTokenProvider.validateRefreshToken(refreshClaimSignedWithAccessKey)).isFalse();
        assertThat(jwtTokenProvider.validateAccessToken(accessClaimSignedWithRefreshKey)).isFalse();
    }

    @Test
    void missingOrWeakSecretsFailFastDuringInitialization() {
        JwtTokenProvider missingAccess = provider(" ", REFRESH_SECRET);
        JwtTokenProvider weakRefresh = provider(ACCESS_SECRET, "too-short");
        JwtTokenProvider sameSecrets = provider(ACCESS_SECRET, ACCESS_SECRET);

        assertThatThrownBy(missingAccess::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.access-secret must be configured");
        assertThatThrownBy(weakRefresh::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.refresh-secret must be at least 32 bytes");
        assertThatThrownBy(sameSecrets::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
    }

    private JwtTokenProvider provider(String accessSecret, String refreshSecret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "accessSecret", accessSecret);
        ReflectionTestUtils.setField(provider, "refreshSecret", refreshSecret);
        ReflectionTestUtils.setField(provider, "expiration", 3_600_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiration", 1_209_600_000L);
        return provider;
    }

    private String token(String type, String secret) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject("1")
                .claim("token_type", type)
                .claim("credential_version", 0L)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 60_000L))
                .signWith(
                        Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }
}

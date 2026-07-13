package com.project.farming.global.jwtToken;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String CREDENTIAL_VERSION_CLAIM = "credential_version";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    @Value("${jwt.access-secret:}")
    private String accessSecret;

    @Value("${jwt.refresh-secret:}")
    private String refreshSecret;

    @Value("${jwt.expiration}")
    private long expiration; // 밀리초

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration; // 밀리초

    private Key accessKey;
    private Key refreshKey;

    @PostConstruct
    public void init() {
        validateSecret("jwt.access-secret", accessSecret);
        validateSecret("jwt.refresh-secret", refreshSecret);
        if (accessSecret.equals(refreshSecret)) {
            throw new IllegalStateException("jwt.access-secret and jwt.refresh-secret must be different");
        }
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId) {
        return generateToken(userId, 0L);
    }

    public String generateToken(Long userId, long credentialVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim(CREDENTIAL_VERSION_CLAIM, credentialVersion)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, 0L);
    }

    public String generateRefreshToken(Long userId, long credentialVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .claim(CREDENTIAL_VERSION_CLAIM, credentialVersion)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getUserIdFromAccessToken(String token) {
        Claims claims = parseAccessClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public Long getUserIdFromRefreshToken(String token) {
        Claims claims = parseRefreshClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public boolean matchesAccessCredentialVersion(String token, long credentialVersion) {
        return matchesCredentialVersion(parseAccessClaims(token), credentialVersion);
    }

    public boolean matchesRefreshCredentialVersion(String token, long credentialVersion) {
        return matchesCredentialVersion(parseRefreshClaims(token), credentialVersion);
    }

    private boolean matchesCredentialVersion(Claims claims, long credentialVersion) {
        Number tokenVersion = claims.get(CREDENTIAL_VERSION_CLAIM, Number.class);
        return tokenVersion != null && tokenVersion.longValue() == credentialVersion;
    }

    public boolean validateAccessToken(String authToken) {
        return validateToken(authToken, ACCESS_TOKEN_TYPE, accessKey);
    }

    public boolean validateRefreshToken(String authToken) {
        return validateToken(authToken, REFRESH_TOKEN_TYPE, refreshKey);
    }

    private boolean validateToken(String authToken, String expectedTokenType, Key signingKey) {
        try {
            Claims claims = parseClaims(authToken, signingKey);
            return expectedTokenType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException ex) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException ex) {
            log.error("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException ex) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException ex) {
            log.error("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    public long getAccessExpirationRemainingTimeMillis(String token) {
        return getExpirationRemainingTimeMillis(token, accessKey);
    }

    public long getRefreshExpirationRemainingTimeMillis(String token) {
        return getExpirationRemainingTimeMillis(token, refreshKey);
    }

    private long getExpirationRemainingTimeMillis(String token, Key signingKey) {
        try {
            Claims claims = parseClaims(token, signingKey);
            Date expirationDate = claims.getExpiration();
            return expirationDate.getTime() - System.currentTimeMillis();
        } catch (ExpiredJwtException ex) {
            // 토큰이 이미 만료된 경우 남은 시간은 0 또는 음수
            return 0;
        } catch (Exception ex) {
            log.error("JWT 토큰 만료 시간 계산 중 오류가 발생했습니다. type={}",
                    ex.getClass().getSimpleName());
            return -1; // 오류를 나타내는 값
        }
    }

    private Claims parseAccessClaims(String token) {
        return parseClaims(token, accessKey);
    }

    private Claims parseRefreshClaims(String token) {
        return parseClaims(token, refreshKey);
    }

    private Claims parseClaims(String token, Key signingKey) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private void validateSecret(String propertyName, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException(propertyName + " must be at least 32 bytes");
        }
    }
}

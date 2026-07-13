package com.project.farming.global.jwtToken;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void store(Long userId, String rawRefreshToken, Instant expiresAt) {
        refreshTokenRepository.saveOrUpdate(
                userId,
                JwtTokenFingerprint.sha256(rawRefreshToken),
                expiresAt
        );
    }

    @Transactional
    public int rotate(Long userId, String oldRawToken, String newRawToken, Instant expiresAt) {
        return refreshTokenRepository.rotateTokenFingerprint(
                userId,
                JwtTokenFingerprint.sha256(oldRawToken),
                JwtTokenFingerprint.sha256(newRawToken),
                expiresAt
        );
    }

    @Transactional
    public void revoke(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}

package com.project.farming.global.jwtToken;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenSessionService refreshTokenSessionService;

    @Test
    void storePassesOnlyFingerprintToRepository() {
        String rawToken = "header.payload.signature";
        Instant expiresAt = Instant.now().plusSeconds(3600);
        ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);

        refreshTokenSessionService.store(1L, rawToken, expiresAt);

        verify(refreshTokenRepository).saveOrUpdate(
                org.mockito.ArgumentMatchers.eq(1L), fingerprintCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(expiresAt));
        assertThat(fingerprintCaptor.getValue())
                .hasSize(64)
                .isEqualTo(JwtTokenFingerprint.sha256(rawToken))
                .doesNotContain(rawToken);
    }

    @Test
    void rotateUsesOldAndNewFingerprintsForCompareAndSwap() {
        String oldToken = "old.header.payload.signature";
        String newToken = "new.header.payload.signature";
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(refreshTokenRepository.rotateTokenFingerprint(
                1L,
                JwtTokenFingerprint.sha256(oldToken),
                JwtTokenFingerprint.sha256(newToken),
                expiresAt
        )).thenReturn(1);

        int rotated = refreshTokenSessionService.rotate(1L, oldToken, newToken, expiresAt);

        assertThat(rotated).isEqualTo(1);
    }
}

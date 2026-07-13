package com.project.farming.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_password_reset_token_expiry", columnList = "expires_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_password_reset_token_fingerprint", columnNames = "token_fingerprint"),
                @UniqueConstraint(name = "uk_password_reset_token_user", columnNames = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_token_id")
    private Long passwordResetTokenId;

    @Column(name = "token_fingerprint", nullable = false, length = 64)
    private String tokenFingerprint;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static PasswordResetToken issue(
            String tokenFingerprint,
            Long userId,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.tokenFingerprint = tokenFingerprint;
        token.userId = userId;
        token.expiresAt = expiresAt;
        token.createdAt = createdAt;
        return token;
    }

    public void markConsumed(LocalDateTime consumedAt) {
        if (this.consumedAt != null) {
            throw new IllegalStateException("Password reset token is already consumed");
        }
        this.consumedAt = consumedAt;
    }
}

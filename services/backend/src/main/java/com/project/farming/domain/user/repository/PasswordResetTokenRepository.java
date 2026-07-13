package com.project.farming.domain.user.repository;

import com.project.farming.domain.user.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.tokenFingerprint = :tokenFingerprint
              AND token.consumedAt IS NULL
              AND token.expiresAt > :now
            """)
    Optional<PasswordResetToken> findConsumableForUpdate(
            @Param("tokenFingerprint") String tokenFingerprint,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken token WHERE token.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken token WHERE token.tokenFingerprint = :tokenFingerprint")
    int deleteByTokenFingerprint(@Param("tokenFingerprint") String tokenFingerprint);
}

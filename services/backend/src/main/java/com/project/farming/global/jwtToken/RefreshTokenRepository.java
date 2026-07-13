package com.project.farming.global.jwtToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenFingerprint(String tokenFingerprint);

    Optional<RefreshToken> findByUserId(Long userId);

    void deleteByUserId(Long userId); // 로그아웃, refresh 토큰 갱신

    // upsert를 위한 네이티브 쿼리 메서드 추가
    @Modifying
    @Query(value = "INSERT INTO refresh_token (user_pk, token_fingerprint, expires_at, created_at) " +
            "VALUES (:user_pk, :token_fingerprint, :expires_at, NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "token_fingerprint = VALUES(token_fingerprint), expires_at = VALUES(expires_at)", nativeQuery = true)
    void saveOrUpdate(@Param("user_pk") Long userPk,
                      @Param("token_fingerprint") String tokenFingerprint,
                      @Param("expires_at") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt "
            + "SET rt.tokenFingerprint = :newFingerprint, rt.expiresAt = :expiresAt "
            + "WHERE rt.userId = :userId AND rt.tokenFingerprint = :oldFingerprint")
    int rotateTokenFingerprint(@Param("userId") Long userId,
                               @Param("oldFingerprint") String oldFingerprint,
                               @Param("newFingerprint") String newFingerprint,
                               @Param("expiresAt") Instant expiresAt);

}

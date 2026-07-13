package com.project.farming.global.jwtToken;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_fingerprint", columnList = "token_fingerprint")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 엔티티를 위한 protected 기본 생성자
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_fingerprint", nullable = false, length = 64)
    private String tokenFingerprint;

    @Column(name = "user_pk", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, updatable = false) // 생성 시에만 설정되도록 updatable=false 추가
    private Timestamp createdAt; // 토큰 생성 시간 (java.sql.Timestamp)

    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist // 엔티티가 영속화되기 전에 실행
    protected void onCreate() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
}

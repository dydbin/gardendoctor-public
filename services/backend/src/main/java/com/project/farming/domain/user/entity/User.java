package com.project.farming.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"oauthProvider", "oauthId"})
        },
        indexes = {
                @Index(name = "idx_user_nickname", columnList = "nickname")
        }
)
@SQLDelete(sql = "UPDATE users SET "
        + "email = CONCAT('deleted-', user_id, '@deleted.local'), "
        + "password = '{withdrawn}', "
        + "nickname = CONCAT('deleted-', user_id), "
        + "oauth_provider = NULL, "
        + "oauth_id = NULL, "
        + "fcm_token = NULL, "
        + "subscription_status = 'WITHDRAWN', "
        + "profile_image_file_id = NULL "
        + "WHERE user_id = ?")
@SQLRestriction("subscription_status <> 'WITHDRAWN'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 50, nullable = false)
    private String nickname;

    @Column(length = 20, nullable = true)
    private String oauthProvider;

    @Column(length = 255, nullable = true)
    private String oauthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(length = 512)
    private String fcmToken;

    @Column(length = 20, nullable = false)
    private String subscriptionStatus;

    @Column(name = "profile_image_file_id")
    private Long profileImageFileId; // ImageFile ID 참조

    @Column(name = "credential_version", nullable = false)
    @Builder.Default
    private long credentialVersion = 0L;

    // 비밀번호 변경을 위한 메서드 추가
    public void updatePassword(String password) {
        this.password = password;
        this.credentialVersion++;
    }

    public void updateProfileImageFile(Long profileImageFileId) {
        this.profileImageFileId = profileImageFileId;
    }

    // --- 업데이트 메서드 ---
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    // 관리자 페이지에서 사용
    public void updateEmail(String email) {
        this.email = email;
    }

    // 관리자 페이지에서 사용
    public void updateRole(UserRole role) {
        this.role = role;
    }

    // 소셜 로그인 연동 시 사용될 setter (CustomOAuth2UserService에서 호출)
    public void setOauthProvider(String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    public void setOauthId(String oauthId) {
        this.oauthId = oauthId;
    }

}

package com.project.farming.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, is_read")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_notification_notice_user",
                columnNames = {"notice_id", "user_id"}
        ),
        @UniqueConstraint(
                name = "uk_notification_event_key",
                columnNames = {"event_key"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;  //알림을 받는 사용자 ID

    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "event_key", length = 160)
    private String eventKey;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title; //알림 제목

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message; //알림 제목

    @Column(nullable = false)
    private boolean isRead; //읽음 여부

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; //알림 생성 시간

    @PrePersist
    protected void onCreate() {
        validateText(title, message);
        this.createdAt = LocalDateTime.now();
        this.isRead = false; // 생성 시 기본값
    }
    @Builder
    public Notification(Long userId, Long noticeId, String title, String message, boolean isRead) {
        this.userId = userId;
        this.noticeId = noticeId;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
    }

    // 알림 읽음 처리
    public void markAsRead() {
        this.isRead = true;
    }

    public static Notification create(Long userId, String title, String message) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("알림 대상 사용자 ID는 양수여야 합니다.");
        }
        validateText(title, message);
        return Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .isRead(false)
                .build();
    }

    public static void validateText(String title, String message) {
        validateRequiredLength("알림 제목", title, MAX_TITLE_LENGTH);
        validateRequiredLength("알림 내용", message, MAX_MESSAGE_LENGTH);
    }

    private static void validateRequiredLength(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은 필수입니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
    }
}

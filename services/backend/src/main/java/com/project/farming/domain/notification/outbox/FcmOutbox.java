package com.project.farming.domain.notification.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "fcm_outbox",
        indexes = {
                @Index(name = "idx_fcm_outbox_due", columnList = "status,next_retry_at,fcm_outbox_id"),
                @Index(name = "idx_fcm_outbox_notice", columnList = "notice_id"),
                @Index(name = "idx_fcm_outbox_source", columnList = "source_type,source_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fcm_outbox_notice_token",
                        columnNames = {"notice_id", "target_token"}
                ),
                @UniqueConstraint(
                        name = "uk_fcm_outbox_notification_user",
                        columnNames = {"source_type", "source_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmOutbox {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_outbox_id")
    private Long fcmOutboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private FcmOutboxSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "target_token", nullable = false, length = 512)
    private String targetToken;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FcmOutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FcmOutbox noticeBroadcast(
            Long noticeId,
            Long userId,
            String targetToken,
            String title,
            String body) {
        if (noticeId == null) {
            throw new IllegalArgumentException("noticeId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (targetToken == null || targetToken.isBlank()) {
            throw new IllegalArgumentException("targetToken must not be blank");
        }
        FcmOutbox outbox = new FcmOutbox();
        outbox.initialize(FcmOutboxSourceType.NOTICE, noticeId, userId, targetToken, title, body);
        outbox.noticeId = noticeId;
        return outbox;
    }

    public static FcmOutbox notificationPush(
            Long notificationId,
            Long userId,
            String targetToken,
            String title,
            String body) {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (targetToken == null || targetToken.isBlank()) {
            throw new IllegalArgumentException("targetToken must not be blank");
        }
        FcmOutbox outbox = new FcmOutbox();
        outbox.initialize(FcmOutboxSourceType.NOTIFICATION, notificationId, userId, targetToken, title, body);
        return outbox;
    }

    private void initialize(
            FcmOutboxSourceType sourceType,
            Long sourceId,
            Long userId,
            String targetToken,
            String title,
            String body) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.userId = userId;
        this.targetToken = targetToken;
        this.title = title;
        this.body = body;
        this.status = FcmOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.nextRetryAt == null) {
            this.nextRetryAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void markSent(LocalDateTime now) {
        this.status = FcmOutboxStatus.SENT;
        this.sentAt = now;
        this.lockedAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markRetry(String errorMessage, LocalDateTime nextRetryAt, LocalDateTime now) {
        this.status = FcmOutboxStatus.PENDING;
        this.attemptCount++;
        this.nextRetryAt = nextRetryAt;
        this.lockedAt = null;
        this.lastError = truncate(errorMessage);
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, LocalDateTime now) {
        this.status = FcmOutboxStatus.FAILED;
        this.attemptCount++;
        this.lockedAt = null;
        this.lastError = truncate(errorMessage);
        this.updatedAt = now;
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}

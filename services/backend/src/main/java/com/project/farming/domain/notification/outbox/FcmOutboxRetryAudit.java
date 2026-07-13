package com.project.farming.domain.notification.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "fcm_outbox_retry_audit",
        indexes = {
                @Index(name = "idx_fcm_retry_audit_outbox_created", columnList = "fcm_outbox_id,created_at"),
                @Index(name = "idx_fcm_retry_audit_admin_created", columnList = "admin_user_id,created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmOutboxRetryAudit {

    private static final String MANUAL_RETRY_REASON = "MANUAL_RETRY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_outbox_retry_audit_id")
    private Long fcmOutboxRetryAuditId;

    @Column(name = "fcm_outbox_id", nullable = false)
    private Long fcmOutboxId;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 32)
    private FcmOutboxStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 32)
    private FcmOutboxStatus resultStatus;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static FcmOutboxRetryAudit manualRetry(Long fcmOutboxId, Long adminUserId, LocalDateTime now) {
        if (fcmOutboxId == null) {
            throw new IllegalArgumentException("fcmOutboxId must not be null");
        }
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId must not be null");
        }
        FcmOutboxRetryAudit audit = new FcmOutboxRetryAudit();
        audit.fcmOutboxId = fcmOutboxId;
        audit.adminUserId = adminUserId;
        audit.previousStatus = FcmOutboxStatus.FAILED;
        audit.resultStatus = FcmOutboxStatus.PENDING;
        audit.reason = MANUAL_RETRY_REASON;
        audit.createdAt = now == null ? LocalDateTime.now() : now;
        return audit;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

package com.project.farming.domain.notification.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;

public interface FcmOutboxRepository extends JpaRepository<FcmOutbox, Long> {

    boolean existsByNoticeId(Long noticeId);

    boolean existsByNoticeIdAndStatusNot(Long noticeId, FcmOutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO fcm_outbox (
                source_type, source_id, notice_id, user_id, target_token,
                title, body, status, attempt_count, next_retry_at, created_at, updated_at
            )
            SELECT
                'NOTICE', n.notice_id, n.notice_id, n.user_id, u.fcm_token,
                n.title, n.message, 'PENDING', 0,
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
            FROM notification n
            JOIN users u ON u.user_id = n.user_id
            WHERE n.notice_id = :noticeId
              AND u.subscription_status <> 'WITHDRAWN'
              AND u.fcm_token IS NOT NULL
              AND TRIM(u.fcm_token) <> ''
            ORDER BY n.user_id
            ON DUPLICATE KEY UPDATE
                target_token = VALUES(target_token),
                title = VALUES(title),
                body = VALUES(body),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    int insertNoticeOutboxes(@Param("noticeId") Long noticeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO fcm_outbox (
                source_type, source_id, user_id, target_token, title, body,
                status, attempt_count, next_retry_at, created_at, updated_at
            ) VALUES (
                'NOTIFICATION', :notificationId, :userId, :targetToken, :title, :body,
                'PENDING', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                target_token = VALUES(target_token),
                title = VALUES(title),
                body = VALUES(body),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    int upsertNotificationOutbox(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId,
            @Param("targetToken") String targetToken,
            @Param("title") String title,
            @Param("body") String body);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM FcmOutbox o WHERE o.noticeId = :noticeId ORDER BY o.fcmOutboxId")
    List<FcmOutbox> findByNoticeIdForUpdate(@Param("noticeId") Long noticeId);

    @Query(
            value = """
            SELECT new com.project.farming.domain.notification.outbox.FcmOutboxAdminRow(
                o.fcmOutboxId,
                o.sourceType,
                o.sourceId,
                o.noticeId,
                o.userId,
                o.targetToken,
                o.title,
                o.body,
                o.status,
                o.attemptCount,
                o.lastError,
                o.nextRetryAt,
                o.createdAt,
                o.updatedAt
            )
            FROM FcmOutbox o
            WHERE o.status = :status
            ORDER BY o.updatedAt DESC, o.fcmOutboxId DESC
            """,
            countQuery = """
            SELECT COUNT(o)
            FROM FcmOutbox o
            WHERE o.status = :status
            """)
    Page<FcmOutboxAdminRow> findAdminRowsByStatus(
            @Param("status") FcmOutboxStatus status,
            Pageable pageable);

    @Query(
            value = """
            SELECT new com.project.farming.domain.notification.outbox.FcmOutboxAdminRow(
                o.fcmOutboxId,
                o.sourceType,
                o.sourceId,
                o.noticeId,
                o.userId,
                o.targetToken,
                o.title,
                o.body,
                o.status,
                o.attemptCount,
                o.lastError,
                o.nextRetryAt,
                o.createdAt,
                o.updatedAt
            )
            FROM FcmOutbox o
            WHERE o.status = :status
              AND (:sourceType IS NULL OR o.sourceType = :sourceType)
              AND (:sourceId IS NULL OR o.sourceId = :sourceId)
              AND (:userId IS NULL OR o.userId = :userId)
            ORDER BY o.updatedAt DESC, o.fcmOutboxId DESC
            """,
            countQuery = """
            SELECT COUNT(o)
            FROM FcmOutbox o
            WHERE o.status = :status
              AND (:sourceType IS NULL OR o.sourceType = :sourceType)
              AND (:sourceId IS NULL OR o.sourceId = :sourceId)
              AND (:userId IS NULL OR o.userId = :userId)
            """)
    Page<FcmOutboxAdminRow> findAdminRowsByStatusAndFilters(
            @Param("status") FcmOutboxStatus status,
            @Param("sourceType") FcmOutboxSourceType sourceType,
            @Param("sourceId") Long sourceId,
            @Param("userId") Long userId,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FcmOutbox o
            SET o.status = :pending,
                o.lockedAt = NULL,
                o.nextRetryAt = :now,
                o.lastError = :lastError,
                o.updatedAt = :now
            WHERE o.status = :processing
              AND o.lockedAt < :expiresBefore
            """)
    int requeueExpiredProcessingJobs(
            @Param("processing") FcmOutboxStatus processing,
            @Param("pending") FcmOutboxStatus pending,
            @Param("expiresBefore") LocalDateTime expiresBefore,
            @Param("now") LocalDateTime now,
            @Param("lastError") String lastError);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FcmOutbox o
            SET o.status = :pending,
                o.attemptCount = 0,
                o.lockedAt = NULL,
                o.nextRetryAt = :now,
                o.lastError = NULL,
                o.updatedAt = :now
            WHERE o.fcmOutboxId = :fcmOutboxId
              AND o.status = :failed
            """)
    int retryFailedOutbox(
            @Param("fcmOutboxId") Long fcmOutboxId,
            @Param("failed") FcmOutboxStatus failed,
            @Param("pending") FcmOutboxStatus pending,
            @Param("now") LocalDateTime now);

    long countByStatus(FcmOutboxStatus status);
}

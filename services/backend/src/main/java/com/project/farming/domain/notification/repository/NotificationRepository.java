package com.project.farming.domain.notification.repository;

import com.project.farming.domain.notification.dto.NotificationResponse;
import com.project.farming.domain.notification.entity.Notification;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
        SELECT new com.project.farming.domain.notification.dto.NotificationResponse(
            n.notificationId,
            n.title,
            n.message,
            n.isRead,
            n.createdAt
        )
        FROM Notification n
        WHERE n.userId = :userId
        ORDER BY n.createdAt DESC
        """)
    Page<NotificationResponse> findResponsePageByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.notification.repository.NotificationReadRow(
            n.notificationId,
            n.userId,
            n.title,
            n.message,
            n.isRead,
            n.createdAt
        )
        FROM Notification n
        WHERE n.notificationId = :notificationId
        """)
    Optional<NotificationReadRow> findReadRowByNotificationId(@Param("notificationId") Long notificationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.isRead = true
        WHERE n.notificationId = :notificationId
          AND n.userId = :userId
          AND n.isRead = false
        """)
    int markAsReadIfUnreadAndOwned(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId);

    // 특정 사용자의 읽지 않은 알림 개수 조회
    long countByUserIdAndIsReadFalse(Long userId);

    // 특정 사용자의 모든 알림 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    boolean existsByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO notification (
            notice_id, user_id, title, message, is_read, created_at
        )
        SELECT :noticeId,
               u.user_id,
               :title,
               :message,
               false,
               CURRENT_TIMESTAMP(6)
        FROM users u
        WHERE u.subscription_status <> 'WITHDRAWN'
        ORDER BY u.user_id
        ON DUPLICATE KEY UPDATE notice_id = notice_id
        """, nativeQuery = true)
    int insertNoticeSnapshots(
            @Param("noticeId") Long noticeId,
            @Param("title") String title,
            @Param("message") String message);
}

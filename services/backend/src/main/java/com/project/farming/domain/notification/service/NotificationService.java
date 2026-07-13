// src/main/java/com/project/farming/domain/notification/service/NotificationService.java
package com.project.farming.domain.notification.service;

import com.project.farming.domain.notification.command.NotificationCommand;
import com.project.farming.domain.notification.dto.NotificationResponse;
import com.project.farming.domain.notification.entity.Notification;
import com.project.farming.domain.notification.outbox.FcmOutboxService;
import com.project.farming.domain.notification.repository.NotificationRepository;
import com.project.farming.domain.notification.repository.NotificationReadRow;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.exception.NotificationNotFoundException;
import com.project.farming.global.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmOutboxService fcmOutboxService;

    /**
     * 특정 사용자에게 알림을 생성하고 FCM 푸시 알림을 발송하는 핵심 로직.
     * 스케줄러나 다른 서비스에서 User 엔티티를 직접 넘겨줄 때 사용됩니다.
     */
    @Transactional
    public void createAndSendNotification(User user, String title, String message) {
        Notification notification = Notification.create(user.getUserId(), title, message);
        Notification savedNotification = notificationRepository.save(notification);

        if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            fcmOutboxService.enqueueNotification(
                    savedNotification.getNotificationId(),
                    user.getUserId(),
                    user.getFcmToken(),
                    title,
                    message
            );
        } else {
            log.warn("⚠️ User with ID {} has no FCM token, skipping push notification.", user.getUserId());
        }
    }

    @Transactional
    public void createAndSendNotification(Long userId, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        createAndSendNotification(user, title, message);
    }

    /**
     * 컨트롤러에서 알림 생성 명령을 받아 알림을 생성하고 발송할 때 사용.
     * User 엔티티를 조회한 후 내부 createAndSendNotification 메서드를 호출합니다.
     * (관리자용- 다숭 사용자)
     */
    @Transactional
    public void createAndSendNotification(NotificationCommand command) {
        // @NotEmpty 요청 검증이 있으므로 여기서 userIds가 null이거나 비어있을 일은 없지만, 방어 코드 추가
        if (command.userIds().isEmpty()) {
            // 이 경고는 @NotEmpty 검증 실패 시 400 Bad Request가 발생하기 때문에 사실상 실행되지 않을 수 있습니다.
            log.warn("🚨 No user IDs provided in NotificationCommand. Skipping notification.");
            return;
        }

        // 모든 대상 사용자 조회
        List<User> users = userRepository.findAllById(command.userIds());

        // 조회된 사용자 목록이 비어있다면 (요청된 모든 ID에 해당하는 유저가 없는 경우)
        if (users.isEmpty()) {
            log.error("❌ No users found for the provided IDs: {}", command.userIds());
            throw new UserNotFoundException("제공된 사용자 ID에 해당하는 유저를 찾을 수 없습니다: " + command.userIds());
        }

        // 일부 사용자만 찾을 수 있었을 경우 경고 (선택 사항)
        if (users.size() != command.userIds().size()) {
            List<Long> foundUserIds = users.stream().map(User::getUserId).collect(Collectors.toList());
            List<Long> notFoundUserIds = command.userIds().stream()
                    .filter(id -> !foundUserIds.contains(id))
                    .collect(Collectors.toList());
            log.warn("⚠️ Some user IDs were not found. Not found IDs: {}", notFoundUserIds);
        }

        // 각 사용자에게 알림 발송
        for (User user : users) {
            createAndSendNotification(user, command.title(), command.message());
        }
    }

    /**
     * 현재 로그인한 사용자의 알림 목록 조회 (페이징 적용)
     */
    public Page<NotificationResponse> getNotificationsForUser(User user, Pageable pageable) {
        return notificationRepository.findResponsePageByUserIdOrderByCreatedAtDesc(user.getUserId(), pageable);
    }

    /**
     * 단일 알림 읽음 처리 (현재 로그인한 사용자의 알림인지 확인)
     */
    @Transactional
    public NotificationResponse markNotificationAsRead(Long notificationId, User currentUser) {
        NotificationReadRow notification = notificationRepository.findReadRowByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + notificationId));

        if (!notification.userId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("User is not authorized to access this notification.");
        }

        if (!notification.isRead()) {
            notificationRepository.markAsReadIfUnreadAndOwned(notificationId, currentUser.getUserId());
        }
        return new NotificationResponse(
                notification.notificationId(),
                notification.title(),
                notification.message(),
                true,
                notification.createdAt()
        );
    }


    /**
     * 현재 로그인한 사용자의 읽지 않은 알림 개수 조회
     */
    public long countUnreadNotifications(User currentUser) {
        return notificationRepository.countByUserIdAndIsReadFalse(currentUser.getUserId());
    }

    /**
     * 특정 알림 삭제 (현재 로그인한 사용자 본인의 알림만 가능)
     */
    @Transactional
    public void deleteNotification(Long notificationId, User currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + notificationId));

        // 알림의 수신자가 현재 사용자가 맞는지 확인 (권한 체크)
        if (!notification.getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("User is not authorized to delete this notification.");
        }

        notificationRepository.delete(notification);
    }

    /**
     * 현재 로그인한 사용자의 모든 알림 삭제
     * 이 메서드는 호출하는 곳에서 사용자 ID와 현재 로그인한 사용자가 일치하는지 확인합니다.
     */
    @Transactional
    public void deleteAllUserNotifications(Long userId, User currentUser) {
        // 알림을 삭제할 userId와 현재 로그인한 사용자의 userId가 일치하는지 확인 (권한 체크)
        if (!userId.equals(currentUser.getUserId())) {
            throw new AccessDeniedException("User is not authorized to delete all notifications for this user ID.");
        }
        notificationRepository.deleteByUserId(userId);
    }

    /**
     * NoticeService에서 사용
     * - 공지사항 알림을 각 사용자 별로 저장
     *
     * @param noticeId 저장할 공지사항 ID
     * @param title 저장할 공지사항 제목
     * @param content 저장할 공지사항 내용
     */
    @Transactional
    public void saveNotice(Long noticeId, String title, String content) {
        if (noticeId == null) {
            throw new IllegalArgumentException("noticeId must not be null");
        }
        int driverReportedRows = notificationRepository.insertNoticeSnapshots(noticeId, title, content);
        if (driverReportedRows > 0) {
            log.info(
                    "공지사항 알림 fan-out upsert를 완료했습니다. noticeId={}, driverReportedRows={}",
                    noticeId,
                    driverReportedRows);
            return;
        }
        if (notificationRepository.existsByNoticeId(noticeId)) {
            log.info("공지사항 알림이 이미 저장되어 있어 중복 생성을 건너뜁니다. noticeId={}", noticeId);
            return;
        }
        log.error("활성 사용자가 존재하지 않습니다.");
        throw new UserNotFoundException("활성 사용자가 존재하지 않습니다.");
    }

    /**
     * NoticeService에서 사용
     * - 삭제된 공지사항 알림을 notification에서도 삭제
     *
     * @param noticeId 삭제할 공지사항 ID
     */
    @Transactional
    public void deleteNotice(Long noticeId) {
        notificationRepository.deleteByNoticeId(noticeId);
    }

}

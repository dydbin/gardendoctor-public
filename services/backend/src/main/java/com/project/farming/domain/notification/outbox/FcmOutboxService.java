package com.project.farming.domain.notification.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmOutboxService {

    private final FcmOutboxRepository fcmOutboxRepository;

    @Transactional
    public int enqueueNotice(Long noticeId) {
        if (noticeId == null) {
            throw new IllegalArgumentException("noticeId must not be null");
        }
        return fcmOutboxRepository.insertNoticeOutboxes(noticeId);
    }

    @Transactional
    public void deleteNoticeOutboxes(Long noticeId) {
        var outboxes = fcmOutboxRepository.findByNoticeIdForUpdate(noticeId);
        boolean deliveryInProgress = outboxes.stream()
                .anyMatch(outbox -> outbox.getStatus() == FcmOutboxStatus.PROCESSING);
        if (deliveryInProgress) {
            throw new NoticeDeliveryInProgressException(
                    "공지사항 알림이 발송 중입니다. 잠시 후 다시 삭제해주세요.");
        }
        fcmOutboxRepository.deleteAllInBatch(outboxes);
    }

    @Transactional
    public int enqueueNotification(
            Long notificationId,
            Long userId,
            String targetToken,
            String title,
            String body) {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null");
        }
        if (targetToken == null || targetToken.isBlank()) {
            log.warn("FCM target token is empty for notificationId {}. Skipping push enqueue.", notificationId);
            return 0;
        }
        return fcmOutboxRepository.upsertNotificationOutbox(
                notificationId,
                userId,
                targetToken,
                title,
                body);
    }
}

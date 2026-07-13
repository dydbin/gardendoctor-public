package com.project.farming.domain.notification.service;

import com.project.farming.domain.notification.entity.Notification;
import com.project.farming.domain.notification.dto.NotificationResponse;
import com.project.farming.domain.notification.outbox.FcmOutboxService;
import com.project.farming.domain.notification.repository.NotificationRepository;
import com.project.farming.domain.notification.repository.NotificationReadRow;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.exception.NotificationNotFoundException;
import com.project.farming.global.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmOutboxService fcmOutboxService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                fcmOutboxService
        );
    }

    @Test
    void createAndSendNotificationShouldPersistInAppNotificationBeforeFcmOutboxEnqueue() {
        User user = user(1L, "token-1");
        Notification savedNotification = savedNotification(100L, 1L, "제목", "내용");
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        notificationService.createAndSendNotification(user, "제목", "내용");

        verify(notificationRepository).save(any(Notification.class));
        verify(fcmOutboxService).enqueueNotification(100L, 1L, "token-1", "제목", "내용");
    }

    @Test
    void createAndSendNotificationShouldPersistInAppNotificationEvenWithoutFcmToken() {
        User user = user(1L, " ");
        Notification savedNotification = savedNotification(100L, 1L, "제목", "내용");
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        notificationService.createAndSendNotification(user, "제목", "내용");

        verify(notificationRepository).save(any(Notification.class));
        verify(fcmOutboxService, never()).enqueueNotification(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void markNotificationAsReadShouldUseConditionalUpdateAndReturnReadResponse() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 9, 10, 0);
        when(notificationRepository.findReadRowByNotificationId(100L))
                .thenReturn(Optional.of(new NotificationReadRow(100L, 1L, "제목", "내용", false, createdAt)));

        NotificationResponse response = notificationService.markNotificationAsRead(100L, user(1L, "token-1"));

        assertThat(response.getNotificationId()).isEqualTo(100L);
        assertThat(response.isRead()).isTrue();
        verify(notificationRepository).markAsReadIfUnreadAndOwned(100L, 1L);
        verify(notificationRepository, never()).findById(anyLong());
    }

    @Test
    void markNotificationAsReadShouldSkipUpdateWhenAlreadyRead() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 9, 10, 0);
        when(notificationRepository.findReadRowByNotificationId(100L))
                .thenReturn(Optional.of(new NotificationReadRow(100L, 1L, "제목", "내용", true, createdAt)));

        NotificationResponse response = notificationService.markNotificationAsRead(100L, user(1L, "token-1"));

        assertThat(response.isRead()).isTrue();
        verify(notificationRepository, never()).markAsReadIfUnreadAndOwned(anyLong(), anyLong());
        verify(notificationRepository, never()).findById(anyLong());
    }

    @Test
    void markNotificationAsReadShouldThrowNotFoundWhenMissing() {
        when(notificationRepository.findReadRowByNotificationId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markNotificationAsRead(100L, user(1L, "token-1")))
                .isInstanceOf(NotificationNotFoundException.class);

        verify(notificationRepository, never()).markAsReadIfUnreadAndOwned(anyLong(), anyLong());
    }

    @Test
    void markNotificationAsReadShouldThrowAccessDeniedForDifferentOwner() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 9, 10, 0);
        when(notificationRepository.findReadRowByNotificationId(100L))
                .thenReturn(Optional.of(new NotificationReadRow(100L, 2L, "제목", "내용", false, createdAt)));

        assertThatThrownBy(() -> notificationService.markNotificationAsRead(100L, user(1L, "token-1")))
                .isInstanceOf(AccessDeniedException.class);

        verify(notificationRepository, never()).markAsReadIfUnreadAndOwned(anyLong(), anyLong());
    }

    @Test
    void saveNoticeShouldSkipDuplicateNoticeNotifications() {
        when(notificationRepository.insertNoticeSnapshots(10L, "공지", "내용")).thenReturn(0);
        when(notificationRepository.existsByNoticeId(10L)).thenReturn(true);

        notificationService.saveNotice(10L, "공지", "내용");

        verify(userRepository, never()).findUsersByFcmToken();
        verify(notificationRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void saveNoticeShouldUseSetBasedInsertWithoutLoadingRecipientEntities() {
        when(notificationRepository.insertNoticeSnapshots(10L, "공지", "내용")).thenReturn(500);

        notificationService.saveNotice(10L, "공지", "내용");

        verify(notificationRepository).insertNoticeSnapshots(10L, "공지", "내용");
        verify(notificationRepository, never()).existsByNoticeId(10L);
        verify(userRepository, never()).findUsersByFcmToken();
        verify(notificationRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void saveNoticeShouldFailWhenNoEligibleRecipientExists() {
        when(notificationRepository.insertNoticeSnapshots(10L, "공지", "내용")).thenReturn(0);
        when(notificationRepository.existsByNoticeId(10L)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.saveNotice(10L, "공지", "내용"))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User user(Long userId, String fcmToken) {
        return User.builder()
                .userId(userId)
                .email("user" + userId + "@example.com")
                .password("password")
                .nickname("user" + userId)
                .role(UserRole.USER)
                .fcmToken(fcmToken)
                .subscriptionStatus("ACTIVE")
                .build();
    }

    private Notification savedNotification(Long notificationId, Long userId, String title, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .isRead(false)
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", notificationId);
        return notification;
    }
}

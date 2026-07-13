package com.project.farming.domain.notification.controller;

import com.project.farming.domain.notification.command.NotificationCommand;
import com.project.farming.domain.notification.dto.NotificationRequest;
import com.project.farming.domain.notification.dto.NotificationResponse;
import com.project.farming.domain.notification.service.NotificationService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        notificationController = new NotificationController(notificationService);
    }

    @Test
    void getMyNotificationsShouldReturnCommonUnauthorizedBodyWhenPrincipalMissing() {
        ResponseEntity<CommonResponse<Page<NotificationResponse>>> response =
                notificationController.getMyNotifications(null, Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void getMyNotificationsShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails(UserRole.USER);
        Page<NotificationResponse> page = new PageImpl<>(List.of(notificationResponse()));
        when(notificationService.getNotificationsForUser(userDetails.getUser(), Pageable.unpaged()))
                .thenReturn(page);

        ResponseEntity<CommonResponse<Page<NotificationResponse>>> response =
                notificationController.getMyNotifications(userDetails, Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("알림 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(page);
    }

    @Test
    void createNotificationShouldReturnAcceptedBecauseFcmDeliveryIsOutboxed() {
        CustomUserDetails adminDetails = userDetails(UserRole.ADMIN);
        NotificationRequest request = new NotificationRequest(List.of(1L), "제목", "내용");

        ResponseEntity<CommonResponse<Void>> response =
                notificationController.createNotification(adminDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("요청이 접수");
        verify(notificationService).createAndSendNotification(org.mockito.ArgumentMatchers.any(NotificationCommand.class));
    }

    @Test
    void createNotificationShouldRejectNonAdminWithCommonAccessDeniedException() {
        NotificationRequest request = new NotificationRequest(List.of(1L), "제목", "내용");

        assertThatThrownBy(() -> notificationController.createNotification(userDetails(UserRole.USER), request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("관리자만");
    }

    @Test
    void markAsReadShouldWrapResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails(UserRole.USER);
        NotificationResponse notification = notificationResponse();
        when(notificationService.markNotificationAsRead(10L, userDetails.getUser())).thenReturn(notification);

        ResponseEntity<CommonResponse<NotificationResponse>> response =
                notificationController.markAsRead(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(notification);
    }

    @Test
    void unreadCountShouldWrapCountInCommonResponse() {
        CustomUserDetails userDetails = userDetails(UserRole.USER);
        when(notificationService.countUnreadNotifications(userDetails.getUser())).thenReturn(3L);

        ResponseEntity<CommonResponse<Long>> response =
                notificationController.getUnreadNotificationCount(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(3L);
    }

    private CustomUserDetails userDetails(UserRole role) {
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .password("password")
                .nickname("user")
                .role(role)
                .subscriptionStatus("ACTIVE")
                .build();
        return new CustomUserDetails(user);
    }

    private NotificationResponse notificationResponse() {
        return new NotificationResponse(10L, "제목", "내용", true, LocalDateTime.of(2026, 7, 9, 10, 0));
    }
}

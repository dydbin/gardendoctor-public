package com.project.farming.domain.notification.controller;

import com.project.farming.domain.notification.outbox.FcmOutboxAdminService;
import com.project.farming.domain.notification.outbox.FcmOutboxAdminFilter;
import com.project.farming.domain.notification.outbox.FcmOutboxBulkRetryRequest;
import com.project.farming.domain.notification.outbox.FcmOutboxResponse;
import com.project.farming.domain.notification.outbox.FcmOutboxSourceType;
import com.project.farming.domain.notification.outbox.FcmOutboxStatus;
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
class FcmOutboxAdminControllerTest {

    @Mock
    private FcmOutboxAdminService fcmOutboxAdminService;

    private FcmOutboxAdminController fcmOutboxAdminController;

    @BeforeEach
    void setUp() {
        fcmOutboxAdminController = new FcmOutboxAdminController(fcmOutboxAdminService);
    }

    @Test
    void getFailedOutboxesShouldReturnCommonUnauthorizedBodyWhenPrincipalMissing() {
        ResponseEntity<CommonResponse<Page<FcmOutboxResponse>>> response =
                fcmOutboxAdminController.getFailedOutboxes(null, null, null, null, Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void getFailedOutboxesShouldRejectNonAdmin() {
        assertThatThrownBy(() -> fcmOutboxAdminController.getFailedOutboxes(userDetails(UserRole.USER), null, null, null, Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("관리자만");
    }

    @Test
    void getFailedOutboxesShouldWrapPageInCommonResponse() {
        Page<FcmOutboxResponse> page = new PageImpl<>(List.of(response()));
        FcmOutboxAdminFilter filter = new FcmOutboxAdminFilter(FcmOutboxSourceType.NOTIFICATION, 100L, 1L);
        when(fcmOutboxAdminService.getFailedOutboxes(filter, Pageable.unpaged())).thenReturn(page);

        ResponseEntity<CommonResponse<Page<FcmOutboxResponse>>> response =
                fcmOutboxAdminController.getFailedOutboxes(
                        userDetails(UserRole.ADMIN),
                        FcmOutboxSourceType.NOTIFICATION,
                        100L,
                        1L,
                        Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("실패 FCM outbox 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(page);
        verify(fcmOutboxAdminService).getFailedOutboxes(filter, Pageable.unpaged());
    }

    @Test
    void retryFailedOutboxShouldReturnAcceptedBecauseDeliveryIsAsync() {
        ResponseEntity<CommonResponse<Void>> response =
                fcmOutboxAdminController.retryFailedOutbox(userDetails(UserRole.ADMIN), 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("재시도 요청");
        verify(fcmOutboxAdminService).retryFailedOutbox(10L, 1L);
    }

    @Test
    void retryFailedOutboxShouldReturnCommonUnauthorizedBodyWhenPrincipalMissing() {
        ResponseEntity<CommonResponse<Void>> response =
                fcmOutboxAdminController.retryFailedOutbox(null, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void retryFailedOutboxesShouldReturnAcceptedWithRetriedCount() {
        FcmOutboxBulkRetryRequest request = new FcmOutboxBulkRetryRequest(List.of(10L, 11L));
        when(fcmOutboxAdminService.retryFailedOutboxes(List.of(10L, 11L), 1L)).thenReturn(2);

        ResponseEntity<CommonResponse<Integer>> response =
                fcmOutboxAdminController.retryFailedOutboxes(userDetails(UserRole.ADMIN), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(2);
        verify(fcmOutboxAdminService).retryFailedOutboxes(List.of(10L, 11L), 1L);
    }

    @Test
    void retryFailedOutboxesShouldReturnCommonUnauthorizedBodyWhenPrincipalMissing() {
        ResponseEntity<CommonResponse<Integer>> response =
                fcmOutboxAdminController.retryFailedOutboxes(null, new FcmOutboxBulkRetryRequest(List.of(10L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
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

    private FcmOutboxResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 12, 0);
        return new FcmOutboxResponse(
                10L,
                FcmOutboxSourceType.NOTIFICATION,
                100L,
                null,
                1L,
                "abcde...vwxyz",
                "제목",
                "내용",
                FcmOutboxStatus.FAILED,
                3,
                "FCM send failed",
                now.plusMinutes(10),
                now.minusHours(1),
                now
        );
    }
}

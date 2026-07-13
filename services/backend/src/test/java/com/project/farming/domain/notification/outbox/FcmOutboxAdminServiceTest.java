package com.project.farming.domain.notification.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmOutboxAdminServiceTest {

    @Mock
    private FcmOutboxRepository fcmOutboxRepository;

    @Mock
    private FcmOutboxRetryAuditRepository fcmOutboxRetryAuditRepository;

    private FcmOutboxAdminService fcmOutboxAdminService;

    @BeforeEach
    void setUp() {
        fcmOutboxAdminService = new FcmOutboxAdminService(fcmOutboxRepository, fcmOutboxRetryAuditRepository);
    }

    @Test
    void getFailedOutboxesShouldMaskTargetToken() {
        FcmOutboxAdminRow row = row("abcde12345vwxyz");
        when(fcmOutboxRepository.findAdminRowsByStatusAndFilters(
                FcmOutboxStatus.FAILED,
                null,
                null,
                null,
                Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<FcmOutboxResponse> result = fcmOutboxAdminService.getFailedOutboxes(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).maskedTargetToken()).isEqualTo("abcde...vwxyz");
        assertThat(result.getContent().get(0).toString()).doesNotContain("abcde12345vwxyz");
    }

    @Test
    void getFailedOutboxesShouldNotExposeShortRawToken() {
        FcmOutboxAdminRow row = row("short");
        when(fcmOutboxRepository.findAdminRowsByStatusAndFilters(
                FcmOutboxStatus.FAILED,
                null,
                null,
                null,
                Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<FcmOutboxResponse> result = fcmOutboxAdminService.getFailedOutboxes(Pageable.unpaged());

        assertThat(result.getContent().get(0).maskedTargetToken()).isEqualTo("****");
        assertThat(result.getContent().get(0).toString()).doesNotContain("short");
    }

    @Test
    void getFailedOutboxesShouldPassFilterToRepository() {
        FcmOutboxAdminFilter filter = new FcmOutboxAdminFilter(FcmOutboxSourceType.NOTIFICATION, 100L, 1L);
        when(fcmOutboxRepository.findAdminRowsByStatusAndFilters(
                FcmOutboxStatus.FAILED,
                FcmOutboxSourceType.NOTIFICATION,
                100L,
                1L,
                Pageable.unpaged()))
                .thenReturn(Page.empty());

        Page<FcmOutboxResponse> result = fcmOutboxAdminService.getFailedOutboxes(filter, Pageable.unpaged());

        assertThat(result).isEmpty();
        verify(fcmOutboxRepository).findAdminRowsByStatusAndFilters(
                FcmOutboxStatus.FAILED,
                FcmOutboxSourceType.NOTIFICATION,
                100L,
                1L,
                Pageable.unpaged());
    }

    @Test
    void retryFailedOutboxShouldResetFailedRowForWorkerRetry() {
        when(fcmOutboxRepository.retryFailedOutbox(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(1);

        fcmOutboxAdminService.retryFailedOutbox(10L, 1L);

        verify(fcmOutboxRepository).retryFailedOutbox(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.PENDING),
                any(LocalDateTime.class));
        verify(fcmOutboxRetryAuditRepository).save(argThat(audit ->
                audit.getFcmOutboxId().equals(10L)
                        && audit.getAdminUserId().equals(1L)
                        && audit.getPreviousStatus() == FcmOutboxStatus.FAILED
                        && audit.getResultStatus() == FcmOutboxStatus.PENDING
                        && audit.getReason().equals("MANUAL_RETRY")));
    }

    @Test
    void retryFailedOutboxShouldThrowNotFoundWhenRowDoesNotExist() {
        when(fcmOutboxRepository.retryFailedOutbox(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(0);
        when(fcmOutboxRepository.existsById(10L)).thenReturn(false);

        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutbox(10L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("FCM outbox를 찾을 수 없습니다");
        verify(fcmOutboxRetryAuditRepository, never()).save(any(FcmOutboxRetryAudit.class));
    }

    @Test
    void retryFailedOutboxShouldRejectNonFailedRows() {
        when(fcmOutboxRepository.retryFailedOutbox(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(FcmOutboxStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(0);
        when(fcmOutboxRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutbox(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실패 상태");
        verify(fcmOutboxRetryAuditRepository, never()).save(any(FcmOutboxRetryAudit.class));
    }

    @Test
    void retryFailedOutboxesShouldRetrySelectedIdsAndCreateAudits() {
        when(fcmOutboxRepository.retryFailedOutbox(
                any(Long.class),
                eq(FcmOutboxStatus.FAILED),
                eq(FcmOutboxStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(1);

        int retriedCount = fcmOutboxAdminService.retryFailedOutboxes(List.of(10L, 11L), 1L);

        assertThat(retriedCount).isEqualTo(2);
        verify(fcmOutboxRepository).retryFailedOutbox(eq(10L), eq(FcmOutboxStatus.FAILED), eq(FcmOutboxStatus.PENDING), any(LocalDateTime.class));
        verify(fcmOutboxRepository).retryFailedOutbox(eq(11L), eq(FcmOutboxStatus.FAILED), eq(FcmOutboxStatus.PENDING), any(LocalDateTime.class));
        verify(fcmOutboxRetryAuditRepository, times(2)).save(any(FcmOutboxRetryAudit.class));
    }

    @Test
    void retryFailedOutboxesShouldRejectEmptyDuplicateNullOrOversizedIds() {
        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutboxes(Collections.emptyList(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선택");
        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutboxes(List.of(10L, 10L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutboxes(List.of(10L, -1L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
        assertThatThrownBy(() -> fcmOutboxAdminService.retryFailedOutboxes(LongStream.rangeClosed(1, 501).boxed().toList(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 500건");

        verify(fcmOutboxRepository, never()).retryFailedOutbox(any(Long.class), any(FcmOutboxStatus.class), any(FcmOutboxStatus.class), any(LocalDateTime.class));
        verify(fcmOutboxRetryAuditRepository, never()).save(any(FcmOutboxRetryAudit.class));
    }

    private FcmOutboxAdminRow row(String targetToken) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 12, 0);
        return new FcmOutboxAdminRow(
                10L,
                FcmOutboxSourceType.NOTIFICATION,
                100L,
                null,
                1L,
                targetToken,
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

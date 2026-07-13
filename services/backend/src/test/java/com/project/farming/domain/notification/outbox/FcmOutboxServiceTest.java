package com.project.farming.domain.notification.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmOutboxServiceTest {

    @Mock
    private FcmOutboxRepository fcmOutboxRepository;

    private FcmOutboxService fcmOutboxService;

    @BeforeEach
    void setUp() {
        fcmOutboxService = new FcmOutboxService(fcmOutboxRepository);
    }

    @Test
    void enqueueNoticeShouldUseSetBasedRecipientInsert() {
        when(fcmOutboxRepository.insertNoticeOutboxes(1L)).thenReturn(2);

        int savedCount = fcmOutboxService.enqueueNotice(1L);

        assertThat(savedCount).isEqualTo(2);
        verify(fcmOutboxRepository).insertNoticeOutboxes(1L);
    }

    @Test
    void enqueueNoticeShouldRemainIdempotentWhenRepositoryReportsNoInsert() {
        when(fcmOutboxRepository.insertNoticeOutboxes(1L)).thenReturn(0);

        int savedCount = fcmOutboxService.enqueueNotice(1L);

        assertThat(savedCount).isZero();
        verify(fcmOutboxRepository).insertNoticeOutboxes(1L);
    }

    @Test
    void deleteNoticeOutboxesShouldRejectInFlightDelivery() {
        FcmOutbox processing = FcmOutbox.noticeBroadcast(1L, 10L, "token", "공지", "내용");
        org.springframework.test.util.ReflectionTestUtils.setField(
                processing, "status", FcmOutboxStatus.PROCESSING);
        when(fcmOutboxRepository.findByNoticeIdForUpdate(1L)).thenReturn(List.of(processing));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fcmOutboxService.deleteNoticeOutboxes(1L))
                .isInstanceOf(NoticeDeliveryInProgressException.class);

        verify(fcmOutboxRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void deleteNoticeOutboxesShouldDeleteLockedNonProcessingRows() {
        FcmOutbox pending = FcmOutbox.noticeBroadcast(1L, 10L, "token", "공지", "내용");
        when(fcmOutboxRepository.findByNoticeIdForUpdate(1L)).thenReturn(List.of(pending));

        fcmOutboxService.deleteNoticeOutboxes(1L);

        verify(fcmOutboxRepository).deleteAllInBatch(List.of(pending));
    }

    @Test
    void enqueueNotificationShouldSaveSingleNotificationPushOutbox() {
        when(fcmOutboxRepository.upsertNotificationOutbox(100L, 1L, "token-1", "제목", "내용"))
                .thenReturn(1);

        int savedCount = fcmOutboxService.enqueueNotification(100L, 1L, "token-1", "제목", "내용");

        assertThat(savedCount).isEqualTo(1);
        verify(fcmOutboxRepository).upsertNotificationOutbox(100L, 1L, "token-1", "제목", "내용");
    }

    @Test
    void enqueueNotificationShouldSkipMissingToken() {
        int savedCount = fcmOutboxService.enqueueNotification(100L, 1L, " ", "제목", "내용");

        assertThat(savedCount).isZero();
        verify(fcmOutboxRepository, never()).upsertNotificationOutbox(
                anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void enqueueNotificationShouldSkipDuplicateNotificationOutbox() {
        when(fcmOutboxRepository.upsertNotificationOutbox(100L, 1L, "token-1", "제목", "내용"))
                .thenReturn(0);

        int savedCount = fcmOutboxService.enqueueNotification(100L, 1L, "token-1", "제목", "내용");

        assertThat(savedCount).isZero();
        verify(fcmOutboxRepository).upsertNotificationOutbox(100L, 1L, "token-1", "제목", "내용");
    }
}

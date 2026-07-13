package com.project.farming.domain.notification.service;

import com.project.farming.domain.notification.dto.NoticeResponse;
import com.project.farming.domain.notification.entity.Notice;
import com.project.farming.domain.notification.outbox.FcmOutboxService;
import com.project.farming.domain.notification.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private FcmOutboxService fcmOutboxService;

    @Mock
    private NotificationService notificationService;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(
                noticeRepository,
                notificationService,
                fcmOutboxService
        );
    }

    @Test
    void findAllNoticesShouldUsePagedProjectionRead() {
        Pageable pageable = PageRequest.of(1, 20);
        Page<NoticeResponse> noticePage = new PageImpl<>(List.of(noticeResponse()), pageable, 30);
        when(noticeRepository.findResponsePageByOrderByNoticeIdAsc(pageable)).thenReturn(noticePage);

        Page<NoticeResponse> result = noticeService.findAllNotices(pageable);

        assertThat(result).isSameAs(noticePage);
        verify(noticeRepository).findResponsePageByOrderByNoticeIdAsc(pageable);
    }

    @Test
    void findNoticesByTitleShouldUsePrefixPatternAndPageableProjection() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<NoticeResponse> noticePage = new PageImpl<>(List.of(noticeResponse()), pageable, 1);
        when(noticeRepository.findResponsePageByTitleKeywordOrderByNoticeIdAsc("공지%", pageable))
                .thenReturn(noticePage);

        Page<NoticeResponse> result = noticeService.findNoticesByKeyword("title", " 공지 ", pageable);

        assertThat(result).isSameAs(noticePage);
        verify(noticeRepository).findResponsePageByTitleKeywordOrderByNoticeIdAsc("공지%", pageable);
    }

    @Test
    void findNoticesByContentShouldUsePrefixPatternAndPageableProjection() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<NoticeResponse> noticePage = new PageImpl<>(List.of(noticeResponse()), pageable, 1);
        when(noticeRepository.findResponsePageByContentPrefixOrderByNoticeIdAsc("공지%", pageable))
                .thenReturn(noticePage);

        Page<NoticeResponse> result = noticeService.findNoticesByKeyword("content", "공지", pageable);

        assertThat(result).isSameAs(noticePage);
        verify(noticeRepository).findResponsePageByContentPrefixOrderByNoticeIdAsc("공지%", pageable);
    }

    @Test
    void sendNoticeShouldPersistInAppNotificationsBeforeFcmOutboxEnqueue() {
        Notice notice = Notice.builder()
                .noticeId(1L)
                .title("공지")
                .content("내용")
                .isSent(false)
                .build();
        when(noticeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(notice));

        noticeService.sendNotice(1L);

        InOrder inOrder = inOrder(notificationService, fcmOutboxService);
        inOrder.verify(notificationService).saveNotice(1L, "공지", "내용");
        inOrder.verify(fcmOutboxService).enqueueNotice(1L);
        verify(noticeRepository, never()).save(any(Notice.class));
    }

    @Test
    void deleteNoticeShouldLockAndDeleteOutboxesBeforeOwnedRowsAndNotice() {
        Notice notice = Notice.builder()
                .noticeId(1L)
                .title("공지")
                .content("내용")
                .isSent(false)
                .build();
        when(noticeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(notice));

        noticeService.deleteNotice(1L);

        InOrder inOrder = inOrder(fcmOutboxService, notificationService, noticeRepository);
        inOrder.verify(fcmOutboxService).deleteNoticeOutboxes(1L);
        inOrder.verify(notificationService).deleteNotice(1L);
        inOrder.verify(noticeRepository).delete(notice);
    }

    private NoticeResponse noticeResponse() {
        return NoticeResponse.builder()
                .noticeId(1L)
                .title("공지")
                .content("내용")
                .isSent(false)
                .build();
    }
}

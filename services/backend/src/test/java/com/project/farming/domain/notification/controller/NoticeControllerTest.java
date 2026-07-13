package com.project.farming.domain.notification.controller;

import com.project.farming.domain.notification.dto.NoticeResponse;
import com.project.farming.domain.notification.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeControllerTest {

    @Mock
    private NoticeService noticeService;

    private NoticeController noticeController;

    @BeforeEach
    void setUp() {
        noticeController = new NoticeController(noticeService);
    }

    @Test
    void listPageShouldExposePagedNoticeModelAndKeepNoticeListCompatibility() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<NoticeResponse> noticePage = new PageImpl<>(List.of(noticeResponse()), pageable, 1);
        when(noticeService.findAllNotices(pageable)).thenReturn(noticePage);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = noticeController.showNoticeListPage(pageable, model);

        assertThat(view).isEqualTo("notice/notice-list");
        assertThat(model.get("noticePage")).isSameAs(noticePage);
        assertThat(model.get("noticeList")).isEqualTo(noticePage.getContent());
        assertThat(model.get("searchType")).isEqualTo("title");
        assertThat(model.get("keyword")).isEqualTo("");
        assertThat(model.get("isSearch")).isEqualTo(false);
        verify(noticeService).findAllNotices(pageable);
    }

    @Test
    void searchPageShouldPreserveSearchParametersForPaginationLinks() {
        Pageable pageable = PageRequest.of(1, 20);
        Page<NoticeResponse> noticePage = new PageImpl<>(List.of(noticeResponse()), pageable, 30);
        when(noticeService.findNoticesByKeyword("content", "공지", pageable)).thenReturn(noticePage);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = noticeController.showSearchNoticeListPage("content", "공지", pageable, model);

        assertThat(view).isEqualTo("notice/notice-list");
        assertThat(model.get("noticePage")).isSameAs(noticePage);
        assertThat(model.get("noticeList")).isEqualTo(noticePage.getContent());
        assertThat(model.get("searchType")).isEqualTo("content");
        assertThat(model.get("keyword")).isEqualTo("공지");
        assertThat(model.get("isSearch")).isEqualTo(true);
        verify(noticeService).findNoticesByKeyword("content", "공지", pageable);
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

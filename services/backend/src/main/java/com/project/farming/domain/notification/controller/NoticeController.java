package com.project.farming.domain.notification.controller;

import com.project.farming.domain.notification.command.NoticeCommand;
import com.project.farming.domain.notification.dto.NoticeRequest;
import com.project.farming.domain.notification.dto.NoticeResponse;
import com.project.farming.domain.notification.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notice Admin API", description = "공지사항 알림 관련 **관리자 전용** API")
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
@Controller
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/create")
    @Operation(summary = "새로운 공지사항 등록 페이지 (관리자 전용)",
            description = "새로운 공지사항을 등록하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showCreateNoticePage() {
        return "notice/create-notice";
    }

    @PostMapping("/createProc")
    @Operation(summary = "새로운 공지사항 등록 (관리자 전용)",
            description = "새로운 공지사항을 등록합니다. **관리자만 접근 가능합니다.**")
    public String createNotice(@Valid NoticeRequest request) {
        noticeService.saveNotice(toCommand(request));
        return "redirect:/admin/notices";
    }

    @GetMapping
    @Operation(summary = "전체 공지사항 목록 조회 페이지 (관리자 전용)",
            description = "DB에 등록된 공지사항을 ID 순으로 페이지 조회합니다. **관리자만 접근 가능합니다.**")
    public String showNoticeListPage(
            @PageableDefault(size = 20, sort = "noticeId", direction = Sort.Direction.ASC) Pageable pageable,
            Model model) {
        Page<NoticeResponse> noticePage = noticeService.findAllNotices(pageable);
        addNoticePageModel(model, noticePage, null, null);
        return "notice/notice-list";
    }

    @GetMapping("/search")
    @Operation(summary = "공지사항 목록 검색 (관리자 전용)",
            description = """
                    제목(title) 또는 내용(content)이 입력 키워드로 시작하는 공지사항을 ID 순으로 페이지 조회합니다.
                    **관리자만 접근 가능합니다.**
                    """)
    public String showSearchNoticeListPage(
            @Parameter(description = "검색 조건: 공지사항 제목(title) 또는 내용(content)")
            @RequestParam(defaultValue = "title") String searchType,
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "noticeId", direction = Sort.Direction.ASC) Pageable pageable,
            Model model) {
        Page<NoticeResponse> noticePage = noticeService.findNoticesByKeyword(searchType, keyword, pageable);
        addNoticePageModel(model, noticePage, searchType, keyword);
        return "notice/notice-list";
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "특정 공지사항 조회 페이지 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항을 조회합니다. **관리자만 접근 가능합니다.**")
    public String showNoticePage(@PathVariable Long noticeId, Model model) {
        NoticeResponse notice = noticeService.findNotice(noticeId);
        model.addAttribute("notice", notice);
        return "notice/notice-detail";
    }

    @GetMapping("/update")
    @Operation(summary = "특정 공지사항 수정 페이지 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항을 수정하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showUpdateNoticePage(@RequestParam Long noticeId, Model model) {
        NoticeResponse notice = noticeService.findNotice(noticeId);
        model.addAttribute("notice", notice);
        return "notice/update-notice";
    }

    @PostMapping("/update/{noticeId}")
    @Operation(summary = "특정 공지사항 수정 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항을 수정합니다. **관리자만 접근 가능합니다.**")
    public String updateNotice(@PathVariable Long noticeId, @Valid NoticeRequest request) {
        noticeService.updateNotice(noticeId, toCommand(request));
        return "redirect:/admin/notices/" + noticeId;
    }

    @GetMapping("/delete")
    @Operation(summary = "특정 공지사항 삭제 페이지 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항을 삭제하는 페이지 - **관리자만 접근 가능합니다.**")
    public String showDeleteNoticePage() {
        return "notice/delete-notice";
    }

    @PostMapping("/delete/{noticeId}")
    @Operation(summary = "특정 공지사항 삭제 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항을 삭제합니다. **관리자만 접근 가능합니다.**")
    public String deleteNotice(@PathVariable Long noticeId) {
        noticeService.deleteNotice(noticeId);
        return "redirect:/admin/notices";
    }

    @PostMapping("/send/{noticeId}")
    @Operation(summary = "특정 공지사항 알림 즉시 전송 (관리자 전용)",
            description = "공지사항 ID에 해당하는 공지사항 알림을 즉시 전송합니다. **관리자만 접근 가능합니다.**")
    public String sendNotice(@PathVariable Long noticeId) {
        noticeService.sendNotice(noticeId);
        return "redirect:/admin/notices/" + noticeId;
    }

    private NoticeCommand toCommand(NoticeRequest request) {
        return new NoticeCommand(
                request.getTitle(),
                request.getContent()
        );
    }

    private void addNoticePageModel(Model model, Page<NoticeResponse> noticePage, String searchType, String keyword) {
        model.addAttribute("noticePage", noticePage);
        model.addAttribute("noticeList", noticePage.getContent());
        model.addAttribute("searchType", searchType == null ? "title" : searchType);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("isSearch", keyword != null && !keyword.isBlank());
    }
}

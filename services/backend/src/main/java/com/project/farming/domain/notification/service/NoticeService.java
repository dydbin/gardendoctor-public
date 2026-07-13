package com.project.farming.domain.notification.service;

import com.project.farming.domain.notification.command.NoticeCommand;
import com.project.farming.domain.notification.dto.NoticeResponse;
import com.project.farming.domain.notification.entity.Notice;
import com.project.farming.domain.notification.outbox.FcmOutboxService;
import com.project.farming.domain.notification.repository.NoticeRepository;
import com.project.farming.global.exception.NoticeNotFoundException;
import com.project.farming.global.search.SearchKeywordPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NotificationService notificationService;
    private final FcmOutboxService fcmOutboxService;

    /**
     * 새로운 공지사항 등록
     *
     * @param command 등록할 공지사항 내용
     */
    @Transactional
    public void saveNotice(NoticeCommand command) {
        if (noticeRepository.existsByTitleAndContent(command.title(), command.content())) {
            log.error("이미 등록된 공지사항입니다: 제목 - {}, 내용 - {}", command.title(), command.content());
            throw new IllegalArgumentException(
                    "이미 등록된 공지사항입니다: 제목 - " + command.title() +  ", 내용 - " + command.content());
        }
        Notice newNotice = Notice.create(command.title(), command.content());
        noticeRepository.save(newNotice);
    }

    /**
     * 전체 공지사항 목록 조회(ID 순)
     *
     * @param pageable 페이지 조건
     * @return 각 공지사항의 페이지 응답
     */
    public Page<NoticeResponse> findAllNotices(Pageable pageable) {
        Page<NoticeResponse> noticePage = noticeRepository.findResponsePageByOrderByNoticeIdAsc(pageable);
        if (noticePage.isEmpty()) {
            log.info("등록된 공지사항이 없습니다.");
        }
        return noticePage;
    }

    /**
     * 공지사항 목록 검색(ID 순)
     * - 공지사항의 제목 또는 내용으로 검색
     *
     * @param searchType 검색 조건(title 또는 content) - 기본값은 title
     * @param keyword 검색어(제목 또는 내용)
     * @param pageable 페이지 조건
     * @return 검색된 공지사항의 페이지 응답
     */
    public Page<NoticeResponse> findNoticesByKeyword(String searchType, String keyword, Pageable pageable) {
        Page<NoticeResponse> noticePage = switch (searchType) {
            case "title" -> noticeRepository.findResponsePageByTitleKeywordOrderByNoticeIdAsc(
                    SearchKeywordPattern.prefix(keyword), pageable);
            case "content" -> noticeRepository.findResponsePageByContentPrefixOrderByNoticeIdAsc(
                    SearchKeywordPattern.prefix(keyword), pageable);
            default -> {
                log.error("지원하지 않는 검색 조건입니다: {}", searchType);
                throw new IllegalArgumentException("지원하지 않는 검색 조건입니다: " + searchType);
            }
        };
        return noticePage;
    }

    /**
     * 특정 공지사항 조회
     *
     * @param noticeId 조회할 공지사항의 ID
     * @return 해당 공지사항의 응답
     */
    public NoticeResponse findNotice(Long noticeId) {
        Notice foundNotice = findNoticeById(noticeId);
        return toNoticeResponseBuilder(foundNotice).build();
    }

    /**
     * 특정 공지사항 내용 수정
     *
     * @param noticeId 수정할 공지사항의 ID
     * @param command 새로 저장할 공지사항 내용
     */
    @Transactional
    public void updateNotice(Long noticeId, NoticeCommand command) {
        Notice notice = findNoticeById(noticeId);
        notice.updateNotice(command.title(), command.content());
        noticeRepository.save(notice);
    }

    /**
     * 특정 공지사항 삭제
     *
     * @param noticeId 삭제할 공지사항의 ID
     */
    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = findNoticeByIdForUpdate(noticeId);
        fcmOutboxService.deleteNoticeOutboxes(notice.getNoticeId());
        notificationService.deleteNotice(notice.getNoticeId());
        noticeRepository.delete(notice);
    }

    /**
     * 공지사항 알림 즉시 전송(전체 사용자 대상)
     *
     * @param noticeId 전송할 공지사항의 ID
     */
    @Transactional
    public void sendNotice(Long noticeId) {
        Notice notice = findNoticeByIdForUpdate(noticeId);
        // 각 사용자 별 공지 저장을 먼저 완료해 FCM 발송 후 인앱 알림이 없는 상태를 피합니다.
        notificationService.saveNotice(notice.getNoticeId(), notice.getTitle(), notice.getContent());
        fcmOutboxService.enqueueNotice(notice.getNoticeId());
    }

    /**
     * ID로 공지사항 조회
     *
     * @param noticeId 조회할 공지사항의 ID
     * @return 조회한 공지사항 내용
     */
    private Notice findNoticeById(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> {
                    log.error("해당 공지사항이 존재하지 않습니다: {}", noticeId);
                    return new NoticeNotFoundException("해당 공지사항이 존재하지 않습니다: " + noticeId);
                });
    }

    private Notice findNoticeByIdForUpdate(Long noticeId) {
        return noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException("해당 공지사항이 존재하지 않습니다: " + noticeId));
    }

    /**
     * 응답로 변환
     *
     * @param notice 응답로 변환할 공지사항 엔티티
     * @return 공지사항 응답
     */
    private NoticeResponse.NoticeResponseBuilder toNoticeResponseBuilder(Notice notice) {
        return NoticeResponse.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .isSent(notice.isSent())
                .sentAt(notice.getSentAt())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt());
    }
}

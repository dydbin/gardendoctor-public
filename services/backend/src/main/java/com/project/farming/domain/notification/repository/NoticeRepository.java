package com.project.farming.domain.notification.repository;

import com.project.farming.domain.notification.dto.NoticeResponse;
import com.project.farming.domain.notification.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notice n WHERE n.noticeId = :noticeId")
    Optional<Notice> findByIdForUpdate(@Param("noticeId") Long noticeId);
    boolean existsByTitleAndContent(String title, String content);

    @Query(
        value = """
        SELECT new com.project.farming.domain.notification.dto.NoticeResponse(
            n.noticeId,
            n.title,
            n.content,
            n.isSent,
            n.sentAt,
            n.createdAt,
            n.updatedAt
        )
        FROM Notice n
        ORDER BY n.noticeId ASC
        """,
        countQuery = """
        SELECT COUNT(n)
        FROM Notice n
        """
    )
    Page<NoticeResponse> findResponsePageByOrderByNoticeIdAsc(Pageable pageable);

    @Query(
        value = """
        SELECT new com.project.farming.domain.notification.dto.NoticeResponse(
            n.noticeId,
            n.title,
            n.content,
            n.isSent,
            n.sentAt,
            n.createdAt,
            n.updatedAt
        )
        FROM Notice n
        WHERE n.title LIKE :keyword ESCAPE '!'
        ORDER BY n.noticeId ASC
        """,
        countQuery = """
        SELECT COUNT(n)
        FROM Notice n
        WHERE n.title LIKE :keyword ESCAPE '!'
        """
    )
    Page<NoticeResponse> findResponsePageByTitleKeywordOrderByNoticeIdAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query(
        value = """
        SELECT new com.project.farming.domain.notification.dto.NoticeResponse(
            n.noticeId,
            n.title,
            n.content,
            n.isSent,
            n.sentAt,
            n.createdAt,
            n.updatedAt
        )
        FROM Notice n
        WHERE n.content LIKE :keyword ESCAPE '!'
        ORDER BY n.noticeId ASC
        """,
        countQuery = """
        SELECT COUNT(n)
        FROM Notice n
        WHERE n.content LIKE :keyword ESCAPE '!'
        """
    )
    Page<NoticeResponse> findResponsePageByContentPrefixOrderByNoticeIdAsc(
            @Param("keyword") String keyword, Pageable pageable);
}

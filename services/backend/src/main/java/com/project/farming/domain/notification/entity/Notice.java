package com.project.farming.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notices",
        indexes = {
                @Index(name = "idx_notice_title", columnList = "title"),
                @Index(name = "idx_notice_content", columnList = "content")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notice {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noticeId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title; // 공지사항 제목

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content; // 공지사항 내용

    @Column(nullable = false)
    private boolean isSent; // 알림 발송 여부

    private LocalDateTime sentAt; // 마지막 알림 발송 시간
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        validateText(title, content);
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        validateText(title, content);
        this.updatedAt = LocalDateTime.now();
    }

    public static Notice create(String title, String content) {
        validateText(title, content);
        return Notice.builder()
                .title(title)
                .content(content)
                .isSent(false)
                .build();
    }

    public void updateNotice(String title, String content) {
        validateText(title, content);
        this.title = title;
        this.content = content;
    }

    public void markAsSent() {
        this.isSent = true;
        this.sentAt = LocalDateTime.now();
    }

    public static void validateText(String title, String content) {
        validateRequiredLength("공지사항 제목", title, MAX_TITLE_LENGTH);
        validateRequiredLength("공지사항 내용", content, MAX_CONTENT_LENGTH);
    }

    private static void validateRequiredLength(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은 필수입니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
    }
}

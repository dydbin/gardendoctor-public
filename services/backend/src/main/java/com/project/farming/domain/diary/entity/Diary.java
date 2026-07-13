package com.project.farming.domain.diary.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "diaries", indexes = {
        @Index(name = "idx_diary_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_diary_user_date", columnList = "user_id, diary_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long diaryId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "dairy_image_file_id")
    private Long diaryImageFileId;

    @Column(nullable = false)
    private LocalDate diaryDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean watered;

    @Column(nullable = false)
    private boolean pruned;

    @Column(nullable = false)
    private boolean fertilized;

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDiary(String title, String content, LocalDate diaryDate, Long diaryImageFileId,
                            boolean watered, boolean pruned, boolean fertilized) {
        this.title = title;
        this.content = content;
        this.diaryDate = diaryDate;
        this.diaryImageFileId = diaryImageFileId;
        this.watered = watered;
        this.pruned = pruned;
        this.fertilized = fertilized;
    }

    public void setDiaryImageFileId(Long diaryImageFileId) {
        this.diaryImageFileId = diaryImageFileId;
    }
}

package com.project.farming.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_analysis", indexes = @Index(name = "idx_user_photo_analysis", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PhotoAnalysis {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long photoAnalysisId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "photo_image_file_id", nullable = false)
    private Long photoImageFileId;

    //summary
    @Column(columnDefinition = "TEXT")
    private String analysisSummary;

    private String detectedDisease; //탐지된 질병

    //solution
    @Column(columnDefinition = "TEXT")
    private String solution;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void updateAnalysisResult(String analysisSummary, String detectedDisease, String solution) {
        this.analysisSummary = analysisSummary;
        this.detectedDisease = detectedDisease;
        this.solution = solution;
    }
    public void updatePhotoImage(Long photoImageFileId) {
        this.photoImageFileId = photoImageFileId;
    }
}

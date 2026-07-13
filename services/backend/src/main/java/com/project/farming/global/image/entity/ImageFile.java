package com.project.farming.global.image.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "image_files", indexes = {
        @Index(name = "idx_image_domain_lookup",
                columnList = "domain_type, domain_id, image_file_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_file_id")
    private Long imageFileId;

    @Column
    private String originalImageName; // 원본 파일명

    @Column(unique = true)
    private String s3Key; // S3에 저장된 파일명

    @Column(nullable = false, length = 512) // URL 길이를 넉넉하게
    private String imageUrl; // S3 이미지 URL 또는 외부 이미지 URL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ImageDomainType domainType;

    @Column(nullable = false)
    private Long domainId;

    public void updateOriginalImageName(String originalImageName) {
        this.originalImageName = originalImageName;
    }

    public void updateS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}

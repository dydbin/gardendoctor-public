package com.project.farming.global.image.repository;

import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageFileRepository extends JpaRepository<ImageFile, Long> {
    List<ImageFile> findByDomainTypeAndDomainId(ImageDomainType domainType, Long domainId);
    Optional<ImageFile> findByImageFileIdAndDomainTypeAndDomainId(
            Long imageFileId, ImageDomainType domainType, Long domainId);
    Optional<ImageFile> findByS3Key(String s3Key);
}

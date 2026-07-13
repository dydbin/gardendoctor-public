package com.project.farming.global.image.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.project.farming.global.exception.ImageUploadException;
import com.project.farming.global.image.entity.DefaultImages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Service
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    public String uploadFile(MultipartFile file, String s3Key) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        try {
            amazonS3.putObject(bucketName, s3Key, file.getInputStream(), metadata);
        } catch (AmazonServiceException | IOException e) {
            log.error("S3 파일 업로드 중 오류 발생: {}", e.getMessage());
            throw new ImageUploadException("이미지 업로드에 실패했습니다.");
        }
        return amazonS3.getUrl(bucketName, s3Key).toString();
    }

    public void deleteFile(String s3Key) {
        if (DefaultImages.isDefaultImage(s3Key)) {
            log.info("기본 이미지는 삭제할 수 없습니다: {}", s3Key);
            return;
        }
        try {
            amazonS3.deleteObject(bucketName, s3Key);
            log.info("S3에서 이미지 객체 삭제: {}", s3Key);
        } catch (AmazonServiceException e) {
            log.warn("S3 파일 삭제 실패 (파일이 없거나 권한 문제 등): {}. DB에서만 제거합니다. 오류: {}", s3Key, e.getMessage());
        }
    }
}

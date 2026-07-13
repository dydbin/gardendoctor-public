package com.project.farming.domain.analysis.service;

import com.project.farming.domain.analysis.dto.AnalysisResultPayload;
import com.project.farming.domain.analysis.dto.PhotoAnalysisSidebarResponse;
import com.project.farming.domain.analysis.entity.PhotoAnalysis;
import com.project.farming.domain.analysis.repository.PhotoAnalysisRepository;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.AiAnalysisException;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoAnalysisService {

    private static final DateTimeFormatter RESPONSE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PhotoAnalysisRepository photoAnalysisRepository;
    private final UserRepository userRepository;
    private final ImageFileService imageFileService;
    private final PhotoAnalysisAiClient photoAnalysisAiClient;
    private final PhotoAnalysisRequestGuard requestGuard;

    public PhotoAnalysisSidebarResponse analyzePhotoAndSave(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("분석을 위해 사진 파일을 반드시 전송해야 합니다.");
        }
        requestGuard.acquire(userId);
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("유저를 찾을 수 없습니다. userId=" + userId);
        }

        ImageFile uploadedImage = imageFileService.uploadImage(file, ImageDomainType.PHOTO, userId);
        try {
            AnalysisResultPayload result = photoAnalysisAiClient.analyze(uploadedImage.getImageUrl());
            if (result == null) {
                throw new AiAnalysisException("AI 서버 분석 결과가 비어 있습니다.");
            }

            PhotoAnalysis saved = photoAnalysisRepository.save(PhotoAnalysis.builder()
                    .userId(userId)
                    .photoImageFileId(uploadedImage.getImageFileId())
                    .analysisSummary(result.getDisease_info() != null
                            ? result.getDisease_info().getSummary() : "정보 없음")
                    .detectedDisease(result.getDisease_info() != null
                            ? result.getDisease_info().getName() : "분석 실패")
                    .solution(result.getDisease_info() != null
                            ? result.getDisease_info().getSolution() : "정보 없음")
                    .build());
            return toSidebarResponse(saved, uploadedImage.getImageUrl());
        } catch (RuntimeException ex) {
            compensateUploadedImage(uploadedImage, ex);
            throw ex;
        }
    }

    private void compensateUploadedImage(ImageFile uploadedImage, RuntimeException originalFailure) {
        try {
            imageFileService.deleteImage(uploadedImage.getImageFileId());
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error("Photo analysis image compensation failed. imageFileId={}",
                    uploadedImage.getImageFileId(), cleanupFailure);
        }
    }

    private PhotoAnalysisSidebarResponse toSidebarResponse(PhotoAnalysis photoAnalysis, String imageUrl) {
        return PhotoAnalysisSidebarResponse.builder()
                .photoAnalysisId(photoAnalysis.getPhotoAnalysisId())
                .createdDate(photoAnalysis.getCreatedAt().format(RESPONSE_DATE_FORMATTER))
                .detectedDisease(photoAnalysis.getDetectedDisease())
                .analysisSummary(photoAnalysis.getAnalysisSummary())
                .solution(photoAnalysis.getSolution())
                .imageUrl(imageUrl)
                .build();
    }
}

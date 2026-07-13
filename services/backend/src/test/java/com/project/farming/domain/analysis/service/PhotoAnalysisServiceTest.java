package com.project.farming.domain.analysis.service;

import com.project.farming.domain.analysis.dto.AnalysisResultPayload;
import com.project.farming.domain.analysis.entity.PhotoAnalysis;
import com.project.farming.domain.analysis.repository.PhotoAnalysisRepository;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.ai.AiServiceTimeoutException;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoAnalysisServiceTest {

    @Mock
    private PhotoAnalysisRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ImageFileService imageFileService;
    @Mock
    private PhotoAnalysisAiClient aiClient;
    @Mock
    private PhotoAnalysisRequestGuard requestGuard;

    private PhotoAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new PhotoAnalysisService(
                repository,
                userRepository,
                imageFileService,
                aiClient,
                requestGuard
        );
    }

    @Test
    void aiFailureShouldCompensateAlreadyCommittedImageUpload() {
        MockMultipartFile file = file();
        ImageFile image = image();
        when(userRepository.existsById(7L)).thenReturn(true);
        when(imageFileService.uploadImage(file, ImageDomainType.PHOTO, 7L)).thenReturn(image);
        when(aiClient.analyze(image.getImageUrl()))
                .thenThrow(new AiServiceTimeoutException("timeout", new IllegalStateException()));

        assertThatThrownBy(() -> service.analyzePhotoAndSave(7L, file))
                .isInstanceOf(AiServiceTimeoutException.class);

        verify(imageFileService).deleteImage(91L);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulAnalysisShouldPersistAfterAiResponse() {
        MockMultipartFile file = file();
        ImageFile image = image();
        AnalysisResultPayload result = new AnalysisResultPayload(
                "leaf.jpg",
                98.0,
                new AnalysisResultPayload.DiseaseInfo("정상", "건강함", "없음"));
        PhotoAnalysis saved = PhotoAnalysis.builder()
                .photoAnalysisId(12L)
                .userId(7L)
                .photoImageFileId(91L)
                .detectedDisease("정상")
                .analysisSummary("건강함")
                .solution("없음")
                .createdAt(LocalDateTime.of(2026, 7, 10, 12, 0))
                .build();
        when(userRepository.existsById(7L)).thenReturn(true);
        when(imageFileService.uploadImage(file, ImageDomainType.PHOTO, 7L)).thenReturn(image);
        when(aiClient.analyze(image.getImageUrl())).thenReturn(result);
        when(repository.save(org.mockito.ArgumentMatchers.any(PhotoAnalysis.class))).thenReturn(saved);

        var response = service.analyzePhotoAndSave(7L, file);

        assertThat(response.getPhotoAnalysisId()).isEqualTo(12L);
        verify(imageFileService, never()).deleteImage(91L);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "leaf.jpg", "image/jpeg", "image".getBytes());
    }

    private ImageFile image() {
        return ImageFile.builder()
                .imageFileId(91L)
                .imageUrl("https://example.test/leaf.jpg")
                .build();
    }
}

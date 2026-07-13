package com.project.farming.global.image.service;

import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.exception.ImageFileNotFoundException;
import com.project.farming.global.image.dto.ImageUploadResponse;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageFileService {

    private final S3Service s3Service;
    private final ImageFileRepository imageFileRepository;

    /**
     * S3에 이미지를 업로드하고, ImageFile 엔티티를 생성하여 DB에 저장합니다.
     *
     * @param multipartFile 업로드할 이미지 파일
     * @param domainType    이미지가 속할 도메인 유형 (예: ImageDomainType.USER)
     * @param domainId      이미지가 속할 도메인 엔티티의 ID
     * @return 저장된 ImageFile 엔티티
     */
    @Transactional
    public ImageFile uploadImage(MultipartFile multipartFile, ImageDomainType domainType, Long domainId) {
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String originalFilename = multipartFile.getOriginalFilename();

        // S3에 저장될 고유한 파일명 생성 (UUID 사용)
        String s3Key = getS3Key(originalFilename, domainType, domainId);

        // S3에 파일 업로드
        String s3Url = s3Service.uploadFile(multipartFile, s3Key);
        deleteUploadedS3AfterRollback(s3Key, "uploadImage");

        // ImageFile 엔티티 생성 및 저장
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(originalFilename)
                .s3Key(s3Key)
                .imageUrl(s3Url)
                .domainType(domainType)
                .domainId(domainId)
                .build();

        return imageFileRepository.save(imageFile);
    }

    @Transactional
    public ImageUploadResponse uploadImageResponseForUser(
            MultipartFile multipartFile,
            ImageDomainType domainType,
            Long domainId,
            Long authenticatedUserId) {
        validatePublicUploadOwnership(domainType, domainId, authenticatedUserId);
        ImageFile uploadedImage = uploadImage(multipartFile, domainType, domainId);
        return ImageUploadResponse.builder()
                .imageFileId(uploadedImage.getImageFileId())
                .imageUrl(uploadedImage.getImageUrl())
                .message("이미지 업로드 성공")
                .build();
    }

    /**
     * S3에 업로드된 이미지를 수정하고, ImageFile 엔티티를 수정하여 DB에 저장합니다.
     *
     * @param oldImageFileId 수정할 ImageFile의 ID
     * @param newFile 업로드할 새로운 이미지 파일
     * @param domainType 이미지가 속할 도메인 유형 (예: ImageDomainType.PLANT)
     * @param domainId 이미지가 속할 도메인 엔티티의 ID
     * @return 수정된 ImageFile 엔티티
     */
    @Transactional
    public ImageFile updateImage(
            Long oldImageFileId,
            MultipartFile newFile, ImageDomainType domainType, Long domainId) {

        ImageFile oldImageFile = imageFileRepository.findById(oldImageFileId)
                .orElseThrow(() -> new ImageFileNotFoundException("존재하지 않는 이미지 파일입니다: " + oldImageFileId));
        String oldImageS3Key = oldImageFile.getS3Key();

        // 기본 이미지 수정 방지(S3, imageFile DB)
        if (DefaultImages.isDefaultImage(oldImageS3Key)) {
            log.info("기본 이미지는 수정할 수 없습니다. 대신 새 이미지를 업로드합니다. ImageFile ID: {}", oldImageFileId);
            // 기본 이미지는 수정이 안되므로, 새 파일이 들어오면 새로운 ImageFile을 생성하여 반환
            return uploadImage(newFile, domainType, domainId);
        }

        String newOriginalImageName = newFile.getOriginalFilename();
        String newS3Key = getS3Key(newOriginalImageName, domainType, domainId);
        String newImageUrl = s3Service.uploadFile(newFile, newS3Key);
        deleteUploadedS3AfterRollback(newS3Key, "updateImage");

        oldImageFile.updateOriginalImageName(newOriginalImageName);
        oldImageFile.updateS3Key(newS3Key);
        oldImageFile.updateImageUrl(newImageUrl);
        deleteS3AfterCommit(oldImageS3Key, oldImageFileId);
        return imageFileRepository.save(oldImageFile);
    }

    /**
     * ImageFile ID를 기반으로 이미지를 삭제합니다.
     * ImageFile에 S3 Key가 있는 경우 S3에서 객체를 삭제하고, DB에서도 ImageFile 레코드를 삭제합니다.
     *
     * @param imageFileId 삭제할 ImageFile의 ID
     */
    @Transactional
    public void deleteImage(Long imageFileId) {
        ImageFile imageFile = imageFileRepository.findById(imageFileId)
                .orElseThrow(() -> new ImageFileNotFoundException("존재하지 않는 이미지 파일입니다: " + imageFileId));

        deleteImageRecord(imageFile);
    }

    @Transactional
    public void deleteImageForUser(Long imageFileId, Long userId) {
        ImageFile imageFile = imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                        imageFileId, ImageDomainType.USER, userId)
                .orElseThrow(() -> new ImageFileNotFoundException("존재하지 않는 이미지 파일입니다: " + imageFileId));

        deleteImageRecord(imageFile);
    }

    private void deleteImageRecord(ImageFile imageFile) {
        Long imageFileId = imageFile.getImageFileId();

        // 기본 이미지 삭제 방지(S3, imageFile DB)
        if (imageFile.getS3Key() != null && DefaultImages.isDefaultImage(imageFile.getS3Key())) {
            log.info("기본 이미지는 삭제할 수 없습니다: {}", imageFileId);
            return;
        }

        // DB에서 ImageFile 레코드 삭제
        imageFileRepository.delete(imageFile);
        log.info("DB에서 ImageFile 레코드 삭제: imageFileId={}", imageFileId);
        deleteS3AfterCommit(imageFile.getS3Key(), imageFileId);
    }

    private void validatePublicUploadOwnership(
            ImageDomainType domainType, Long domainId, Long authenticatedUserId) {
        if (authenticatedUserId == null
                || domainId == null
                || domainType != ImageDomainType.USER
                || !Objects.equals(domainId, authenticatedUserId)) {
            throw new AccessDeniedException("공용 이미지 API에서는 본인의 프로필 이미지만 업로드할 수 있습니다.");
        }
    }

    /**
     * S3 키를 사용하여 ImageFile 엔티티를 조회합니다.
     * (예: DefaultImages의 S3 키로 기본 ImageFile을 찾을 때 사용)
     * @param s3Key S3 객체 키
     * @return 조회된 ImageFile Optional
     */
    public Optional<ImageFile> getImageFileByS3Key(String s3Key) {
        return imageFileRepository.findByS3Key(s3Key);
    }

    /**
     * 특정 도메인에 속하는 모든 이미지 파일을 조회합니다.
     * (예: 특정 유저가 업로드한 모든 이미지 조회 등)
     */
    public List<ImageFile> getImagesByDomainAndId(ImageDomainType domainType, Long domainId) {
        return imageFileRepository.findByDomainTypeAndDomainId(domainType, domainId);
    }

    private String getS3Key(String originalFilename, ImageDomainType domainType, Long domainId) {
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 기본 구조를 사용하려면: UUID.randomUUID().toString() + fileExtension;
        return domainType.name().toLowerCase() + "/" + domainId + "/" + UUID.randomUUID() + fileExtension;
    }

    private void deleteS3AfterCommit(String s3Key, Long imageFileId) {
        if (s3Key == null || s3Key.isBlank()) {
            log.info("S3 키가 없어 S3에서 파일을 삭제하지 않습니다. ImageFile ID: {}", imageFileId);
            return;
        }
        if (DefaultImages.isDefaultImage(s3Key)) {
            log.info("기본 이미지는 삭제할 수 없습니다: {}", imageFileId);
            return;
        }

        Runnable deleteTask = () -> {
            s3Service.deleteFile(s3Key);
            log.info("트랜잭션 커밋 후 S3에서 파일 삭제: s3Key={}", s3Key);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteTask.run();
                }
            });
            log.info("S3 파일 삭제를 트랜잭션 커밋 이후로 예약했습니다. ImageFile ID: {}", imageFileId);
            return;
        }
        deleteTask.run();
    }

    private void deleteUploadedS3AfterRollback(String s3Key, String operation) {
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }
        if (DefaultImages.isDefaultImage(s3Key)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn(
                    "활성 트랜잭션 동기화가 없어 S3 업로드 rollback 보상 삭제를 예약하지 못했습니다. operation={}, s3Key={}",
                    operation, s3Key);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    s3Service.deleteFile(s3Key);
                    log.info("트랜잭션 rollback 후 신규 업로드 S3 파일 보상 삭제: operation={}, s3Key={}", operation, s3Key);
                }
            }
        });
        log.info("S3 업로드 rollback 보상 삭제를 예약했습니다. operation={}, s3Key={}", operation, s3Key);
    }

    /**
     * PlantDataInitializer에서 사용
     * - 기본 식물 이미지 반환
     *
     * @return 기본 식물 이미지
     */
    public ImageFile getDefaultPlantImage() {
        return imageFileRepository.findByS3Key(DefaultImages.DEFAULT_PLANT_IMAGE)
                .orElseThrow(() -> new ImageFileNotFoundException("기본 식물 이미지가 존재하지 않습니다."));
    }

    /**
     * PlantDataInitializer에서 사용
     * - 각 식물의 이미지 저장
     *
     * @param plantImageUrl 저장할 식물 이미지 URL
     * @param plantId 식물 ID
     * @return 저장된 식물 이미지
     */
    @Transactional
    public ImageFile savePlantImage(String originalImageName, String plantImageUrl, Long plantId) {
        if (plantImageUrl.isBlank()) {
            return getDefaultPlantImage();
        }
        String s3Key = extractS3Key(plantImageUrl);
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(originalImageName)
                .s3Key(s3Key)
                .imageUrl(plantImageUrl)
                .domainType(ImageDomainType.PLANT)
                .domainId(plantId)
                .build();
        return imageFileRepository.save(imageFile);
    }

    /**
     * PlantDataInitializer에서 사용
     * - URL에서 s3Key 추출
     *
     * @param imageUrl 추출할 이미지 URL
     * @return 추출된 s3Key
     */
    private String extractS3Key(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            return url.getPath().substring(1); // 맨 앞의 '/' 제거
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid S3 URL: " + imageUrl, e);
        }
    }

    /**
     * ImageFileDataInitializer에서 사용
     * - 기본 이미지들 저장
     *
     * @param imageFileList 저장할 기본 이미지 목록
     */
    @Transactional
    public void saveDefaultImages(List<ImageFile> imageFileList) {
        imageFileRepository.saveAll(imageFileList);
    }
}

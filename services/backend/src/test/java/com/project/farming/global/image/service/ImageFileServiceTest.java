package com.project.farming.global.image.service;

import com.project.farming.global.exception.AccessDeniedException;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.exception.ImageFileNotFoundException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageFileServiceTest {

    private final S3Service s3Service = mock(S3Service.class);
    private final ImageFileRepository imageFileRepository = mock(ImageFileRepository.class);
    private final ImageFileService imageFileService = new ImageFileService(s3Service, imageFileRepository);

    @Test
    void uploadImageKeepsUploadedS3ObjectWhenTransactionCommits() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("profile.jpg");
        when(s3Service.uploadFile(eq(file), startsWith("user/1/")))
                .thenReturn("https://bucket/user/1/profile.jpg");
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<String> s3KeyCaptor = ArgumentCaptor.forClass(String.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.uploadImage(file, ImageDomainType.USER, 1L);

            verify(s3Service).uploadFile(eq(file), s3KeyCaptor.capture());
            verify(s3Service, never()).deleteFile(anyString());

            triggerAfterCommit();
            triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            verify(s3Service, never()).deleteFile(s3KeyCaptor.getValue());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadImageDeletesUploadedS3ObjectWhenTransactionRollsBack() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("profile.jpg");
        when(s3Service.uploadFile(eq(file), startsWith("user/1/")))
                .thenReturn("https://bucket/user/1/profile.jpg");
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<String> s3KeyCaptor = ArgumentCaptor.forClass(String.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.uploadImage(file, ImageDomainType.USER, 1L);

            verify(s3Service).uploadFile(eq(file), s3KeyCaptor.capture());
            verify(s3Service, never()).deleteFile(anyString());

            triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(s3Service).deleteFile(s3KeyCaptor.getValue());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadImageResponseForUserShouldRejectEveryNonUserDomainBeforeMutation() {
        MultipartFile file = mock(MultipartFile.class);

        for (ImageDomainType domainType : ImageDomainType.values()) {
            if (domainType == ImageDomainType.USER) {
                continue;
            }
            assertThatThrownBy(() -> imageFileService.uploadImageResponseForUser(
                    file, domainType, 1L, 1L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(s3Service);
        verify(imageFileRepository, never()).save(any(ImageFile.class));
    }

    @Test
    void uploadImageResponseForUserShouldRejectAnotherUsersProfileBeforeMutation() {
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> imageFileService.uploadImageResponseForUser(
                file, ImageDomainType.USER, 2L, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(s3Service);
        verify(imageFileRepository, never()).save(any(ImageFile.class));
    }

    @Test
    void uploadImageResponseForUserShouldRejectMissingOwnershipIdentifiersBeforeMutation() {
        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> imageFileService.uploadImageResponseForUser(
                file, ImageDomainType.USER, null, 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> imageFileService.uploadImageResponseForUser(
                file, ImageDomainType.USER, 1L, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> imageFileService.uploadImageResponseForUser(
                file, null, 1L, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(s3Service);
        verify(imageFileRepository, never()).save(any(ImageFile.class));
    }

    @Test
    void uploadImageResponseForUserShouldAllowOwnProfileImage() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("profile.jpg");
        when(s3Service.uploadFile(eq(file), startsWith("user/1/")))
                .thenReturn("https://bucket/user/1/profile.jpg");
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = imageFileService.uploadImageResponseForUser(
                file, ImageDomainType.USER, 1L, 1L);

        assertThat(response.getImageUrl()).isEqualTo("https://bucket/user/1/profile.jpg");
        verify(s3Service).uploadFile(eq(file), startsWith("user/1/"));
        verify(imageFileRepository).save(any(ImageFile.class));
    }

    @Test
    void deleteImageDeletesDatabaseRowImmediatelyButDefersS3DeleteUntilCommit() {
        ImageFile imageFile = imageFile(1L, "user/1/profile.jpg");
        when(imageFileRepository.findById(1L)).thenReturn(Optional.of(imageFile));

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.deleteImage(1L);

            verify(imageFileRepository).delete(imageFile);
            verify(s3Service, never()).deleteFile(anyString());

            triggerAfterCommit();

            verify(s3Service).deleteFile("user/1/profile.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteImageDoesNotDeleteS3ObjectWhenTransactionRollsBack() {
        ImageFile imageFile = imageFile(2L, "diary/2/photo.jpg");
        when(imageFileRepository.findById(2L)).thenReturn(Optional.of(imageFile));

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.deleteImage(2L);

            verify(imageFileRepository).delete(imageFile);
            verify(s3Service, never()).deleteFile(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(s3Service, never()).deleteFile(anyString());
    }

    @Test
    void updateImageUploadsNewObjectButDefersOldObjectDeleteUntilCommit() {
        ImageFile oldImageFile = imageFile(3L, "userplant/3/old.jpg");
        MultipartFile newFile = mock(MultipartFile.class);
        when(newFile.getOriginalFilename()).thenReturn("new.jpg");
        when(imageFileRepository.findById(3L)).thenReturn(Optional.of(oldImageFile));
        when(s3Service.uploadFile(eq(newFile), startsWith("userplant/3/")))
                .thenReturn("https://bucket/userplant/3/new.jpg");

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.updateImage(3L, newFile, ImageDomainType.USERPLANT, 3L);

            verify(s3Service).uploadFile(eq(newFile), startsWith("userplant/3/"));
            verify(s3Service, never()).deleteFile("userplant/3/old.jpg");
            verify(imageFileRepository).save(oldImageFile);

            triggerAfterCommit();

            verify(s3Service).deleteFile("userplant/3/old.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateImageDeletesNewObjectOnRollbackAndKeepsOldObject() {
        ImageFile oldImageFile = imageFile(5L, "userplant/5/old.jpg");
        MultipartFile newFile = mock(MultipartFile.class);
        when(newFile.getOriginalFilename()).thenReturn("new.jpg");
        when(imageFileRepository.findById(5L)).thenReturn(Optional.of(oldImageFile));
        when(s3Service.uploadFile(eq(newFile), startsWith("userplant/5/")))
                .thenReturn("https://bucket/userplant/5/new.jpg");

        ArgumentCaptor<String> s3KeyCaptor = ArgumentCaptor.forClass(String.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.updateImage(5L, newFile, ImageDomainType.USERPLANT, 5L);

            verify(s3Service).uploadFile(eq(newFile), s3KeyCaptor.capture());
            verify(s3Service, never()).deleteFile(anyString());

            triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(s3Service).deleteFile(s3KeyCaptor.getValue());
            verify(s3Service, never()).deleteFile("userplant/5/old.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteImageKeepsDefaultImageUntouched() {
        ImageFile defaultImageFile = imageFile(4L, DefaultImages.DEFAULT_USER_IMAGE);
        when(imageFileRepository.findById(4L)).thenReturn(Optional.of(defaultImageFile));

        imageFileService.deleteImage(4L);

        verify(imageFileRepository, never()).delete(defaultImageFile);
        verify(s3Service, never()).deleteFile(anyString());
    }

    @Test
    void deleteImageForUserShouldHideAndKeepNonUserDomainImage() {
        ImageFile diaryImage = imageFile(6L, "diary/20/photo.jpg", ImageDomainType.DIARY, 20L);
        when(imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                6L, ImageDomainType.USER, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageFileService.deleteImageForUser(6L, 1L))
                .isInstanceOf(ImageFileNotFoundException.class);

        verify(imageFileRepository, never()).delete(diaryImage);
        verify(s3Service, never()).deleteFile(anyString());
    }

    @Test
    void deleteImageForUserShouldHideAndKeepAnotherUsersProfileImage() {
        ImageFile anotherUsersImage = imageFile(7L, "user/2/profile.jpg", ImageDomainType.USER, 2L);
        when(imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                7L, ImageDomainType.USER, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageFileService.deleteImageForUser(7L, 1L))
                .isInstanceOf(ImageFileNotFoundException.class);

        verify(imageFileRepository, never()).delete(anotherUsersImage);
        verify(s3Service, never()).deleteFile(anyString());
    }

    @Test
    void deleteImageForUserShouldDeleteOwnProfileImageAfterCommit() {
        ImageFile ownImage = imageFile(8L, "user/1/profile.jpg", ImageDomainType.USER, 1L);
        when(imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                8L, ImageDomainType.USER, 1L)).thenReturn(Optional.of(ownImage));

        TransactionSynchronizationManager.initSynchronization();
        try {
            imageFileService.deleteImageForUser(8L, 1L);

            verify(imageFileRepository).delete(ownImage);
            verify(s3Service, never()).deleteFile(anyString());

            triggerAfterCommit();

            verify(s3Service).deleteFile("user/1/profile.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ImageFile imageFile(Long imageFileId, String s3Key) {
        return imageFile(imageFileId, s3Key, ImageDomainType.USER, 1L);
    }

    private ImageFile imageFile(
            Long imageFileId, String s3Key, ImageDomainType domainType, Long domainId) {
        return ImageFile.builder()
                .imageFileId(imageFileId)
                .originalImageName("image.jpg")
                .s3Key(s3Key)
                .imageUrl("https://bucket/" + s3Key)
                .domainType(domainType)
                .domainId(domainId)
                .build();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private void triggerAfterCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}

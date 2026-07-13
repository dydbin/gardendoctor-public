package com.project.farming.domain.diary.service;

import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.diary.repository.DiaryRepository;
import com.project.farming.domain.diary.repository.DiaryUserPlantRepository;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import com.project.farming.domain.user.entity.User;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.pagination.CreatedAtIdCursor;
import com.project.farming.global.pagination.CreatedAtIdCursorCodec;
import com.project.farming.global.response.CursorPageResponse;
import com.project.farming.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DiaryService {

    private static final int MAX_CURSOR_PAGE_SIZE = 100;

    private final DiaryRepository diaryRepository;
    private final DiaryUserPlantRepository diaryUserPlantRepository;
    private final UserPlantRepository userPlantRepository;
    private final ImageFileService imageFileService;
    private final ImageFileRepository imageFileRepository;

    /**
     * 일지 생성
     */
    @Transactional
    public Diary createDiary(User user, String title, String content, LocalDate diaryDate,
                             MultipartFile imageFile, boolean watered, boolean pruned, boolean fertilized,
                             List<Long> selectedUserPlantIds) {
        // 새로운 Diary 엔티티 생성 및 기본 정보 설정
        Diary diary = Diary.builder()
                .userId(user.getUserId())
                .title(title)
                .content(content)
                .diaryDate(diaryDate)
                .watered(watered)
                .pruned(pruned)
                .fertilized(fertilized)
                .build();
        diaryRepository.save(diary);

        if (imageFile != null && !imageFile.isEmpty()) {
            ImageFile uploadedImage = imageFileService.uploadImage(imageFile, ImageDomainType.DIARY, diary.getDiaryId());
            diary.setDiaryImageFileId(uploadedImage.getImageFileId());
        }

        List<UserPlant> userPlants = findOwnedUserPlants(
                user.getUserId(), selectedUserPlantIds, "일부 선택된 식물을 찾을 수 없습니다.");
        recordCareCompletions(user.getUserId(), userPlants, watered, pruned, fertilized);
        saveDiaryUserPlantLinks(diary.getDiaryId(), userPlants);

        return diary;
    }

    /**
     * 일지 수정
     */
    @Transactional
    public Diary updateDiary(Long diaryId, User user, String title, String content, LocalDate diaryDate,
                             MultipartFile newImageFile, boolean deleteExistingImage,
                             boolean watered, boolean pruned, boolean fertilized,
                             List<Long> newUserPlantIds) {

        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new NoSuchElementException("해당 ID의 일지를 찾을 수 없습니다: " + diaryId));

        if (!diary.getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("해당 일지에 대한 수정 권한이 없습니다.");
        }

        Long currentImageFileId = diary.getDiaryImageFileId();
        if (newImageFile != null && !newImageFile.isEmpty()) {
            ImageFile uploadedImage = imageFileService.uploadImage(newImageFile, ImageDomainType.DIARY, diary.getDiaryId());
            diary.setDiaryImageFileId(uploadedImage.getImageFileId());
            diaryRepository.flush();
            if (currentImageFileId != null) {
                imageFileService.deleteImage(currentImageFileId);
            }
        } else if (deleteExistingImage && currentImageFileId != null) {
            diary.setDiaryImageFileId(null);
            diaryRepository.flush();
            imageFileService.deleteImage(currentImageFileId);
        }

        diary.updateDiary(title, content, diaryDate, diary.getDiaryImageFileId(), watered, pruned, fertilized);

        diaryUserPlantRepository.deleteByDiaryId(diary.getDiaryId());

        List<UserPlant> newUserPlants = findOwnedUserPlants(
                user.getUserId(), newUserPlantIds, "일부 선택된 식물을 찾을 수 없습니다 (수정).");
        recordCareCompletions(user.getUserId(), newUserPlants, watered, pruned, fertilized);
        saveDiaryUserPlantLinks(diary.getDiaryId(), newUserPlants);

        return diary;
    }

    /**
     * 일지 삭제
     */
    @Transactional
    public void deleteDiary(Long diaryId, User user) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new NoSuchElementException("해당 ID의 일지를 찾을 수 없습니다: " + diaryId));
        if (!diary.getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("해당 일지에 대한 삭제 권한이 없습니다.");
        }
        Long diaryImageFileId = diary.getDiaryImageFileId();
        diaryUserPlantRepository.deleteByDiaryId(diary.getDiaryId());
        diaryRepository.delete(diary);
        diaryRepository.flush();
        if (diaryImageFileId != null) {
            imageFileService.deleteImage(diaryImageFileId);
        }
    }

    /**
     * 특정 일지 조회
     */
    public DiaryResponse getDiaryById(Long diaryId, User user) {
        Diary diary = diaryRepository.findByDiaryIdAndUserId(diaryId, user.getUserId())
                .orElseThrow(() -> new NoSuchElementException("해당 ID의 일지를 찾을 수 없습니다: " + diaryId));
        return toResponse(diary);
    }

    /**
     * 특정 사용자의 모든 일지 조회 (캘린더 기본 뷰 - 최신순)
     */
    public PageResponse<DiaryResponse> getAllDiariesByUser(User user, Pageable pageable) {
        Slice<Diary> diaries = diaryRepository.findByUserIdOrderByCreatedAtDescDiaryIdDesc(
                user.getUserId(), PageRequestPolicy.stable(pageable));
        return toPageResponse(diaries);
    }

    public CursorPageResponse<DiaryResponse> getDiaryFeedByUser(User user, String encodedCursor, int size) {
        validateCursorPageSize(size);
        Pageable firstSlice = PageRequest.of(0, size);
        Slice<Diary> diaries;
        if (encodedCursor == null || encodedCursor.isBlank()) {
            diaries = diaryRepository.findByUserIdOrderByCreatedAtDescDiaryIdDesc(
                    user.getUserId(), firstSlice);
        } else {
            CreatedAtIdCursor cursor = CreatedAtIdCursorCodec.decode(encodedCursor);
            diaries = diaryRepository.findByUserIdBeforeCursor(
                    user.getUserId(), cursor.createdAt(), cursor.id(), firstSlice);
        }

        List<Diary> diaryContent = diaries.getContent();
        String nextCursor = null;
        if (diaries.hasNext() && !diaryContent.isEmpty()) {
            Diary lastDiary = diaryContent.get(diaryContent.size() - 1);
            nextCursor = CreatedAtIdCursorCodec.encode(
                    new CreatedAtIdCursor(lastDiary.getCreatedAt(), lastDiary.getDiaryId()));
        }
        return new CursorPageResponse<>(
                toResponses(diaryContent),
                size,
                diaries.hasNext(),
                nextCursor
        );
    }

    /**
     * 특정 사용자의 특정 기간 동안의 일지 조회 (캘린더 날짜별 정렬)
     */
    public PageResponse<DiaryResponse> getDiariesByUserAndDateRange(
            User user, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Slice<Diary> diaries = diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAscDiaryIdAsc(
                user.getUserId(), startDate, endDate, PageRequestPolicy.stable(pageable));
        return toPageResponse(diaries);
    }

    /**
     * 특정 사용자가 등록한 특정 UserPlant(사용자 식물)별 일지 조회 (닉네임 기반 태그 검색)
     */
    public PageResponse<DiaryResponse> getDiariesByUserAndUserPlant(
            User user, Long userPlantId, Pageable pageable) {
        if (!userPlantRepository.existsByUserIdAndUserPlantIdAndDeletedFalse(user.getUserId(), userPlantId)) {
            throw new NoSuchElementException("사용자에 대한 해당 식물을 찾을 수 없습니다: " + userPlantId);
        }
        Slice<Diary> diaries = diaryRepository.findByUserIdAndUserPlantId(
                user.getUserId(), userPlantId, PageRequestPolicy.stable(pageable));
        return toPageResponse(diaries);
    }

    /**
     * 특정 사용자가 등록한 여러 UserPlant 중 하나라도 포함된 일지 조회 (다중 태그 검색)
     */
    public PageResponse<DiaryResponse> getDiariesByUserAndUserPlants(
            User user, List<Long> userPlantIds, Pageable pageable) {
        List<UserPlant> userPlants = findOwnedUserPlants(
                user.getUserId(), userPlantIds, "일부 선택된 식물을 찾을 수 없습니다.");
        if (userPlants.isEmpty()) {
            return PageResponse.empty(PageRequestPolicy.stable(pageable));
        }
        List<Long> ownedUserPlantIds = userPlants.stream()
                .map(UserPlant::getUserPlantId)
                .collect(Collectors.toList());
        Slice<Diary> diaries = diaryRepository.findByUserIdAndUserPlantIds(
                user.getUserId(), ownedUserPlantIds, PageRequestPolicy.stable(pageable));
        return toPageResponse(diaries);
    }

    private DiaryResponse toResponse(Diary diary) {
        return toResponses(List.of(diary)).get(0);
    }

    private PageResponse<DiaryResponse> toPageResponse(Slice<Diary> diaries) {
        return PageResponse.from(diaries, toResponses(diaries.getContent()));
    }

    private List<DiaryResponse> toResponses(List<Diary> diaries) {
        if (diaries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> diaryIds = diaries.stream()
                .map(Diary::getDiaryId)
                .collect(Collectors.toList());
        Map<Long, List<Long>> connectedUserPlantIdsByDiaryId = diaryUserPlantRepository.findByDiaryIdIn(diaryIds).stream()
                .collect(Collectors.groupingBy(
                        DiaryUserPlant::getDiaryId,
                        Collectors.mapping(DiaryUserPlant::getUserPlantId, Collectors.toList())
                ));

        List<Long> diaryImageFileIds = diaries.stream()
                .map(Diary::getDiaryImageFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> imageUrlByImageFileId = diaryImageFileIds.isEmpty()
                ? Collections.emptyMap()
                : imageFileRepository.findAllById(diaryImageFileIds).stream()
                .collect(Collectors.toMap(ImageFile::getImageFileId, ImageFile::getImageUrl));

        return diaries.stream()
                .map(diary -> toResponse(diary, connectedUserPlantIdsByDiaryId, imageUrlByImageFileId))
                .collect(Collectors.toList());
    }

    private void validateCursorPageSize(int size) {
        if (size < 1 || size > MAX_CURSOR_PAGE_SIZE) {
            throw new IllegalArgumentException("cursor page size는 1 이상 100 이하여야 합니다.");
        }
    }

    private DiaryResponse toResponse(
            Diary diary,
            Map<Long, List<Long>> connectedUserPlantIdsByDiaryId,
            Map<Long, String> imageUrlByImageFileId) {
        return new DiaryResponse(
                diary.getDiaryId(),
                diary.getUserId(),
                diary.getTitle(),
                diary.getContent(),
                diary.getDiaryDate(),
                imageUrlByImageFileId.get(diary.getDiaryImageFileId()),
                diary.isWatered(),
                diary.isPruned(),
                diary.isFertilized(),
                diary.getCreatedAt(),
                diary.getUpdatedAt(),
                connectedUserPlantIdsByDiaryId.getOrDefault(diary.getDiaryId(), Collections.emptyList())
        );
    }

    private List<UserPlant> findOwnedUserPlants(Long userId, List<Long> userPlantIds, String errorMessage) {
        if (userPlantIds == null || userPlantIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> uniqueUserPlantIds = userPlantIds.stream()
                .distinct()
                .collect(Collectors.toList());
        List<UserPlant> userPlants = userPlantRepository.findAllByUserIdAndUserPlantIdIn(userId, uniqueUserPlantIds);
        if (userPlants.size() != uniqueUserPlantIds.size()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return userPlants;
    }

    private void saveDiaryUserPlantLinks(Long diaryId, List<UserPlant> userPlants) {
        if (userPlants.isEmpty()) {
            return;
        }
        List<DiaryUserPlant> diaryUserPlants = userPlants.stream()
                .map(userPlant -> DiaryUserPlant.builder()
                        .diaryId(diaryId)
                        .userPlantId(userPlant.getUserPlantId())
                        .build())
                .collect(Collectors.toList());
        diaryUserPlantRepository.saveAll(diaryUserPlants);
    }

    private void recordCareCompletions(
            Long userId,
            List<UserPlant> userPlants,
            boolean watered,
            boolean pruned,
            boolean fertilized) {
        if (userPlants.isEmpty()) {
            return;
        }
        List<Long> userPlantIds = userPlants.stream()
                .map(UserPlant::getUserPlantId)
                .toList();
        int updatedRows = 0;
        if (watered) {
            updatedRows += userPlantRepository.recordWateringCompletion(userId, userPlantIds);
        }
        if (pruned) {
            updatedRows += userPlantRepository.recordPruningCompletion(userId, userPlantIds);
        }
        if (fertilized) {
            updatedRows += userPlantRepository.recordFertilizingCompletion(userId, userPlantIds);
        }
        log.debug(
                "사용자 식물 관리 완료 상태를 갱신했습니다. userId={}, requestedPlants={}, updatedRows={}",
                userId,
                userPlantIds.size(),
                updatedRows
        );
    }
}

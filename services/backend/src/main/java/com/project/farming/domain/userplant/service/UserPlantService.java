package com.project.farming.domain.userplant.service;

import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.domain.userplant.command.UserPlantCommand;
import com.project.farming.domain.userplant.dto.UserPlantDetailResponse;
import com.project.farming.domain.userplant.dto.UserPlantListResponse;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.domain.plant.repository.PlantRepository;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.*;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.response.PageResponse;
import com.project.farming.global.search.SearchKeywordPattern;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserPlantService {

    private final UserPlantRepository userPlantRepository;
    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final FarmRepository farmRepository;
    private final ImageFileService imageFileService;
    private final ImageFileRepository imageFileRepository;

    /**
     * 사용자 식물 정보 등록
     *
     * @param userId 사용자 ID
     * @param command 등록할 사용자 식물 정보
     * @param file 업로드할 사용자 식물 이미지 파일 (선택적)
     * @return 저장된 사용자 식물 정보의 응답
     */
    @Transactional
    public UserPlantDetailResponse saveUserPlant(Long userId, UserPlantCommand command, MultipartFile file) {
        validateUserExists(userId);
        if (userPlantRepository.existsByUserIdAndPlantNicknameAndDeletedFalse(userId, command.plantNickname())) {
            throw new IllegalArgumentException("이미 등록된 사용자 식물입니다: " + command.plantNickname());
        }
        Plant plant = findPlantByPlantName(command.plantName());
        String plantName = getPlantName(plant.getPlantName(), command.plantName());
        Farm farm = findFarmByGardenUniqueId(command.gardenUniqueId());
        String plantingPlace = getPlantingPlace(farm.getFarmName(), farm.getLotNumberAddress(), command.plantingPlace());
        ImageFile defaultImageFile = imageFileRepository.findByS3Key(DefaultImages.DEFAULT_PLANT_IMAGE)
                .orElseThrow(() -> new ImageFileNotFoundException("기본 식물 이미지가 존재하지 않습니다."));
        UserPlant newUserPlant = UserPlant.builder()
                .userId(userId)
                .plantId(plant.getPlantId())
                .plantName(plantName)
                .plantNickname(command.plantNickname())
                .farmId(farm.getFarmId())
                .plantingPlace(plantingPlace)
                .plantedDate(command.plantedDate())
                .notes(command.notes())
                .isNotificationEnabled(command.notificationEnabled())
                .waterIntervalDays(command.waterIntervalDays())
                .watered(command.watered())
                .pruneIntervalDays(command.pruneIntervalDays())
                .pruned(command.pruned())
                .fertilizeIntervalDays(command.fertilizeIntervalDays())
                .fertilized(command.fertilized())
                .userPlantImageFileId(defaultImageFile.getImageFileId())
                .build();
        newUserPlant.replaceDailyCareStatus(command.watered(), command.pruned(), command.fertilized());
        UserPlant savedUserPlant = userPlantRepository.save(newUserPlant);
        Long userPlantId = savedUserPlant.getUserPlantId();

        if (file != null && !file.isEmpty()) {
            // 이미지 파일이 첨부되어 있는 경우
            ImageFile imageFile = imageFileService.uploadImage(file, ImageDomainType.USERPLANT, userPlantId);
            savedUserPlant.updateUserPlantImage(imageFile.getImageFileId());
        }
        return findUserPlant(userId, userPlantId);
    }

    /**
     * 사용자 식물 목록 조회(별명순)
     * - 일부 정보만 반환
     *
     * @param userId 사용자 ID
     * @return 각 사용자 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<UserPlantListResponse> findAllUserPlants(Long userId, Pageable pageable) {
        validateUserExists(userId);
        return PageResponse.from(
                userPlantRepository.findListResponsesByUserIdOrderByPlantNicknameAsc(
                        userId, PageRequestPolicy.stable(pageable)));
    }

    /**
     * 사용자 식물 목록 검색(별명순)
     * - 사용자 식물의 종류(Plant) 또는 별명으로 검색(통합)
     * - 일부 정보만 반환
     *
     * @param userId 사용자 ID
     * @param keyword 검색어(사용자 식물 별명)
     * @return 검색된 사용자 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<UserPlantListResponse> findUserPlantsByKeyword(
            Long userId, String keyword, Pageable pageable) {
        return PageResponse.from(userPlantRepository.findListResponsesByUserAndKeywordOrderByPlantNicknameAsc(
                userId,
                SearchKeywordPattern.prefix(keyword),
                PageRequestPolicy.stable(pageable)));
    }

    /**
     * 특정 사용자 식물 정보 조회
     * - 식물(Plant) 정보도 반환
     *
     * @param userId 사용자 ID
     * @param userPlantId 조회할 사용자 식물 정보의 ID
     * @return 해당 사용자 식물 정보의 응답
     */
    @Transactional(readOnly = true)
    public UserPlantDetailResponse findUserPlant(Long userId, Long userPlantId) {
        validateUserExists(userId);
        return userPlantRepository.findDetailResponseByUserIdAndUserPlantId(userId, userPlantId)
                .orElseThrow(() -> new UserPlantNotFoundException("사용자(" + userId + ")가 등록하지 않은 식물입니다: " + userPlantId));
    }

    /**
     * 특정 사용자 식물 정보 수정
     *
     * @param userId 사용자 ID
     * @param userPlantId 수정할 사용자 식물 정보의 ID
     * @param command 새로 저장할 사용자 식물 정보
     * @param newFile 새로 업로드할 사용자 식물 이미지 파일 (선택적)
     * @return 수정된 사용자 식물 정보의 응답
     */
    @Transactional
    public UserPlantDetailResponse updateUserPlant(
            Long userId, Long userPlantId, UserPlantCommand command, MultipartFile newFile) {

        validateUserExists(userId);
        UserPlant userPlant = findUserPlantByUserIdAndUserPlantId(userId, userPlantId);
        if (newFile != null && !newFile.isEmpty()) {
            // 새로운 이미지 파일이 첨부되어 있는 경우
            ImageFile imageFile = imageFileService.updateImage(
                    userPlant.getUserPlantImageFileId(), // 기존 이미지 파일
                    newFile, ImageDomainType.USERPLANT, userPlantId);
            userPlant.updateUserPlantImage(imageFile.getImageFileId());
        }
        if (isOtherPlant(registeredPlantName(userPlant), command.plantName())) {
            // 사용자 입력 식물인 경우 수정
            if (!Objects.equals(command.plantName(), userPlant.getPlantName())) {
                userPlant.updatePlantName(command.plantName());
            }
        }
        Farm farm = findFarmByGardenUniqueId(command.gardenUniqueId());
        String plantingPlace = getPlantingPlace(farm.getFarmName(), farm.getLotNumberAddress(), command.plantingPlace());
        userPlant.updatePlantingPlace(farm.getFarmId(), plantingPlace);
        userPlant.updateUserPlantInfo(command.plantNickname(), command.notes());
        userPlant.updateIsNotificationEnabled(command.notificationEnabled());
        userPlant.updateUserPlantIntervalDays(
                command.waterIntervalDays(), command.pruneIntervalDays(), command.fertilizeIntervalDays());
        userPlant.replaceDailyCareStatus(command.watered(), command.pruned(), command.fertilized());
        UserPlant updatedUserPlant = userPlantRepository.save(userPlant);
        return findUserPlant(userId, updatedUserPlant.getUserPlantId());
    }

    /**
     * 특정 사용자 식물 정보 삭제
     *
     * @param userId 사용자 ID
     * @param userPlantId 삭제할 사용자 식물 정보의 ID
     */
    @Transactional
    public void deleteUserPlant(Long userId, Long userPlantId) {
        validateUserExists(userId);
        UserPlant userPlant = findUserPlantByUserIdAndUserPlantId(userId, userPlantId);
        userPlantRepository.delete(userPlant);
        userPlantRepository.flush();
    }

    private String registeredPlantName(UserPlant userPlant) {
        Plant plant = plantRepository.findById(userPlant.getPlantId())
                .orElseThrow(() -> new PlantNotFoundException("해당 식물이 존재하지 않습니다: " + userPlant.getPlantId()));
        return plant.getPlantName();
    }

    /**
     * ID로 사용자 조회
     *
     * @param userId 조회할 사용자의 ID
     * @return 조회한 사용자
     */
    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }
    }

    /**
     * 사용자 ID와 ID로 사용자 식물 정보 조회
     *
     * @param userId 사용자 ID
     * @param userPlantId 조회할 사용자 식물 정보의 ID
     * @return 조회한 사용자 식물 정보
     */
    private UserPlant findUserPlantByUserIdAndUserPlantId(Long userId, Long userPlantId) {
        return userPlantRepository.findByUserIdAndUserPlantIdAndDeletedFalse(userId, userPlantId)
                .orElseThrow(() -> new UserPlantNotFoundException("사용자(" + userId + ")가 등록하지 않은 식물입니다: " + userPlantId));
    }

    /**
     * 식물 이름으로 식물(Plant) 정보 조회
     *
     * @param plantName 조회할 식물의 이름(한글, 영어)
     * @return 조회한 식물(Plant) 정보
     */
    private Plant findPlantByPlantName(String plantName) {
        return plantRepository.findReferenceCandidatesForShare(plantName).stream()
                .findFirst()
                .orElseGet(() -> plantRepository.findOtherPlantForShare("기타")
                        .orElseThrow(() -> new PlantNotFoundException("DB에 '기타' 항목이 존재하지 않습니다.")));
    }

    /**
     * 식물 종류 설정
     *
     * @param oldPlantName 원래 식물 이름(Plant)
     * @param requestPlantName 사용자가 작성한 식물 이름(other)
     * @return 설정된 식물 이름
     */
    private String getPlantName(String oldPlantName, String requestPlantName) {
        String newPlantName = oldPlantName;
        if (Objects.equals(oldPlantName, "기타")) {
            newPlantName = requestPlantName;
        }
        return newPlantName;
    }

    /**
     * 텃밭 고유번호로 텃밭 정보 조회
     *
     * @param gardenUniqueId 조회할 텃밭의 고유번호
     * @return 조회한 텃밭 정보
     */
    private Farm findFarmByGardenUniqueId(int gardenUniqueId) {
        return farmRepository.findReferenceByGardenUniqueIdForShare(gardenUniqueId)
                .orElseGet(() -> farmRepository.findOtherFarmCandidatesForShare("기타(Other)").stream()
                        .findFirst()
                        .orElseThrow(() -> new FarmNotFoundException("DB에 '기타(Other)' 항목이 존재하지 않습니다.")));
    }

    /**
     * 심은 장소 설정
     *
     * @param oldFarmName 원래 텃밭 이름(Farm)
     * @param lotNumberAddress 텃밭 이름이 없는 경우에 사용할 이름(주소)
     * @param requestFarmName 사용자가 작성한 장소 이름(Other)
     * @return 설정된 심은 장소 이름
     */
    private String getPlantingPlace(
            String oldFarmName, String lotNumberAddress, String requestFarmName) {
        String plantingPlace = oldFarmName;
        if (Objects.equals(oldFarmName, "기타(Other)")) {
            plantingPlace = requestFarmName;
        }
        if (Objects.equals(plantingPlace, "N/A")) {
            plantingPlace = lotNumberAddress;
        }
        return plantingPlace;
    }

    /**
     * 사용자 입력 식물인지 아닌지 확인
     *
     * @param oldPlantName 기존 사용자 식물 종류 이름(Plant의 PlantName)
     * @param requestPlantName 사용자가 작성한 식물 이름(other)
     * @return 결과값(TF)
     */
    private boolean isOtherPlant(String oldPlantName, String requestPlantName) {
        return Objects.equals(oldPlantName, "기타")
                && !plantRepository.existsAnyByPlantName(requestPlantName)
                && !plantRepository.existsAnyByPlantEnglishName(requestPlantName);
    }
}

package com.project.farming.domain.plant.service;

import com.project.farming.domain.plant.command.PlantAdminCommand;
import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.repository.PlantRepository;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import com.project.farming.global.exception.ImageFileNotFoundException;
import com.project.farming.global.exception.PlantNotFoundException;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.search.SearchKeywordPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlantAdminService {

    private static final String OTHER_PLANT_NAME = "기타";

    private final PlantRepository plantRepository;
    private final ImageFileService imageFileService;
    private final ImageFileRepository imageFileRepository;
    private final UserPlantRepository userPlantRepository;

    /**
     * 새로운 식물 정보 등록
     *
     * @param command 등록할 식물 정보
     * @param file 업로드할 식물 이미지 파일 (선택적)
     */
    @Transactional
    public void savePlant(PlantAdminCommand command, MultipartFile file) {
        if (plantRepository.existsAnyByPlantName(command.plantName())) {
            log.error("이미 존재하는 식물입니다: {}", command.plantName());
            throw new IllegalArgumentException("이미 존재하는 식물입니다: " + command.plantName());
        }
        ImageFile defaultImageFile = getDefaultImageFile();
        Plant newPlant = Plant.builder()
                .plantName(getOrDefault(command.plantName()))
                .plantEnglishName(getOrDefault(command.plantEnglishName()))
                .species(getOrDefault(command.species()))
                .season(getOrDefault(command.season()))
                .plantImageFileId(defaultImageFile.getImageFileId())
                .build();
        Plant savedPlant = plantRepository.save(newPlant);
        Long plantId = savedPlant.getPlantId();

        if (file != null && !file.isEmpty()) {
            // 이미지 파일이 첨부되어 있는 경우
            ImageFile imageFile = imageFileService.uploadImage(file, ImageDomainType.PLANT, plantId);
            savedPlant.updatePlantImage(imageFile.getImageFileId());
        }
    }

    /**
     * 전체 식물 목록 조회(ID 순)
     *
     * @return 각 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public Page<PlantResponse> findAllPlants(Pageable pageable) {
        Page<PlantResponse> foundPlants = plantRepository.findAllAdminResponsesByOrderByPlantIdAsc(
                PageRequestPolicy.stable(pageable));
        if (foundPlants.isEmpty()) {
            log.info("등록된 식물이 없습니다.");
        }
        return foundPlants;
    }

    /**
     * 식물 목록 검색(ID 순)
     * - 식물의 한글 이름 또는 영어 이름으로 검색(통합)
     *
     * @param keyword 검색어(식물 이름)
     * @return 검색된 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public Page<PlantResponse> findPlantsByKeyword(String keyword, Pageable pageable) {
        return plantRepository.findAdminResponsesByKeywordOrderByPlantIdAsc(
                SearchKeywordPattern.prefix(keyword), PageRequestPolicy.stable(pageable));
    }

    /**
     * 특정 식물 정보 수정
     *
     * @param plantId 수정할 식물 정보의 ID
     * @param command 새로 저장할 식물 정보
     * @param newFile 새로 업로드할 식물 이미지 파일 (선택적)
     */
    @Transactional
    public void updatePlant(Long plantId, PlantAdminCommand command, MultipartFile newFile) {
        Plant plant = findPlantByIdForUpdate(plantId);
        String nextPlantName = getOrDefault(command.plantName());
        if (OTHER_PLANT_NAME.equals(plant.getPlantName()) && !OTHER_PLANT_NAME.equals(nextPlantName)) {
            throw new IllegalArgumentException("기본 식물의 이름은 변경할 수 없습니다.");
        }
        if (newFile != null && !newFile.isEmpty()) {
            // 새로운 이미지 파일이 첨부되어 있는 경우
            ImageFile imageFile = imageFileService.updateImage(
                    plant.getPlantImageFileId(), // 기존 이미지 파일
                    newFile, ImageDomainType.PLANT, plantId);
            plant.updatePlantImage(imageFile.getImageFileId());
        }
        plant.updatePlantInfo(nextPlantName,
                getOrDefault(command.plantEnglishName()),
                getOrDefault(command.species()), getOrDefault(command.season()));
        plantRepository.save(plant);
    }

    /**
     * 특정 식물 정보 삭제
     * - 삭제할 식물과 매핑된 userPlant가 있다면
     *   해당 userPlant의 식물을 '기타'로 변경
     *
     * @param plantId 삭제할 식물 정보의 ID
     */
    @Transactional
    public void deletePlant(Long plantId) {
        Plant plant = findPlantByIdForUpdate(plantId);
        if (OTHER_PLANT_NAME.equals(plant.getPlantName())) {
            log.error("기본 식물 정보는 삭제할 수 없습니다.");
            throw new RuntimeException("기본 식물 정보는 삭제할 수 없습니다.");
        }
        Plant otherPlant = plantRepository.findOtherPlantForShare(OTHER_PLANT_NAME)
                .orElseThrow(() -> {
                    log.error("DB에 '기타' 항목이 존재하지 않습니다.");
                    return new PlantNotFoundException("DB에 '기타' 항목이 존재하지 않습니다.");
                });
        int updatedCount = userPlantRepository.reassignPlant(otherPlant.getPlantId(), plant.getPlantId());
        if (updatedCount == 0) log.info("해당 식물({})과 매핑된 사용자 식물이 없습니다.", plantId);
        else log.info(
                "해당 식물({})과 매핑된 사용자 식물 {}개의 식물 정보가 '기타'로 수정되었습니다.", plantId, updatedCount);
        plantRepository.delete(plant);
        plantRepository.flush();
    }

    /**
     * 식물 기본 이미지 반환
     *
     * @return 식물 기본 이미지
     */
    private ImageFile getDefaultImageFile() {
        return imageFileRepository.findByS3Key(DefaultImages.DEFAULT_PLANT_IMAGE)
                .orElseThrow(() -> {
                    log.error("기본 식물 이미지가 존재하지 않습니다.");
                    return new ImageFileNotFoundException("기본 식물 이미지가 존재하지 않습니다.");
                });
    }

    /**
     * 기본값 설정(String)
     *
     * @param val 확인할 값
     * @return 기본값 또는 request 값
     */
    private String getOrDefault(String val) {
        return val == null || val.isBlank() || val.equals("-") ? "N/A" : val;
    }

    /**
     * ID로 식물 정보 조회
     *
     * @param plantId 조회할 식물 정보의 ID
     * @return 조회한 식물 정보
     */
    private Plant findPlantByIdForUpdate(Long plantId) {
        return plantRepository.findByPlantIdForUpdate(plantId)
                .orElseThrow(() -> {
                    log.error("해당 식물이 존재하지 않습니다: {}", plantId);
                    return new PlantNotFoundException("해당 식물이 존재하지 않습니다: " + plantId);
                });
    }
}

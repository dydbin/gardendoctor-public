package com.project.farming.domain.plant.service;

import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.repository.PlantRepository;
import com.project.farming.global.exception.PlantNotFoundException;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.search.SearchKeywordPattern;
import com.project.farming.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlantService {

    private final PlantRepository plantRepository;
    private final ImageFileService imageFileService;

    /**
     * 전체 식물 목록 조회(이름순)
     *
     * @return 각 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<PlantResponse> findAllPlants(Pageable pageable) {
        var foundPlants = plantRepository.findAllPlantResponsesByOrderByPlantNameAsc(
                PageRequestPolicy.stable(pageable));
        if (foundPlants.isEmpty()) {
            log.info("등록된 식물이 없습니다.");
        }
        return PageResponse.from(foundPlants);
    }

    /**
     * 식물 목록 검색(이름순)
     * - 식물의 한글 이름 또는 영어 이름으로 검색(통합)
     *
     * @param keyword 검색어(식물 이름)
     * @return 검색된 식물 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<PlantResponse> findPlantsByKeyword(String keyword, Pageable pageable) {
        return PageResponse.from(plantRepository.findPlantResponsesByKeywordOrderByPlantNameAsc(
                SearchKeywordPattern.prefix(keyword), PageRequestPolicy.stable(pageable)));
    }

    /**
     * 특정 식물 정보 조회
     *
     * @param plantId 조회할 식물 정보의 ID
     * @return 해당 식물 정보의 응답
     */
    @Transactional(readOnly = true)
    public PlantResponse findPlant(Long plantId) {
        return plantRepository.findPlantResponseByPlantId(plantId)
                .orElseThrow(() -> new PlantNotFoundException("해당 식물이 존재하지 않습니다: " + plantId));
    }

    @Transactional
    public Plant saveSeedPlant(
            String plantName,
            String plantEnglishName,
            String species,
            String season,
            String originalImageName,
            String plantImageUrl) {
        ImageFile defaultImage = imageFileService.getDefaultPlantImage();
        Plant plant = plantRepository.saveAndFlush(Plant.builder()
                .plantName(plantName)
                .plantEnglishName(plantEnglishName)
                .species(species)
                .season(season)
                .plantImageFileId(defaultImage.getImageFileId())
                .build());

        ImageFile plantImage = imageFileService.savePlantImage(
                originalImageName,
                plantImageUrl,
                plant.getPlantId());
        plant.updatePlantImage(plantImage.getImageFileId());
        return plant;
    }
}

package com.project.farming.domain.plant.controller;

import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.service.PlantService;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Plant API", description = "AI 기능을 사용할 수 있는 식물 관련 API")
@SecurityRequirement(name = "jwtAuth")
@RequestMapping("/api/plants")
@RequiredArgsConstructor
@RestController
public class PlantController {

    private final PlantService plantService;

    @GetMapping
    @Operation(summary = "전체 식물 목록 조회", description = "DB에 등록된 모든 식물을 이름순으로 조회합니다.")
    public ResponseEntity<CommonResponse<PageResponse<PlantResponse>>> getAllPlants(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success("식물 목록 조회 성공", plantService.findAllPlants(pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "식물 목록 검색",
            description = "한글 또는 영어 식물 이름이 입력 키워드로 시작하는 식물을 이름순으로 페이지 조회합니다.")
    public ResponseEntity<CommonResponse<PageResponse<PlantResponse>>> searchPlants(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success("식물 검색 성공", plantService.findPlantsByKeyword(keyword, pageable)));
    }

    @GetMapping("/{plantId}")
    @Operation(summary = "특정 식물 정보 조회", description = "식물 ID에 해당하는 식물의 상세 정보를 조회합니다.")
    public ResponseEntity<CommonResponse<PlantResponse>> getPlant(@PathVariable Long plantId) {
        return ResponseEntity.ok(CommonResponse.success("식물 상세 조회 성공", plantService.findPlant(plantId)));
    }
}

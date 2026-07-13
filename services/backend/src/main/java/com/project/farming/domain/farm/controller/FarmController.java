package com.project.farming.domain.farm.controller;

import com.project.farming.domain.farm.dto.FarmResponse;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Farm API", description = "텃밭 관련 API")
@SecurityRequirement(name = "jwtAuth")
@RequestMapping("/api/farms")
@RequiredArgsConstructor
@RestController
public class FarmController {

    private final FarmService farmService;

    @GetMapping
    @Operation(summary = "전체 텃밭 목록 조회",
            description = """
                    DB에 등록된 모든 텃밭을 고유번호순으로 조회합니다.
                    일부 정보만 반환합니다.
                    (farmId, gardenUniqueId(고유번호), operator(운영주체), farmName(텃밭 이름),
                     lotNumberAddress(주소), updatedAt(최종 수정일), farmImageUrl(이미지 URL))
                    """)
    public ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> getAllFarms(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success("텃밭 목록 조회 성공", farmService.findAllFarms(pageable)));
    }

    @GetMapping("/search")
    @Operation(summary = "텃밭 목록 검색",
            description = """
                    텃밭 이름 또는 도로명/지번 주소가 입력 키워드로 시작하는 텃밭을 고유번호순으로 페이지 조회합니다.
                    일부 정보만 반환합니다.
                    (farmId, gardenUniqueId(고유번호), operator(운영주체), farmName(텃밭 이름),
                     lotNumberAddress(주소), updatedAt(최종 수정일), farmImageUrl(이미지 URL))
                    """)
    public ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> searchFarms(
            @Parameter(description = "텃밭 이름 또는 주소(도로명/지번)")
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success(
                "텃밭 검색 성공", farmService.findFarmsByKeyword(keyword, pageable)));
    }

    @GetMapping("/{farmId}")
    @Operation(summary = "특정 텃밭 정보 조회", description = "텃밭 ID에 해당하는 텃밭의 상세 정보를 조회합니다. 전체 정보를 반환합니다.")
    public ResponseEntity<CommonResponse<FarmResponse>> getFarm(@PathVariable Long farmId) {
        return ResponseEntity.ok(CommonResponse.success("텃밭 상세 조회 성공", farmService.findFarm(farmId)));
    }

    @GetMapping("/nearby")
    @Operation(summary = "주변 텃밭 정보 조회",
            description = """
                    사용자의 현재 위치(위도, 경도)를 기준으로 원하는 반경(km 단위, 기본값: 20km) 내에 위치한 텃밭 정보를 조회합니다.
                    전체 정보를 반환합니다.
                    """)
    public ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> getFarmsByLocation(
            @Parameter(description = "현재 위치의 위도") @RequestParam Double latitude,
            @Parameter(description = "현재 위치의 경도") @RequestParam Double longitude,
            @Parameter(description = "조회 반경(km 단위, 기본값: 20km, 최대: 100km)") @RequestParam(defaultValue = "20") Double radius,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success(
                "주변 텃밭 조회 성공",
                farmService.findFarmsByCurrentLocation(latitude, longitude, radius, pageable)));
    }
}

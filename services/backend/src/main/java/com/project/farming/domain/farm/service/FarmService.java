package com.project.farming.domain.farm.service;

import com.project.farming.domain.farm.dto.FarmResponse;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.repository.FarmNearbyResponseRow;
import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.global.exception.FarmNotFoundException;
import com.project.farming.global.exception.ImageFileNotFoundException;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.response.PageResponse;
import com.project.farming.global.search.SearchKeywordPattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class FarmService {

    private static final double KILOMETERS_PER_LATITUDE_DEGREE = 111.32;
    private static final double METERS_PER_KILOMETER = 1000.0;
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;
    private static final double MAX_RADIUS_KILOMETERS = 100.0;

    private final FarmRepository farmRepository;
    private final ImageFileRepository imageFileRepository;

    /**
     * 전체 텃밭 목록 조회(고유번호순)
     * - 일부 정보만 반환
     *
     * @return 각 텃밭 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> findAllFarms(Pageable pageable) {
        var foundFarms = farmRepository.findAllListResponsesByOrderByGardenUniqueIdAsc(
                PageRequestPolicy.stable(pageable));
        if (foundFarms.isEmpty()) {
            log.info("등록된 텃밭이 없습니다.");
        }
        return PageResponse.from(foundFarms);
    }

    /**
     * 텃밭 목록 검색(고유번호순)
     * - 텃밭의 이름 또는 주소(도로명주소, 지번주소)로 검색(통합)
     * - 일부 정보만 반환
     *
     * @param keyword 검색어(텃밭 이름 또는 주소)
     * @return 검색된 텃밭 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> findFarmsByKeyword(String keyword, Pageable pageable) {
        return PageResponse.from(farmRepository.findListResponsesByKeywordOrderByGardenUniqueIdAsc(
                SearchKeywordPattern.prefix(keyword), PageRequestPolicy.stable(pageable)));
    }

    /**
     * 특정 텃밭 정보 조회
     *
     * @param farmId 조회할 텃밭 정보의 ID
     * @return 해당 텃밭 정보의 응답
     */
    @Transactional(readOnly = true)
    public FarmResponse findFarm(Long farmId) {
        return farmRepository.findDetailResponseByFarmId(farmId)
                .orElseThrow(() -> new FarmNotFoundException("해당 텃밭이 존재하지 않습니다: " + farmId));
    }

    /**
     * 주변 텃밭 정보 조회
     *  - 현재 위치를 기준으로 지정된 반경 내에 위치한 텃밭들의 정보 조회
     *
     * @param latitude 현재 위치의 위도
     * @param longitude 현재 위치의 경도
     * @param radius 조회할 반경(단위: km) - 기본값은 20km
     * @return 지정된 반경 내에 위치한 텃밭의 정보 응답 리스트
     */
    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> findFarmsByCurrentLocation(
            Double latitude, Double longitude, Double radius, Pageable pageable) {
        validateLocationSearch(latitude, longitude, radius);
        log.info("현재 위치: {}, {} / 반경: {}", latitude, longitude, radius);

        BoundingBox boundingBox = boundingBox(latitude, longitude, radius);
        Slice<FarmNearbyResponseRow> farms = farmRepository.findNearbyResponseRows(
                        latitude,
                        longitude,
                        radius * METERS_PER_KILOMETER,
                        boundingBox.minLatitude(),
                        boundingBox.maxLatitude(),
                        boundingBox.minLongitude(),
                        boundingBox.maxLongitude(),
                        PageRequestPolicy.stable(pageable)
                );
        List<FarmResponse> responses = farms.getContent().stream()
                .map(this::toFarmResponse)
                .toList();
        return PageResponse.from(farms, responses);
    }

    private void validateLocationSearch(Double latitude, Double longitude, Double radius) {
        if (latitude == null || longitude == null || radius == null) {
            throw new IllegalArgumentException("위도, 경도, 반경은 필수입니다.");
        }
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException("위도는 -90부터 90 사이여야 합니다.");
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException("경도는 -180부터 180 사이여야 합니다.");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("조회 반경은 0보다 커야 합니다.");
        }
        if (radius > MAX_RADIUS_KILOMETERS) {
            throw new IllegalArgumentException("조회 반경은 100km 이하여야 합니다.");
        }
    }

    private BoundingBox boundingBox(double latitude, double longitude, double radiusKilometers) {
        double latitudeDelta = radiusKilometers / KILOMETERS_PER_LATITUDE_DEGREE;
        double cosineLatitude = Math.cos(Math.toRadians(latitude));
        double longitudeDelta = Math.abs(cosineLatitude) < 0.000001
                ? MAX_LONGITUDE
                : radiusKilometers / (KILOMETERS_PER_LATITUDE_DEGREE * Math.abs(cosineLatitude));

        return new BoundingBox(
                clamp(latitude - latitudeDelta, MIN_LATITUDE, MAX_LATITUDE),
                clamp(latitude + latitudeDelta, MIN_LATITUDE, MAX_LATITUDE),
                clamp(longitude - longitudeDelta, MIN_LONGITUDE, MAX_LONGITUDE),
                clamp(longitude + longitudeDelta, MIN_LONGITUDE, MAX_LONGITUDE)
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private FarmResponse toFarmResponse(FarmNearbyResponseRow farm) {
        return FarmResponse.builder()
                .farmId(farm.getFarmId())
                .gardenUniqueId(farm.getGardenUniqueId())
                .operator(farm.getOperator())
                .farmName(farm.getFarmName())
                .roadNameAddress(farm.getRoadNameAddress())
                .lotNumberAddress(farm.getLotNumberAddress())
                .facilities(farm.getFacilities())
                .contact(farm.getContact())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .available(farm.getAvailable())
                .createdAt(farm.getCreatedAt())
                .updatedAt(farm.getUpdatedAt())
                .farmImageUrl(farm.getFarmImageUrl())
                .build();
    }

    private record BoundingBox(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude
    ) {
    }

    /**
     * 텃밭 기본 이미지 반환
     *
     * @return 텃밭 기본 이미지
     */
    private ImageFile getDefaultImageFile() {
        return imageFileRepository.findByS3Key(DefaultImages.DEFAULT_FARM_IMAGE)
                .orElseThrow(() -> new ImageFileNotFoundException("기본 텃밭 이미지가 존재하지 않습니다."));
    }

    /**
     * FarmDataInitializer에서 사용
     * - 텃밭 정보 리스트 저장
     *
     * @param farmList 저장할 초기 텃밭 정보 목록
     */
    @Transactional
    public void saveFarms(List<Farm> farmList) {
        farmRepository.saveAll(farmList);
    }

    /**
     * FarmDataInitializer에서 사용
     * - 사용자 입력 옵션 저장
     */
    @Transactional
    public void saveOtherFarmOption() {
        ImageFile defaultImageFile = getDefaultImageFile();
        Farm otherFarmOption = Farm.builder()
                .gardenUniqueId(1)
                .operator("N/A")
                .farmName("기타(Other)")
                .roadNameAddress("N/A")
                .lotNumberAddress("N/A")
                .facilities("N/A")
                .contact("N/A")
                .latitude(0.0)
                .longitude(0.0)
                .available(true)
                .farmImageFileId(defaultImageFile.getImageFileId())
                .build();
        farmRepository.save(otherFarmOption);
    }
}

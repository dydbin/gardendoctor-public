package com.project.farming.domain.farm.controller;

import com.project.farming.domain.farm.dto.FarmResponse;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmControllerTest {

    @Mock
    private FarmService farmService;

    private FarmController farmController;

    @BeforeEach
    void setUp() {
        farmController = new FarmController(farmService);
    }

    @Test
    void getAllFarmsShouldWrapPageInCommonResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<FarmResponse> farms = page(farmResponse());
        when(farmService.findAllFarms(pageable)).thenReturn(farms);

        ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> response = farmController.getAllFarms(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("텃밭 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(farms);
    }

    @Test
    void searchFarmsShouldWrapPageInCommonResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<FarmResponse> farms = page(farmResponse());
        when(farmService.findFarmsByKeyword("강남", pageable)).thenReturn(farms);

        ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> response =
                farmController.searchFarms("강남", pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(farms);
    }

    @Test
    void getFarmShouldWrapDetailInCommonResponse() {
        FarmResponse farm = farmResponse();
        when(farmService.findFarm(1L)).thenReturn(farm);

        ResponseEntity<CommonResponse<FarmResponse>> response = farmController.getFarm(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(farm);
    }

    @Test
    void getFarmsByLocationShouldWrapPageInCommonResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<FarmResponse> farms = page(farmResponse());
        when(farmService.findFarmsByCurrentLocation(37.5, 127.0, 20.0, pageable)).thenReturn(farms);

        ResponseEntity<CommonResponse<PageResponse<FarmResponse>>> response =
                farmController.getFarmsByLocation(37.5, 127.0, 20.0, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(farms);
    }

    private FarmResponse farmResponse() {
        return FarmResponse.builder()
                .farmId(1L)
                .gardenUniqueId(100)
                .operator("서울시")
                .farmName("강남 텃밭")
                .roadNameAddress("서울 강남구")
                .lotNumberAddress("서울 강남구 1")
                .facilities("수도")
                .contact("온라인")
                .latitude(37.5)
                .longitude(127.0)
                .available(true)
                .createdAt(LocalDate.of(2026, 7, 9))
                .updatedAt(LocalDate.of(2026, 7, 9))
                .farmImageUrl("https://example.com/farm.jpg")
                .build();
    }

    private PageResponse<FarmResponse> page(FarmResponse farm) {
        return PageResponse.of(List.of(farm), 0, 20, false, 1);
    }
}

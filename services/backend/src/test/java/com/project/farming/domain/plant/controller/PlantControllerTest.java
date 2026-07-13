package com.project.farming.domain.plant.controller;

import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.service.PlantService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantControllerTest {

    @Mock
    private PlantService plantService;

    private PlantController plantController;

    @BeforeEach
    void setUp() {
        plantController = new PlantController(plantService);
    }

    @Test
    void getAllPlantsShouldWrapPageInCommonResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<PlantResponse> plants = page(plantResponse());
        when(plantService.findAllPlants(pageable)).thenReturn(plants);

        ResponseEntity<CommonResponse<PageResponse<PlantResponse>>> response =
                plantController.getAllPlants(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("식물 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(plants);
    }

    @Test
    void searchPlantsShouldWrapPageInCommonResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<PlantResponse> plants = page(plantResponse());
        when(plantService.findPlantsByKeyword("토마토", pageable)).thenReturn(plants);

        ResponseEntity<CommonResponse<PageResponse<PlantResponse>>> response =
                plantController.searchPlants("토마토", pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(plants);
    }

    @Test
    void getPlantShouldWrapDetailInCommonResponse() {
        PlantResponse plant = plantResponse();
        when(plantService.findPlant(1L)).thenReturn(plant);

        ResponseEntity<CommonResponse<PlantResponse>> response = plantController.getPlant(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(plant);
    }

    private PlantResponse plantResponse() {
        return PlantResponse.builder()
                .plantId(1L)
                .plantName("토마토")
                .plantEnglishName("Tomato")
                .species("채소")
                .season("봄")
                .plantImageUrl("https://example.com/tomato.jpg")
                .build();
    }

    private PageResponse<PlantResponse> page(PlantResponse plant) {
        return PageResponse.of(List.of(plant), 0, 20, false, 1);
    }
}

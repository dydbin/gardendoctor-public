package com.project.farming.domain.farm.service;

import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.global.image.repository.ImageFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private ImageFileRepository imageFileRepository;

    @Test
    void nearbySearchShouldRejectRadiusAboveOneHundredKilometersBeforeQuerying() {
        FarmService farmService = new FarmService(farmRepository, imageFileRepository);

        assertThatThrownBy(() -> farmService.findFarmsByCurrentLocation(
                37.5, 127.0, 100.1, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 반경은 100km 이하여야 합니다.");

        verifyNoInteractions(farmRepository);
    }
}

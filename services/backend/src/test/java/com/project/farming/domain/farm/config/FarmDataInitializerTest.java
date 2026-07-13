package com.project.farming.domain.farm.config;

import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmDataInitializerTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FarmService farmService;

    @Mock
    private ImageFileRepository imageFileRepository;

    @Test
    void shouldLoadOnlySyntheticPublicFarmFixtures() throws Exception {
        when(farmRepository.countAllIncludingDeleted()).thenReturn(0L);
        when(imageFileRepository.findByS3Key(DefaultImages.DEFAULT_FARM_IMAGE))
                .thenReturn(Optional.of(defaultFarmImage()));

        FarmDataInitializer initializer = new FarmDataInitializer(
                farmRepository,
                farmService,
                imageFileRepository
        );

        initializer.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Farm>> farmsCaptor = ArgumentCaptor.forClass(List.class);
        verify(farmService).saveOtherFarmOption();
        verify(farmService).saveFarms(farmsCaptor.capture());

        List<Farm> farms = farmsCaptor.getValue();
        assertThat(farms).hasSize(3);
        assertThat(farms).extracting(Farm::getGardenUniqueId).doesNotHaveDuplicates();
        assertThat(farms).allSatisfy(farm -> {
            assertThat(farm.getOperator()).isEqualTo("GardenDoctor 공개 데모");
            assertThat(farm.getFarmName()).startsWith("GardenDoctor 데모 텃밭");
            assertThat(farm.getRoadNameAddress()).contains("공개 데모");
            assertThat(farm.getContact()).isEqualTo("N/A");
            assertThat(farm.getFarmImageFileId()).isEqualTo(42L);
            assertThat(farm.isAvailable()).isTrue();
        });
    }

    private ImageFile defaultFarmImage() {
        return ImageFile.builder()
                .imageFileId(42L)
                .originalImageName("public-default-farm.png")
                .s3Key(DefaultImages.DEFAULT_FARM_IMAGE)
                .imageUrl("https://example.invalid/public-default-farm.png")
                .domainType(ImageDomainType.FARM)
                .domainId(0L)
                .build();
    }
}

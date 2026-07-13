package com.project.farming.integration;

import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.service.PlantService;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class PlantSeedIdentityIntegrationDiagnosticsTest {

    @Autowired
    private PlantService plantService;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedPlantImageShouldReferenceDatabaseGeneratedPlantIdAboveOneHundred() {
        ImageFile defaultImage = ensureDefaultPlantImage();
        long idFloor = Math.max(100L, maxPlantId() + 100L);
        advancePlantAutoIncrement(idFloor, defaultImage.getImageFileId());
        String suffix = UUID.randomUUID().toString();

        Plant plant = plantService.saveSeedPlant(
                "seed-plant-" + suffix,
                "Seed Plant " + suffix,
                "diagnostic",
                "all",
                "seed-plant.png",
                "https://example.test/plants/seed-plant.png"
        );
        ImageFile image = imageFileRepository.findById(plant.getPlantImageFileId()).orElseThrow();

        assertThat(plant.getPlantId()).isGreaterThan(idFloor);
        assertThat(image.getDomainType()).isEqualTo(ImageDomainType.PLANT);
        assertThat(image.getDomainId()).isEqualTo(plant.getPlantId());
        assertThat(defaultImage.getDomainId()).isZero();
    }

    private ImageFile ensureDefaultPlantImage() {
        return imageFileRepository.findByS3Key(DefaultImages.DEFAULT_PLANT_IMAGE)
                .orElseGet(() -> imageFileRepository.saveAndFlush(ImageFile.builder()
                        .originalImageName(DefaultImages.DEFAULT_PLANT_IMAGE)
                        .s3Key(DefaultImages.DEFAULT_PLANT_IMAGE)
                        .imageUrl("https://example.test/default_plant.png")
                        .domainType(ImageDomainType.PLANT)
                        .domainId(0L)
                        .build()));
    }

    private long maxPlantId() {
        Long maxPlantId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(plant_id), 0) FROM plant_info",
                Long.class);
        return maxPlantId == null ? 0L : maxPlantId;
    }

    private void advancePlantAutoIncrement(long plantId, long imageFileId) {
        String marker = "seed-id-floor-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO plant_info (
                    plant_id, plant_name, plant_english_name, species, season,
                    plant_image_file_id, created_at, updated_at, deleted
                ) VALUES (?, ?, ?, 'diagnostic', 'all', ?, CURRENT_DATE, CURRENT_DATE, FALSE)
                """, plantId, marker, marker, imageFileId);
        jdbcTemplate.update("DELETE FROM plant_info WHERE plant_id = ?", plantId);
    }
}

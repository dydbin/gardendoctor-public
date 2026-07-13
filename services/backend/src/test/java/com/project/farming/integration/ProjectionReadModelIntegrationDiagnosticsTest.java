package com.project.farming.integration;

import com.project.farming.domain.farm.dto.FarmResponse;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.service.FarmAdminService;
import com.project.farming.domain.farm.service.FarmService;
import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.service.PlantAdminService;
import com.project.farming.domain.plant.service.PlantService;
import com.project.farming.domain.user.dto.UserAdminResponse;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.service.UserAdminService;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class ProjectionReadModelIntegrationDiagnosticsTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlantService plantService;

    @Autowired
    private PlantAdminService plantAdminService;

    @Autowired
    private FarmService farmService;

    @Autowired
    private FarmAdminService farmAdminService;

    @Autowired
    private UserAdminService userAdminService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void projectionReadModelsShouldReturnExpectedFieldsFromRealDatabase() {
        ProjectionSeed seed = seedProjectionGraph();
        flushAndClear();

        PlantResponse publicPlant = plantService.findPlant(seed.plantId());
        List<PlantResponse> adminPlants = plantAdminService.findPlantsByKeyword(
                "projection-plant-" + seed.suffix(), PageRequest.of(0, 20)).getContent();
        FarmResponse publicFarm = farmService.findFarm(seed.farmId());
        List<FarmResponse> adminFarms = farmAdminService.findFarmsByKeyword(
                "name", "projection-farm-" + seed.suffix(), PageRequest.of(0, 20)).getContent();
        UserAdminResponse user = userAdminService.findUser(seed.userId());
        List<UserAdminResponse> searchedUsers = userAdminService.findUsersByKeyword(
                "email", "projection-" + seed.suffix(), PageRequest.of(0, 20)).getContent();
        UserAdminResponse userWithoutImage = userAdminService.findUser(seed.userWithoutImageId());

        assertThat(publicPlant.getPlantImageUrl()).isEqualTo(seed.plantImageUrl());
        assertThat(adminPlants)
                .extracting(PlantResponse::getPlantId)
                .contains(seed.plantId());
        assertThat(publicFarm.getFarmImageUrl()).isEqualTo(seed.farmImageUrl());
        assertThat(adminFarms)
                .extracting(FarmResponse::getFarmId)
                .contains(seed.farmId());
        assertThat(user.getProfileImageUrl()).isEqualTo(seed.userImageUrl());
        assertThat(user.getRole()).isEqualTo(UserRole.USER.name());
        assertThat(searchedUsers)
                .extracting(UserAdminResponse::getUserId)
                .contains(seed.userId());
        assertThat(userWithoutImage.getProfileImageUrl())
                .as("User admin LEFT JOIN projection should tolerate users without profile images.")
                .isNull();
    }

    @Test
    void nearbyFarmReadShouldUseSingleProjectionQueryWithBoundingBox() {
        NearbySeed seed = seedNearbyFarms();
        flushAndClear();

        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<FarmResponse> responses = farmService.findFarmsByCurrentLocation(
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                5.0,
                PageRequest.of(0, 20)
        ).content();

        long queryCount = statistics.getPrepareStatementCount();
        List<Long> responseIds = responses.stream()
                .map(FarmResponse::getFarmId)
                .toList();

        System.out.printf(
                "Farm nearby query count: responseCount=%d, queries=%d%n",
                responses.size(),
                queryCount
        );

        assertThat(responseIds).contains(seed.centerFarmId(), seed.nearFarmId());
        assertThat(responseIds).doesNotContain(seed.farFarmId());
        assertThat(queryCount)
                .as("Farm nearby should use one native projection query with image URL joined.")
                .isEqualTo(1L);
        assertThat(responses)
                .filteredOn(response -> response.getFarmId().equals(seed.centerFarmId()))
                .singleElement()
                .extracting(FarmResponse::getFarmImageUrl)
                .isEqualTo(seed.centerFarmImageUrl());
    }

    @Test
    void plantCollectionShouldBeBoundedAndExposeFirstAndFinalPageMetadata() {
        String prefix = "page-bound-" + System.nanoTime() + "-";
        ImageFile image = image(prefix + "image", ImageDomainType.PLANT, 0L);
        for (int index = 0; index < 23; index++) {
            entityManager.persist(Plant.builder()
                    .plantName(prefix + String.format("%02d", index))
                    .plantEnglishName("Page Bound " + index)
                    .species("diagnostic")
                    .season("all")
                    .plantImageFileId(image.getImageFileId())
                    .build());
        }
        flushAndClear();

        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var firstPage = plantService.findPlantsByKeyword(
                prefix, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "plantName")));
        long firstPageQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        var finalPage = plantService.findPlantsByKeyword(prefix, PageRequest.of(4, 5));
        long finalPageQueries = statistics.getPrepareStatementCount();

        System.out.printf(
                "Plant pagination: total=%d, firstRows=%d, finalRows=%d, firstQueries=%d, finalQueries=%d%n",
                firstPage.totalElements(),
                firstPage.content().size(),
                finalPage.content().size(),
                firstPageQueries,
                finalPageQueries);

        assertThat(firstPage.content()).hasSize(5);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.totalElements()).isEqualTo(23);
        assertThat(firstPage.totalPages()).isEqualTo(5);
        assertThat(firstPage.content())
                .extracting(PlantResponse::getPlantName)
                .isSorted();
        assertThat(finalPage.content()).hasSize(3);
        assertThat(finalPage.hasNext()).isFalse();
        assertThat(firstPageQueries).isEqualTo(2);
        assertThat(finalPageQueries)
                .as("Spring Data should infer the final-page total without a separate count query.")
                .isEqualTo(1);
    }

    private ProjectionSeed seedProjectionGraph() {
        String suffix = "projection-" + System.nanoTime();
        ImageFile userImage = image("projection-user-" + suffix, ImageDomainType.USER, 0L);
        ImageFile plantImage = image("projection-plant-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile farmImage = image("projection-farm-" + suffix, ImageDomainType.FARM, 0L);

        User user = User.builder()
                .email("projection-" + suffix + "@example.com")
                .password("encoded-password")
                .nickname("projection-" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .profileImageFileId(userImage.getImageFileId())
                .build();
        entityManager.persist(user);

        User userWithoutImage = User.builder()
                .email("projection-no-image-" + suffix + "@example.com")
                .password("encoded-password")
                .nickname("noimg-" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
        entityManager.persist(userWithoutImage);

        Plant plant = Plant.builder()
                .plantName("projection-plant-" + suffix)
                .plantEnglishName("Projection Plant")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(plantImage.getImageFileId())
                .build();
        entityManager.persist(plant);

        Farm farm = farm(
                "projection-farm-" + suffix,
                "projection road " + suffix,
                "projection lot " + suffix,
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                farmImage,
                gardenUniqueId(suffix, 0)
        );
        entityManager.persist(farm);

        return new ProjectionSeed(
                suffix,
                plant.getPlantId(),
                plantImage.getImageUrl(),
                farm.getFarmId(),
                farmImage.getImageUrl(),
                user.getUserId(),
                userImage.getImageUrl(),
                userWithoutImage.getUserId()
        );
    }

    private NearbySeed seedNearbyFarms() {
        String suffix = "nearby-" + System.nanoTime();

        ImageFile centerImage = image("nearby-center-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile nearImage = image("nearby-near-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile farImage = image("nearby-far-" + suffix, ImageDomainType.FARM, 0L);

        Farm centerFarm = farm(
                "nearby-center-" + suffix,
                "nearby center road",
                "nearby center lot",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                centerImage,
                gardenUniqueId(suffix, 10)
        );
        Farm nearFarm = farm(
                "nearby-near-" + suffix,
                "nearby near road",
                "nearby near lot",
                SEOUL_LATITUDE + 0.01,
                SEOUL_LONGITUDE + 0.01,
                nearImage,
                gardenUniqueId(suffix, 11)
        );
        Farm farFarm = farm(
                "nearby-far-" + suffix,
                "nearby far road",
                "nearby far lot",
                SEOUL_LATITUDE + 0.5,
                SEOUL_LONGITUDE + 0.5,
                farImage,
                gardenUniqueId(suffix, 12)
        );
        entityManager.persist(centerFarm);
        entityManager.persist(nearFarm);
        entityManager.persist(farFarm);

        return new NearbySeed(
                centerFarm.getFarmId(),
                centerImage.getImageUrl(),
                nearFarm.getFarmId(),
                farFarm.getFarmId()
        );
    }

    private Farm farm(
            String farmName,
            String roadNameAddress,
            String lotNumberAddress,
            double latitude,
            double longitude,
            ImageFile farmImage,
            int gardenUniqueId
    ) {
        return Farm.builder()
                .gardenUniqueId(gardenUniqueId)
                .operator("diagnostic")
                .farmName(farmName)
                .roadNameAddress(roadNameAddress)
                .lotNumberAddress(lotNumberAddress)
                .facilities("none")
                .contact("none")
                .latitude(latitude)
                .longitude(longitude)
                .available(true)
                .farmImageFileId(farmImage.getImageFileId())
                .build();
    }

    private ImageFile image(String s3Key, ImageDomainType domainType, Long domainId) {
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(s3Key + ".png")
                .s3Key(s3Key)
                .imageUrl("https://example.test/" + s3Key + ".png")
                .domainType(domainType)
                .domainId(domainId)
                .build();
        entityManager.persist(imageFile);
        return imageFile;
    }

    private int gardenUniqueId(String suffix, int offset) {
        String numericSuffix = suffix.replaceAll("\\D", "");
        long seed = numericSuffix.isBlank() ? System.nanoTime() : Long.parseLong(numericSuffix);
        return 100_000_000 + Math.floorMod(Long.hashCode(seed + offset), 800_000_000);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        return entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    private record ProjectionSeed(
            String suffix,
            Long plantId,
            String plantImageUrl,
            Long farmId,
            String farmImageUrl,
            Long userId,
            String userImageUrl,
            Long userWithoutImageId
    ) {
    }

    private record NearbySeed(
            Long centerFarmId,
            String centerFarmImageUrl,
            Long nearFarmId,
            Long farFarmId
    ) {
    }
}

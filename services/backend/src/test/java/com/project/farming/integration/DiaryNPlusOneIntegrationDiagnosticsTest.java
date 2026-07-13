package com.project.farming.integration;

import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.diary.service.DiaryService;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class DiaryNPlusOneIntegrationDiagnosticsTest {

    private static final int SMALL_DIARY_COUNT = 1;
    private static final int LARGE_DIARY_COUNT = 30;
    private static final long QUERY_GROWTH_TOLERANCE = 1;
    private static final long FIXED_QUERY_BUDGET = 5;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DiaryService diaryService;

    @Test
    void diaryListReadShouldNotScaleQueriesWithDiaryRows() {
        Long smallUserId = seedDiaryGraph(SMALL_DIARY_COUNT);
        entityManager.flush();
        entityManager.clear();

        QueryMeasurement smallMeasurement = measureDiaryListRead(smallUserId, SMALL_DIARY_COUNT);

        Long largeUserId = seedDiaryGraph(LARGE_DIARY_COUNT);
        entityManager.flush();
        entityManager.clear();

        QueryMeasurement largeMeasurement = measureDiaryListRead(largeUserId, LARGE_DIARY_COUNT);

        System.out.printf(
                "Diary list query count: %d diaries -> %d, %d diaries -> %d%n",
                smallMeasurement.diaryCount(),
                smallMeasurement.queryCount(),
                largeMeasurement.diaryCount(),
                largeMeasurement.queryCount()
        );

        assertThat(smallMeasurement.responseCount()).isEqualTo(SMALL_DIARY_COUNT);
        assertThat(largeMeasurement.responseCount()).isEqualTo(LARGE_DIARY_COUNT);
        assertThat(largeMeasurement.queryCount())
                .as("Diary list query count should not grow linearly with row count. %s diaries used %s queries, but %s diaries used %s queries.",
                        SMALL_DIARY_COUNT, smallMeasurement.queryCount(), LARGE_DIARY_COUNT, largeMeasurement.queryCount())
                .isLessThanOrEqualTo(smallMeasurement.queryCount() + QUERY_GROWTH_TOLERANCE);
        assertThat(largeMeasurement.queryCount())
                .as("Diary list query count should stay within the fixed read-model budget after N+1 removal.")
                .isLessThanOrEqualTo(FIXED_QUERY_BUDGET);
    }

    private QueryMeasurement measureDiaryListRead(Long userId, int expectedDiaryCount) {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        User userReference = entityManager.getReference(User.class, userId);
        List<DiaryResponse> responses = diaryService.getAllDiariesByUser(
                userReference, PageRequest.of(0, expectedDiaryCount)).content();

        return new QueryMeasurement(expectedDiaryCount, statistics.getPrepareStatementCount(), responses.size());
    }

    private Long seedDiaryGraph(int diaryCount) {
        String suffix = String.valueOf(System.nanoTime());

        ImageFile userImage = image("portfolio-user-" + suffix, ImageDomainType.USER, 0L);
        ImageFile plantImage = image("portfolio-plant-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile farmImage = image("portfolio-farm-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile userPlantImage = image("portfolio-user-plant-" + suffix, ImageDomainType.USERPLANT, 0L);

        User user = User.builder()
                .email("portfolio-nplus-" + suffix + "@example.com")
                .password("encoded-password")
                .nickname("nplus" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .profileImageFileId(userImage.getImageFileId())
                .build();
        entityManager.persist(user);

        Plant plant = Plant.builder()
                .plantName("portfolio-plant-" + suffix)
                .plantEnglishName("Portfolio Plant")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(plantImage.getImageFileId())
                .build();
        entityManager.persist(plant);

        Farm farm = Farm.builder()
                .gardenUniqueId(Math.floorMod(suffix.hashCode(), 1_000_000_000))
                .operator("diagnostic")
                .farmName("portfolio-farm-" + diaryCount + "-" + suffix)
                .roadNameAddress("road")
                .lotNumberAddress("lot")
                .facilities("none")
                .contact("none")
                .latitude(37.5)
                .longitude(127.0)
                .available(true)
                .farmImageFileId(farmImage.getImageFileId())
                .build();
        entityManager.persist(farm);

        UserPlant userPlant = UserPlant.builder()
                .userId(user.getUserId())
                .plantId(plant.getPlantId())
                .plantName(plant.getPlantName())
                .plantNickname("nplus-plant")
                .farmId(farm.getFarmId())
                .plantingPlace(farm.getFarmName())
                .plantedDate(LocalDateTime.now())
                .notes("diagnostic")
                .isNotificationEnabled(false)
                .waterIntervalDays(1)
                .watered(false)
                .pruneIntervalDays(1)
                .pruned(false)
                .fertilizeIntervalDays(1)
                .fertilized(false)
                .userPlantImageFileId(userPlantImage.getImageFileId())
                .build();
        entityManager.persist(userPlant);

        for (int index = 0; index < diaryCount; index++) {
            ImageFile diaryImage = image("portfolio-diary-" + index + "-" + suffix, ImageDomainType.DIARY, 0L);
            Diary diary = Diary.builder()
                    .userId(user.getUserId())
                    .title("diagnostic diary " + index)
                    .content("N+1 baseline")
                    .diaryDate(LocalDate.now().minusDays(index))
                    .diaryImageFileId(diaryImage.getImageFileId())
                    .watered(false)
                    .pruned(false)
                    .fertilized(false)
                    .build();
            entityManager.persist(diary);

            DiaryUserPlant diaryUserPlant = DiaryUserPlant.builder()
                    .diaryId(diary.getDiaryId())
                    .userPlantId(userPlant.getUserPlantId())
                    .build();
            entityManager.persist(diaryUserPlant);
        }

        return user.getUserId();
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

    private Statistics statistics() {
        return entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    private record QueryMeasurement(int diaryCount, long queryCount, int responseCount) {
    }
}

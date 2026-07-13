package com.project.farming.integration;

import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.farm.command.FarmAdminCommand;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.service.FarmAdminService;
import com.project.farming.domain.plant.command.PlantAdminCommand;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.service.PlantAdminService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.domain.userplant.service.UserPlantService;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("portfolio-delete-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class CascadeDeleteBehaviorIntegrationDiagnosticsTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPlantService userPlantService;

    @Autowired
    private PlantAdminService plantAdminService;

    @Autowired
    private FarmAdminService farmAdminService;

    @MockBean
    private ImageFileService imageFileService;

    @Test
    void deletingDiaryUserPlantLinkShouldNotDeleteParentDiary() {
        SeededGraph graph = seedGraph("link-delete");
        flushAndClear();

        DiaryUserPlant link = entityManager.find(DiaryUserPlant.class, graph.diaryUserPlantId());
        entityManager.remove(link);
        flushAndClear();

        assertThat(entityManager.find(Diary.class, graph.diaryId()))
                .as("DiaryUserPlant is a link row; deleting the link must not cascade-remove parent Diary.")
                .isNotNull();
    }

    @Test
    void explicitlyDeletingUserPlantLinksBeforeUserPlantShouldKeepDiary() {
        SeededGraph graph = seedGraph("user-plant-delete");
        flushAndClear();

        deleteUserPlantLinks(graph.userPlantId());
        flushAndClear();

        UserPlant userPlant = entityManager.find(UserPlant.class, graph.userPlantId());
        entityManager.remove(userPlant);
        flushAndClear();

        assertThat(entityManager.find(Diary.class, graph.diaryId()))
                .as("Deleting a UserPlant must not delete historical Diary rows connected through DiaryUserPlant.")
                .isNotNull();
    }

    @Test
    void deletingUserPlantShouldSoftDeleteAndKeepDiaryLink() {
        SeededGraph graph = seedGraph("user-plant-soft-delete");
        flushAndClear();

        userPlantService.deleteUserPlant(graph.userId(), graph.userPlantId());
        flushAndClear();

        long userPlantRows = countUserPlantRows(graph.userPlantId());
        long diaryUserPlantLinks = countDiaryUserPlantLinks(graph.diaryId(), graph.userPlantId());

        System.out.printf(
                "UserPlant delete result: userPlantRows=%d, diaryUserPlantLinks=%d%n",
                userPlantRows,
                diaryUserPlantLinks
        );

        assertThat(userPlantRows)
                .as("Deleting a UserPlant should keep the row as a historical soft-deleted record.")
                .isEqualTo(1L);
        assertThat(diaryUserPlantLinks)
                .as("Deleting a UserPlant should keep DiaryUserPlant links for Diary history.")
                .isEqualTo(1L);

        UserPlantDeletionRow deletionRow = findUserPlantDeletionRow(graph.userPlantId());
        assertThat(deletionRow.deleted()).isTrue();
        assertThat(deletionRow.deletedAt()).isNotNull();
        assertThat(deletionRow.notificationEnabled()).isFalse();
        assertThat(deletionRow.plantNickname()).startsWith("deleted-");
        assertThat(entityManager.find(UserPlant.class, graph.userPlantId()))
                .as("Default UserPlant entity reads should hide soft-deleted rows.")
                .isNull();
        verify(imageFileService, never()).deleteImage(graph.userPlantImageFileId());
    }

    @Test
    void deletingDiaryShouldNotCascadeDeleteImageMetadata() {
        SeededGraph graph = seedGraph("diary-delete");
        flushAndClear();

        deleteDiaryLinks(graph.diaryId());
        flushAndClear();

        Diary diary = entityManager.find(Diary.class, graph.diaryId());
        entityManager.remove(diary);
        flushAndClear();

        assertThat(entityManager.find(ImageFile.class, graph.diaryImageFileId()))
                .as("Diary image metadata should be cleaned by an explicit policy, not by JPA remove cascade.")
                .isNotNull();
    }

    @Test
    void withdrawingUserShouldKeepUserRowAndHistoricalDiary() {
        SeededGraph graph = seedGraph("user-withdrawal", false);
        flushAndClear();

        userRepository.deleteById(graph.userId());
        flushAndClear();

        assertThat(countUserRows(graph.userId()))
                .as("User withdrawal should be a logical delete/anonymization, not a physical delete.")
                .isEqualTo(1L);
        UserWithdrawalRow userRow = findUserWithdrawalRow(graph.userId());
        assertThat(userRow.subscriptionStatus()).isEqualTo("WITHDRAWN");
        assertThat(userRow.email()).startsWith("deleted-" + graph.userId());
        assertThat(entityManager.find(Diary.class, graph.diaryId()))
                .as("User withdrawal must preserve historical Diary rows.")
                .isNotNull();
    }

    @Test
    void deletingPlantShouldSoftDeleteReassignUserPlantAndKeepImage() {
        CatalogDeleteSeed seed = seedCatalogDeleteGraph("plant-soft-delete");
        flushAndClear();

        plantAdminService.deletePlant(seed.targetPlantId());
        flushAndClear();

        CatalogDeletionRow deletionRow = findCatalogDeletionRow(
                "plant_info", "plant_id", seed.targetPlantId());
        Long reassignedPlantId = userPlantReferenceId(
                "plant_id", seed.userPlantId());

        assertThat(deletionRow.rowCount()).isEqualTo(1L);
        assertThat(deletionRow.deleted()).isTrue();
        assertThat(deletionRow.deletedAt()).isNotNull();
        assertThat(reassignedPlantId).isEqualTo(seed.otherPlantId());
        assertThat(entityManager.find(Plant.class, seed.targetPlantId()))
                .as("Default Plant reads should hide soft-deleted catalog rows.")
                .isNull();
        assertThat(entityManager.find(ImageFile.class, seed.targetPlantImageFileId()))
                .as("Soft-deleted Plant should retain image metadata for reference integrity and audit.")
                .isNotNull();
        verify(imageFileService, never()).deleteImage(seed.targetPlantImageFileId());
    }

    @Test
    void deletingFarmShouldSoftDeleteReassignUserPlantAndKeepImage() {
        CatalogDeleteSeed seed = seedCatalogDeleteGraph("farm-soft-delete");
        flushAndClear();

        farmAdminService.deleteFarm(seed.targetFarmId());
        flushAndClear();

        CatalogDeletionRow deletionRow = findCatalogDeletionRow(
                "farm_info", "farm_id", seed.targetFarmId());
        Long reassignedFarmId = userPlantReferenceId(
                "farm_id", seed.userPlantId());

        assertThat(deletionRow.rowCount()).isEqualTo(1L);
        assertThat(deletionRow.deleted()).isTrue();
        assertThat(deletionRow.deletedAt()).isNotNull();
        assertThat(reassignedFarmId).isEqualTo(seed.otherFarmId());
        assertThat(entityManager.find(Farm.class, seed.targetFarmId()))
                .as("Default Farm reads should hide soft-deleted catalog rows.")
                .isNull();
        assertThat(entityManager.find(ImageFile.class, seed.targetFarmImageFileId()))
                .as("Soft-deleted Farm should retain image metadata for reference integrity and audit.")
                .isNotNull();
        verify(imageFileService, never()).deleteImage(seed.targetFarmImageFileId());
    }

    @Test
    void reservedOtherCatalogNamesShouldNotBeMutable() {
        CatalogDeleteSeed seed = seedCatalogDeleteGraph("reserved-name");
        flushAndClear();

        assertThatThrownBy(() -> plantAdminService.updatePlant(
                seed.otherPlantId(),
                new PlantAdminCommand("renamed-other", "Other", "reserved", "all"),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기본 식물의 이름은 변경할 수 없습니다.");
        assertThatThrownBy(() -> farmAdminService.updateFarm(
                seed.otherFarmId(),
                new FarmAdminCommand(
                        negativeUniqueId("reserved-update"),
                        "reserved",
                        "renamed-other-farm",
                        "N/A",
                        "N/A",
                        "N/A",
                        "N/A",
                        0.0,
                        0.0,
                        false),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기본 텃밭의 이름은 변경할 수 없습니다.");
    }

    private SeededGraph seedGraph(String label) {
        return seedGraph(label, true);
    }

    private SeededGraph seedGraph(String label, boolean includeProfileImage) {
        String suffix = label + "-" + System.nanoTime();

        ImageFile userImage = includeProfileImage ? image("cascade-user-" + suffix, ImageDomainType.USER, 0L) : null;
        ImageFile plantImage = image("cascade-plant-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile farmImage = image("cascade-farm-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile userPlantImage = image("cascade-user-plant-" + suffix, ImageDomainType.USERPLANT, 0L);
        ImageFile diaryImage = image("cascade-diary-" + suffix, ImageDomainType.DIARY, 0L);

        User user = User.builder()
                .email("cascade-" + suffix + "@example.com")
                .password("encoded-password")
                .nickname("cascade" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .profileImageFileId(userImage == null ? null : userImage.getImageFileId())
                .build();
        entityManager.persist(user);

        Plant plant = Plant.builder()
                .plantName("cascade-plant-" + suffix)
                .plantEnglishName("Cascade Plant")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(plantImage.getImageFileId())
                .build();
        entityManager.persist(plant);

        Farm farm = Farm.builder()
                .gardenUniqueId(Math.floorMod(suffix.hashCode(), 1_000_000_000))
                .operator("diagnostic")
                .farmName("cascade-farm-" + suffix)
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
                .plantNickname("cascade-plant")
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

        Diary diary = Diary.builder()
                .userId(user.getUserId())
                .title("cascade diagnostic diary")
                .content("delete behavior")
                .diaryDate(LocalDate.now())
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

        return new SeededGraph(
                user.getUserId(),
                userPlant.getUserPlantId(),
                userPlantImage.getImageFileId(),
                diary.getDiaryId(),
                diaryUserPlant.getDiaryUserPlantId(),
                diaryImage.getImageFileId()
        );
    }

    private CatalogDeleteSeed seedCatalogDeleteGraph(String label) {
        String suffix = label + "-" + System.nanoTime();
        ImageFile targetPlantImage = image("catalog-target-plant-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile targetFarmImage = image("catalog-target-farm-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile otherImage = image("catalog-other-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile userPlantImage = image("catalog-user-plant-" + suffix, ImageDomainType.USERPLANT, 0L);

        Plant otherPlant = findActivePlantByName("기타");
        if (otherPlant == null) {
            otherPlant = Plant.builder()
                    .plantName("기타")
                    .plantEnglishName("Other")
                    .species("reserved")
                    .season("all")
                    .plantImageFileId(otherImage.getImageFileId())
                    .build();
            entityManager.persist(otherPlant);
        }

        Farm otherFarm = findActiveFarmByName("기타(Other)");
        if (otherFarm == null) {
            otherFarm = Farm.builder()
                    .gardenUniqueId(negativeUniqueId(suffix + "-other"))
                    .operator("reserved")
                    .farmName("기타(Other)")
                    .roadNameAddress("N/A")
                    .lotNumberAddress("N/A")
                    .facilities("N/A")
                    .contact("N/A")
                    .latitude(0.0)
                    .longitude(0.0)
                    .available(false)
                    .farmImageFileId(otherImage.getImageFileId())
                    .build();
            entityManager.persist(otherFarm);
        }

        Plant targetPlant = Plant.builder()
                .plantName("catalog-target-" + suffix)
                .plantEnglishName("Catalog Target")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(targetPlantImage.getImageFileId())
                .build();
        entityManager.persist(targetPlant);

        Farm targetFarm = Farm.builder()
                .gardenUniqueId(negativeUniqueId(suffix + "-target"))
                .operator("diagnostic")
                .farmName("catalog-target-" + suffix)
                .roadNameAddress("road")
                .lotNumberAddress("lot")
                .facilities("none")
                .contact("none")
                .latitude(37.5)
                .longitude(127.0)
                .available(true)
                .farmImageFileId(targetFarmImage.getImageFileId())
                .build();
        entityManager.persist(targetFarm);

        UserPlant userPlant = UserPlant.builder()
                .userId(9_000_000_000L + Math.floorMod(System.nanoTime(), 100_000_000L))
                .plantId(targetPlant.getPlantId())
                .plantName(targetPlant.getPlantName())
                .plantNickname("catalog-" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .farmId(targetFarm.getFarmId())
                .plantingPlace(targetFarm.getFarmName())
                .plantedDate(LocalDateTime.now())
                .notes("catalog delete diagnostic")
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

        return new CatalogDeleteSeed(
                targetPlant.getPlantId(),
                otherPlant.getPlantId(),
                targetPlantImage.getImageFileId(),
                targetFarm.getFarmId(),
                otherFarm.getFarmId(),
                targetFarmImage.getImageFileId(),
                userPlant.getUserPlantId());
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

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void deleteDiaryLinks(Long diaryId) {
        entityManager.createQuery("DELETE FROM DiaryUserPlant dup WHERE dup.diaryId = :diaryId")
                .setParameter("diaryId", diaryId)
                .executeUpdate();
    }

    private void deleteUserPlantLinks(Long userPlantId) {
        entityManager.createQuery("DELETE FROM DiaryUserPlant dup WHERE dup.userPlantId = :userPlantId")
                .setParameter("userPlantId", userPlantId)
                .executeUpdate();
    }

    private long countUserRows(Long userId) {
        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM users WHERE user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue();
    }

    private long countUserPlantRows(Long userPlantId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM user_plants WHERE user_plant_id = :userPlantId"
                )
                .setParameter("userPlantId", userPlantId)
                .getSingleResult();
        return count.longValue();
    }

    private long countDiaryUserPlantLinks(Long diaryId, Long userPlantId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM diary_user_plant
                        WHERE diary_id = :diaryId
                          AND user_plant_id = :userPlantId
                        """)
                .setParameter("diaryId", diaryId)
                .setParameter("userPlantId", userPlantId)
                .getSingleResult();
        return count.longValue();
    }

    private UserWithdrawalRow findUserWithdrawalRow(Long userId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT email, subscription_status FROM users WHERE user_id = :userId"
                )
                .setParameter("userId", userId)
                .getSingleResult();
        return new UserWithdrawalRow((String) row[0], (String) row[1]);
    }

    private UserPlantDeletionRow findUserPlantDeletionRow(Long userPlantId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(
                        """
                        SELECT deleted, deleted_at, is_notification_enabled, plant_nickname
                        FROM user_plants
                        WHERE user_plant_id = :userPlantId
                        """
                )
                .setParameter("userPlantId", userPlantId)
                .getSingleResult();
        return new UserPlantDeletionRow(asBoolean(row[0]), row[1], asBoolean(row[2]), (String) row[3]);
    }

    private CatalogDeletionRow findCatalogDeletionRow(String tableName, String idColumn, Long id) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT COUNT(*), MAX(deleted), MAX(deleted_at)
                        FROM %s
                        WHERE %s = :id
                        """.formatted(tableName, idColumn))
                .setParameter("id", id)
                .getSingleResult();
        return new CatalogDeletionRow(
                ((Number) row[0]).longValue(),
                asBoolean(row[1]),
                row[2]);
    }

    private Long userPlantReferenceId(String columnName, Long userPlantId) {
        Number value = (Number) entityManager.createNativeQuery("""
                        SELECT %s
                        FROM user_plants
                        WHERE user_plant_id = :userPlantId
                        """.formatted(columnName))
                .setParameter("userPlantId", userPlantId)
                .getSingleResult();
        return value.longValue();
    }

    private Plant findActivePlantByName(String plantName) {
        List<Plant> plants = entityManager.createQuery("""
                        SELECT p FROM Plant p
                        WHERE p.plantName = :plantName
                        ORDER BY p.plantId
                        """, Plant.class)
                .setParameter("plantName", plantName)
                .setMaxResults(1)
                .getResultList();
        return plants.isEmpty() ? null : plants.get(0);
    }

    private Farm findActiveFarmByName(String farmName) {
        List<Farm> farms = entityManager.createQuery("""
                        SELECT f FROM Farm f
                        WHERE f.farmName = :farmName
                        ORDER BY f.farmId
                        """, Farm.class)
                .setParameter("farmName", farmName)
                .setMaxResults(1)
                .getResultList();
        return farms.isEmpty() ? null : farms.get(0);
    }

    private int negativeUniqueId(String value) {
        return -1 - Math.floorMod(value.hashCode(), 1_000_000_000);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record SeededGraph(
            Long userId,
            Long userPlantId,
            Long userPlantImageFileId,
            Long diaryId,
            Long diaryUserPlantId,
            Long diaryImageFileId
    ) {
    }

    private record UserWithdrawalRow(String email, String subscriptionStatus) {
    }

    private record UserPlantDeletionRow(
            boolean deleted,
            Object deletedAt,
            boolean notificationEnabled,
            String plantNickname
    ) {
    }

    private record CatalogDeleteSeed(
            Long targetPlantId,
            Long otherPlantId,
            Long targetPlantImageFileId,
            Long targetFarmId,
            Long otherFarmId,
            Long targetFarmImageFileId,
            Long userPlantId
    ) {
    }

    private record CatalogDeletionRow(long rowCount, boolean deleted, Object deletedAt) {
    }
}

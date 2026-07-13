package com.project.farming.integration;

import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.farm.repository.FarmRepository;
import com.project.farming.domain.farm.service.FarmAdminService;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.plant.repository.PlantRepository;
import com.project.farming.domain.plant.service.PlantAdminService;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class CatalogReferenceConcurrencyIntegrationDiagnosticsTest {

    private static final long AWAIT_SECONDS = 10;
    private static final long BLOCK_ASSERT_MILLIS = 300;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private PlantAdminService plantAdminService;

    @Autowired
    private FarmAdminService farmAdminService;

    @MockBean
    private ImageFileService imageFileService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void createFirstShouldMakePlantDeleteWaitAndReassignCommittedUserPlant() throws Exception {
        CatalogSeed seed = transactionTemplate().execute(status -> seedCatalogGraph("create-first"));
        assertThat(seed).isNotNull();

        CountDownLatch referenceLocksAcquired = new CountDownLatch(1);
        CountDownLatch allowReferenceCommit = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Long userPlantId = null;

        try {
            Future<Long> createFuture = executor.submit(() -> transactionTemplate().execute(status -> {
                Plant plant = plantRepository.findReferenceCandidatesForShare(seed.targetPlantName()).stream()
                        .findFirst()
                        .orElseThrow();
                Farm farm = farmRepository.findReferenceByGardenUniqueIdForShare(seed.targetGardenUniqueId())
                        .orElseThrow();
                referenceLocksAcquired.countDown();
                await(allowReferenceCommit);
                return persistUserPlant(seed, plant.getPlantId(), farm.getFarmId(), "create-first");
            }));

            assertThat(referenceLocksAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<Void> deleteFuture = executor.submit(() -> {
                deleteStarted.countDown();
                plantAdminService.deletePlant(seed.targetPlantId());
                return null;
            });
            assertThat(deleteStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            boolean deleteBlockedBeforeCreateCommit = isBlocked(deleteFuture);
            allowReferenceCommit.countDown();
            userPlantId = createFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            deleteFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS);

            CatalogOutcome outcome = plantOutcome(seed, userPlantId);

            System.out.printf(
                    "Catalog create-first result: deleteBlocked=%s, physicalParentRows=%d, "
                            + "deletedParentRows=%d, activeDeletedParentReferences=%d, reassignedToOther=%d%n",
                    deleteBlockedBeforeCreateCommit,
                    outcome.physicalParentRows(),
                    outcome.deletedParentRows(),
                    outcome.activeDeletedParentReferences(),
                    outcome.reassignedToOther());

            assertThat(deleteBlockedBeforeCreateCommit).isTrue();
            assertCatalogOutcome(outcome);
            assertThat(userPlantReferenceId("plant_id", userPlantId)).isEqualTo(seed.otherPlantId());
            assertThat(count("SELECT COUNT(*) FROM image_files WHERE image_file_id = ?", seed.targetPlantImageId()))
                    .isEqualTo(1L);
        } finally {
            allowReferenceCommit.countDown();
            try {
                shutdown(executor);
            } finally {
                cleanup(seed, userPlantId);
            }
        }
    }

    @Test
    void userPlantReassignIndexesShouldMatchCatalogReferencePredicates() {
        List<String> plantIndexColumns = indexColumns("idx_userplant_plant_active");
        List<String> farmIndexColumns = indexColumns("idx_userplant_farm_active");
        String plantPlanKey = explainReferenceLookupKey(
                "idx_userplant_plant_active", "plant_id", Long.MAX_VALUE - 10);
        String farmPlanKey = explainReferenceLookupKey(
                "idx_userplant_farm_active", "farm_id", Long.MAX_VALUE - 20);

        System.out.printf(
                "Catalog reassign index plan: plantColumns=%s, plantKey=%s, farmColumns=%s, farmKey=%s%n",
                plantIndexColumns,
                plantPlanKey,
                farmIndexColumns,
                farmPlanKey);

        assertThat(plantIndexColumns).containsExactly("plant_id", "deleted");
        assertThat(farmIndexColumns).containsExactly("farm_id", "deleted");
        assertThat(plantPlanKey).isEqualTo("idx_userplant_plant_active");
        assertThat(farmPlanKey).isEqualTo("idx_userplant_farm_active");
    }

    @Test
    void deleteFirstShouldMakeFarmReferenceWaitAndResolveToOtherFarm() throws Exception {
        CatalogSeed seed = transactionTemplate().execute(status -> seedCatalogGraph("delete-first"));
        assertThat(seed).isNotNull();

        CountDownLatch deleteLockAcquired = new CountDownLatch(1);
        CountDownLatch allowDeleteCommit = new CountDownLatch(1);
        CountDownLatch referenceStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Long userPlantId = null;

        try {
            Future<Void> deleteFuture = executor.submit(() -> transactionTemplate().execute(status -> {
                farmRepository.findByFarmIdForUpdate(seed.targetFarmId()).orElseThrow();
                deleteLockAcquired.countDown();
                await(allowDeleteCommit);
                farmAdminService.deleteFarm(seed.targetFarmId());
                return null;
            }));

            assertThat(deleteLockAcquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<Long> createFuture = executor.submit(() -> transactionTemplate().execute(status -> {
                Plant plant = plantRepository.findReferenceCandidatesForShare(seed.targetPlantName()).stream()
                        .findFirst()
                        .orElseThrow();
                referenceStarted.countDown();
                Farm farm = farmRepository.findReferenceByGardenUniqueIdForShare(seed.targetGardenUniqueId())
                        .orElseGet(() -> farmRepository.findOtherFarmCandidatesForShare("기타(Other)").stream()
                                .findFirst()
                                .orElseThrow());
                return persistUserPlant(seed, plant.getPlantId(), farm.getFarmId(), "delete-first");
            }));

            assertThat(referenceStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            boolean referenceBlockedBeforeDeleteCommit = isBlocked(createFuture);
            allowDeleteCommit.countDown();
            deleteFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            userPlantId = createFuture.get(AWAIT_SECONDS, TimeUnit.SECONDS);

            CatalogOutcome outcome = farmOutcome(seed, userPlantId);

            System.out.printf(
                    "Catalog delete-first result: referenceBlocked=%s, physicalParentRows=%d, "
                            + "deletedParentRows=%d, activeDeletedParentReferences=%d, resolvedToOther=%d%n",
                    referenceBlockedBeforeDeleteCommit,
                    outcome.physicalParentRows(),
                    outcome.deletedParentRows(),
                    outcome.activeDeletedParentReferences(),
                    outcome.reassignedToOther());

            assertThat(referenceBlockedBeforeDeleteCommit).isTrue();
            assertCatalogOutcome(outcome);
            assertThat(userPlantReferenceId("farm_id", userPlantId)).isEqualTo(seed.otherFarmId());
            assertThat(count("SELECT COUNT(*) FROM image_files WHERE image_file_id = ?", seed.targetFarmImageId()))
                    .isEqualTo(1L);
        } finally {
            allowDeleteCommit.countDown();
            try {
                shutdown(executor);
            } finally {
                cleanup(seed, userPlantId);
            }
        }
    }

    private CatalogSeed seedCatalogGraph(String label) {
        String suffix = label + "-" + Long.toUnsignedString(System.nanoTime(), 36);
        ImageFile targetPlantImage = image("target-plant-" + suffix, ImageDomainType.PLANT);
        ImageFile targetFarmImage = image("target-farm-" + suffix, ImageDomainType.FARM);
        ImageFile userPlantImage = image("user-plant-" + suffix, ImageDomainType.USERPLANT);
        ImageFile otherImage = image("other-" + suffix, ImageDomainType.PLANT);

        ExistingId otherPlant = findActiveId(
                "SELECT plant_id FROM plant_info WHERE plant_name = '기타' AND deleted = false ORDER BY plant_id LIMIT 1");
        if (otherPlant.id() == null) {
            Plant created = Plant.builder()
                    .plantName("기타")
                    .plantEnglishName("Other")
                    .species("reserved")
                    .season("all")
                    .plantImageFileId(otherImage.getImageFileId())
                    .build();
            entityManager.persist(created);
            otherPlant = new ExistingId(created.getPlantId(), true);
        }

        ExistingId otherFarm = findActiveId("""
                SELECT farm_id
                FROM farm_info
                WHERE farm_name = '기타(Other)'
                  AND deleted = false
                ORDER BY farm_id
                LIMIT 1
                """);
        if (otherFarm.id() == null) {
            Farm created = Farm.builder()
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
            entityManager.persist(created);
            otherFarm = new ExistingId(created.getFarmId(), true);
        }

        String targetPlantName = "catalog-" + suffix;
        Plant targetPlant = Plant.builder()
                .plantName(targetPlantName)
                .plantEnglishName("Catalog Concurrency")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(targetPlantImage.getImageFileId())
                .build();
        entityManager.persist(targetPlant);

        int targetGardenUniqueId = negativeUniqueId(suffix + "-target");
        Farm targetFarm = Farm.builder()
                .gardenUniqueId(targetGardenUniqueId)
                .operator("diagnostic")
                .farmName("catalog-" + suffix)
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
        entityManager.flush();

        return new CatalogSeed(
                targetPlant.getPlantId(),
                targetPlantName,
                otherPlant.id(),
                otherPlant.created(),
                targetPlantImage.getImageFileId(),
                targetFarm.getFarmId(),
                targetGardenUniqueId,
                otherFarm.id(),
                otherFarm.created(),
                targetFarmImage.getImageFileId(),
                userPlantImage.getImageFileId(),
                otherImage.getImageFileId(),
                8_000_000_000L + Math.floorMod(System.nanoTime(), 100_000_000L),
                "lock-" + suffix.substring(Math.max(0, suffix.length() - 10)));
    }

    private ImageFile image(String key, ImageDomainType domainType) {
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(key + ".png")
                .s3Key(key)
                .imageUrl("https://example.test/" + key + ".png")
                .domainType(domainType)
                .domainId(0L)
                .build();
        entityManager.persist(imageFile);
        return imageFile;
    }

    private Long persistUserPlant(CatalogSeed seed, Long plantId, Long farmId, String operation) {
        UserPlant userPlant = UserPlant.builder()
                .userId(seed.syntheticUserId())
                .plantId(plantId)
                .plantName(seed.targetPlantName())
                .plantNickname(seed.nicknamePrefix() + operation.substring(0, 2))
                .farmId(farmId)
                .plantingPlace("catalog concurrency")
                .plantedDate(LocalDateTime.now())
                .notes(operation)
                .isNotificationEnabled(false)
                .waterIntervalDays(1)
                .watered(false)
                .pruneIntervalDays(1)
                .pruned(false)
                .fertilizeIntervalDays(1)
                .fertilized(false)
                .userPlantImageFileId(seed.userPlantImageId())
                .build();
        entityManager.persist(userPlant);
        entityManager.flush();
        return userPlant.getUserPlantId();
    }

    private CatalogOutcome plantOutcome(CatalogSeed seed, Long userPlantId) {
        return catalogOutcome(
                "plant_info", "plant_id", seed.targetPlantId(),
                "plant_id", seed.otherPlantId(), userPlantId);
    }

    private CatalogOutcome farmOutcome(CatalogSeed seed, Long userPlantId) {
        return catalogOutcome(
                "farm_info", "farm_id", seed.targetFarmId(),
                "farm_id", seed.otherFarmId(), userPlantId);
    }

    private CatalogOutcome catalogOutcome(
            String parentTable,
            String parentIdColumn,
            Long parentId,
            String childReferenceColumn,
            Long otherId,
            Long userPlantId) {
        long physicalRows = count(
                "SELECT COUNT(*) FROM %s WHERE %s = ?".formatted(parentTable, parentIdColumn),
                parentId);
        long deletedRows = count(
                "SELECT COUNT(*) FROM %s WHERE %s = ? AND deleted = true".formatted(parentTable, parentIdColumn),
                parentId);
        long activeDeletedReferences = count("""
                SELECT COUNT(*)
                FROM user_plants up
                JOIN %s parent ON parent.%s = up.%s
                WHERE up.user_plant_id = ?
                  AND up.deleted = false
                  AND parent.deleted = true
                """.formatted(parentTable, parentIdColumn, childReferenceColumn), userPlantId);
        long reassignedToOther = count(
                "SELECT COUNT(*) FROM user_plants WHERE user_plant_id = ? AND %s = %d"
                        .formatted(childReferenceColumn, otherId),
                userPlantId);
        return new CatalogOutcome(physicalRows, deletedRows, activeDeletedReferences, reassignedToOther);
    }

    private void assertCatalogOutcome(CatalogOutcome outcome) {
        assertThat(outcome.physicalParentRows()).isEqualTo(1L);
        assertThat(outcome.deletedParentRows()).isEqualTo(1L);
        assertThat(outcome.activeDeletedParentReferences()).isZero();
        assertThat(outcome.reassignedToOther()).isEqualTo(1L);
    }

    private boolean isBlocked(Future<?> future) throws Exception {
        try {
            future.get(BLOCK_ASSERT_MILLIS, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException expected) {
            return true;
        }
    }

    private ExistingId findActiveId(String sql) {
        List<?> rows = entityManager.createNativeQuery(sql).getResultList();
        if (rows.isEmpty()) {
            return new ExistingId(null, false);
        }
        return new ExistingId(((Number) rows.get(0)).longValue(), false);
    }

    private List<String> indexColumns(String indexName) {
        return jdbcTemplate.queryForList("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'user_plants'
                          AND index_name = ?
                        ORDER BY seq_in_index
                        """,
                String.class,
                indexName);
    }

    private String explainReferenceLookupKey(String indexName, String referenceColumn, Long referenceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                EXPLAIN
                SELECT user_plant_id
                FROM user_plants FORCE INDEX (%s)
                WHERE %s = ?
                  AND deleted = false
                """.formatted(indexName, referenceColumn), referenceId);
        Object key = rows.get(0).get("key");
        return key == null ? "" : key.toString();
    }

    private Long userPlantReferenceId(String columnName, Long userPlantId) {
        Number value = jdbcTemplate.queryForObject(
                "SELECT %s FROM user_plants WHERE user_plant_id = ?".formatted(columnName),
                Number.class,
                userPlantId);
        return value == null ? null : value.longValue();
    }

    private long count(String sql, Object parameter) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameter);
        return count == null ? 0L : count;
    }

    private void cleanup(CatalogSeed seed, Long userPlantId) {
        transactionTemplate().executeWithoutResult(status -> {
            if (userPlantId != null) {
                jdbcTemplate.update("DELETE FROM user_plants WHERE user_plant_id = ?", userPlantId);
            }
            jdbcTemplate.update("DELETE FROM user_plants WHERE user_id = ?", seed.syntheticUserId());
            jdbcTemplate.update("DELETE FROM plant_info WHERE plant_id = ?", seed.targetPlantId());
            jdbcTemplate.update("DELETE FROM farm_info WHERE farm_id = ?", seed.targetFarmId());
            if (seed.createdOtherPlant()) {
                jdbcTemplate.update("DELETE FROM plant_info WHERE plant_id = ?", seed.otherPlantId());
            }
            if (seed.createdOtherFarm()) {
                jdbcTemplate.update("DELETE FROM farm_info WHERE farm_id = ?", seed.otherFarmId());
            }
            jdbcTemplate.update("DELETE FROM image_files WHERE image_file_id IN (?, ?, ?, ?)",
                    seed.targetPlantImageId(),
                    seed.targetFarmImageId(),
                    seed.userPlantImageId(),
                    seed.otherImageId());
        });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Catalog concurrency executor did not terminate.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Catalog concurrency executor shutdown was interrupted.", exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while waiting for catalog concurrency coordination.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Catalog concurrency coordination was interrupted.", exception);
        }
    }

    private int negativeUniqueId(String value) {
        return -1 - Math.floorMod(value.hashCode(), 1_000_000_000);
    }

    private record ExistingId(Long id, boolean created) {
    }

    private record CatalogSeed(
            Long targetPlantId,
            String targetPlantName,
            Long otherPlantId,
            boolean createdOtherPlant,
            Long targetPlantImageId,
            Long targetFarmId,
            int targetGardenUniqueId,
            Long otherFarmId,
            boolean createdOtherFarm,
            Long targetFarmImageId,
            Long userPlantImageId,
            Long otherImageId,
            Long syntheticUserId,
            String nicknamePrefix
    ) {
    }

    private record CatalogOutcome(
            long physicalParentRows,
            long deletedParentRows,
            long activeDeletedParentReferences,
            long reassignedToOther
    ) {
    }
}

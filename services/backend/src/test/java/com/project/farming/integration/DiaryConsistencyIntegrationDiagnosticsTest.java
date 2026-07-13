package com.project.farming.integration;

import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.diary.repository.DiaryRepository;
import com.project.farming.domain.diary.repository.DiaryUserPlantRepository;
import com.project.farming.domain.diary.service.DiaryService;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class DiaryConsistencyIntegrationDiagnosticsTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private DiaryUserPlantRepository diaryUserPlantRepository;

    @Autowired
    private UserPlantRepository userPlantRepository;

    @Autowired
    private DiaryService diaryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    DiaryConsistencyIntegrationDiagnosticsTest(
            @Autowired PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanupDiagnosticData() {
        jdbcTemplate.update("""
                DELETE link
                FROM diary_user_plant link
                JOIN diaries diary ON diary.diary_id = link.diary_id
                JOIN users u ON u.user_id = diary.user_id
                WHERE u.email LIKE 'consistency-%@example.com'
                """);
        jdbcTemplate.update("""
                DELETE diary
                FROM diaries diary
                JOIN users u ON u.user_id = diary.user_id
                WHERE u.email LIKE 'consistency-%@example.com'
                """);
        jdbcTemplate.update("""
                DELETE user_plant
                FROM user_plants user_plant
                JOIN users u ON u.user_id = user_plant.user_id
                WHERE u.email LIKE 'consistency-%@example.com'
                """);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'consistency-%@example.com'");
        jdbcTemplate.update("DELETE FROM plant_info WHERE plant_name LIKE 'consistency-plant-%'");
        jdbcTemplate.update("DELETE FROM farm_info WHERE farm_name LIKE 'consistency-farm-%'");
        jdbcTemplate.update("DELETE FROM image_files WHERE s3key LIKE 'consistency-%'");
    }

    @Test
    void concurrentDiaryUpdatesShouldRejectOneWriter() throws Exception {
        SeededGraph graph = seedGraph("optimistic", false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothDiariesLoaded = new CountDownLatch(2);

        try {
            Callable<UpdateOutcome> firstUpdate = updateDiaryTask(graph.diaryId(), "first update", bothDiariesLoaded);
            Callable<UpdateOutcome> secondUpdate = updateDiaryTask(graph.diaryId(), "second update", bothDiariesLoaded);

            Future<UpdateOutcome> firstResult = executor.submit(firstUpdate);
            Future<UpdateOutcome> secondResult = executor.submit(secondUpdate);

            List<UpdateOutcome> outcomes = List.of(firstResult.get(), secondResult.get());
            long successes = outcomes.stream().filter(UpdateOutcome.SUCCESS::equals).count();
            long optimisticConflicts = outcomes.stream().filter(UpdateOutcome.OPTIMISTIC_CONFLICT::equals).count();

            System.out.printf(
                    "Diary concurrent update result: successes=%d, optimisticConflicts=%d%n",
                    successes,
                    optimisticConflicts
            );

            assertThat(successes).isEqualTo(1);
            assertThat(optimisticConflicts).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            cleanupDiaryLinks(graph.diaryId());
        }
    }

    @Test
    void concurrentUserPlantEditsShouldRejectOneStaleWriter() throws Exception {
        SeededGraph graph = seedGraph("user-plant-optimistic", true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothUserPlantsLoaded = new CountDownLatch(2);

        try {
            Callable<UpdateOutcome> firstUpdate = updateUserPlantTask(
                    graph.userPlantId(), "first nickname", bothUserPlantsLoaded);
            Callable<UpdateOutcome> secondUpdate = updateUserPlantTask(
                    graph.userPlantId(), "second nickname", bothUserPlantsLoaded);

            Future<UpdateOutcome> firstResult = executor.submit(firstUpdate);
            Future<UpdateOutcome> secondResult = executor.submit(secondUpdate);
            List<UpdateOutcome> outcomes = List.of(firstResult.get(), secondResult.get());
            long successes = outcomes.stream().filter(UpdateOutcome.SUCCESS::equals).count();
            long optimisticConflicts = outcomes.stream().filter(UpdateOutcome.OPTIMISTIC_CONFLICT::equals).count();

            System.out.printf(
                    "UserPlant concurrent edit baseline: successes=%d, optimisticConflicts=%d%n",
                    successes,
                    optimisticConflicts
            );

            assertThat(successes).isEqualTo(1);
            assertThat(optimisticConflicts).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            cleanupDiaryLinks(graph.diaryId());
        }
    }

    @Test
    void concurrentDiaryCareCompletionsShouldMergeAndRemainIdempotent() throws Exception {
        SeededGraph graph = seedGraph("user-plant-care", true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothCareUpdatesReady = new CountDownLatch(2);

        try {
            Future<Integer> watering = executor.submit(recordCareTask(graph, CareType.WATERING, bothCareUpdatesReady));
            Future<Integer> pruning = executor.submit(recordCareTask(graph, CareType.PRUNING, bothCareUpdatesReady));

            int wateringRows = watering.get();
            int pruningRows = pruning.get();
            UserPlant afterConcurrentUpdates = loadUserPlant(graph.userPlantId());
            Long versionAfterConcurrentUpdates = afterConcurrentUpdates.getVersion();

            int repeatedWateringRows = transactionTemplate.execute(status ->
                    userPlantRepository.recordWateringCompletion(
                            graph.userId(), List.of(graph.userPlantId())));
            transactionTemplate.execute(status -> userPlantRepository.resetDailyCareStatuses());
            UserPlant finalUserPlant = loadUserPlant(graph.userPlantId());

            System.out.printf(
                    "UserPlant atomic care update: wateringRows=%d, pruningRows=%d, repeatedWateringRows=%d, "
                            + "watered=%s, pruned=%s, version=%d%n",
                    wateringRows,
                    pruningRows,
                    repeatedWateringRows,
                    finalUserPlant.isWatered(),
                    finalUserPlant.isPruned(),
                    finalUserPlant.getVersion()
            );

            assertThat(wateringRows).isEqualTo(1);
            assertThat(pruningRows).isEqualTo(1);
            assertThat(repeatedWateringRows).isZero();
            assertThat(finalUserPlant.isWatered()).isTrue();
            assertThat(finalUserPlant.isPruned()).isTrue();
            assertThat(finalUserPlant.getLastWateredDate()).isEqualTo(LocalDate.now());
            assertThat(finalUserPlant.getLastPrunedDate()).isEqualTo(LocalDate.now());
            assertThat(finalUserPlant.getVersion()).isEqualTo(versionAfterConcurrentUpdates);
        } finally {
            executor.shutdownNow();
            cleanupDiaryLinks(graph.diaryId());
        }
    }

    @Test
    void diaryCreateUpdateAndDeleteShouldKeepDbCareStateCumulative() {
        SeededGraph graph = seedGraph("diary-care-flow", true);
        User user = transactionTemplate.execute(status -> userRepository.findById(graph.userId())
                .orElseThrow(() -> new IllegalStateException("Seeded User not found: " + graph.userId())));

        Diary wateringDiary = diaryService.createDiary(
                user,
                "watering",
                "first completion",
                LocalDate.now(),
                null,
                true,
                false,
                false,
                List.of(graph.userPlantId())
        );
        Diary pruningDiary = diaryService.createDiary(
                user,
                "pruning",
                "later false must not erase watering",
                LocalDate.now(),
                null,
                false,
                true,
                false,
                List.of(graph.userPlantId())
        );
        diaryService.updateDiary(
                graph.diaryId(),
                user,
                "fertilizing",
                "update path must write DB care state",
                LocalDate.now(),
                null,
                false,
                false,
                false,
                true,
                List.of(graph.userPlantId())
        );

        diaryService.deleteDiary(wateringDiary.getDiaryId(), user);
        UserPlant finalUserPlant = loadUserPlant(graph.userPlantId());

        System.out.printf(
                "Diary care flow: watered=%s, pruned=%s, fertilized=%s, version=%d%n",
                finalUserPlant.isWatered(),
                finalUserPlant.isPruned(),
                finalUserPlant.isFertilized(),
                finalUserPlant.getVersion()
        );

        assertThat(finalUserPlant.isWatered()).isTrue();
        assertThat(finalUserPlant.isPruned()).isTrue();
        assertThat(finalUserPlant.isFertilized()).isTrue();

        diaryService.deleteDiary(pruningDiary.getDiaryId(), user);
        cleanupDiaryLinks(graph.diaryId());
    }

    @Test
    void dailyResetShouldClearOnlyStaleCareCompletion() {
        SeededGraph graph = seedGraph("date-aware-reset", true);

        transactionTemplate.execute(status -> userPlantRepository.recordWateringCompletion(
                graph.userId(), List.of(graph.userPlantId())));
        int currentDateResetRows = transactionTemplate.execute(status ->
                userPlantRepository.resetDailyCareStatuses());
        UserPlant currentCompletion = loadUserPlant(graph.userPlantId());

        jdbcTemplate.update("""
                UPDATE user_plants
                SET watered = TRUE,
                    last_watered_date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)
                WHERE user_plant_id = ?
                """, graph.userPlantId());
        int staleResetRows = transactionTemplate.execute(status ->
                userPlantRepository.resetDailyCareStatuses());
        UserPlant staleCompletion = loadUserPlant(graph.userPlantId());

        System.out.printf(
                "UserPlant date-aware reset: currentDateResetRows=%d, staleResetRows=%d, "
                        + "currentPreserved=%s, staleCleared=%s%n",
                currentDateResetRows,
                staleResetRows,
                currentCompletion.isWatered(),
                !staleCompletion.isWatered()
        );

        assertThat(currentCompletion.isWatered()).isTrue();
        assertThat(staleCompletion.isWatered()).isFalse();
        assertThat(staleResetRows).isPositive();
        cleanupDiaryLinks(graph.diaryId());
    }

    @Test
    void duplicateDiaryUserPlantLinksShouldBeRejectedByDatabaseConstraint() {
        SeededGraph graph = seedGraph("duplicate-link", true);

        try {
            Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
                diaryUserPlantRepository.saveAndFlush(DiaryUserPlant.builder()
                        .diaryId(graph.diaryId())
                        .userPlantId(graph.userPlantId())
                        .build());
                diaryUserPlantRepository.saveAndFlush(DiaryUserPlant.builder()
                        .diaryId(graph.diaryId())
                        .userPlantId(graph.userPlantId())
                        .build());
            }));

            System.out.printf(
                    "DiaryUserPlant duplicate link rejected: rejected=%s%n",
                    thrown != null
            );

            assertThat(thrown)
                    .as("DB must reject duplicate diary-userPlant links.")
                    .isNotNull();
            assertThat(hasCause(thrown, DataIntegrityViolationException.class)
                    || hasCause(thrown, ConstraintViolationException.class))
                    .as("Duplicate link should fail with a DB constraint violation.")
                    .isTrue();
        } finally {
            cleanupDiaryLinks(graph.diaryId());
        }
    }

    private Callable<UpdateOutcome> updateDiaryTask(
            Long diaryId, String title, CountDownLatch bothDiariesLoaded) {
        return () -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Diary diary = diaryRepository.findById(diaryId)
                            .orElseThrow(() -> new IllegalStateException("Seeded Diary not found: " + diaryId));
                    bothDiariesLoaded.countDown();
                    awaitBothLoaded(bothDiariesLoaded);
                    diary.updateDiary(
                            title,
                            "concurrent update diagnostic",
                            LocalDate.now(),
                            diary.getDiaryImageFileId(),
                            false,
                            false,
                            false
                    );
                });
                return UpdateOutcome.SUCCESS;
            } catch (Throwable throwable) {
                if (isOptimisticConflict(throwable)) {
                    return UpdateOutcome.OPTIMISTIC_CONFLICT;
                }
                throw throwable;
            }
        };
    }

    private Callable<UpdateOutcome> updateUserPlantTask(
            Long userPlantId, String nickname, CountDownLatch bothUserPlantsLoaded) {
        return () -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    UserPlant userPlant = entityManager.find(UserPlant.class, userPlantId);
                    if (userPlant == null) {
                        throw new IllegalStateException("Seeded UserPlant not found: " + userPlantId);
                    }
                    bothUserPlantsLoaded.countDown();
                    awaitBothLoaded(bothUserPlantsLoaded);
                    userPlant.updateUserPlantInfo(nickname, "concurrent user plant edit");
                });
                return UpdateOutcome.SUCCESS;
            } catch (Throwable throwable) {
                if (isOptimisticConflict(throwable)) {
                    return UpdateOutcome.OPTIMISTIC_CONFLICT;
                }
                throw throwable;
            }
        };
    }

    private Callable<Integer> recordCareTask(
            SeededGraph graph, CareType careType, CountDownLatch bothCareUpdatesReady) {
        return () -> transactionTemplate.execute(status -> {
            bothCareUpdatesReady.countDown();
            awaitBothLoaded(bothCareUpdatesReady);
            return switch (careType) {
                case WATERING -> userPlantRepository.recordWateringCompletion(
                        graph.userId(), List.of(graph.userPlantId()));
                case PRUNING -> userPlantRepository.recordPruningCompletion(
                        graph.userId(), List.of(graph.userPlantId()));
            };
        });
    }

    private UserPlant loadUserPlant(Long userPlantId) {
        return transactionTemplate.execute(status -> userPlantRepository.findById(userPlantId)
                .orElseThrow(() -> new IllegalStateException("Seeded UserPlant not found: " + userPlantId)));
    }

    private SeededGraph seedGraph(String label, boolean includeUserPlant) {
        return transactionTemplate.execute(status -> {
            String suffix = label + "-" + System.nanoTime();

            User user = User.builder()
                    .email("consistency-" + suffix + "@example.com")
                    .password("encoded-password")
                    .nickname("consistency" + suffix.substring(Math.max(0, suffix.length() - 8)))
                    .oauthProvider("LOCAL")
                    .role(UserRole.USER)
                    .subscriptionStatus("FREE")
                    .build();
            entityManager.persist(user);

            Long userPlantId = null;
            if (includeUserPlant) {
                ImageFile plantImage = image("consistency-plant-" + suffix, ImageDomainType.PLANT, 0L);
                ImageFile farmImage = image("consistency-farm-" + suffix, ImageDomainType.FARM, 0L);
                ImageFile userPlantImage = image("consistency-user-plant-" + suffix, ImageDomainType.USERPLANT, 0L);

                Plant plant = Plant.builder()
                        .plantName("consistency-plant-" + suffix)
                        .plantEnglishName("Consistency Plant")
                        .species("diagnostic")
                        .season("all")
                        .plantImageFileId(plantImage.getImageFileId())
                        .build();
                entityManager.persist(plant);

                Farm farm = Farm.builder()
                        .gardenUniqueId(Math.floorMod(suffix.hashCode(), 1_000_000_000))
                        .operator("diagnostic")
                        .farmName("consistency-farm-" + suffix)
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
                        .plantNickname("consistency-plant")
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
                userPlantId = userPlant.getUserPlantId();
            }

            Diary diary = Diary.builder()
                    .userId(user.getUserId())
                    .title("consistency diagnostic diary")
                    .content("concurrency")
                    .diaryDate(LocalDate.now())
                    .watered(false)
                    .pruned(false)
                    .fertilized(false)
                    .build();
            entityManager.persist(diary);
            entityManager.flush();

            return new SeededGraph(user.getUserId(), diary.getDiaryId(), userPlantId);
        });
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

    private void cleanupDiaryLinks(Long diaryId) {
        transactionTemplate.executeWithoutResult(status -> diaryUserPlantRepository.deleteByDiaryId(diaryId));
    }

    private void awaitBothLoaded(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent Diary reads.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent Diary reads.", exception);
        }
    }

    private boolean isOptimisticConflict(Throwable throwable) {
        return hasCause(throwable, ObjectOptimisticLockingFailureException.class)
                || hasCause(throwable, JpaOptimisticLockingFailureException.class);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private enum UpdateOutcome {
        SUCCESS,
        OPTIMISTIC_CONFLICT
    }

    private enum CareType {
        WATERING,
        PRUNING
    }

    private record SeededGraph(Long userId, Long diaryId, Long userPlantId) {
    }
}

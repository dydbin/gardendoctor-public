package com.project.farming.integration;

import com.project.farming.domain.analysis.entity.PhotoAnalysis;
import com.project.farming.domain.chat.entity.Chat;
import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.notification.entity.Notification;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.userplant.entity.UserPlant;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.integrity.IdentifierReferenceIntegrityService;
import com.project.farming.global.jwtToken.RefreshToken;
import com.project.farming.global.jwtToken.JwtTokenFingerprint;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class IdentifierReferenceIntegrityIntegrationDiagnosticsTest {

    private static final List<String> EXPECTED_REFERENCE_NAMES = List.of(
            "users.profile_image_file_id",
            "plant_info.plant_image_file_id",
            "farm_info.farm_image_file_id",
            "user_plants.user_id",
            "user_plants.plant_id",
            "user_plants.farm_id",
            "user_plants.active_plant_id",
            "user_plants.active_farm_id",
            "user_plants.user_plant_image_file_id",
            "diaries.user_id",
            "diaries.dairy_image_file_id",
            "diary_user_plant.diary_id",
            "diary_user_plant.user_plant_id",
            "notification.user_id",
            "notification.notice_id",
            "fcm_outbox.notice_id",
            "fcm_outbox.source_id",
            "fcm_outbox.user_id",
            "chat.user_id",
            "photo_analysis.user_id",
            "photo_analysis.photo_image_file_id",
            "refresh_token.user_pk",
            "image_files.USER.domain_id",
            "image_files.PLANT.domain_id",
            "image_files.DIARY.domain_id",
            "image_files.FARM.domain_id",
            "image_files.USERPLANT.domain_id",
            "image_files.PHOTO.domain_id"
    );

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private IdentifierReferenceIntegrityService integrityService;

    @Test
    void identifierReferencesShouldResolveToExistingRowsInSeededGraph() {
        seedIdentifierReferenceGraph();
        flushAndClear();

        Map<String, Long> orphanCounts = integrityService.countOrphansByReference();

        System.out.printf("Identifier reference orphan inventory: %s%n", orphanCounts);

        assertThat(integrityService.referenceNames())
                .as("Every current identifier-reference field should be covered by the explicit integrity catalog.")
                .containsExactlyElementsOf(EXPECTED_REFERENCE_NAMES);
        assertThat(orphanCounts)
                .as("Every identifier-based reference in the current database should resolve to an existing row.")
                .containsOnlyKeys(EXPECTED_REFERENCE_NAMES)
                .allSatisfy((reference, orphanCount) -> assertThat(orphanCount)
                        .as(reference)
                        .isZero());
    }

    private void seedIdentifierReferenceGraph() {
        String suffix = "identifier-reference-" + System.nanoTime();
        ImageFile userImage = image("identifier-user-" + suffix, ImageDomainType.USER, 0L);
        ImageFile plantImage = image("identifier-plant-" + suffix, ImageDomainType.PLANT, 0L);
        ImageFile farmImage = image("identifier-farm-" + suffix, ImageDomainType.FARM, 0L);
        ImageFile userPlantImage = image("identifier-user-plant-" + suffix, ImageDomainType.USERPLANT, 0L);
        ImageFile diaryImage = image("identifier-diary-" + suffix, ImageDomainType.DIARY, 0L);
        ImageFile analysisImage = image("identifier-analysis-" + suffix, ImageDomainType.PHOTO, 0L);

        User user = User.builder()
                .email("identifier-reference-" + suffix + "@example.com")
                .password("encoded-password")
                .nickname("iref" + suffix.substring(Math.max(0, suffix.length() - 8)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .profileImageFileId(userImage.getImageFileId())
                .build();
        entityManager.persist(user);

        Plant plant = Plant.builder()
                .plantName("identifier-plant-" + suffix)
                .plantEnglishName("Identifier Plant")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(plantImage.getImageFileId())
                .build();
        entityManager.persist(plant);

        Farm farm = Farm.builder()
                .gardenUniqueId(Math.floorMod(suffix.hashCode(), 1_000_000_000))
                .operator("diagnostic")
                .farmName("identifier-farm-" + suffix)
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
                .plantNickname("identifier-plant")
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
                .title("identifier diagnostic diary")
                .content("reference integrity")
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

        Notification notification = Notification.builder()
                .userId(user.getUserId())
                .title("identifier reference")
                .message("diagnostic")
                .isRead(false)
                .build();
        entityManager.persist(notification);

        Chat chat = Chat.builder()
                .userId(user.getUserId())
                .pythonSessionId(System.nanoTime())
                .build();
        entityManager.persist(chat);

        PhotoAnalysis photoAnalysis = PhotoAnalysis.builder()
                .userId(user.getUserId())
                .photoImageFileId(analysisImage.getImageFileId())
                .analysisSummary("summary")
                .detectedDisease("none")
                .solution("none")
                .build();
        entityManager.persist(photoAnalysis);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getUserId())
                .tokenFingerprint(JwtTokenFingerprint.sha256("identifier-refresh-" + suffix))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        entityManager.persist(refreshToken);
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
}

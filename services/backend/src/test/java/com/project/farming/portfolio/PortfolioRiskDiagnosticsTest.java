package com.project.farming.portfolio;

import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.entity.DiaryUserPlant;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.userplant.entity.UserPlant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("portfolio-diagnostic")
class PortfolioRiskDiagnosticsTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");
    private static final Path TEST_SOURCE = Path.of("src/test/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path INFRA_BACKEND_CONFIG = Path.of("../../infra/config/backend");

    @Test
    void diaryReadModelShouldNotExposeNPlusOneProneLazyTraversal() throws IOException {
        String diaryService = readSource("com/project/farming/domain/diary/service/DiaryService.java");
        String diaryResponse = readSource("com/project/farming/domain/diary/dto/DiaryResponse.java");

        assertSoftly(softly -> {
            softly.assertThat(diaryService)
                    .as("Diary list responses should use a read model/projection/batch assembler, not DiaryResponse::new per row.")
                    .doesNotContain("map(DiaryResponse::new)");
            softly.assertThat(diaryResponse)
                    .as("DiaryResponse should not traverse lazy Diary associations; this is the current N+1 risk.")
                    .doesNotContain("getUser()")
                    .doesNotContain("getDiaryImageFile()")
                    .doesNotContain("getDiaryUserPlants()");
        });
    }

    @Test
    void otherListReadModelsShouldNotDereferenceLazyGraphOneRowAtATime() throws IOException {
        String plantService = readSource("com/project/farming/domain/plant/service/PlantService.java");
        String farmService = readSource("com/project/farming/domain/farm/service/FarmService.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");

        assertSoftly(softly -> {
            softly.assertThat(plantService)
                    .as("Plant list DTO mapping should not dereference lazy image one row at a time.")
                    .doesNotContain("getPlantImageFile().getImageUrl()");
            softly.assertThat(farmService)
                    .as("Farm list DTO mapping should not dereference lazy image one row at a time.")
                    .doesNotContain("getFarmImageFile().getImageUrl()");
            softly.assertThat(userPlantService)
                    .as("UserPlant list/detail mapping should not dereference lazy image/plant graph without an explicit fetch strategy.")
                    .doesNotContain("getUserPlantImageFile().getImageUrl()")
                    .doesNotContain("getPlant().getPlantName()")
                    .doesNotContain("getPlantImageFile().getImageUrl()");
        });
    }

    @Test
    void deleteMappingsShouldNotCascadeRemoveFromChildOrReferenceSide() throws NoSuchFieldException {
        CascadeType[] diaryUserPlantToDiaryCascade = manyToOneCascade(DiaryUserPlant.class, "diary");
        CascadeType[] diaryImageCascade = manyToOneCascade(Diary.class, "diaryImageFile");
        CascadeType[] userPlantCascade = oneToManyCascade(User.class, "userPlants");
        CascadeType[] userNotificationCascade = oneToManyCascade(User.class, "notifications");
        CascadeType[] userRefreshTokenCascade = oneToManyCascade(User.class, "refreshTokens");
        CascadeType[] userChatCascade = oneToManyCascade(User.class, "chats");

        assertSoftly(softly -> {
            softly.assertThat(diaryUserPlantToDiaryCascade)
                    .as("DiaryUserPlant is a link row; deleting it must not cascade-remove parent Diary.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
            softly.assertThat(diaryImageCascade)
                    .as("Diary should not cascade-remove ImageFile through a reference-side image mapping.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
            softly.assertThat(userPlantCascade)
                    .as("User deletion policy should be explicit; broad hard-delete cascade to UserPlant is a data-loss risk.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
            softly.assertThat(userNotificationCascade)
                    .as("User deletion policy should be explicit; broad hard-delete cascade to Notification should be reviewed.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
            softly.assertThat(userRefreshTokenCascade)
                    .as("RefreshToken cleanup can be hard-delete, but it should be handled explicitly by the auth service/repository.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
            softly.assertThat(userChatCascade)
                    .as("User deletion policy should be explicit; broad hard-delete cascade to Chat should be reviewed.")
                    .doesNotContain(CascadeType.REMOVE, CascadeType.ALL);
        });
    }

    @Test
    void diarySliceShouldUseIdentifierReferencesInsteadOfCrossAggregateRelationships() {
        List<String> relationshipFields = relationshipAnnotatedFields(Map.of(
                Diary.class, List.of("user", "diaryImageFile", "diaryUserPlants"),
                DiaryUserPlant.class, List.of("diary", "userPlant"),
                UserPlant.class, List.of("diaryUserPlants")
        ));

        assertThat(relationshipFields)
                .as("Diary read/write/delete policy should not depend on cross-aggregate JPA relationships.")
                .isEmpty();
    }

    @Test
    void userPlantShouldUseIdentifierReferencesInsteadOfCrossAggregateRelationships() throws IOException {
        List<String> relationshipFields = relationshipAnnotatedFields(Map.of(
                UserPlant.class, List.of("user", "plant", "farm", "userPlantImageFile")
        ));
        String userPlant = readSource("com/project/farming/domain/userplant/entity/UserPlant.java");
        String userPlantRepository = readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");

        assertSoftly(softly -> {
            softly.assertThat(relationshipFields)
                    .as("UserPlant should keep only FK identifier fields for cross-aggregate references.")
                    .isEmpty();
            softly.assertThat(userPlant)
                    .as("UserPlant should store referenced aggregate identities explicitly.")
                    .contains("private Long userId;")
                    .contains("private Long plantId;")
                    .contains("private Long farmId;")
                    .contains("private Long userPlantImageFileId;")
                    .doesNotContain("@ManyToOne")
                    .doesNotContain("@JoinColumn");
            softly.assertThat(userPlantRepository)
                    .as("UserPlant reads should join reference tables explicitly only when response fields require them.")
                    .contains("LEFT JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId")
                    .contains("JOIN Plant plant ON plant.plantId = up.plantId")
                    .doesNotContain("JOIN FETCH up.user")
                    .doesNotContain("JOIN FETCH up.plant")
                    .doesNotContain("JOIN FETCH up.farm");
            softly.assertThat(userPlantService)
                    .as("UserPlant writes should pass IDs to the aggregate instead of attaching managed reference entities.")
                    .contains(".userId(userId)")
                    .contains(".plantId(plant.getPlantId())")
                    .contains(".farmId(farm.getFarmId())")
                    .contains(".userPlantImageFileId(defaultImageFile.getImageFileId())")
                    .doesNotContain(".user(user)")
                    .doesNotContain(".plant(plant)")
                    .doesNotContain(".farm(farm)")
                    .doesNotContain(".userPlantImageFile(");
        });
    }

    @Test
    void mainJpaEntitiesShouldNotDeclareObjectGraphRelationships() throws IOException {
        String physicalFkInventoryDiagnostic = readTestSource(
                "com/project/farming/integration/PhysicalForeignKeyInventoryIntegrationDiagnosticsTest.java");
        List<String> relationshipAnnotatedSources;
        try (var sources = Files.walk(MAIN_SOURCE)) {
            relationshipAnnotatedSources = sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/entity/") || path.toString().contains("/jwtToken/RefreshToken.java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("@ManyToOne")
                                    || source.contains("@OneToMany")
                                    || source.contains("@OneToOne")
                                    || source.contains("@ManyToMany")
                                    || source.contains("@JoinColumn")
                                    || source.contains("@JoinTable");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(MAIN_SOURCE::relativize)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(relationshipAnnotatedSources)
                .as("Entity mappings should use identifier fields for cross-aggregate references unless a future owned-child exception is documented.")
                .isEmpty();
        assertThat(physicalFkInventoryDiagnostic)
                .as("Physical FK policy should be checked against the live Docker schema, not inferred from JPA source only.")
                .contains("information_schema.key_column_usage")
                .contains("referenced_table_name IS NOT NULL")
                .contains("Physical FK inventory");
    }

    @Test
    void identifierReferenceIntegrityShouldBeManagedExplicitly() throws IOException {
        String integrityService = readSource(
                "com/project/farming/global/integrity/IdentifierReferenceIntegrityService.java");
        String integrityReport = readSource(
                "com/project/farming/global/integrity/IdentifierReferenceIntegrityReport.java");
        String integrityHealth = readSource(
                "com/project/farming/global/integrity/IdentifierReferenceIntegrityHealthIndicator.java");
        String integrityMonitor = readSource(
                "com/project/farming/global/integrity/IdentifierReferenceIntegrityMonitor.java");
        String integrityDiagnostic = readTestSource(
                "com/project/farming/integration/IdentifierReferenceIntegrityIntegrationDiagnosticsTest.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(integrityService)
                    .as("When physical FKs are removed, every cross-domain ID reference needs an explicit orphan check.")
                    .contains("users.profile_image_file_id")
                    .contains("plant_info.plant_image_file_id")
                    .contains("farm_info.farm_image_file_id")
                    .contains("user_plants.user_id")
                    .contains("user_plants.plant_id")
                    .contains("user_plants.farm_id")
                    .contains("user_plants.active_plant_id")
                    .contains("user_plants.active_farm_id")
                    .contains("user_plants.user_plant_image_file_id")
                    .contains("diaries.user_id")
                    .contains("diaries.dairy_image_file_id")
                    .contains("diary_user_plant.diary_id")
                    .contains("diary_user_plant.user_plant_id")
                    .contains("notification.user_id")
                    .contains("chat.user_id")
                    .contains("photo_analysis.user_id")
                    .contains("photo_analysis.photo_image_file_id")
                    .contains("refresh_token.user_pk")
                    .contains("image_files.USER.domain_id")
                    .contains("image_files.PLANT.domain_id")
                    .contains("image_files.DIARY.domain_id")
                    .contains("image_files.FARM.domain_id")
                    .contains("image_files.USERPLANT.domain_id")
                    .contains("image_files.PHOTO.domain_id")
                    .contains("image.domain_id > 0")
                    .contains("IdentifierReferenceIntegrityReport inspect()");
            softly.assertThat(integrityReport)
                    .as("The orphan catalog should be summarized as an operational report.")
                    .contains("record IdentifierReferenceIntegrityReport")
                    .contains("checkedReferenceCount")
                    .contains("totalOrphanCount")
                    .contains("orphanedReferences")
                    .contains("isClean");
            softly.assertThat(integrityHealth)
                    .as("Identifier reference integrity should have an opt-in Actuator health surface.")
                    .contains("HealthIndicator")
                    .contains("ConditionalOnProperty")
                    .contains("app.integrity.identifier-reference.health")
                    .contains("Health.down()")
                    .contains("orphanCounts");
            softly.assertThat(integrityMonitor)
                    .as("Identifier reference integrity should have an opt-in scheduled monitor.")
                    .contains("@Scheduled")
                    .contains("app.integrity.identifier-reference.monitor")
                    .contains("inspectIdentifierReferences")
                    .contains("log.warn")
                    .doesNotContain("delete")
                    .doesNotContain("cleanup");
            softly.assertThat(integrityDiagnostic)
                    .as("The Docker-backed diagnostic should exercise the reusable integrity service, not duplicate SQL.")
                    .contains("IdentifierReferenceIntegrityService")
                    .contains("countOrphansByReference")
                    .contains("Identifier reference orphan inventory")
                    .doesNotContain("countOrphans(String sql");
            softly.assertThat(applicationProperties)
                    .as("Operational integrity scans should be opt-in and controlled by non-secret environment variables.")
                    .contains("APP_INTEGRITY_IDENTIFIER_REFERENCE_HEALTH_ENABLED:false")
                    .contains("APP_INTEGRITY_IDENTIFIER_REFERENCE_MONITOR_ENABLED:false")
                    .contains("APP_INTEGRITY_IDENTIFIER_REFERENCE_MONITOR_CRON");
        });
    }

    @Test
    void responseDtosShouldNotBeCoupledToJpaEntities() throws IOException {
        boolean diaryResponseAcceptsEntity = Arrays.stream(DiaryResponse.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .anyMatch(parameterTypes -> parameterTypes.length == 1 && parameterTypes[0].equals(Diary.class));
        String diaryController = readSource("com/project/farming/domain/diary/controller/DiaryController.java");

        assertSoftly(softly -> {
            softly.assertThat(diaryResponseAcceptsEntity)
                    .as("Response DTOs should be built from read models or explicit fields, not directly from JPA entities.")
                    .isFalse();
            softly.assertThat(diaryController)
                    .as("Controller should not wrap mutable JPA entities with new DiaryResponse(entity) after write operations.")
                    .doesNotContain("new DiaryResponse(diary)")
                    .doesNotContain("new DiaryResponse(updatedDiary)");
        });
    }

    @Test
    void diaryApiResponsesShouldUseCommonEnvelope() throws IOException {
        String diaryController = readSource("com/project/farming/domain/diary/controller/DiaryController.java");

        assertSoftly(softly -> {
            softly.assertThat(diaryController)
                    .as("Diary non-delete success responses should use the shared CommonResponse envelope.")
                    .contains("ResponseEntity<CommonResponse<DiaryResponse>>")
                    .contains("ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>>")
                    .contains("CommonResponse.success(\"일지 생성 성공\"")
                    .contains("CommonResponse.success(\"일지 조회 성공\"")
                    .contains("CommonResponse.success(\"일지 목록 조회 성공\"")
                    .contains("CommonResponse.success(\"일지 수정 성공\"")
                    .doesNotContain("ResponseEntity<DiaryResponse>")
                    .doesNotContain("ResponseEntity<List<DiaryResponse>>");
            softly.assertThat(diaryController)
                    .as("Diary missing-principal responses should return a typed CommonResponse error body.")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .doesNotContain("ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()");
            softly.assertThat(diaryController)
                    .as("Diary delete should preserve 204 No Content semantics after envelope migration.")
                    .contains("ResponseEntity<CommonResponse<Void>> deleteDiary")
                    .contains("ResponseEntity.noContent().build()");
        });
    }

    @Test
    void remainingPublicApiResponsesShouldUseCommonEnvelope() throws IOException {
        String userPlantController = readSource("com/project/farming/domain/userplant/controller/UserPlantController.java");
        String imageFileController = readSource("com/project/farming/global/image/controller/ImageFileController.java");
        String photoAnalysisController = readSource("com/project/farming/domain/analysis/controller/PhotoAnalysisController.java");
        String chatController = readSource("com/project/farming/domain/chat/controller/ChatController.java");
        String authController = readSource("com/project/farming/domain/user/controller/AuthController.java");

        assertSoftly(softly -> {
            softly.assertThat(userPlantController)
                    .as("UserPlant API should expose the same success/error envelope as the rest of the public API.")
                    .contains("ResponseEntity<CommonResponse<UserPlantDetailResponse>>")
                    .contains("ResponseEntity<CommonResponse<PageResponse<UserPlantListResponse>>>")
                    .contains("ResponseEntity<CommonResponse<Void>> deleteUserPlant")
                    .contains("\"사용자 식물 등록 성공\"")
                    .contains("\"사용자 식물 목록 조회 성공\"")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .doesNotContain("ResponseEntity<UserPlantDetailResponse>")
                    .doesNotContain("ResponseEntity<List<UserPlantListResponse>>")
                    .doesNotContain("ResponseEntity<Void> deleteUserPlant");
            softly.assertThat(imageFileController)
                    .as("Image upload/delete API should not mix raw upload DTO and empty unauthorized responses.")
                    .contains("ResponseEntity<CommonResponse<ImageUploadResponse>>")
                    .contains("ResponseEntity<CommonResponse<Void>> deleteImage")
                    .contains("CommonResponse.success(\"이미지 업로드 성공\"")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .doesNotContain("ResponseEntity<ImageUploadResponse>")
                    .doesNotContain("ResponseEntity<Void> deleteImage");
            softly.assertThat(photoAnalysisController)
                    .as("Photo analysis API should wrap analysis output in the common envelope.")
                    .contains("ResponseEntity<CommonResponse<PhotoAnalysisSidebarResponse>>")
                    .contains("CommonResponse.success(\"사진 분석 성공\"")
                    .doesNotContain("ResponseEntity<PhotoAnalysisSidebarResponse>");
            softly.assertThat(chatController)
                    .as("Chat API should consistently wrap chat responses and list responses.")
                    .contains("ResponseEntity<CommonResponse<ChatResponse>>")
                    .contains("ResponseEntity<CommonResponse<PageResponse<ChatMessageResponse>>>")
                    .contains("ResponseEntity<CommonResponse<PageResponse<ChatRoomResponse>>>")
                    .contains("ResponseEntity<CommonResponse<Void>> deleteChatRoom")
                    .contains("CommonResponse.success(\"챗봇 응답 성공\"")
                    .doesNotContain("ResponseEntity<ChatResponse>")
                    .doesNotContain("ResponseEntity<List<ChatMessageResponse>>")
                    .doesNotContain("ResponseEntity<List<ChatRoomResponse>>")
                    .doesNotContain("ResponseEntity<Void> deleteChatRoom");
            softly.assertThat(authController)
                    .as("Auth API should keep token and profile responses in CommonResponse.")
                    .contains("ResponseEntity<CommonResponse<JwtToken>> login")
                    .contains("ResponseEntity<CommonResponse<JwtToken>> refreshToken")
                    .contains("ResponseEntity<CommonResponse<UserMyPageResponse>> getCurrentUser")
                    .contains("ResponseEntity<CommonResponse<UserMyPageResponse>> updateNickname")
                    .contains("ResponseEntity<CommonResponse<UserMyPageResponse>> updateProfileImageByUpload")
                    .contains("ResponseEntity<CommonResponse<UserMyPageResponse>> deleteProfileImage")
                    .contains("ResponseEntity<CommonResponse<Void>> deleteMyPage")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .doesNotContain("ResponseEntity<JwtToken>")
                    .doesNotContain("ResponseEntity<UserMyPageResponse>")
                    .doesNotContain("ResponseEntity<Void> deleteMyPage");
        });
    }

    @Test
    void swaggerResponsesShouldInferSuccessPayloadsAndReuseOneErrorSchema() throws IOException {
        String userPlantController = readSource("com/project/farming/domain/userplant/controller/UserPlantController.java");
        String chatController = readSource("com/project/farming/domain/chat/controller/ChatController.java");
        String authController = readSource("com/project/farming/domain/user/controller/AuthController.java");
        String notificationController = readSource(
                "com/project/farming/domain/notification/controller/NotificationController.java");
        String fcmOutboxController = readSource(
                "com/project/farming/domain/notification/controller/FcmOutboxAdminController.java");
        String imageController = readSource("com/project/farming/global/image/controller/ImageFileController.java");
        String photoController = readSource(
                "com/project/farming/domain/analysis/controller/PhotoAnalysisController.java");
        String diaryController = readSource("com/project/farming/domain/diary/controller/DiaryController.java");
        String swaggerConfig = readSource("com/project/farming/global/config/SwaggerConfig.java");
        String generatedContractTest = readTestSource(
                "com/project/farming/global/config/OpenApiContractTest.java");

        assertSoftly(softly -> {
            List.of(
                            userPlantController,
                            chatController,
                            authController,
                            notificationController,
                            fcmOutboxController,
                            imageController,
                            photoController,
                            diaryController)
                    .forEach(controller -> softly.assertThat(controller)
                            .as("Raw CommonResponse schemas must not overwrite method generic payload types.")
                            .doesNotContain("implementation = CommonResponse.class"));
            softly.assertThat(swaggerConfig)
                    .contains("CommonErrorResponse")
                    .contains("OperationCustomizer")
                    .contains("\"204\".equals(statusCode)")
                    .contains("!statusCode.startsWith(\"2\")")
                    .doesNotContain("addSecurityItem");
            softly.assertThat(generatedContractTest)
                    .contains("PageResponse\", \"PlantResponse")
                    .contains("PageResponse\", \"ChatRoomResponse")
                    .contains("CommonErrorResponse")
                    .contains("/auth/login")
                    .contains("jwtAuth")
                    .contains("204");
        });
    }

    @Test
    void dtoBoundariesShouldNotLeakGlobalExternalOrEntityContracts() throws IOException {
        String globalExceptionHandler = readSource("com/project/farming/global/exception/GlobalExceptionHandler.java");
        String chatController = readSource("com/project/farming/domain/chat/controller/ChatController.java");
        String photoAnalysisController = readSource("com/project/farming/domain/analysis/controller/PhotoAnalysisController.java");

        assertSoftly(softly -> {
            softly.assertThat(globalExceptionHandler)
                    .as("Global exception responses should use a global response DTO, not a user-domain auth DTO.")
                    .contains("global.response.CommonResponse")
                    .doesNotContain("domain.user.dto.AuthResponseDto");
            softly.assertThat(chatController)
                    .as("Public chat API should not expose Python integration DTOs or access repositories directly.")
                    .doesNotContain("PythonChatPayload")
                    .doesNotContain("ChatRepository");
            softly.assertThat(photoAnalysisController)
                    .as("PhotoAnalysis controller should receive API DTOs, not map JPA entities directly.")
                    .doesNotContain("domain.analysis.entity.PhotoAnalysis")
                    .doesNotContain("PhotoAnalysis saved");
            softly.assertThat(MAIN_SOURCE.resolve("com/project/farming/domain/user/dto/AuthResponseDto.java"))
                    .as("The generic response envelope should no longer live under the user DTO package.")
                    .doesNotExist();
        });
    }

    @Test
    void dtoTypeNamesShouldUseRoleBasedSuffixes() throws IOException {
        List<String> legacyDtoNamedSources;
        try (var sources = Files.walk(MAIN_SOURCE)) {
            legacyDtoNamedSources = sources
                    .filter(Files::isRegularFile)
                    .map(MAIN_SOURCE::relativize)
                    .map(Path::toString)
                    .filter(path -> path.endsWith(".java"))
                    .filter(path -> path.contains("Dto") || path.contains("DTO"))
                    .toList();
        }

        String globalExceptionHandler = readSource("com/project/farming/global/exception/GlobalExceptionHandler.java");
        String chatService = readSource("com/project/farming/domain/chat/service/ChatService.java");
        String imageService = readSource("com/project/farming/global/image/service/ImageFileService.java");

        assertSoftly(softly -> {
            softly.assertThat(legacyDtoNamedSources)
                    .as("DTO class/file names should use role-based suffixes such as Request, Response, Command, Row, or Payload.")
                    .isEmpty();
            softly.assertThat(globalExceptionHandler)
                    .as("Global response envelope should use the role name CommonResponse.")
                    .contains("CommonResponse")
                    .doesNotContain("CommonResponseDto");
            softly.assertThat(chatService)
                    .as("External Python transport objects should be named as payloads, not generic DTOs.")
                    .contains("PythonChatPayload")
                    .contains("PythonSessionPayload")
                    .doesNotContain("PythonChatDto")
                    .doesNotContain("PythonSessionDto");
            softly.assertThat(imageService)
                    .as("Image API response should use a role-based response name.")
                    .contains("ImageUploadResponse uploadImageResponse")
                    .doesNotContain("ImageUploadResponseDto");
        });
    }

    @Test
    void userPlantPublicReadDtosShouldBeSeparatedByUseCase() throws IOException {
        String userPlantController = readSource("com/project/farming/domain/userplant/controller/UserPlantController.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");
        String userPlantRepository = readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java");

        assertSoftly(softly -> {
            softly.assertThat(MAIN_SOURCE.resolve("com/project/farming/domain/userplant/dto/UserPlantResponse.java"))
                    .as("The broad multipurpose UserPlantResponse should stay removed.")
                    .doesNotExist();
            softly.assertThat(userPlantController)
                    .as("List/search endpoints should return the narrow list DTO; detail/write endpoints should return the detail DTO.")
                    .contains("PageResponse<UserPlantListResponse>")
                    .contains("CommonResponse<UserPlantDetailResponse>")
                    .doesNotContain("UserPlantResponse");
            softly.assertThat(userPlantService)
                    .as("UserPlant list/search reads should use projection responses instead of entity-to-broad-DTO mapping.")
                    .contains("PageResponse<UserPlantListResponse>")
                    .contains("UserPlantDetailResponse")
                    .doesNotContain("toUserPlantResponseBuilder")
                    .doesNotContain("List<UserPlantResponse>");
            softly.assertThat(userPlantRepository)
                    .as("UserPlant public reads should expose explicit query DTO shapes.")
                    .contains("new com.project.farming.domain.userplant.dto.UserPlantListResponse")
                    .contains("new com.project.farming.domain.userplant.dto.UserPlantDetailResponse");
        });
    }

    @Test
    void plantAndFarmPublicReadsShouldUseProjectionResponses() throws IOException {
        String plantService = readSource("com/project/farming/domain/plant/service/PlantService.java");
        String plantRepository = readSource("com/project/farming/domain/plant/repository/PlantRepository.java");
        String farmService = readSource("com/project/farming/domain/farm/service/FarmService.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");

        assertSoftly(softly -> {
            softly.assertThat(plantService)
                    .as("Plant public reads should not assemble image URLs from entity graphs in the service.")
                    .doesNotContain("toPlantResponseBuilder")
                    .doesNotContain("getPlantImageFile()");
            softly.assertThat(plantRepository)
                    .as("Plant public reads should define constructor projections for response fields.")
                    .contains("new com.project.farming.domain.plant.dto.PlantResponse");
            softly.assertThat(farmService)
                    .as("Farm list/search/detail public reads should call response projection methods.")
                    .contains("findAllListResponsesByOrderByGardenUniqueIdAsc")
                    .contains("findListResponsesByKeywordOrderByGardenUniqueIdAsc")
                    .contains("findDetailResponseByFarmId");
            softly.assertThat(farmRepository)
                    .as("Farm normal public reads should define constructor projections for response fields.")
                    .contains("new com.project.farming.domain.farm.dto.FarmResponse");
        });
    }

    @Test
    void adminReadDtosShouldUseProjectionRowsInsteadOfEntityGraphMapping() throws IOException {
        String plantAdminService = readSource("com/project/farming/domain/plant/service/PlantAdminService.java");
        String plantRepository = readSource("com/project/farming/domain/plant/repository/PlantRepository.java");
        String farmAdminService = readSource("com/project/farming/domain/farm/service/FarmAdminService.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");
        String userAdminService = readSource("com/project/farming/domain/user/service/UserAdminService.java");
        String userRepository = readSource("com/project/farming/domain/user/repository/UserRepository.java");
        String userAdminResponse = readSource("com/project/farming/domain/user/dto/UserAdminResponse.java");

        assertSoftly(softly -> {
            softly.assertThat(plantAdminService)
                    .as("Plant admin reads should use response projections instead of entity-to-DTO builders.")
                    .contains("findAllAdminResponsesByOrderByPlantIdAsc")
                    .contains("findAdminResponsesByKeywordOrderByPlantIdAsc")
                    .doesNotContain("toPlantResponseBuilder")
                    .doesNotContain("getPlantImageFile().getImageUrl()")
                    .doesNotContain("findAllByOrderByPlantIdAsc")
                    .doesNotContain("findByPlantNameContainingOrderByPlantIdAsc");
            softly.assertThat(plantRepository)
                    .as("Plant admin read repository methods should project directly to PlantResponse.")
                    .contains("findAllAdminResponsesByOrderByPlantIdAsc")
                    .contains("findAdminResponsesByKeywordOrderByPlantIdAsc")
                    .contains("new com.project.farming.domain.plant.dto.PlantResponse");

            softly.assertThat(farmAdminService)
                    .as("Farm admin search should not map Farm entities to DTOs one row at a time.")
                    .contains("findAdminListResponsesByFarmNameOrderByGardenUniqueIdAsc")
                    .contains("findAdminListResponsesByAddressOrderByGardenUniqueIdAsc")
                    .doesNotContain("FarmResponse.builder()")
                    .doesNotContain("getFarmImageFile().getImageUrl()")
                    .doesNotContain("findByFarmNameContainingOrderByGardenUniqueIdAsc")
                    .doesNotContain("findByAddressContainingOrderByGardenUniqueIdAsc");
            softly.assertThat(farmRepository)
                    .as("Farm admin search repository methods should project directly to FarmResponse.")
                    .contains("findAdminListResponsesByFarmNameOrderByGardenUniqueIdAsc")
                    .contains("findAdminListResponsesByAddressOrderByGardenUniqueIdAsc")
                    .contains("new com.project.farming.domain.farm.dto.FarmResponse");

            softly.assertThat(userAdminService)
                    .as("User admin reads should map repository rows, not User entities with lazy profile images.")
                    .contains("UserAdminResponseRow")
                    .contains("toUserAdminResponse(UserAdminResponseRow user)")
                    .doesNotContain("toUserAdminResponseBuilder")
                    .doesNotContain("getProfileImageFile().getImageUrl()")
                    .doesNotContain("findAllByOrderByUserIdAsc")
                    .doesNotContain("findByNicknameContainingOrderByNicknameAsc")
                    .doesNotContain("findByEmailContainingOrderByEmailAsc");
            softly.assertThat(userRepository)
                    .as("User admin reads should expose row projections with image URL aliases.")
                    .contains("UserAdminResponseRow")
                    .contains("image.imageUrl AS profileImageUrl");
            softly.assertThat(userAdminResponse)
                    .as("User admin response should keep role as a public string field.")
                    .doesNotContain("domain.user.entity.UserRole")
                    .contains("private String role;");
        });
    }

    @Test
    void searchQueriesShouldNormalizeKeywordAndEscapeLikeWildcards() throws IOException {
        String searchKeywordPattern = readSource("com/project/farming/global/search/SearchKeywordPattern.java");
        List<String> searchServices = List.of(
                readSource("com/project/farming/domain/plant/service/PlantService.java"),
                readSource("com/project/farming/domain/plant/service/PlantAdminService.java"),
                readSource("com/project/farming/domain/farm/service/FarmService.java"),
                readSource("com/project/farming/domain/farm/service/FarmAdminService.java"),
                readSource("com/project/farming/domain/user/service/UserAdminService.java"),
                readSource("com/project/farming/domain/userplant/service/UserPlantService.java"),
                readSource("com/project/farming/domain/notification/service/NoticeService.java")
        );
        List<String> searchRepositories = List.of(
                readSource("com/project/farming/domain/plant/repository/PlantRepository.java"),
                readSource("com/project/farming/domain/farm/repository/FarmRepository.java"),
                readSource("com/project/farming/domain/user/repository/UserRepository.java"),
                readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java"),
                readSource("com/project/farming/domain/notification/repository/NoticeRepository.java")
        );

        assertSoftly(softly -> {
            softly.assertThat(searchKeywordPattern)
                    .as("Search keyword handling should reject blank terms and escape SQL LIKE wildcard characters.")
                    .contains("검색어는 비어 있을 수 없습니다.")
                    .contains("current == '%'")
                    .contains("current == '_'")
                    .contains("LIKE_ESCAPE_CHARACTER");
            searchServices.forEach(service -> softly.assertThat(service)
                    .as("Search services should delegate LIKE pattern creation to SearchKeywordPattern.")
                    .contains("SearchKeywordPattern.")
                    .doesNotContain("\"%\" + keyword")
                    .doesNotContain("\"%\"+keyword")
                    .doesNotContain("keyword + \"%\""));
            searchRepositories.forEach(repository -> softly.assertThat(repository)
                    .as("Explicit LIKE queries should use the same escape character as SearchKeywordPattern.")
                    .contains("ESCAPE '!'")
                    .doesNotContain("LIKE :keyword\n")
                    .doesNotContain("LIKE :keyword OR"));
        });
    }

    @Test
    void writeDtosShouldBeConvertedToCommandsBeforeServiceBoundary() throws IOException {
        String userPlantController = readSource("com/project/farming/domain/userplant/controller/UserPlantController.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");
        String userPlantCommand = readSource("com/project/farming/domain/userplant/command/UserPlantCommand.java");

        assertSoftly(softly -> {
            softly.assertThat(userPlantController)
                    .as("Controller should convert HTTP request DTOs into service commands.")
                    .contains("UserPlantCommand")
                    .contains("toCommand(UserPlantRequest request)");
            softly.assertThat(userPlantService)
                    .as("Service boundary should not depend on web/request DTO classes.")
                    .contains("UserPlantCommand")
                    .doesNotContain("UserPlantRequest")
                    .doesNotContain("domain.userplant.dto.UserPlantRequest");
            softly.assertThat(userPlantCommand)
                    .as("Command should carry required domain inputs with primitive booleans/intervals after controller validation.")
                    .contains("record UserPlantCommand")
                    .contains("boolean notificationEnabled")
                    .contains("int waterIntervalDays");
        });
    }

    @Test
    void adminAndNotificationWriteDtosShouldBeConvertedToCommandsBeforeServiceBoundary() throws IOException {
        String plantAdminController = readSource("com/project/farming/domain/plant/controller/PlantAdminController.java");
        String plantAdminService = readSource("com/project/farming/domain/plant/service/PlantAdminService.java");
        String farmAdminController = readSource("com/project/farming/domain/farm/controller/FarmAdminController.java");
        String farmAdminService = readSource("com/project/farming/domain/farm/service/FarmAdminService.java");
        String userAdminController = readSource("com/project/farming/domain/user/controller/UserAdminController.java");
        String userAdminService = readSource("com/project/farming/domain/user/service/UserAdminService.java");
        String noticeController = readSource("com/project/farming/domain/notification/controller/NoticeController.java");
        String noticeService = readSource("com/project/farming/domain/notification/service/NoticeService.java");
        String notificationController = readSource("com/project/farming/domain/notification/controller/NotificationController.java");
        String notificationService = readSource("com/project/farming/domain/notification/service/NotificationService.java");
        String notificationCommand = readSource("com/project/farming/domain/notification/command/NotificationCommand.java");

        assertSoftly(softly -> {
            softly.assertThat(plantAdminController)
                    .as("Plant admin controller should convert validated form requests into service commands.")
                    .contains("PlantAdminCommand")
                    .contains("toCommand(PlantAdminRequest request)")
                    .doesNotContain("catch (Exception");
            softly.assertThat(plantAdminService)
                    .as("Plant admin service should not depend on web request DTOs.")
                    .contains("PlantAdminCommand")
                    .doesNotContain("PlantAdminRequest");

            softly.assertThat(farmAdminController)
                    .as("Farm admin controller should convert validated form requests into service commands.")
                    .contains("FarmAdminCommand")
                    .contains("toCommand(FarmAdminRequest request)")
                    .doesNotContain("catch (Exception");
            softly.assertThat(farmAdminService)
                    .as("Farm admin service should not depend on web request DTOs.")
                    .contains("FarmAdminCommand")
                    .doesNotContain("FarmAdminRequest");

            softly.assertThat(userAdminController)
                    .as("User admin controller should convert validated form requests into service commands.")
                    .contains("UserAdminCommand")
                    .contains("toCommand(UserAdminRequest request)")
                    .doesNotContain("catch (Exception");
            softly.assertThat(userAdminService)
                    .as("User admin service should not depend on web request DTOs.")
                    .contains("UserAdminCommand")
                    .doesNotContain("UserAdminRequest");

            softly.assertThat(noticeController)
                    .as("Notice controller should convert validated form requests into service commands.")
                    .contains("NoticeCommand")
                    .contains("toCommand(NoticeRequest request)")
                    .doesNotContain("catch (Exception");
            softly.assertThat(noticeService)
                    .as("Notice service should not depend on web request DTOs.")
                    .contains("NoticeCommand")
                    .doesNotContain("NoticeRequest");

            softly.assertThat(notificationController)
                    .as("Notification controller should convert JSON requests into service commands.")
                    .contains("NotificationCommand")
                    .contains("toCommand(NotificationRequest request)")
                    .contains("ResponseEntity<CommonResponse<Void>> createNotification")
                    .contains("HttpStatus.ACCEPTED")
                    .contains("FCM 발송 요청이 접수되었습니다")
                    .doesNotContain("ResponseEntity<Void> createNotification")
                    .doesNotContain("return ResponseEntity.ok().build();");
            softly.assertThat(notificationService)
                    .as("Notification service should not depend on web request DTOs.")
                    .contains("NotificationCommand")
                    .doesNotContain("NotificationRequest")
                    .doesNotContain("createAndSendNotificationFromRequest");
            softly.assertThat(notificationCommand)
                    .as("Notification command should defensively copy user IDs crossing the service boundary.")
                    .contains("record NotificationCommand")
                    .contains("List.copyOf(userIds)");
        });
    }

    @Test
    void farmNearbyReadShouldUseNativeProjectionWithBoundingBox() throws IOException {
        String farmEntity = readSource("com/project/farming/domain/farm/entity/Farm.java");
        String farmService = readSource("com/project/farming/domain/farm/service/FarmService.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");
        String farmNearbyRow = readSource("com/project/farming/domain/farm/repository/FarmNearbyResponseRow.java");
        String farmQueryPlanDiagnostic = readTestSource(
                "com/project/farming/integration/FarmNearbyQueryPlanIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(farmEntity)
                    .as("Farm nearby bounding-box query should have a latitude/longitude index declared in the schema model.")
                    .contains("idx_farm_location")
                    .contains("latitude, longitude");
            softly.assertThat(farmService)
                    .as("Farm nearby service should use bounding-box projection reads, not entity plus image batch mapping.")
                    .contains("boundingBox(latitude, longitude, radius)")
                    .contains("findNearbyResponseRows")
                    .doesNotContain("findFarmsWithImages")
                    .doesNotContain("toFarmResponseBuilder")
                    .doesNotContain("findFarmsWithinRadius");
            softly.assertThat(farmRepository)
                    .as("Farm nearby query should prefilter by latitude/longitude and join image URL in the same native query.")
                    .contains("f.deleted = false")
                    .contains("f.latitude BETWEEN :minLatitude AND :maxLatitude")
                    .contains("f.longitude BETWEEN :minLongitude AND :maxLongitude")
                    .contains("JOIN image_files image")
                    .contains("ST_Distance_Sphere")
                    .doesNotContain("findByFarmIdInWithImage")
                    .doesNotContain("findFarmsWithinRadius");
            softly.assertThat(farmNearbyRow)
                    .as("Native projection row should expose the same response fields FarmResponse needs.")
                    .contains("interface FarmNearbyResponseRow")
                    .contains("String getFarmImageUrl()");
            softly.assertThat(farmQueryPlanDiagnostic)
                    .as("Farm nearby index work should keep a large-seed EXPLAIN diagnostic.")
                    .contains("FAR_FARM_COUNT = 50_000")
                    .contains("IGNORE INDEX (idx_farm_location)")
                    .contains("FORCE INDEX (idx_farm_location)")
                    .contains("boundingBoxCandidateRows")
                    .contains("LatencyStats")
                    .contains("p95Millis");
        });
    }

    @Test
    void catalogReferenceDeletionShouldUseSoftDeleteAndSharedExclusiveLocks() throws IOException {
        String plantEntity = readSource("com/project/farming/domain/plant/entity/Plant.java");
        String farmEntity = readSource("com/project/farming/domain/farm/entity/Farm.java");
        String userPlantEntity = readSource("com/project/farming/domain/userplant/entity/UserPlant.java");
        String plantRepository = readSource("com/project/farming/domain/plant/repository/PlantRepository.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");
        String plantAdminService = readSource("com/project/farming/domain/plant/service/PlantAdminService.java");
        String farmAdminService = readSource("com/project/farming/domain/farm/service/FarmAdminService.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");
        String integrityService = readSource(
                "com/project/farming/global/integrity/IdentifierReferenceIntegrityService.java");
        String globalExceptionHandler = readSource(
                "com/project/farming/global/exception/GlobalExceptionHandler.java");
        String concurrencyDiagnostic = readTestSource(
                "com/project/farming/integration/CatalogReferenceConcurrencyIntegrationDiagnosticsTest.java");
        String deleteDiagnostic = readTestSource(
                "com/project/farming/integration/CascadeDeleteBehaviorIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(plantEntity)
                    .contains("@SQLDelete")
                    .contains("UPDATE plant_info")
                    .contains("@SQLRestriction(\"deleted = false\")")
                    .contains("private boolean deleted = false")
                    .contains("private LocalDateTime deletedAt");
            softly.assertThat(farmEntity)
                    .contains("@SQLDelete")
                    .contains("UPDATE farm_info")
                    .contains("@SQLRestriction(\"deleted = false\")")
                    .contains("private boolean deleted = false")
                    .contains("private LocalDateTime deletedAt");
            softly.assertThat(userPlantEntity)
                    .contains("idx_userplant_plant_active")
                    .contains("plant_id, deleted")
                    .contains("idx_userplant_farm_active")
                    .contains("farm_id, deleted");
            softly.assertThat(plantRepository)
                    .contains("LockModeType.PESSIMISTIC_READ")
                    .contains("findReferenceCandidatesForShare")
                    .contains("findOtherPlantForShare")
                    .contains("LockModeType.PESSIMISTIC_WRITE")
                    .contains("findByPlantIdForUpdate");
            softly.assertThat(farmRepository)
                    .contains("LockModeType.PESSIMISTIC_READ")
                    .contains("findReferenceByGardenUniqueIdForShare")
                    .contains("findOtherFarmCandidatesForShare")
                    .contains("LockModeType.PESSIMISTIC_WRITE")
                    .contains("findByFarmIdForUpdate")
                    .contains("f.deleted = false");
            softly.assertThat(userPlantService)
                    .contains("findReferenceCandidatesForShare")
                    .contains("findReferenceByGardenUniqueIdForShare")
                    .contains("findOtherPlantForShare")
                    .contains("findOtherFarmCandidatesForShare");
            softly.assertThat(plantAdminService.substring(
                            plantAdminService.indexOf("public void deletePlant"),
                            plantAdminService.indexOf("private ImageFile getDefaultImageFile")))
                    .contains("findPlantByIdForUpdate")
                    .contains("reassignPlant")
                    .doesNotContain("imageFileService.deleteImage");
            softly.assertThat(plantAdminService)
                    .contains("OTHER_PLANT_NAME")
                    .contains("기본 식물의 이름은 변경할 수 없습니다.");
            softly.assertThat(farmAdminService.substring(
                            farmAdminService.indexOf("public void deleteFarm"),
                            farmAdminService.indexOf("private ImageFile getDefaultImageFile")))
                    .contains("findFarmByIdForUpdate")
                    .contains("reassignFarm")
                    .doesNotContain("imageFileService.deleteImage");
            softly.assertThat(farmAdminService)
                    .contains("OTHER_FARM_NAME")
                    .contains("기본 텃밭의 이름은 변경할 수 없습니다.");
            softly.assertThat(integrityService)
                    .contains("user_plants.active_plant_id")
                    .contains("plant.deleted = true")
                    .contains("user_plants.active_farm_id")
                    .contains("farm.deleted = true");
            softly.assertThat(globalExceptionHandler)
                    .contains("PessimisticLockingFailureException")
                    .contains("PESSIMISTIC_LOCK_CONFLICT")
                    .contains("HttpStatus.CONFLICT");
            softly.assertThat(concurrencyDiagnostic)
                    .contains("createFirstShouldMakePlantDeleteWaitAndReassignCommittedUserPlant")
                    .contains("deleteFirstShouldMakeFarmReferenceWaitAndResolveToOtherFarm")
                    .contains("userPlantReassignIndexesShouldMatchCatalogReferencePredicates")
                    .contains("deleteBlockedBeforeCreateCommit")
                    .contains("referenceBlockedBeforeDeleteCommit")
                    .contains("activeDeletedParentReferences")
                    .contains("idx_userplant_plant_active")
                    .contains("idx_userplant_farm_active");
            softly.assertThat(deleteDiagnostic)
                    .contains("deletingPlantShouldSoftDeleteReassignUserPlantAndKeepImage")
                    .contains("deletingFarmShouldSoftDeleteReassignUserPlantAndKeepImage")
                    .contains("reservedOtherCatalogNamesShouldNotBeMutable");
        });
    }

    @Test
    void keywordSearchShouldUsePrefixPatternsAndMatchingIndexesForHotPaths() throws IOException {
        String searchKeywordPattern = readSource("com/project/farming/global/search/SearchKeywordPattern.java");
        String plantEntity = readSource("com/project/farming/domain/plant/entity/Plant.java");
        String farmEntity = readSource("com/project/farming/domain/farm/entity/Farm.java");
        String userEntity = readSource("com/project/farming/domain/user/entity/User.java");
        String noticeEntity = readSource("com/project/farming/domain/notification/entity/Notice.java");
        String userPlantEntity = readSource("com/project/farming/domain/userplant/entity/UserPlant.java");
        String plantService = readSource("com/project/farming/domain/plant/service/PlantService.java");
        String plantAdminService = readSource("com/project/farming/domain/plant/service/PlantAdminService.java");
        String farmService = readSource("com/project/farming/domain/farm/service/FarmService.java");
        String farmAdminService = readSource("com/project/farming/domain/farm/service/FarmAdminService.java");
        String userPlantService = readSource("com/project/farming/domain/userplant/service/UserPlantService.java");
        String userAdminService = readSource("com/project/farming/domain/user/service/UserAdminService.java");
        String noticeService = readSource("com/project/farming/domain/notification/service/NoticeService.java");
        String noticeContentIndexDiagnostic = readTestSource(
                "com/project/farming/integration/NoticeContentSearchIndexIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(searchKeywordPattern)
                    .as("Search pattern helper should separate index-friendly prefix search from explicit contains search.")
                    .contains("public static String prefix(String keyword)")
                    .contains("return escapeLike(normalizedKeyword) + \"%\";")
                    .contains("return \"%\" + escapeLike(normalizedKeyword) + \"%\";");
            softly.assertThat(plantService)
                    .as("Public plant catalog search should use prefix LIKE so plant name indexes are usable.")
                    .contains("SearchKeywordPattern.prefix(keyword)")
                    .doesNotContain("SearchKeywordPattern.contains(keyword)");
            softly.assertThat(plantAdminService)
                    .as("Plant admin search should use the same prefix search policy.")
                    .contains("SearchKeywordPattern.prefix(keyword)");
            softly.assertThat(farmService)
                    .as("Public farm search should use prefix LIKE for name/address entry.")
                    .contains("SearchKeywordPattern.prefix(keyword)");
            softly.assertThat(farmAdminService)
                    .as("Farm admin name/address search should use prefix LIKE.")
                    .contains("SearchKeywordPattern.prefix(keyword)");
            softly.assertThat(userPlantService)
                    .as("UserPlant search should use user-scoped prefix indexes.")
                    .contains("SearchKeywordPattern.prefix(keyword)");
            softly.assertThat(userAdminService)
                    .as("Admin user name/email search should use prefix search.")
                    .contains("SearchKeywordPattern.prefix(keyword)");
            softly.assertThat(noticeService)
                    .as("Notice title and content admin search should use prefix search so matching B-Tree indexes are usable.")
                    .contains("case \"title\" -> noticeRepository.findResponsePageByTitleKeywordOrderByNoticeIdAsc(\n                    SearchKeywordPattern.prefix(keyword), pageable)")
                    .contains("case \"content\" -> noticeRepository.findResponsePageByContentPrefixOrderByNoticeIdAsc(\n                    SearchKeywordPattern.prefix(keyword), pageable)")
                    .doesNotContain("SearchKeywordPattern.contains(keyword)");
            softly.assertThat(plantEntity)
                    .as("Plant English-name search should have an index; plantName already has a unique index.")
                    .contains("idx_plant_english_name")
                    .contains("plant_english_name");
            softly.assertThat(farmEntity)
                    .as("Farm prefix search columns should have matching B-Tree indexes.")
                    .contains("idx_farm_name")
                    .contains("farm_name")
                    .contains("idx_farm_road_name_address")
                    .contains("road_name_address")
                    .contains("idx_farm_lot_number_address")
                    .contains("lot_number_address");
            softly.assertThat(userEntity)
                    .as("Admin nickname search should have an explicit index; email already has a unique index.")
                    .contains("idx_user_nickname")
                    .contains("nickname")
                    .contains("@Column(unique = true, nullable = false)");
            softly.assertThat(noticeEntity)
                    .as("Notice title/content prefix search should have matching indexes.")
                    .contains("idx_notice_title")
                    .contains("title")
                    .contains("idx_notice_content")
                    .contains("content");
            softly.assertThat(userPlantEntity)
                    .as("UserPlant keyword search should have user-scoped composite indexes for both searched fields.")
                    .contains("idx_userplant_user_nickname")
                    .contains("user_id, deleted, plant_nickname")
                    .contains("idx_userplant_user_plant_name")
                    .contains("user_id, deleted, plant_name");
            softly.assertThat(noticeContentIndexDiagnostic)
                    .as("Notice content prefix search should keep a Docker-backed EXPLAIN/latency diagnostic.")
                    .contains("NOISE_NOTICE_COUNT = 30_000")
                    .contains("idx_notice_content")
                    .contains("IGNORE INDEX (idx_notice_content)")
                    .contains("FORCE INDEX (idx_notice_content)")
                    .contains("p95Millis")
                    .contains("isLessThan(noContentIndexLatency.p95Nanos())");
        });
    }

    @Test
    void noticeAdminReadsShouldUsePagedProjectionInsteadOfUnboundedEntityList() throws IOException {
        String noticeController = readSource("com/project/farming/domain/notification/controller/NoticeController.java");
        String noticeService = readSource("com/project/farming/domain/notification/service/NoticeService.java");
        String noticeRepository = readSource("com/project/farming/domain/notification/repository/NoticeRepository.java");
        String noticeResponse = readSource("com/project/farming/domain/notification/dto/NoticeResponse.java");
        String noticeListTemplate = Files.readString(Path.of("src/main/resources/templates/notice/notice-list.html"));

        assertSoftly(softly -> {
            softly.assertThat(noticeController)
                    .as("Notice admin list/search should accept Pageable and expose a Page model for bounded reads.")
                    .contains("@PageableDefault(size = 20")
                    .contains("Page<NoticeResponse> noticePage")
                    .contains("noticePage.getContent()")
                    .contains("isSearch");
            softly.assertThat(noticeService)
                    .as("Notice admin list/search should not load every Notice entity and map in memory.")
                    .contains("public Page<NoticeResponse> findAllNotices(Pageable pageable)")
                    .contains("public Page<NoticeResponse> findNoticesByKeyword(String searchType, String keyword, Pageable pageable)")
                    .contains("noticeRepository.findResponsePageByOrderByNoticeIdAsc(pageable)")
                    .doesNotContain("findAllByOrderByNoticeIdAsc()")
                    .doesNotContain(".stream()\n                .map(notice -> toNoticeResponseBuilder(notice).build())");
            softly.assertThat(noticeRepository)
                    .as("Notice repository should use JPQL constructor projections plus count queries for paged admin reads.")
                    .contains("Page<NoticeResponse> findResponsePageByOrderByNoticeIdAsc(Pageable pageable)")
                    .contains("SELECT new com.project.farming.domain.notification.dto.NoticeResponse")
                    .contains("countQuery")
                    .contains("Page<NoticeResponse> findResponsePageByTitleKeywordOrderByNoticeIdAsc")
                    .contains("Page<NoticeResponse> findResponsePageByContentPrefixOrderByNoticeIdAsc")
                    .doesNotContain("findAllByOrderByNoticeIdAsc");
            softly.assertThat(noticeResponse)
                    .as("JPQL constructor projection needs a matching DTO constructor.")
                    .contains("@AllArgsConstructor");
            softly.assertThat(noticeListTemplate)
                    .as("The admin template should keep pagination/search state instead of rendering an unbounded list only.")
                    .contains("noticePage.totalElements")
                    .contains("noticePage.totalPages")
                    .contains("@{/admin/notices(page=${pageNumber}, size=${noticePage.size})}")
                    .contains("@{/admin/notices/search(searchType=${searchType}, keyword=${keyword}, page=${pageNumber}, size=${noticePage.size})}");
        });
    }

    @Test
    void imageFileDomainLookupShouldUsePredicateFirstIndex() throws IOException {
        String imageFileEntity = readSource("com/project/farming/global/image/entity/ImageFile.java");
        String imageFileRepository = readSource("com/project/farming/global/image/repository/ImageFileRepository.java");
        String imageIndexDiagnostic = readTestSource(
                "com/project/farming/integration/ImageFileDomainLookupIndexIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(imageFileRepository)
                    .as("ImageFile repository domain lookup is driven by domainType/domainId predicates.")
                    .contains("findByDomainTypeAndDomainId")
                    .contains("findByS3Key");
            softly.assertThat(imageFileEntity)
                    .as("ImageFile should declare a predicate-first index for domain image lookup.")
                    .contains("idx_image_domain_lookup")
                    .contains("domain_type, domain_id, image_file_id")
                    .doesNotContain("idx_covering_image_file")
                    .doesNotContain("image_file_id, image_url, domain_type, domain_id");
            softly.assertThat(imageIndexDiagnostic)
                    .as("ImageFile domain lookup index work should have a Docker-backed benchmark diagnostic.")
                    .contains("ImageFileDomainLookupIndexIntegrationDiagnosticsTest")
                    .contains("idx_image_domain_lookup")
                    .contains("imageNoIndexHint")
                    .contains("idx_covering_image_file")
                    .contains("FORCE INDEX (idx_image_domain_lookup)")
                    .contains("p95Millis");
            softly.assertThat(imageIndexDiagnostic)
                    .as("Public image deletion should have a real-MySQL ownership predicate diagnostic.")
                    .contains("publicDeleteOwnershipLookupShouldMatchOnlyOwnedUserImage")
                    .contains("findByImageFileIdAndDomainTypeAndDomainId")
                    .contains("assertThat(ownedMatches).isEqualTo(1)")
                    .contains("assertThat(wrongOwnerMatches).isZero()")
                    .contains("assertThat(nonUserDomainMatches).isZero()");
        });
    }

    @Test
    void diaryAndNotificationQueriesShouldUseCompositeIndexes() throws IOException {
        String diaryEntity = readSource("com/project/farming/domain/diary/entity/Diary.java");
        String diaryUserPlantEntity = readSource("com/project/farming/domain/diary/entity/DiaryUserPlant.java");
        String notificationEntity = readSource("com/project/farming/domain/notification/entity/Notification.java");
        String diaryRepository = readSource("com/project/farming/domain/diary/repository/DiaryRepository.java");
        String notificationRepository = readSource("com/project/farming/domain/notification/repository/NotificationRepository.java");
        String queryIndexDiagnostic = readTestSource(
                "com/project/farming/integration/DiaryNotificationQueryIndexIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(diaryRepository)
                    .as("Diary hot reads filter by user and then sort/filter by createdAt or diaryDate.")
                    .contains("findByUserIdOrderByCreatedAtDesc")
                    .contains("findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc");
            softly.assertThat(diaryEntity)
                    .as("Diary should declare composite indexes matching latest-list and calendar-range queries.")
                    .contains("idx_diary_user_created")
                    .contains("user_id, created_at")
                    .contains("idx_diary_user_date")
                    .contains("user_id, diary_date")
                    .doesNotContain("idx_user_diary");
            softly.assertThat(diaryUserPlantEntity)
                    .as("Diary tag lookup should be able to read diary IDs from the userPlant-first index.")
                    .contains("idx_diary_user_plant_user_plant_diary")
                    .contains("user_plant_id, diary_id");

            softly.assertThat(notificationRepository)
                    .as("Notification hot reads filter by user and then sort by createdAt or count unread rows.")
                    .contains("findResponsePageByUserIdOrderByCreatedAtDesc")
                    .contains("countByUserIdAndIsReadFalse");
            softly.assertThat(notificationEntity)
                    .as("Notification should declare composite indexes matching latest-list and unread-count queries.")
                    .contains("idx_notification_user_created")
                    .contains("user_id, created_at")
                    .contains("idx_notification_user_read")
                    .contains("user_id, is_read")
                    .doesNotContain("idx_user_notification");

            softly.assertThat(queryIndexDiagnostic)
                    .as("Diary/Notification index work should include Docker-backed before/after measurements.")
                    .contains("DiaryNotificationQueryIndexIntegrationDiagnosticsTest")
                    .contains("IGNORE INDEX")
                    .contains("FORCE INDEX (idx_diary_user_created)")
                    .contains("FORCE INDEX (idx_diary_user_date)")
                    .contains("FORCE INDEX (idx_notification_user_created)")
                    .contains("FORCE INDEX (idx_notification_user_read)")
                    .contains("p95Millis")
                    .contains("p95Improvement");
        });
    }

    @Test
    void userPlantSchedulerQueriesShouldUseDueDateIndexesInsteadOfDatediff() throws IOException {
        String userPlantEntity = readSource("com/project/farming/domain/userplant/entity/UserPlant.java");
        String userPlantRepository = readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java");
        String userPlantCareScheduler = readSource("com/project/farming/domain/userplant/service/UserPlantCareScheduler.java");
        String diaryService = readSource("com/project/farming/domain/diary/service/DiaryService.java");
        String consistencyDiagnostic = readTestSource(
                "com/project/farming/integration/DiaryConsistencyIntegrationDiagnosticsTest.java");
        String userPlantDueDateDiagnostic = readTestSource(
                "com/project/farming/integration/UserPlantSchedulerDueDateIndexIntegrationDiagnosticsTest.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(userPlantEntity)
                    .as("UserPlant should materialize care due dates so scheduler queries can use range indexes.")
                    .contains("private LocalDate nextWateringDate")
                    .contains("private LocalDate nextPruningDate")
                    .contains("private LocalDate nextFertilizingDate")
                    .contains("idx_userplant_due_watering")
                    .contains("is_notification_enabled, deleted, next_watering_date, watered")
                    .contains("idx_userplant_due_pruning")
                    .contains("is_notification_enabled, deleted, next_pruning_date, pruned")
                    .contains("idx_userplant_due_fertilizing")
                    .contains("is_notification_enabled, deleted, next_fertilizing_date, fertilized")
                    .contains("@Version")
                    .contains("private Long version")
                    .contains("AND version = ?")
                    .contains("refreshNextCareDates")
                    .contains("nextCareDate");
            softly.assertThat(userPlantRepository)
                    .as("Scheduler repository queries should be sargable due-date predicates, not per-row DATEDIFF calculations.")
                    .contains("up.nextWateringDate <= :executionDate")
                    .contains("up.nextPruningDate <= :executionDate")
                    .contains("up.nextFertilizingDate <= :executionDate")
                    .contains("recordWateringCompletion")
                    .contains("recordPruningCompletion")
                    .contains("recordFertilizingCompletion")
                    .contains("version = version + 1")
                    .contains("up.version = up.version + 1")
                    .contains("up.lastWateredDate < CURRENT_DATE")
                    .contains("int resetDailyCareStatuses()")
                    .doesNotContain("DATEDIFF")
                    .doesNotContain("FUNCTION('DATEDIFF'");
            softly.assertThat(userPlantCareScheduler)
                    .as("Daily status reset should use a bulk update instead of hydrating every user plant.")
                    .contains("jobService::resetDailyCareStatuses")
                    .doesNotContain("findAllByDeletedFalse()")
                    .doesNotContain("saveAll(userPlantList)");
            softly.assertThat(diaryService)
                    .as("Diary care completion should have one DB owner and no Redis dual write.")
                    .contains("recordCareCompletions")
                    .contains("recordWateringCompletion")
                    .doesNotContain("UserPlantDailyStatusRedisService")
                    .doesNotContain("updateDailyStatusCache");
            softly.assertThat(applicationProperties)
                    .as("Removed Redis daily care state should not keep a misleading runtime toggle.")
                    .doesNotContain("APP_NOTIFICATION_DAILY_REDIS_SCHEDULER_ENABLED");
            softly.assertThat(consistencyDiagnostic)
                    .as("Care state work should include stale-write and atomic merge diagnostics.")
                    .contains("concurrentUserPlantEditsShouldRejectOneStaleWriter")
                    .contains("concurrentDiaryCareCompletionsShouldMergeAndRemainIdempotent")
                    .contains("diaryCreateUpdateAndDeleteShouldKeepDbCareStateCumulative")
                    .contains("dailyResetShouldClearOnlyStaleCareCompletion")
                    .contains("repeatedWateringRows")
                    .contains("resetDailyCareStatuses");
            softly.assertThat(userPlantDueDateDiagnostic)
                    .as("UserPlant scheduler index work should include Docker-backed before/after p95 evidence.")
                    .contains("UserPlantSchedulerDueDateIndexIntegrationDiagnosticsTest")
                    .contains("DATEDIFF(%s, last_watered_date)")
                    .contains("FORCE INDEX (idx_userplant_due_watering)")
                    .contains("p95Improvement")
                    .contains("p95Millis");
        });
    }

    @Test
    void responseDtosShouldNotImportEntitiesOrDomainEnums() throws IOException {
        String notificationResponse = readSource("com/project/farming/domain/notification/dto/NotificationResponse.java");
        String notificationRepository = readSource("com/project/farming/domain/notification/repository/NotificationRepository.java");
        String notificationReadRow = readSource("com/project/farming/domain/notification/repository/NotificationReadRow.java");
        String notificationService = readSource("com/project/farming/domain/notification/service/NotificationService.java");
        String diaryNotificationIndexDiagnostic = readTestSource(
                "com/project/farming/integration/DiaryNotificationQueryIndexIntegrationDiagnosticsTest.java");
        String userMyPageResponse = readSource("com/project/farming/domain/user/dto/UserMyPageResponse.java");
        String authService = readSource("com/project/farming/domain/user/service/AuthService.java");

        assertSoftly(softly -> {
            softly.assertThat(notificationResponse)
                    .as("Notification response DTO should stay independent from the JPA entity.")
                    .contains("@AllArgsConstructor")
                    .doesNotContain("domain.notification.entity.Notification")
                    .doesNotContain("from(Notification");
            softly.assertThat(notificationRepository)
                    .as("Notification list reads and mark-as-read should use projections instead of loading write entities.")
                    .contains("Page<NotificationResponse> findResponsePageByUserIdOrderByCreatedAtDesc")
                    .doesNotContain("List<NotificationResponse> findResponsesByUserIdOrderByCreatedAtDesc")
                    .contains("new com.project.farming.domain.notification.dto.NotificationResponse")
                    .contains("ORDER BY n.createdAt DESC")
                    .contains("Optional<NotificationReadRow> findReadRowByNotificationId")
                    .contains("int markAsReadIfUnreadAndOwned")
                    .contains("AND n.isRead = false");
            softly.assertThat(notificationReadRow)
                    .as("Mark-as-read should read only the ownership and response fields it needs.")
                    .contains("record NotificationReadRow")
                    .contains("Long userId")
                    .contains("boolean isRead");
            softly.assertThat(notificationService)
                    .as("Notification reads should not map managed entities to responses in the service.")
                    .contains("findResponsePageByUserIdOrderByCreatedAtDesc")
                    .doesNotContain("findResponsesByUserIdOrderByCreatedAtDesc")
                    .contains("findReadRowByNotificationId")
                    .contains("markAsReadIfUnreadAndOwned")
                    .satisfies(source -> assertThat(source.substring(
                            source.indexOf("public NotificationResponse markNotificationAsRead"),
                            source.indexOf("public long countUnreadNotifications")))
                            .doesNotContain("findById")
                            .contains("findReadRowByNotificationId")
                            .contains("markAsReadIfUnreadAndOwned"))
                    .doesNotContain("toNotificationResponse(Notification notification)")
                    .doesNotContain("notifications.map(this::toNotificationResponse)")
                    .doesNotContain(".map(this::toNotificationResponse)")
                    .doesNotContain("NotificationResponse::from")
                    .doesNotContain("NotificationResponse.from");
            softly.assertThat(diaryNotificationIndexDiagnostic)
                    .as("Mark-as-read conditional update should have Docker-backed affected-row evidence.")
                    .contains("notificationMarkAsReadShouldUseIdempotentConditionalUpdate")
                    .contains("markAsReadIfUnreadAndOwned")
                    .contains("firstUnread=%d, repeated=%d, ")
                    .contains("alreadyRead=%d, wrongOwner=%d%n")
                    .contains("assertThat(firstUpdate).isEqualTo(1)")
                    .contains("assertThat(repeatedUpdate).isZero()")
                    .contains("assertThat(alreadyReadUpdate).isZero()")
                    .contains("assertThat(wrongOwnerUpdate).isZero()");
            softly.assertThat(userMyPageResponse)
                    .as("MyPage response should expose role as an API string, not the domain enum type.")
                    .doesNotContain("domain.user.entity.UserRole")
                    .contains("private String role;");
            softly.assertThat(authService)
                    .as("Auth service should convert the domain enum to its public string value at the boundary.")
                    .contains(".role(user.getRole().name())");
        });
    }

    @Test
    void imageControllerShouldNotMapImageEntitiesDirectly() throws IOException {
        String imageController = readSource("com/project/farming/global/image/controller/ImageFileController.java");
        String imageService = readSource("com/project/farming/global/image/service/ImageFileService.java");
        String imageRepository = readSource("com/project/farming/global/image/repository/ImageFileRepository.java");

        assertSoftly(softly -> {
            softly.assertThat(imageController)
                    .as("Image controller should not import or inspect ImageFile entities directly.")
                    .doesNotContain("global.image.entity.ImageFile")
                    .doesNotContain("getImageFileById")
                    .doesNotContain("ErrorResponseDto");
            softly.assertThat(imageController)
                    .as("Image controller should use the global response schema for errors.")
                    .contains("CommonResponse");
            softly.assertThat(imageService)
                    .as("Image response DTO assembly and delete ownership checks should live in the service boundary.")
                    .contains("ImageUploadResponse uploadImageResponse")
                    .contains("void deleteImageForUser");
            softly.assertThat(imageController)
                    .as("The public image API should pass the authenticated identity into the service authorization boundary.")
                    .contains("uploadImageResponseForUser(")
                    .contains("customUserDetails.getUser().getUserId()");
            softly.assertThat(imageService)
                    .as("Public upload should allow only the authenticated user's USER image.")
                    .contains("domainType != ImageDomainType.USER")
                    .contains("!Objects.equals(domainId, authenticatedUserId)")
                    .contains("throw new AccessDeniedException");
            softly.assertThat(imageRepository)
                    .as("Public delete should scope the lookup by image ID, USER domain, and owner ID.")
                    .contains("findByImageFileIdAndDomainTypeAndDomainId");
            softly.assertThat(imageService)
                    .contains("imageFileId, ImageDomainType.USER, userId")
                    .doesNotContain("TODO: 다른 도메인 타입에 대한 삭제 권한 로직 추가");
            softly.assertThat(imageService)
                    .as("S3 delete side effects should be deferred until after DB transaction commit.")
                    .contains("deleteS3AfterCommit")
                    .contains("afterCommit()")
                    .doesNotContain("s3Service.updateFile");
            softly.assertThat(imageService)
                    .as("New S3 uploads should be cleaned up if the DB transaction rolls back.")
                    .contains("deleteUploadedS3AfterRollback")
                    .contains("afterCompletion(int status)")
                    .contains("STATUS_ROLLED_BACK");
            softly.assertThat(MAIN_SOURCE.resolve("com/project/farming/global/image/dto/ErrorResponseDto.java"))
                    .as("The image-local duplicate error DTO should stay removed.")
                    .doesNotExist();
        });
    }

    @Test
    void commonResponseEnvelopeShouldBeTyped() throws IOException {
        String commonResponse = readSource("com/project/farming/global/response/CommonResponse.java");
        String globalExceptionHandler = readSource("com/project/farming/global/exception/GlobalExceptionHandler.java");
        String authController = readSource("com/project/farming/domain/user/controller/AuthController.java");
        String authService = readSource("com/project/farming/domain/user/service/AuthService.java");

        assertSoftly(softly -> {
            softly.assertThat(commonResponse)
                    .as("Global response envelope should type its data payload instead of using Object.")
                    .contains("class CommonResponse<T>")
                    .contains("private T data;")
                    .doesNotContain("Object data");
            softly.assertThat(globalExceptionHandler)
                    .as("Global exception responses should use typed CommonResponse variants.")
                    .contains("CommonResponse<Map<String, String>>")
                    .contains("CommonResponse<Void>")
                    .doesNotContain("ResponseEntity<CommonResponse>");
            softly.assertThat(authController)
                    .as("Auth generic responses should declare whether their payload is String or absent.")
                    .contains("CommonResponse<String>")
                    .contains("CommonResponse<Void>")
                    .doesNotContain("ResponseEntity<CommonResponse>");
            softly.assertThat(authController)
                    .as("Auth controller should not log full request DTOs that may include passwords.")
                    .contains("회원가입 요청 수신")
                    .doesNotContain("RegisterRequest: {}")
                    .doesNotContain("log.info(\"RegisterRequest");
            softly.assertThat(globalExceptionHandler)
                    .as("Refresh token failures should use a 401 auth-specific response, not generic 400.")
                    .contains("InvalidRefreshTokenException")
                    .contains("HttpStatus.UNAUTHORIZED")
                    .contains("INVALID_REFRESH_TOKEN");
            softly.assertThat(authService)
                    .as("Refresh token failure paths should throw the auth-specific exception.")
                    .contains("InvalidRefreshTokenException")
                    .doesNotContain("throw new IllegalArgumentException(\"유효하지 않은 리프레시 토큰")
                    .doesNotContain("throw new IllegalArgumentException(\"DB에 존재하지 않는 리프레시 토큰")
                    .doesNotContain("throw new IllegalArgumentException(\"만료된 Refresh Token");
            softly.assertThat(globalExceptionHandler)
                    .as("Login authentication failures should remain 401 authentication failures.")
                    .contains("AuthenticationException")
                    .contains("AUTHENTICATION_FAILED")
                    .contains("HttpStatus.UNAUTHORIZED");
            softly.assertThat(authService)
                    .as("Login failures should not be converted to generic IllegalArgumentException or log raw identifiers.")
                    .contains("BadCredentialsException")
                    .doesNotContain("throw new IllegalArgumentException(\"이메일 또는 비밀번호가 잘못되었습니다.")
                    .doesNotContain("이메일: {}");
        });
    }

    @Test
    void loginShouldUseRedisBackedRateLimitWithoutStoringRawEmailKeys() throws IOException {
        String authService = readSource("com/project/farming/domain/user/service/AuthService.java");
        String loginAttemptService = readSource("com/project/farming/domain/user/service/LoginAttemptService.java");
        String credentialFingerprint = readSource("com/project/farming/domain/user/service/CredentialFingerprint.java");
        String globalExceptionHandler = readSource("com/project/farming/global/exception/GlobalExceptionHandler.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(authService)
                    .as("Login flow should check rate limit before authentication, record failures, and clear on success.")
                    .contains("loginAttemptService.assertAllowed(email)")
                    .contains("loginAttemptService.recordSuccess(email)")
                    .contains("loginAttemptService.recordFailure(email)");
            softly.assertThat(loginAttemptService)
                    .as("Login rate limit should use Redis TTL counters keyed by email fingerprint, not raw email.")
                    .contains("StringRedisTemplate")
                    .contains("auth:login:failure:")
                    .contains("CredentialFingerprint.email(email)")
                    .contains("redisTemplate.opsForValue().increment")
                    .contains("redisTemplate.expire")
                    .contains("recordSuccess")
                    .contains("redisTemplate.delete")
                    .doesNotContain("KEY_PREFIX + normalize(email)");
            softly.assertThat(credentialFingerprint)
                    .as("Credential fingerprints should use a one-way SHA-256 hex representation.")
                    .contains("MessageDigest.getInstance(\"SHA-256\")")
                    .contains("HexFormat.of().formatHex");
            softly.assertThat(globalExceptionHandler)
                    .as("Repeated login failures should be surfaced as 429 so clients know to slow down.")
                    .contains("LoginRateLimitExceededException")
                    .contains("HttpStatus.TOO_MANY_REQUESTS")
                    .contains("LOGIN_RATE_LIMITED");
            softly.assertThat(applicationProperties)
                    .as("Rate limit thresholds should be runtime-tunable non-secret settings.")
                    .contains("APP_AUTH_LOGIN_RATE_LIMIT_ENABLED:true")
                    .contains("APP_AUTH_LOGIN_RATE_LIMIT_MAX_FAILURES:5")
                    .contains("APP_AUTH_LOGIN_RATE_LIMIT_FAILURE_WINDOW_SECONDS:900")
                    .contains("APP_AUTH_LOGIN_RATE_LIMIT_LOCK_DURATION_SECONDS:900");
        });
    }

    @Test
    void reviewRemediationContractsShouldStayAligned() throws IOException {
        String globalExceptionHandler = readSource("com/project/farming/global/exception/GlobalExceptionHandler.java");
        String securityConfig = readSource("com/project/farming/global/security/SecurityConfig.java");
        String authController = readSource("com/project/farming/domain/user/controller/AuthController.java");
        String fcmService = readSource("com/project/farming/global/fcm/FcmServiceImpl.java");
        String noticeService = readSource("com/project/farming/domain/notification/service/NoticeService.java");
        String fcmOutbox = readSource("com/project/farming/domain/notification/outbox/FcmOutbox.java");
        String fcmOutboxSourceType = readSource("com/project/farming/domain/notification/outbox/FcmOutboxSourceType.java");
        String fcmOutboxRepository = readSource("com/project/farming/domain/notification/outbox/FcmOutboxRepository.java");
        String fcmOutboxBatchStore = readSource("com/project/farming/domain/notification/outbox/FcmOutboxBatchStore.java");
        String fcmOutboxProcessor = readSource("com/project/farming/domain/notification/outbox/FcmOutboxProcessor.java");
        String fcmOutboxWorker = readSource("com/project/farming/domain/notification/outbox/FcmOutboxWorker.java");
        String fcmOutboxAdminService = readSource("com/project/farming/domain/notification/outbox/FcmOutboxAdminService.java");
        String fcmOutboxAdminFilter = readSource("com/project/farming/domain/notification/outbox/FcmOutboxAdminFilter.java");
        String fcmOutboxBulkRetryRequest = readSource("com/project/farming/domain/notification/outbox/FcmOutboxBulkRetryRequest.java");
        String fcmOutboxResponse = readSource("com/project/farming/domain/notification/outbox/FcmOutboxResponse.java");
        String fcmOutboxRetryAudit = readSource("com/project/farming/domain/notification/outbox/FcmOutboxRetryAudit.java");
        String fcmOutboxRetryAuditRepository = readSource("com/project/farming/domain/notification/outbox/FcmOutboxRetryAuditRepository.java");
        String fcmOutboxAdminController = readSource("com/project/farming/domain/notification/controller/FcmOutboxAdminController.java");
        String notificationController = readSource("com/project/farming/domain/notification/controller/NotificationController.java");
        String notificationService = readSource("com/project/farming/domain/notification/service/NotificationService.java");
        String notificationRepository = readSource("com/project/farming/domain/notification/repository/NotificationRepository.java");
        String plantController = readSource("com/project/farming/domain/plant/controller/PlantController.java");
        String farmController = readSource("com/project/farming/domain/farm/controller/FarmController.java");
        String userRepository = readSource("com/project/farming/domain/user/repository/UserRepository.java");
        String plantRepository = readSource("com/project/farming/domain/plant/repository/PlantRepository.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");
        String userPlantRepository = readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java");

        assertSoftly(softly -> {
            softly.assertThat(globalExceptionHandler)
                    .as("Domain not-found and NoSuchElement paths should not fall through to the generic 500 handler.")
                    .contains("NoSuchElementException")
                    .contains("ImageFileNotFoundException.class")
                    .contains("UserPlantNotFoundException.class")
                    .contains("RESOURCE_NOT_FOUND");
            softly.assertThat(securityConfig)
                    .as("Spring Security unauthenticated/forbidden API responses should use the common error envelope.")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .contains("accessDeniedHandler")
                    .contains("HttpServletResponse.SC_FORBIDDEN")
                    .contains("CommonResponse.error(\"접근 권한이 없습니다.\", \"ACCESS_DENIED\")")
                    .doesNotContain("{\\\"error\\\":\\\"Unauthorized\\\"");
            softly.assertThat(notificationController)
                    .as("Notification API should expose the same common envelope for manual auth failures and async send acceptance.")
                    .contains("CommonResponse<Page<NotificationResponse>>")
                    .contains("CommonResponse<NotificationResponse>")
                    .contains("CommonResponse<Long>")
                    .contains("CommonResponse.error(\"인증이 필요합니다.\", \"AUTHENTICATION_REQUIRED\")")
                    .contains("HttpStatus.ACCEPTED")
                    .doesNotContain("ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()")
                    .doesNotContain("org.springframework.security.access.AccessDeniedException");
            softly.assertThat(authController)
                    .as("Invalid refresh-token semantics should be documented as 401, while missing body fields remain 400.")
                    .contains("@ApiResponse(responseCode = \"400\", description = \"잘못된 요청 (리프레시 토큰 누락 등 유효성 검증 실패)\"")
                    .contains("@ApiResponse(responseCode = \"401\", description = \"인증 실패 (유효하지 않거나 블랙리스트에 있는 리프레시 토큰)\"");
            softly.assertThat(fcmService)
                    .as("FCM sends should support per-recipient batch results and should not log raw failed tokens.")
                    .contains("MAX_MULTICAST_TOKENS = 500")
                    .contains("sendMessageChunk")
                    .contains("sendEach(messages)")
                    .contains("List<FcmBatchResult> sendBatch")
                    .contains("failedTokens.add(maskToken(targetTokens.get(i)))")
                    .doesNotContain("failedTokens.add(targetTokens.get(i))");
            softly.assertThat(noticeService)
                    .as("Notice broadcast should create in-app notifications before enqueuing durable FCM work.")
                    .contains("notificationService.saveNotice(notice.getNoticeId(), notice.getTitle(), notice.getContent());")
                    .contains("fcmOutboxService.enqueueNotice")
                    .doesNotContain("fcmService.sendMessagesTo")
                    .satisfies(source -> assertThat(source.indexOf("notificationService.saveNotice"))
                            .isLessThan(source.indexOf("fcmOutboxService.enqueueNotice")));
            softly.assertThat(fcmOutbox)
                    .as("Notice FCM fan-out should be persisted as a virtual-FK outbox, not a JPA object graph.")
                    .contains("name = \"fcm_outbox\"")
                    .contains("notice_id")
                    .contains("target_token")
                    .contains("source_type")
                    .contains("source_id")
                    .contains("FcmOutboxStatus")
                    .contains("notificationPush")
                    .doesNotContain("@ManyToOne")
                    .doesNotContain("@OneToOne");
            softly.assertThat(fcmOutboxSourceType)
                    .as("The shared FCM outbox should distinguish notice broadcast rows from individual notification rows.")
                    .contains("NOTICE")
                    .contains("NOTIFICATION");
            softly.assertThat(fcmOutboxRepository)
                    .as("Outbox repository should retain retry and admin query contracts.")
                    .contains("requeueExpiredProcessingJobs")
                    .contains("Page<FcmOutboxAdminRow> findAdminRowsByStatus")
                    .contains("findAdminRowsByStatusAndFilters")
                    .contains("(:sourceType IS NULL OR o.sourceType = :sourceType)")
                    .contains("(:sourceId IS NULL OR o.sourceId = :sourceId)")
                    .contains("(:userId IS NULL OR o.userId = :userId)")
                    .contains("int retryFailedOutbox")
                    .contains("o.attemptCount = 0")
                    .contains("o.status = :failed");
            softly.assertThat(fcmOutboxBatchStore)
                    .as("Outbox delivery should claim indexed queue rows in short transactions and persist individual results.")
                    .contains("FOR UPDATE SKIP LOCKED")
                    .contains("Isolation.READ_COMMITTED")
                    .contains("ORDER BY next_retry_at ASC, fcm_outbox_id ASC")
                    .contains("status = 'CANCELLED'")
                    .contains("completeBatch")
                    .contains("existsByNoticeIdAndStatusNot");
            softly.assertThat(fcmOutboxProcessor)
                    .as("Outbox worker should send a claimed batch outside a DB transaction and persist classified results.")
                    .contains("FcmSendException")
                    .contains("fcmService.sendBatch")
                    .contains("batchStore.completeBatch")
                    .doesNotContain("@Transactional");
            softly.assertThat(fcmOutboxWorker)
                    .as("FCM outbox worker should be property-gated and scheduled.")
                    .contains("ConditionalOnProperty")
                    .contains("app.fcm.outbox.worker.enabled")
                    .contains("@Scheduled")
                    .contains("fixed-delay-ms");
            softly.assertThat(fcmOutboxResponse)
                    .as("Failed outbox admin responses should mask tokens and avoid exposing the raw targetToken property.")
                    .contains("maskedTargetToken")
                    .contains("maskToken")
                    .doesNotContain("String targetToken,");
            softly.assertThat(fcmOutboxAdminService)
                    .as("Manual failed-outbox retry should only requeue failed rows and reset attempts for a fresh worker pass.")
                    .contains("getFailedOutboxes")
                    .contains("FcmOutboxAdminFilter")
                    .contains("retryFailedOutbox")
                    .contains("retryFailedOutboxes")
                    .contains("MAX_BULK_RETRY_SIZE = 500")
                    .contains("validateBulkRetryIds")
                    .contains("LinkedHashSet")
                    .contains("adminUserId")
                    .contains("FcmOutboxStatus.FAILED")
                    .contains("FcmOutboxStatus.PENDING")
                    .contains("fcmOutboxRetryAuditRepository.save")
                    .contains("NoSuchElementException");
            softly.assertThat(fcmOutboxAdminFilter)
                    .as("Failed outbox list filtering should be identifier based.")
                    .contains("FcmOutboxSourceType sourceType")
                    .contains("Long sourceId")
                    .contains("Long userId");
            softly.assertThat(fcmOutboxBulkRetryRequest)
                    .as("Bulk retry should be based on explicit selected ids, not filter-wide retry.")
                    .contains("fcmOutboxIds")
                    .contains("@Size(max = 500")
                    .doesNotContain("sourceType")
                    .doesNotContain("userId");
            softly.assertThat(fcmOutboxRetryAudit)
                    .as("Manual retry audit should store identifiers and status transition only, not raw push payloads.")
                    .contains("name = \"fcm_outbox_retry_audit\"")
                    .contains("fcmOutboxId")
                    .contains("adminUserId")
                    .contains("previousStatus")
                    .contains("resultStatus")
                    .contains("MANUAL_RETRY")
                    .doesNotContain("targetToken")
                    .doesNotContain("String title")
                    .doesNotContain("String body");
            softly.assertThat(fcmOutboxRetryAuditRepository)
                    .as("Manual retry audit should be queryable by outbox id for incident review.")
                    .contains("findByFcmOutboxIdOrderByCreatedAtDesc");
            softly.assertThat(fcmOutboxAdminController)
                    .as("Failed outbox operations should be admin-only, common-response wrapped, and asynchronous retry accepted.")
                    .contains("@RequestMapping(\"/api/notifications/admin/fcm-outbox\")")
                    .contains("@GetMapping(\"/failed\")")
                    .contains("@PatchMapping(\"/failed/{fcmOutboxId}/retry\")")
                    .contains("@PatchMapping(\"/failed/retry\")")
                    .contains("@RequestParam(required = false) FcmOutboxSourceType sourceType")
                    .contains("FcmOutboxBulkRetryRequest")
                    .contains("CommonResponse<Page<FcmOutboxResponse>>")
                    .contains("CommonResponse<Integer>")
                    .contains("AUTHENTICATION_REQUIRED")
                    .contains("UserRole.ADMIN")
                    .contains("getUserId()")
                    .contains("HttpStatus.ACCEPTED");
            softly.assertThat(plantController)
                    .as("Plant catalog API should use the common success envelope instead of returning raw catalog DTOs.")
                    .contains("CommonResponse<PageResponse<PlantResponse>>")
                    .contains("CommonResponse<PlantResponse>")
                    .contains("CommonResponse.success(\"식물 목록 조회 성공\"")
                    .doesNotContain("ResponseEntity<List<PlantResponse>>")
                    .doesNotContain("ResponseEntity<PlantResponse>");
            softly.assertThat(farmController)
                    .as("Farm catalog API should use the common success envelope instead of returning raw catalog DTOs.")
                    .contains("CommonResponse<PageResponse<FarmResponse>>")
                    .contains("CommonResponse<FarmResponse>")
                    .contains("CommonResponse.success(\"텃밭 목록 조회 성공\"")
                    .contains("\"주변 텃밭 조회 성공\"")
                    .doesNotContain("ResponseEntity<List<FarmResponse>>")
                    .doesNotContain("ResponseEntity<FarmResponse>");
            softly.assertThat(notificationService)
                    .as("Notice notification creation should use immutable source identity for retry idempotency.")
                    .contains("insertNoticeSnapshots(noticeId, title, content)")
                    .contains("existsByNoticeId(noticeId)")
                    .contains("중복 생성을 건너뜁니다");
            softly.assertThat(notificationService)
                    .as("Individual notification push should enqueue durable FCM outbox work instead of sending Firebase synchronously.")
                    .contains("fcmOutboxService.enqueueNotification")
                    .doesNotContain("FcmService")
                    .doesNotContain("fcmService.sendMessageTo");
            softly.assertThat(notificationRepository)
                    .as("Repository should use Notice identity rather than mutable content for idempotency and deletion.")
                    .contains("boolean existsByNoticeId(Long noticeId);")
                    .contains("void deleteByNoticeId(Long noticeId);")
                    .contains("int insertNoticeSnapshots(")
                    .contains("ON DUPLICATE KEY UPDATE")
                    .doesNotContain("existsByTitleAndMessage")
                    .doesNotContain("deleteByTitleAndMessage");
            softly.assertThat(userRepository)
                    .as("FCM broadcast should read only token projections instead of all User entities.")
                    .contains("List<String> findFcmTokens()")
                    .contains("SELECT u.fcmToken");
            softly.assertThat(plantRepository)
                    .as("Optional image references in plant projections should not hide plant rows.")
                    .doesNotContain("\n        JOIN ImageFile image ON image.imageFileId = p.plantImageFileId")
                    .contains("LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId");
            softly.assertThat(farmRepository)
                    .as("Optional image references in farm projections should not hide farm rows.")
                    .doesNotContain("\n        JOIN ImageFile image ON image.imageFileId = f.farmImageFileId")
                    .doesNotContain("\n        JOIN image_files image ON image.image_file_id = f.farm_image_file_id")
                    .contains("LEFT JOIN ImageFile image ON image.imageFileId = f.farmImageFileId")
                    .contains("LEFT JOIN image_files image ON image.image_file_id = f.farm_image_file_id");
            softly.assertThat(userPlantRepository)
                    .as("Optional user-plant image references should not hide user plant rows.")
                    .doesNotContain("\n        JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId")
                    .contains("LEFT JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId");
        });
    }

    @Test
    void adminBrowserMutationsShouldRequireCsrfProtectedNonGetRequests() throws IOException {
        String securityConfig = readSource("com/project/farming/global/security/SecurityConfig.java");
        String plantAdminController = readSource("com/project/farming/domain/plant/controller/PlantAdminController.java");
        String farmAdminController = readSource("com/project/farming/domain/farm/controller/FarmAdminController.java");
        String userAdminController = readSource("com/project/farming/domain/user/controller/UserAdminController.java");
        String noticeController = readSource("com/project/farming/domain/notification/controller/NoticeController.java");
        String plantDeleteTemplate = readResource("templates/plant/delete-plant.html");
        String farmDeleteTemplate = readResource("templates/farm/delete-farm.html");
        String userDeleteTemplate = readResource("templates/user/delete-user.html");
        String noticeDeleteTemplate = readResource("templates/notice/delete-notice.html");
        String noticeDetailTemplate = readResource("templates/notice/notice-detail.html");

        assertSoftly(softly -> {
            softly.assertThat(securityConfig)
                    .as("Session-backed admin forms must not run with CSRF globally disabled.")
                    .doesNotContain(".csrf(c -> c.disable())")
                    .contains("ignoringRequestMatchers(\"/api/**\", \"/auth/**\", \"/images/**\")");
            softly.assertThat(plantAdminController)
                    .contains("@PostMapping(\"/delete/{plantId}\")")
                    .doesNotContain("@GetMapping(\"/delete/{plantId}\")");
            softly.assertThat(farmAdminController)
                    .contains("@PostMapping(\"/delete/{farmId}\")")
                    .doesNotContain("@GetMapping(\"/delete/{farmId}\")");
            softly.assertThat(userAdminController)
                    .contains("@PostMapping(\"/delete/{userId}\")")
                    .doesNotContain("@GetMapping(\"/delete/{userId}\")");
            softly.assertThat(noticeController)
                    .contains("@PostMapping(\"/delete/{noticeId}\")")
                    .contains("@PostMapping(\"/send/{noticeId}\")")
                    .doesNotContain("@GetMapping(\"/delete/{noticeId}\")")
                    .doesNotContain("@GetMapping(\"/send/{noticeId}\")");
            List.of(
                    plantDeleteTemplate,
                    farmDeleteTemplate,
                    userDeleteTemplate,
                    noticeDeleteTemplate,
                    noticeDetailTemplate
            ).forEach(template -> softly.assertThat(template)
                    .contains("method=\"post\"")
                    .contains("${_csrf.parameterName}")
                    .contains("${_csrf.token}"));
        });
    }

    @Test
    void passwordRecoveryShouldUseNonEnumeratingSingleUseTokenFlow() throws IOException {
        String securityConfig = readSource("com/project/farming/global/security/SecurityConfig.java");
        String authController = readSource("com/project/farming/domain/user/controller/AuthController.java");
        String authService = readSource("com/project/farming/domain/user/service/AuthService.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(securityConfig)
                    .contains("/auth/forgot-password", "/auth/password-reset/confirm")
                    .contains("permitAll()");
            softly.assertThat(authController)
                    .contains("HttpStatus.ACCEPTED")
                    .contains("requestPasswordReset")
                    .contains("confirmPasswordReset")
                    .contains("PasswordResetConfirmRequest")
                    .doesNotContain("sendPasswordResetEmail");
            softly.assertThat(authService)
                    .doesNotContain("UUID.randomUUID().toString().substring(0, 8)")
                    .doesNotContain("JavaMailSender")
                    .doesNotContain("sendPasswordResetEmail");
            softly.assertThat(applicationProperties)
                    .contains("APP_AUTH_PASSWORD_RESET_TOKEN_TTL_SECONDS:900")
                    .contains("APP_AUTH_PASSWORD_RESET_REQUEST_COOLDOWN_SECONDS:60")
                    .contains("APP_AUTH_PASSWORD_RESET_CONFIRM_URL:");
        });
    }

    @Test
    void userPlantCareSchedulerShouldBeConditionalLockedAndIdempotent() throws IOException {
        String scheduler = readSource("com/project/farming/domain/userplant/service/UserPlantCareScheduler.java");
        String jobService = readSource("com/project/farming/domain/userplant/service/UserPlantCareJobService.java");
        String repository = readSource("com/project/farming/domain/userplant/repository/UserPlantRepository.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(applicationProperties)
                    .doesNotContain("spring.task.scheduling.enabled")
                    .contains("APP_USERPLANT_CARE_SCHEDULER_ENABLED:false")
                    .contains("APP_USERPLANT_CARE_SCHEDULER_ZONE:Asia/Seoul")
                    .contains("APP_USERPLANT_CARE_SCHEDULER_BATCH_SIZE:1000");
            softly.assertThat(scheduler)
                    .contains("@ConditionalOnProperty")
                    .contains("zone = \"${app.userplant.care-scheduler.zone:Asia/Seoul}\"")
                    .contains("executeWithLock")
                    .doesNotContain("UserPlantRepository")
                    .doesNotContain("NotificationService");
            softly.assertThat(jobService)
                    .contains("findDueCareUserIdsAfter")
                    .contains("findIncompleteCareUserIdsAfter")
                    .contains("aggregateByUser")
                    .contains("CareNotificationBatchWriter")
                    .contains("PageRequest.of")
                    .doesNotContain("findUserPlantsNeedWateringToday")
                    .doesNotContain("findUserPlantsIncompleteWateringToday");
            softly.assertThat(repository)
                    .contains("List<Long> findDueCareUserIdsAfter")
                    .contains("List<Long> findIncompleteCareUserIdsAfter")
                    .contains("up.userId > :afterUserId")
                    .contains("u.subscriptionStatus <> 'WITHDRAWN'");
        });
    }

    @Test
    void aiCallsShouldHaveBoundedNonTransactionalCompensatableBoundaries() throws IOException {
        String webClientConfig = readSource("com/project/farming/global/config/WebClientConfig.java");
        String chatService = readSource("com/project/farming/domain/chat/service/ChatService.java");
        String chatRequest = readSource("com/project/farming/domain/chat/dto/ChatRequest.java");
        String photoAnalysisService = readSource("com/project/farming/domain/analysis/service/PhotoAnalysisService.java");
        String applicationProperties = readBackendConfig("application.properties");

        assertSoftly(softly -> {
            softly.assertThat(webClientConfig)
                    .contains("CONNECT_TIMEOUT_MILLIS")
                    .contains("responseTimeout")
                    .contains("ReactorClientHttpConnector");
            softly.assertThat(chatService)
                    .contains("ChatAiClient")
                    .contains("ChatDeletionOutboxService")
                    .doesNotContain("WebClient")
                    .doesNotContain(".block()")
                    .doesNotContain("getSessionListFromPython");
            softly.assertThat(chatRequest)
                    .contains("@NotBlank")
                    .contains("@Size")
                    .contains("@Positive");
            softly.assertThat(photoAnalysisService)
                    .contains("PhotoAnalysisAiClient")
                    .contains("PhotoAnalysisRequestGuard")
                    .contains("deleteImage")
                    .doesNotContain("WebClient")
                    .doesNotContain(".block()");
            softly.assertThat(applicationProperties)
                    .contains("PYTHON_CONNECT_TIMEOUT_MS:2000")
                    .contains("PYTHON_RESPONSE_TIMEOUT_MS:15000")
                    .contains("APP_CHAT_DELETION_WORKER_ENABLED:false")
                    .contains("APP_PHOTO_ANALYSIS_REQUEST_COOLDOWN_SECONDS:10");
        });
    }

    @Test
    void publicAndAdminCollectionsShouldBePageBounded() throws IOException {
        String chatController = readSource("com/project/farming/domain/chat/controller/ChatController.java");
        String diaryController = readSource("com/project/farming/domain/diary/controller/DiaryController.java");
        String farmController = readSource("com/project/farming/domain/farm/controller/FarmController.java");
        String plantController = readSource("com/project/farming/domain/plant/controller/PlantController.java");
        String userPlantController = readSource("com/project/farming/domain/userplant/controller/UserPlantController.java");
        String userAdminController = readSource("com/project/farming/domain/user/controller/UserAdminController.java");
        String plantAdminController = readSource("com/project/farming/domain/plant/controller/PlantAdminController.java");
        String farmAdminController = readSource("com/project/farming/domain/farm/controller/FarmAdminController.java");

        assertSoftly(softly -> {
            List.of(chatController, diaryController, farmController, plantController, userPlantController)
                    .forEach(controller -> softly.assertThat(controller)
                            .contains("PageResponse<")
                            .doesNotContain("CommonResponse<List<"));
            List.of(userAdminController, plantAdminController, farmAdminController)
                    .forEach(controller -> softly.assertThat(controller)
                            .contains("Pageable")
                            .contains("Page<")
                            .doesNotContain("List<"));
        });
    }

    @Test
    void chatPaginationShouldUseMeasuredInnoDbIndexWithoutKeepingUnusedPhotoLatestQuery() throws IOException {
        String chatEntity = readSource("com/project/farming/domain/chat/entity/Chat.java");
        String chatRepository = readSource("com/project/farming/domain/chat/repository/ChatRepository.java");
        String photoAnalysisEntity = readSource("com/project/farming/domain/analysis/entity/PhotoAnalysis.java");
        String photoAnalysisRepository = readSource(
                "com/project/farming/domain/analysis/repository/PhotoAnalysisRepository.java");
        String integrationDiagnostic = readTestSource(
                "com/project/farming/integration/ChatQueryIndexIntegrationDiagnosticsTest.java");

        assertSoftly(softly -> {
            softly.assertThat(chatRepository)
                    .contains("findByUserIdOrderByChatIdDesc");
            softly.assertThat(chatEntity)
                    .as("InnoDB's user secondary index should reuse the implicit primary key for chat-id ordering.")
                    .contains("idx_chat_user")
                    .contains("columnList = \"user_id\"")
                    .doesNotContain("user_id, chat_id");
            softly.assertThat(photoAnalysisRepository)
                    .as("The Redis request guard replaced the unused latest-analysis lookup.")
                    .doesNotContain("findTopByUserIdOrderByCreatedAtDesc");
            softly.assertThat(photoAnalysisEntity)
                    .as("Do not pay write cost for an unused user/created-at index.")
                    .contains("idx_user_photo_analysis")
                    .doesNotContain("user_id, created_at");
            softly.assertThat(integrationDiagnostic)
                    .contains("IGNORE INDEX")
                    .contains("FORCE INDEX")
                    .contains("baselineP95Millis")
                    .contains("optimizedP95Millis");
        });
    }

    @Test
    void configurationAndDeadCodeShouldStayClean() throws IOException {
        String securityConfig = readSource("com/project/farming/global/security/SecurityConfig.java");
        String application = readBackendConfig("application.properties");
        String applicationTest = Files.readString(Path.of("src/test/resources/application-test.properties"));
        String applicationContextTest = Files.readString(
                Path.of("src/test/resources/application-context-test.properties"));
        String buildGradle = Files.readString(Path.of("build.gradle"));
        String plantRepository = readSource("com/project/farming/domain/plant/repository/PlantRepository.java");
        String farmRepository = readSource("com/project/farming/domain/farm/repository/FarmRepository.java");
        String userRepository = readSource("com/project/farming/domain/user/repository/UserRepository.java");
        String userPlantRepository = readSource(
                "com/project/farming/domain/userplant/repository/UserPlantRepository.java");
        String notificationRepository = readSource(
                "com/project/farming/domain/notification/repository/NotificationRepository.java");
        String notificationService = readSource(
                "com/project/farming/domain/notification/service/NotificationService.java");
        String imageFileService = readSource("com/project/farming/global/image/service/ImageFileService.java");
        String s3Service = readSource("com/project/farming/global/image/service/S3Service.java");
        String chatService = readSource("com/project/farming/domain/chat/service/ChatService.java");
        String refreshToken = readSource("com/project/farming/global/jwtToken/RefreshToken.java");
        String oauthUserService = readSource("com/project/farming/global/oauth/CustomOAuth2UserService.java");
        String allMainJava = readAllMainJavaSources();

        assertSoftly(softly -> {
            softly.assertThat(MAIN_SOURCE.resolve(
                            "com/project/farming/domain/user/controller/LoginDashBoard.java"))
                    .doesNotExist();
            softly.assertThat(MAIN_RESOURCES.resolve("templates/logindashboard.html")).doesNotExist();
            softly.assertThat(MAIN_RESOURCES.resolve("templates/login-success.html")).doesNotExist();
            softly.assertThat(MAIN_SOURCE.resolve(
                            "com/project/farming/global/oauth/HttpCookieOAuth2AuthorizationRequestRepository.java"))
                    .doesNotExist();
            softly.assertThat(MAIN_SOURCE.resolve(
                            "com/project/farming/global/oauth/SessionOAuth2AuthorizationRequestRepository.java"))
                    .exists();
            softly.assertThat(securityConfig)
                    .contains("/api/notifications/**")
                    .doesNotContain("/logindashboard", "/login-success", "/api/notify", "/api/alarms")
                    .doesNotContain("/users/profile", "/users/fcm-token");
            List.of(application, applicationTest).forEach(properties -> softly.assertThat(properties)
                    .contains("spring.jpa.open-in-view=false")
                    .doesNotContain("spring.jpa.properties.hibernate.dialect")
                    .doesNotContain("hibernate.temp.use_jdbc_metadata_defaults")
                    .doesNotContain("spring.task.scheduling.enabled"));
            softly.assertThat(applicationTest)
                    .doesNotContain("spring.jpa.database-platform")
                    .doesNotContain("hibernate.boot.allow_jdbc_metadata_access");
            softly.assertThat(applicationContextTest)
                    .contains("spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect")
                    .contains("spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false");
            softly.assertThat(application).contains("spring.data.redis.repositories.enabled=false");
            softly.assertThat(buildGradle)
                    .contains("exclude group: 'commons-logging', module: 'commons-logging'")
                    .doesNotContain("최신 버전 확인");
            softly.assertThat(plantRepository)
                    .doesNotContain("findAllByOrderByPlantNameAsc")
                    .doesNotContain("findByPlantNameContainingOrderByPlantNameAsc")
                    .doesNotContain("findAllByOrderByPlantIdAsc")
                    .doesNotContain("findByPlantNameContainingOrderByPlantIdAsc");
            softly.assertThat(farmRepository)
                    .doesNotContain("findAllByOrderByGardenUniqueIdAsc")
                    .doesNotContain("findByFarmNameContainingOrderByGardenUniqueIdAsc")
                    .doesNotContain("findByAddressContainingOrderByGardenUniqueIdAsc")
                    .doesNotContain("findByFarmNameOrAddressContainingOrderByGardenUniqueIdAsc");
            softly.assertThat(userRepository)
                    .doesNotContain("findAllByOrderByUserIdAsc")
                    .doesNotContain("findByNicknameContainingOrderByNicknameAsc")
                    .doesNotContain("findByEmailContainingOrderByEmailAsc");
            softly.assertThat(userPlantRepository)
                    .doesNotContain("findByUserIdAndDeletedFalseOrderByPlantNicknameAsc")
                    .doesNotContain("findByUserAndPlantContainingOrderByPlantNicknameAsc");
            softly.assertThat(notificationRepository)
                    .doesNotContain("findResponsesByUserIdOrderByCreatedAtDesc");
            softly.assertThat(notificationService)
                    .doesNotContain("getUserNotifications")
                    .doesNotContain("deleteAllUserNotificationsInternal")
                    .doesNotContain("markAsReadInternal");
            softly.assertThat(imageFileService)
                    .doesNotContain("createExternalImageFile")
                    .doesNotContain("getImageFileById");
            softly.assertThat(s3Service).doesNotContain("updateFile(");
            softly.assertThat(chatService).doesNotContain("validateChatHistoryOwnership");
            softly.assertThat(refreshToken).doesNotContain("updateRefreshToken(");
            softly.assertThat(oauthUserService)
                    .doesNotContain("(Map<String, Object>)")
                    .contains("instanceof Map<?, ?>");
            softly.assertThat(allMainJava)
                    .contains("HttpSessionOAuth2AuthorizationRequestRepository")
                    .doesNotContain("SerializationUtils")
                    .doesNotContain("import io.swagger.v3.oas.annotations.media.Content;")
                    .doesNotContain("// ✨", "// ⭐", "// ✅", "[수정된 부분]", "[새로 추가된 메소드]");
        });
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(MAIN_SOURCE.resolve(relativePath));
    }

    private static String readTestSource(String relativePath) throws IOException {
        return Files.readString(TEST_SOURCE.resolve(relativePath));
    }

    private static String readResource(String relativePath) throws IOException {
        return Files.readString(MAIN_RESOURCES.resolve(relativePath));
    }

    private static String readBackendConfig(String relativePath) throws IOException {
        return Files.readString(INFRA_BACKEND_CONFIG.resolve(relativePath));
    }

    private static String readAllMainJavaSources() throws IOException {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(MAIN_SOURCE)) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(path));
            }
        }
        return source.toString();
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static CascadeType[] manyToOneCascade(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = findField(entityType, fieldName);
        if (field == null) {
            return new CascadeType[0];
        }
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        if (manyToOne == null) {
            return new CascadeType[0];
        }
        return manyToOne.cascade();
    }

    private static CascadeType[] oneToManyCascade(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = findField(entityType, fieldName);
        if (field == null) {
            return new CascadeType[0];
        }
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany == null) {
            return new CascadeType[0];
        }
        return oneToMany.cascade();
    }

    private static List<String> relationshipAnnotatedFields(Map<Class<?>, List<String>> fieldsByType) {
        List<String> relationshipFields = new ArrayList<>();
        fieldsByType.forEach((entityType, fieldNames) -> fieldNames.forEach(fieldName -> {
            Field field = findField(entityType, fieldName);
            if (field != null && hasRelationshipAnnotation(field)) {
                relationshipFields.add(entityType.getSimpleName() + "." + fieldName);
            }
        }));
        return relationshipFields;
    }

    private static Field findField(Class<?> entityType, String fieldName) {
        try {
            return entityType.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static boolean hasRelationshipAnnotation(Field field) {
        return field.getAnnotation(ManyToOne.class) != null
                || field.getAnnotation(OneToMany.class) != null
                || field.getAnnotation(OneToOne.class) != null
                || field.getAnnotation(ManyToMany.class) != null;
    }
}

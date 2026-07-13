package com.project.farming.domain.userplant.repository;

import com.project.farming.domain.userplant.dto.UserPlantDetailResponse;
import com.project.farming.domain.userplant.dto.UserPlantListResponse;
import com.project.farming.domain.userplant.entity.UserPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserPlantRepository extends JpaRepository<UserPlant, Long>  {
    boolean existsByUserIdAndPlantNicknameAndDeletedFalse(Long userId, String plantNickname);

    @Query("""
        SELECT new com.project.farming.domain.userplant.dto.UserPlantListResponse(
            up.userPlantId,
            up.plantName,
            up.plantNickname,
            up.plantingPlace,
            up.isNotificationEnabled,
            up.waterIntervalDays,
            up.pruneIntervalDays,
            up.fertilizeIntervalDays,
            userPlantImage.imageUrl
        )
        FROM UserPlant up
        LEFT JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId
        WHERE up.userId = :userId
          AND up.deleted = false
        ORDER BY up.plantNickname ASC, up.userPlantId ASC
        """)
    Page<UserPlantListResponse> findListResponsesByUserIdOrderByPlantNicknameAsc(
            @Param("userId") Long userId, Pageable pageable);

    List<UserPlant> findAllByDeletedFalse();

    @Query("""
        SELECT new com.project.farming.domain.userplant.dto.UserPlantListResponse(
            up.userPlantId,
            up.plantName,
            up.plantNickname,
            up.plantingPlace,
            up.isNotificationEnabled,
            up.waterIntervalDays,
            up.pruneIntervalDays,
            up.fertilizeIntervalDays,
            userPlantImage.imageUrl
        )
        FROM UserPlant up
        LEFT JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId
        WHERE up.userId = :userId
          AND up.deleted = false
          AND (up.plantName LIKE :keyword ESCAPE '!' OR up.plantNickname LIKE :keyword ESCAPE '!')
        ORDER BY up.plantNickname ASC, up.userPlantId ASC
        """)
    Page<UserPlantListResponse> findListResponsesByUserAndKeywordOrderByPlantNicknameAsc(
            @Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    Optional<UserPlant> findByUserIdAndUserPlantIdAndDeletedFalse(Long userId, Long userPlantId);

    @Query("""
        SELECT new com.project.farming.domain.userplant.dto.UserPlantDetailResponse(
            up.userPlantId,
            up.plantName,
            up.plantNickname,
            up.plantingPlace,
            up.plantedDate,
            up.notes,
            up.isNotificationEnabled,
            up.waterIntervalDays,
            up.pruneIntervalDays,
            up.fertilizeIntervalDays,
            up.watered,
            up.pruned,
            up.fertilized,
            userPlantImage.imageUrl,
            CASE WHEN plant.plantName <> '기타' THEN plant.plantEnglishName ELSE null END,
            CASE WHEN plant.plantName <> '기타' THEN plant.species ELSE null END,
            CASE WHEN plant.plantName <> '기타' THEN plant.season ELSE null END,
            CASE WHEN plant.plantName <> '기타' THEN plantImage.imageUrl ELSE null END
        )
        FROM UserPlant up
        LEFT JOIN ImageFile userPlantImage ON userPlantImage.imageFileId = up.userPlantImageFileId
        JOIN Plant plant ON plant.plantId = up.plantId
        LEFT JOIN ImageFile plantImage ON plantImage.imageFileId = plant.plantImageFileId
        WHERE up.userId = :userId
          AND up.userPlantId = :userPlantId
          AND up.deleted = false
        """)
    Optional<UserPlantDetailResponse> findDetailResponseByUserIdAndUserPlantId(
            @Param("userId") Long userId, @Param("userPlantId") Long userPlantId);

    boolean existsByUserIdAndUserPlantIdAndDeletedFalse(Long userId, Long userPlantId);

    @Query("""
        SELECT up FROM UserPlant up
        WHERE up.userId = :userId
          AND up.deleted = false
          AND up.userPlantId IN :userPlantIds
        """)
    List<UserPlant> findAllByUserIdAndUserPlantIdIn(
            @Param("userId") Long userId, @Param("userPlantIds") List<Long> userPlantIds);

    @Modifying
    @Query("""
        UPDATE UserPlant up
        SET up.farmId = :otherFarmId,
            up.version = up.version + 1
        WHERE up.farmId = :oldFarmId
          AND up.deleted = false
        """)
    int reassignFarm(
            @Param("otherFarmId") Long otherFarmId, @Param("oldFarmId") Long oldFarmId);

    @Modifying
    @Query("""
        UPDATE UserPlant up
        SET up.plantId = :otherPlantId,
            up.version = up.version + 1
        WHERE up.plantId = :oldPlantId
          AND up.deleted = false
        """)
    int reassignPlant(
            @Param("otherPlantId") Long otherPlantId, @Param("oldPlantId") Long oldPlantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE user_plants
        SET watered = TRUE,
            last_watered_date = CURRENT_DATE,
            next_watering_date = DATE_ADD(CURRENT_DATE, INTERVAL water_interval_days DAY),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE user_id = :userId
          AND user_plant_id IN (:userPlantIds)
          AND deleted = FALSE
          AND (watered = FALSE OR last_watered_date IS NULL OR last_watered_date < CURRENT_DATE)
        """, nativeQuery = true)
    int recordWateringCompletion(
            @Param("userId") Long userId,
            @Param("userPlantIds") List<Long> userPlantIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE user_plants
        SET pruned = TRUE,
            last_pruned_date = CURRENT_DATE,
            next_pruning_date = DATE_ADD(CURRENT_DATE, INTERVAL prune_interval_days DAY),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE user_id = :userId
          AND user_plant_id IN (:userPlantIds)
          AND deleted = FALSE
          AND (pruned = FALSE OR last_pruned_date IS NULL OR last_pruned_date < CURRENT_DATE)
        """, nativeQuery = true)
    int recordPruningCompletion(
            @Param("userId") Long userId,
            @Param("userPlantIds") List<Long> userPlantIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE user_plants
        SET fertilized = TRUE,
            last_fertilized_date = CURRENT_DATE,
            next_fertilizing_date = DATE_ADD(CURRENT_DATE, INTERVAL fertilize_interval_days DAY),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE user_id = :userId
          AND user_plant_id IN (:userPlantIds)
          AND deleted = FALSE
          AND (fertilized = FALSE OR last_fertilized_date IS NULL OR last_fertilized_date < CURRENT_DATE)
        """, nativeQuery = true)
    int recordFertilizingCompletion(
            @Param("userId") Long userId,
            @Param("userPlantIds") List<Long> userPlantIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE UserPlant up
        SET up.watered = CASE
                WHEN up.watered = true
                 AND (up.lastWateredDate IS NULL OR up.lastWateredDate < CURRENT_DATE)
                THEN false ELSE up.watered END,
            up.pruned = CASE
                WHEN up.pruned = true
                 AND (up.lastPrunedDate IS NULL OR up.lastPrunedDate < CURRENT_DATE)
                THEN false ELSE up.pruned END,
            up.fertilized = CASE
                WHEN up.fertilized = true
                 AND (up.lastFertilizedDate IS NULL OR up.lastFertilizedDate < CURRENT_DATE)
                THEN false ELSE up.fertilized END,
            up.updatedAt = CURRENT_TIMESTAMP,
            up.version = up.version + 1
        WHERE up.deleted = false
          AND (
                (up.watered = true AND (up.lastWateredDate IS NULL OR up.lastWateredDate < CURRENT_DATE))
             OR (up.pruned = true AND (up.lastPrunedDate IS NULL OR up.lastPrunedDate < CURRENT_DATE))
             OR (up.fertilized = true AND (up.lastFertilizedDate IS NULL OR up.lastFertilizedDate < CURRENT_DATE))
          )
        """)
    int resetDailyCareStatuses();

    @Query("""
        SELECT DISTINCT up.userId
        FROM UserPlant up
        JOIN User u ON u.userId = up.userId
        WHERE up.userId > :afterUserId
          AND up.userId <= :upperUserId
          AND u.subscriptionStatus <> 'WITHDRAWN'
          AND u.fcmToken IS NOT NULL
          AND TRIM(u.fcmToken) <> ''
          AND up.isNotificationEnabled = true
          AND up.deleted = false
          AND (
               up.nextWateringDate <= :executionDate
            OR up.nextPruningDate <= :executionDate
            OR up.nextFertilizingDate <= :executionDate
          )
        ORDER BY up.userId ASC
        """)
    List<Long> findDueCareUserIdsAfter(
            @Param("afterUserId") Long afterUserId,
            @Param("upperUserId") Long upperUserId,
            @Param("executionDate") LocalDate executionDate,
            Pageable pageable);

    @Query("""
        SELECT DISTINCT up.userId
        FROM UserPlant up
        JOIN User u ON u.userId = up.userId
        WHERE up.userId > :afterUserId
          AND up.userId <= :upperUserId
          AND u.subscriptionStatus <> 'WITHDRAWN'
          AND u.fcmToken IS NOT NULL
          AND TRIM(u.fcmToken) <> ''
          AND up.isNotificationEnabled = true
          AND up.deleted = false
          AND (
               (up.watered = false AND up.nextWateringDate <= :executionDate)
            OR (up.pruned = false AND up.nextPruningDate <= :executionDate)
            OR (up.fertilized = false AND up.nextFertilizingDate <= :executionDate)
          )
        ORDER BY up.userId ASC
        """)
    List<Long> findIncompleteCareUserIdsAfter(
            @Param("afterUserId") Long afterUserId,
            @Param("upperUserId") Long upperUserId,
            @Param("executionDate") LocalDate executionDate,
            Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.userplant.repository.UserPlantCareTaskRow(
            up.userPlantId,
            up.userId,
            up.plantName,
            up.plantNickname,
            CASE WHEN up.nextWateringDate <= :executionDate THEN true ELSE false END,
            CASE WHEN up.nextPruningDate <= :executionDate THEN true ELSE false END,
            CASE WHEN up.nextFertilizingDate <= :executionDate THEN true ELSE false END
        )
        FROM UserPlant up
        JOIN User u ON u.userId = up.userId
        WHERE up.userId IN :userIds
          AND u.subscriptionStatus <> 'WITHDRAWN'
          AND u.fcmToken IS NOT NULL
          AND TRIM(u.fcmToken) <> ''
          AND up.isNotificationEnabled = true
          AND up.deleted = false
          AND (
               up.nextWateringDate <= :executionDate
            OR up.nextPruningDate <= :executionDate
            OR up.nextFertilizingDate <= :executionDate
          )
        ORDER BY up.userId ASC, up.userPlantId ASC
        """)
    List<UserPlantCareTaskRow> findDueCareTaskRowsByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("executionDate") LocalDate executionDate);

    @Query("""
        SELECT new com.project.farming.domain.userplant.repository.UserPlantCareTaskRow(
            up.userPlantId,
            up.userId,
            up.plantName,
            up.plantNickname,
            CASE WHEN up.watered = false AND up.nextWateringDate <= :executionDate THEN true ELSE false END,
            CASE WHEN up.pruned = false AND up.nextPruningDate <= :executionDate THEN true ELSE false END,
            CASE WHEN up.fertilized = false AND up.nextFertilizingDate <= :executionDate THEN true ELSE false END
        )
        FROM UserPlant up
        JOIN User u ON u.userId = up.userId
        WHERE up.userId IN :userIds
          AND u.subscriptionStatus <> 'WITHDRAWN'
          AND u.fcmToken IS NOT NULL
          AND TRIM(u.fcmToken) <> ''
          AND up.isNotificationEnabled = true
          AND up.deleted = false
          AND (
               (up.watered = false AND up.nextWateringDate <= :executionDate)
            OR (up.pruned = false AND up.nextPruningDate <= :executionDate)
            OR (up.fertilized = false AND up.nextFertilizingDate <= :executionDate)
          )
        ORDER BY up.userId ASC, up.userPlantId ASC
        """)
    List<UserPlantCareTaskRow> findIncompleteCareTaskRowsByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("executionDate") LocalDate executionDate);
}

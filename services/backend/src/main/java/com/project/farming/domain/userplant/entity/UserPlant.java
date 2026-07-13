package com.project.farming.domain.userplant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Check;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_plants",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "plant_nickname"})
        },
        indexes = {
                @Index(name = "idx_user_plant", columnList = "user_id"),
                @Index(name = "idx_userplant_user_nickname",
                        columnList = "user_id, deleted, plant_nickname"),
                @Index(name = "idx_userplant_user_plant_name",
                        columnList = "user_id, deleted, plant_name"),
                @Index(name = "idx_userplant_plant_active",
                        columnList = "plant_id, deleted"),
                @Index(name = "idx_userplant_farm_active",
                        columnList = "farm_id, deleted"),
                @Index(name = "idx_userplant_due_watering",
                        columnList = "is_notification_enabled, deleted, next_watering_date, watered"),
                @Index(name = "idx_userplant_due_pruning",
                        columnList = "is_notification_enabled, deleted, next_pruning_date, pruned"),
                @Index(name = "idx_userplant_due_fertilizing",
                        columnList = "is_notification_enabled, deleted, next_fertilizing_date, fertilized")
        })
@SQLDelete(sql = """
        UPDATE user_plants
        SET deleted = true,
            deleted_at = NOW(),
            updated_at = NOW(),
            is_notification_enabled = false,
            plant_nickname = CONCAT('deleted-', SUBSTRING(REPLACE(UUID(), '-', ''), 1, 12)),
            version = version + 1
        WHERE user_plant_id = ?
          AND version = ?
        """, verify = Expectation.RowCount.class)
@SQLRestriction("deleted = false")
@Check(constraints = "water_interval_days BETWEEN 1 AND 365 "
        + "AND prune_interval_days BETWEEN 1 AND 365 "
        + "AND fertilize_interval_days BETWEEN 1 AND 365")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userPlantId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plant_id", nullable = false)
    private Long plantId; // 등록된 식물 ID

    private String plantName; // 식물 종류(등록된 식물, 직접 입력)

    @Column(nullable = false, length = 20)
    private String plantNickname;

    @Column(name = "farm_id", nullable = false)
    private Long farmId; // 등록된 텃밭 ID
    
    private String plantingPlace; // 심은 장소(등록된 텃밭, 직접 입력)
    private LocalDateTime plantedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // 알림 관련
    @Column(nullable = false)
    private boolean isNotificationEnabled; // 알림 수신 여부

    @Column(nullable = false)
    private int waterIntervalDays; // 물 주는 간격(일 단위)
    
    private LocalDate lastWateredDate; // 마지막 물 준 날짜

    @Column(name = "next_watering_date")
    private LocalDate nextWateringDate;
    
    @Column(nullable = false)
    private boolean watered; // 물 주기 여부

    @Column(nullable = false)
    private int pruneIntervalDays; // 가지치기 간격(일 단위)

    private LocalDate lastPrunedDate; // 마지막 가지치기 날짜

    @Column(name = "next_pruning_date")
    private LocalDate nextPruningDate;

    @Column(nullable = false)
    private boolean pruned; // 가지치기 여부

    @Column(nullable = false)
    private int fertilizeIntervalDays; // 영양제 주는 간격(일 단위)

    private LocalDate lastFertilizedDate; // 마지막 영양제 준 날짜

    @Column(name = "next_fertilizing_date")
    private LocalDate nextFertilizingDate;

    @Column(nullable = false)
    private boolean fertilized; // 영양제 주기 여부

    @Column(name = "user_plant_image_file_id", nullable = false)
    private Long userPlantImageFileId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        validateCareIntervals(waterIntervalDays, pruneIntervalDays, fertilizeIntervalDays);
        this.createdAt = this.updatedAt = LocalDateTime.now();
        refreshNextCareDates();
    }

    @PreUpdate
    protected void onUpdate() {
        validateCareIntervals(waterIntervalDays, pruneIntervalDays, fertilizeIntervalDays);
        this.updatedAt = LocalDateTime.now();
        refreshNextCareDates();
    }

    // 사용자 입력 식물이면 식물 이름 수정 가능
    public void updatePlantName(String plantName) {
        this.plantName = plantName;
    }

    public void updateUserPlantInfo(String plantNickname, String notes) {
        this.plantNickname = plantNickname;
        this.notes = notes;
    }

    // 식물을 다른 곳에 옮겨 심는 경우
    public void updatePlantingPlace(Long farmId, String plantingPlace) {
        this.farmId = farmId;
        this.plantingPlace = plantingPlace;
    }

    public void updateIsNotificationEnabled(boolean isNotificationEnabled) {
        this.isNotificationEnabled = isNotificationEnabled;
    }

    public void updateUserPlantIntervalDays(
            int waterIntervalDays, int pruneIntervalDays, int fertilizeIntervalDays) {
        validateCareIntervals(waterIntervalDays, pruneIntervalDays, fertilizeIntervalDays);
        this.waterIntervalDays = waterIntervalDays;
        this.pruneIntervalDays = pruneIntervalDays;
        this.fertilizeIntervalDays = fertilizeIntervalDays;
        refreshNextCareDates();
    }

    public void replaceDailyCareStatus(boolean watered, boolean pruned, boolean fertilized) {
        this.watered = watered;
        if (watered) {
            this.lastWateredDate = LocalDate.now();
        }

        this.pruned = pruned;
        if (pruned) {
            this.lastPrunedDate = LocalDate.now();
        }

        this.fertilized = fertilized;
        if (fertilized) {
            this.lastFertilizedDate = LocalDate.now();
        }
        refreshNextCareDates();
    }

    public void updateUserPlantImage(Long userPlantImageFileId) {
        this.userPlantImageFileId = userPlantImageFileId;
    }

    private void refreshNextCareDates() {
        this.nextWateringDate = nextCareDate(lastWateredDate, waterIntervalDays);
        this.nextPruningDate = nextCareDate(lastPrunedDate, pruneIntervalDays);
        this.nextFertilizingDate = nextCareDate(lastFertilizedDate, fertilizeIntervalDays);
    }

    private LocalDate nextCareDate(LocalDate lastCareDate, int intervalDays) {
        if (lastCareDate == null) {
            return null;
        }
        return lastCareDate.plusDays(intervalDays);
    }

    public static void validateCareIntervals(
            int waterIntervalDays,
            int pruneIntervalDays,
            int fertilizeIntervalDays) {
        validateCareInterval("물 주기", waterIntervalDays);
        validateCareInterval("가지치기", pruneIntervalDays);
        validateCareInterval("영양제 주기", fertilizeIntervalDays);
    }

    private static void validateCareInterval(String name, int intervalDays) {
        if (intervalDays < 1 || intervalDays > 365) {
            throw new IllegalArgumentException(name + " 간격은 1일 이상 365일 이하여야 합니다.");
        }
    }
}

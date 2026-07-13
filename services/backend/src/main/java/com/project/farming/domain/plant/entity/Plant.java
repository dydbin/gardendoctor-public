package com.project.farming.domain.plant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "plant_info",
        indexes = {
                @Index(name = "idx_plant_english_name", columnList = "plant_english_name")
        }
)
@SQLDelete(sql = """
        UPDATE plant_info
        SET deleted = true,
            deleted_at = NOW(),
            updated_at = CURRENT_DATE
        WHERE plant_id = ?
          AND deleted = false
        """, verify = Expectation.RowCount.class)
@SQLRestriction("deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long plantId;

    @Column(unique = true, nullable = false)
    private String plantName;

    private String plantEnglishName;
    private String species; // 식물 분류
    private String season;

    @Column(name = "plant_image_file_id", nullable = false)
    private Long plantImageFileId;

    private LocalDate createdAt;
    private LocalDate updatedAt;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDate.now();
    }

    public void updatePlantInfo(
            String plantName, String plantEnglishName,
            String species, String season) {
        
        this.plantName = plantName;
        this.plantEnglishName = plantEnglishName;
        this.species = species;
        this.season = season;
    }

    public void updatePlantImage(Long plantImageFileId) {
        this.plantImageFileId = plantImageFileId;
    }
}

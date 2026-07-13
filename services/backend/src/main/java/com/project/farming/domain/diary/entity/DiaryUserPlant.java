package com.project.farming.domain.diary.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "diary_user_plant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_diary_user_plant_diary_user_plant",
                columnNames = {"diary_id", "user_plant_id"}
        ),
        indexes = {
                @Index(name = "idx_diary_user_plant_diary", columnList = "diary_id"),
                @Index(name = "idx_diary_user_plant_user_plant_diary", columnList = "user_plant_id, diary_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiaryUserPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long diaryUserPlantId;

    @Column(name = "diary_id", nullable = false)
    private Long diaryId;

    @Column(name = "user_plant_id", nullable = false)
    private Long userPlantId;

}

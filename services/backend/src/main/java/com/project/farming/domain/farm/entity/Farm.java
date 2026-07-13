package com.project.farming.domain.farm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "farm_info",
        indexes = {
                @Index(name = "idx_farm_location", columnList = "latitude, longitude"),
                @Index(name = "idx_farm_name", columnList = "farm_name"),
                @Index(name = "idx_farm_road_name_address", columnList = "road_name_address"),
                @Index(name = "idx_farm_lot_number_address", columnList = "lot_number_address")
        }
)
@SQLDelete(sql = """
        UPDATE farm_info
        SET deleted = true,
            deleted_at = NOW(),
            updated_at = CURRENT_DATE
        WHERE farm_id = ?
          AND deleted = false
        """, verify = Expectation.RowCount.class)
@SQLRestriction("deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Farm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long farmId;

    @Column(unique = true, nullable = false)
    private int gardenUniqueId; // 텃밭 고유번호

    private String operator; // 운영주체
    private String farmName; // 텃밭 이름
    private String roadNameAddress; // 도로명 주소

    @Column(nullable = false)
    private String lotNumberAddress; // 지번 주소

    private String facilities; // 부대시설
    private String contact; // 신청 방법
    private Double latitude; // 위도
    private Double longitude; // 경도

    @Column(nullable = false)
    private boolean available; // 운영 여부

    @Column(name = "farm_image_file_id", nullable = false)
    private Long farmImageFileId; // 텃밭 사진 이미지 ID

    private LocalDate createdAt;
    private LocalDate updatedAt;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDate.now();
        if (this.updatedAt == null) this.updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDate.now();
    }

    public void updateFarmInfo(
            Integer gardenUniqueId, String operator, String farmName,
            String roadNameAddress, String lotNumberAddress,
            String facilities, String contact,
            Double latitude, Double longitude, Boolean available) {

        this.gardenUniqueId = gardenUniqueId;
        this.operator = operator;
        this.farmName = farmName;
        this.roadNameAddress = roadNameAddress;
        this.lotNumberAddress = lotNumberAddress;
        this.facilities = facilities;
        this.contact = contact;
        this.latitude = latitude;
        this.longitude = longitude;
        this.available = available;
    }

    public void updateFarmImage(Long farmImageFileId) {
        this.farmImageFileId = farmImageFileId;
    }
}

package com.project.farming.domain.farm.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FarmResponse {
    private Long farmId;
    private int gardenUniqueId; // 텃밭 고유번호
    private String operator; // 운영주체
    private String farmName;
    private String roadNameAddress; // 도로명 주소
    private String lotNumberAddress; // 지번 주소
    private String facilities; // 부대시설
    private String contact; // 신청방법
    private Double latitude; // 위도
    private Double longitude; // 경도
    private Boolean available; // 운영 여부
    private LocalDate createdAt;
    private LocalDate updatedAt;
    private String farmImageUrl;
}

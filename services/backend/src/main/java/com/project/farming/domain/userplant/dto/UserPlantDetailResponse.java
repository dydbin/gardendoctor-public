package com.project.farming.domain.userplant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserPlantDetailResponse {
    private Long userPlantId;
    private String plantName;
    private String plantNickname;
    private String plantingPlace;
    private LocalDateTime plantedDate;
    private String notes;

    @JsonProperty("isNotificationEnabled")
    private boolean isNotificationEnabled;

    private int waterIntervalDays;
    private int pruneIntervalDays;
    private int fertilizeIntervalDays;
    private Boolean watered;
    private Boolean pruned;
    private Boolean fertilized;
    private String userPlantImageUrl;
    private String plantEnglishName;
    private String species;
    private String season;
    private String plantImageUrl;
}

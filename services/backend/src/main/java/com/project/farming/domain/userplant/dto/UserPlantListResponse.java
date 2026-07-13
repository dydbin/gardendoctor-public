package com.project.farming.domain.userplant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserPlantListResponse {
    private Long userPlantId;
    private String plantName;
    private String plantNickname;
    private String plantingPlace;

    @JsonProperty("isNotificationEnabled")
    private boolean isNotificationEnabled;

    private int waterIntervalDays;
    private int pruneIntervalDays;
    private int fertilizeIntervalDays;
    private String userPlantImageUrl;
}

package com.project.farming.domain.userplant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserPlantRequest {

    @NotBlank(message = "식물의 종류를 입력해주세요.")
    private String plantName;

    @NotBlank(message = "식물의 별명을 입력해주세요.")
    @Size(max = 20, message = "식물의 별명은 최대 20글자까지 입력할 수 있습니다.")
    private String plantNickname; // 등록된 식물, 직접 입력

    @NotNull(message = "식물을 심은 장소를 입력해주세요.")
    private Integer gardenUniqueId; // 텃밭 고유번호

    private String plantingPlace; // 등록된 텃밭, 직접 입력
    private LocalDateTime plantedDate;
    private String notes;

    @NotNull(message = "알림 수신 여부를 입력해주세요.")
    private Boolean isNotificationEnabled;
    
    @NotNull(message = "물 주는 간격(일 단위)를 입력해주세요.")
    @Min(value = 1, message = "물 주기 간격은 1일 이상이어야 합니다.")
    @Max(value = 365, message = "물 주기 간격은 365일 이하여야 합니다.")
    private Integer waterIntervalDays;

    @NotNull(message = "가지치기 간격(일 단위)를 입력해주세요.")
    @Min(value = 1, message = "가지치기 간격은 1일 이상이어야 합니다.")
    @Max(value = 365, message = "가지치기 간격은 365일 이하여야 합니다.")
    private Integer pruneIntervalDays;

    @NotNull(message = "영양제 주는 간격(일 단위)를 입력해주세요.")
    @Min(value = 1, message = "영양제 주기 간격은 1일 이상이어야 합니다.")
    @Max(value = 365, message = "영양제 주기 간격은 365일 이하여야 합니다.")
    private Integer fertilizeIntervalDays;

    @NotNull(message = "물 주기 여부를 입력해주세요.")
    private Boolean watered;

    @NotNull(message = "가지치기 여부를 입력해주세요.")
    private Boolean pruned;

    @NotNull(message = "영양제 주기 여부를 입력해주세요.")
    private Boolean fertilized;
}

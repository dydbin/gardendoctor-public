package com.project.farming.domain.plant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlantAdminRequest {

    @NotBlank(message = "식물의 이름을 입력해주세요.")
    private String plantName;

    private String plantEnglishName;
    private String species; // 식물 분류
    private String season;
}

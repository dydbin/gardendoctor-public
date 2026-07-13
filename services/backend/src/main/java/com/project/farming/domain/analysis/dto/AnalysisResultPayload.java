package com.project.farming.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultPayload {

    private String filename;
    private Double confidence; // 정확도
    private DiseaseInfo disease_info;  // 여기 이름이 JSON 필드명과 동일해야 함

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiseaseInfo {
        private String name;
        private String summary;
        private String solution;
    }
}
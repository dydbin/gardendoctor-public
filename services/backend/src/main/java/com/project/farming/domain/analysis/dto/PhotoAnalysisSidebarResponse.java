package com.project.farming.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoAnalysisSidebarResponse {
    private Long photoAnalysisId;
    private String createdDate;       // yyyy-MM-dd 형태
    private String detectedDisease;   // AI 모델에서 리턴
    private String analysisSummary;
    private String solution;
    private String imageUrl;
}

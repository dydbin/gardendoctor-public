package com.project.farming.domain.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "일지 응답")
public class DiaryResponse {
    @Schema(description = "일지 ID", example = "1")
    private Long diaryId;

    @Schema(description = "사용자 ID", example = "10")
    private Long userId;

    @Schema(description = "일지 제목", example = "우리 토마토 첫 열매 맺은 날!")
    private String title;

    @Schema(description = "일지 내용", example = "드디어 토마토에 작은 열매가 보이기 시작했어요. 너무 신기하네요.")
    private String content;

    @Schema(description = "일지 기록 날짜", example = "2024-08-01")
    private LocalDate diaryDate;

    @Schema(description = "이미지 URL", example = "https://your-s3-bucket/diary/image123.jpg")
    private String imageUrl;

    @Schema(description = "물주기 여부", example = "true")
    private boolean watered;

    @Schema(description = "가지치기 여부", example = "false")
    private boolean pruned;

    @Schema(description = "영양제 주기 여부", example = "true")
    private boolean fertilized;

    @Schema(description = "생성일", example = "2024-07-25T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일", example = "2024-07-25T11:30:00")
    private LocalDateTime updatedAt;

    @Schema(description = "연결된 사용자 식물 ID 목록", example = "[1, 3, 5]")
    private List<Long> connectedUserPlantIds;
}

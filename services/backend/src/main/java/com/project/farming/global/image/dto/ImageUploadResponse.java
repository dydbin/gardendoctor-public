package com.project.farming.global.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "이미지 업로드 응답")
public class ImageUploadResponse {
    @Schema(description = "업로드된 이미지 파일의 고유 ID", example = "1")
    private Long imageFileId;

    @Schema(description = "업로드된 이미지의 S3 URL", example = "https://your-bucket.s3.ap-northeast-2.amazonaws.com/uuid-image.jpg")
    private String imageUrl;

    @Schema(description = "응답 메시지", example = "이미지 업로드 성공")
    private String message;
}

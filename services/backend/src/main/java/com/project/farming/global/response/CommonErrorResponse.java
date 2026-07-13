package com.project.farming.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 오류 응답")
public record CommonErrorResponse(
        @Schema(description = "사용자에게 표시할 오류 메시지", example = "요청을 처리할 수 없습니다.")
        String message,
        @Schema(description = "클라이언트가 분기할 안정적인 오류 코드", example = "BAD_REQUEST")
        String errorCode,
        @Schema(description = "필드 검증 오류 등 선택적 상세 정보", nullable = true)
        Object data
) {
}

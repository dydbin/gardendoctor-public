package com.project.farming.domain.notification.dto;

import com.project.farming.domain.notification.command.NotificationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

//FCM 연동 및 테스트용 수동 생성 시 사용:
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "알림 생성 요청 (단일 또는 다중 사용자)")
public class NotificationRequest {

    @NotEmpty(message = "알림을 받을 사용자 ID는 필수 입력 사항입니다.")
    @Size(max = NotificationCommand.MAX_TARGET_USERS,
            message = "알림 대상 사용자는 한 번에 500명을 초과할 수 없습니다.")
    @Schema(description = "알림을 받을 사용자 ID 목록", example = "[1, 2, 3]")
    private List<@NotNull(message = "사용자 ID에는 null이 포함될 수 없습니다.")
            @Positive(message = "사용자 ID는 양수여야 합니다.") Long> userIds;

    @NotBlank(message = "알림 제목은 필수입니다.")
    @Size(max = 100, message = "알림 제목은 100자를 초과할 수 없습니다.")
    private String title;

    @NotBlank(message = "알림 내용은 필수입니다.")
    @Size(max = 500, message = "알림 내용은 500자를 초과할 수 없습니다.")
    private String message;
}

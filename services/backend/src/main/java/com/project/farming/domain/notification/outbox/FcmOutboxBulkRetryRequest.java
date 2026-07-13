package com.project.farming.domain.notification.outbox;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FcmOutboxBulkRetryRequest(
        @NotEmpty(message = "재시도할 FCM outbox ID는 필수입니다.")
        @Size(max = 500, message = "FCM outbox는 한 번에 500건까지 재시도할 수 있습니다.")
        List<@NotNull(message = "FCM outbox ID에는 null이 포함될 수 없습니다.")
                @Positive(message = "FCM outbox ID는 양수여야 합니다.") Long> fcmOutboxIds
) {
}

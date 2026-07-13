package com.project.farming.global.fcm;

public record FcmBatchResult(
        Long correlationId,
        boolean successful,
        boolean permanentFailure,
        String errorMessage
) {

    public static FcmBatchResult success(Long correlationId) {
        return new FcmBatchResult(correlationId, true, false, null);
    }

    public static FcmBatchResult failure(
            Long correlationId,
            boolean permanentFailure,
            String errorMessage) {
        return new FcmBatchResult(correlationId, false, permanentFailure, errorMessage);
    }
}

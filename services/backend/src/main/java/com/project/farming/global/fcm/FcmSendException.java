package com.project.farming.global.fcm;

public class FcmSendException extends RuntimeException {

    private final boolean permanentFailure;

    private FcmSendException(String message, boolean permanentFailure, Throwable cause) {
        super(message, cause);
        this.permanentFailure = permanentFailure;
    }

    public static FcmSendException permanent(String message, Throwable cause) {
        return new FcmSendException(message, true, cause);
    }

    public static FcmSendException retryable(String message, Throwable cause) {
        return new FcmSendException(message, false, cause);
    }

    public boolean isPermanentFailure() {
        return permanentFailure;
    }
}

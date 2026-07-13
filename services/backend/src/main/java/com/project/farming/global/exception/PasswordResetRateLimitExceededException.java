package com.project.farming.global.exception;

public class PasswordResetRateLimitExceededException extends RuntimeException {
    public PasswordResetRateLimitExceededException(String message) {
        super(message);
    }
}

package com.project.farming.global.exception;

public class PasswordResetUnavailableException extends RuntimeException {
    public PasswordResetUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

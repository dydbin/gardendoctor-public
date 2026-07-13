package com.project.farming.global.ai;

public class AiServiceTimeoutException extends RuntimeException {
    public AiServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

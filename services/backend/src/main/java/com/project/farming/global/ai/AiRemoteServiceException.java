package com.project.farming.global.ai;

public class AiRemoteServiceException extends RuntimeException {

    private final int statusCode;

    public AiRemoteServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

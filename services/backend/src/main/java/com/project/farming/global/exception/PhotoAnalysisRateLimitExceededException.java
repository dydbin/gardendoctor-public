package com.project.farming.global.exception;

public class PhotoAnalysisRateLimitExceededException extends RuntimeException {
    public PhotoAnalysisRateLimitExceededException(String message) {
        super(message);
    }
}

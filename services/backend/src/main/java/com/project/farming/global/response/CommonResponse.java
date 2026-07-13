package com.project.farming.global.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommonResponse<T> {
    private String message;
    private String errorCode;
    private T data;

    public static CommonResponse<Void> success(String message) {
        return CommonResponse.<Void>builder()
                .message(message)
                .build();
    }

    public static <T> CommonResponse<T> success(String message, T data) {
        return CommonResponse.<T>builder()
                .message(message)
                .data(data)
                .build();
    }

    public static <T> CommonResponse<T> error(String message, String errorCode) {
        return CommonResponse.<T>builder()
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    public static <T> CommonResponse<T> error(String message, String errorCode, T data) {
        return CommonResponse.<T>builder()
                .message(message)
                .errorCode(errorCode)
                .data(data)
                .build();
    }
}

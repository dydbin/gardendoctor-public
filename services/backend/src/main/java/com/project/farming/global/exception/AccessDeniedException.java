// src/main/java/com/project/farming/global/exception/AccessDeniedException.java
package com.project.farming.global.exception;

/**
 * 권한이 없을 때 (403) 발생시키는 예외
 * 비즈니스 로직 상의 접근 거부를 나타내는 커스텀 예외입니다.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}

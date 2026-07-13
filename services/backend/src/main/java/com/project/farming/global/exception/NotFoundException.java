// src/main/java/com/project/farming/global/exception/NotFoundException.java
package com.project.farming.global.exception;

/**
 * 리소스를 찾을 수 없을 때 (404) 발생시키는 예외
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

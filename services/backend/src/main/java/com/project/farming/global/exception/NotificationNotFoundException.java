// src/main/java/com/project/farming/global/exception/AccessDeniedException.java
package com.project.farming.global.exception;

/**
 * 알림이 없을 때 예외 발생.
 */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String message) {
        super(message);
    }
}

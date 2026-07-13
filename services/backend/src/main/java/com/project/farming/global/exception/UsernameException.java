// src/main/java/com/project/farming/exception/UsernameException.java
package com.project.farming.global.exception;

public class UsernameException extends RuntimeException {

    public UsernameException(String message) {
        super(message);
    }

    public UsernameException(String message, Throwable cause) {
        super(message, cause);
    }
}
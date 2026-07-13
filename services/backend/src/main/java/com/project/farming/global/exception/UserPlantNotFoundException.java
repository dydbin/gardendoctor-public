package com.project.farming.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UserPlantNotFoundException extends RuntimeException {
    public UserPlantNotFoundException(String message) {
        super(message);
    }
}

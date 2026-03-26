package com.rmsys.backend.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final String errorCode;

    public AppException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static AppException notFound(String entity, Object id) {
        return new AppException(entity.toUpperCase() + "_NOT_FOUND", entity + " not found: " + id);
    }
}


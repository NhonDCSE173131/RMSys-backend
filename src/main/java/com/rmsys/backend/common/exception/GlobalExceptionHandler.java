package com.rmsys.backend.common.exception;

import com.rmsys.backend.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var fieldErrors = new LinkedHashMap<String, String>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Validation failed")
                .errorCode("VALIDATION_ERROR")
                .data(fieldErrors)
                .timestamp(java.time.Instant.now())
                .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "VALIDATION_ERROR"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON payload: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("Malformed JSON request", "MALFORMED_JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        log.warn("Type mismatch: {} ({})", message, ex.getValue());
        return ResponseEntity.badRequest().body(ApiResponse.error(message, "INVALID_PARAMETER_TYPE"));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        log.warn("App error: {} - {}", ex.getErrorCode(), ex.getMessage());
        var status = ex.getErrorCode().contains("NOT_FOUND")
                ? HttpStatus.NOT_FOUND
                : ex.getErrorCode().contains("UNAUTHORIZED")
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        if (isClientAbort(ex)) {
            log.debug("Client disconnected while response was being written: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", "INTERNAL_ERROR"));
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                String message = current.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (lower.contains("broken pipe")
                            || lower.contains("connection reset")
                            || lower.contains("connection aborted")
                            || lower.contains("was aborted")
                            || lower.contains("forcibly closed")) {
                        return true;
                    }
                }
            }

            String className = current.getClass().getName();
            if (className.contains("ClientAbortException") || className.contains("AsyncRequestNotUsableException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}


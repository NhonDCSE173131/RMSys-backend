package com.rmsys.backend.common.exception;

import com.rmsys.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        if (wantsEventStream(request)) {
            return ResponseEntity.badRequest().<ApiResponse<Map<String, String>>>build();
        }

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
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        if (wantsEventStream(request)) {
            return ResponseEntity.badRequest().<ApiResponse<Void>>build();
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "VALIDATION_ERROR"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON payload: {}", ex.getMessage());
        if (wantsEventStream(request)) {
            return ResponseEntity.badRequest().<ApiResponse<Void>>build();
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Malformed JSON request", "MALFORMED_JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        log.warn("Type mismatch: {} ({})", message, ex.getValue());
        if (wantsEventStream(request)) {
            return ResponseEntity.badRequest().<ApiResponse<Void>>build();
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(message, "INVALID_PARAMETER_TYPE"));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex, HttpServletRequest request) {
        log.warn("App error: {} - {}", ex.getErrorCode(), ex.getMessage());
        var status = ex.getErrorCode().contains("NOT_FOUND")
                ? HttpStatus.NOT_FOUND
                : ex.getErrorCode().contains("UNAUTHORIZED")
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
        if (wantsEventStream(request)) {
            return ResponseEntity.status(status).<ApiResponse<Void>>build();
        }
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletResponse response) {
        // SSE connection timed out – this is normal. The async response is already
        // closed so we must NOT attempt to write a body; just log at DEBUG level.
        log.debug("SSE async timeout (normal behavior – client will reconnect): {}", ex.getMessage());
        // Set a 503 status only if the response is still writable
        if (response != null && !response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        // Explicit early-exit for async timeouts – must be FIRST, before any helper call,
        // because isAsyncTimeout() may miss the exception in certain classloader scenarios.
        if (ex instanceof AsyncRequestTimeoutException
                || ex.getClass().getName().contains("AsyncRequestTimeoutException")) {
            log.debug("SSE async timeout caught in handleGeneric fallback – suppressed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<ApiResponse<Void>>build();
        }
        if (isClientAbort(ex) || isAsyncTimeout(ex)) {
            log.debug("Ignored async disconnect/timeout while writing response: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unexpected error", ex);
        if (wantsEventStream(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<ApiResponse<Void>>build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", "INTERNAL_ERROR"));
    }

    private boolean wantsEventStream(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }

    private boolean isAsyncTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AsyncRequestTimeoutException
                    || current.getClass().getName().contains("AsyncRequestTimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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


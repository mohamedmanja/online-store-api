package com.harmony.store.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorBody(int statusCode, String message) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorBody(ex.getStatusCode().value(), ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(Map.of("statusCode", 400, "message", "Validation failed", "errors", errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody(403, "Forbidden"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorBody> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody(401, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody(500, "Internal server error"));
    }
}

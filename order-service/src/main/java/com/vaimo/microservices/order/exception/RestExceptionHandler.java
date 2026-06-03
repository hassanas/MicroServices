package com.vaimo.microservices.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            MethodArgumentNotValidException methodArgumentNotValidException,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = methodArgumentNotValidException.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage()
                ))
                .toList();

        ValidationErrorResponse response = new ValidationErrorResponse(
                Instant.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    public record ValidationErrorResponse(
            String timestamp,
            int status,
            String error,
            String message,
            String path,
            List<FieldValidationError> fieldErrors
    ) {
    }

    public record FieldValidationError(
            String field,
            String message
    ) {
    }
}


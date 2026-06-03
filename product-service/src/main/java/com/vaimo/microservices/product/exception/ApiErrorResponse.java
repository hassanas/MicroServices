package com.vaimo.microservices.product.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(name = "ApiErrorResponse", description = "Standard error payload returned by the API")
public record ApiErrorResponse(
        @Schema(description = "Error timestamp in UTC", example = "2026-05-25T17:00:00Z")
        Instant timestamp,
        @Schema(description = "HTTP status code", example = "400")
        int status,
        @Schema(description = "HTTP status reason", example = "Bad Request")
        String error,
        @Schema(description = "High-level error message", example = "Validation failed")
        String message,
        @Schema(description = "Request path that triggered the error", example = "/api/product")
        String path,
        @Schema(description = "Per-field validation messages when status is 400")
        Map<String, String> fieldErrors) {
}


package com.vaimo.microservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryRequest(
        @NotBlank(message = "productId is required")
        @Schema(description = "MongoDB product ID (24-char hex string)", example = "507f1f77bcf86cd799439011", requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @NotNull(message = "quantity is required")
        @Min(value = 0, message = "quantity must be at least 0")
        @Schema(description = "Stock quantity", example = "100", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity
) {}


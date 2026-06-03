package com.vaimo.microservices.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ProductRequest", description = "Request payload used to create a product")
public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        @Schema(description = "Product display name", example = "IPhone 18 Pro", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        @Schema(description = "Short marketing description", example = "Flagship device with improved camera", requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @NotBlank(message = "sku is required")
        @Size(max = 64, message = "sku must be at most 64 characters")
        @Schema(description = "Stock keeping unit", example = "IP18PRO-256", requiredMode = Schema.RequiredMode.REQUIRED)
        String sku,
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        @Schema(description = "Product price", example = "5699.99", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price) {
}

package com.vaimo.microservices.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@JsonDeserialize(using = OrderRequestDeserializer.class)
@Schema(description = "Order creation request payload")
public record OrderRequest(
        @Schema(hidden = true)
        Long id,

        @Schema(hidden = true)
        String orderNumber,

        @NotBlank(message = "productId is required")
        @Pattern(regexp = "^[a-fA-F0-9]{24}$", message = "productId must be a 24-character hex string")
        @Schema(description = "MongoDB product ID (24-char hex string)", example = "507f1f77bcf86cd799439011", requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        @Schema(description = "Product price", example = "99.99", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price,

        @NotBlank(message = "sku is required")
        @Size(max = 100, message = "sku must be at most 100 characters")
        @Schema(description = "Stock keeping unit", example = "SKU-12345", requiredMode = Schema.RequiredMode.REQUIRED)
        String sku,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Schema(description = "Order quantity", example = "5", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must be at most 254 characters")
        @Schema(description = "Customer email address", example = "customer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email
) {
}
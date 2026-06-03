package com.vaimo.microservices.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Order response payload")
public record OrderResponse(
        @Schema(description = "Order ID", example = "1")
        Long id,
        @Schema(description = "Unique order number", example = "ORD-20260525-001")
        String orderNumber,
        @Schema(description = "MongoDB product ID", example = "507f1f77bcf86cd799439011")
        String productId,
        @Schema(description = "Product price at order time", example = "99.99")
        BigDecimal price,
        @Schema(description = "Stock keeping unit", example = "SKU-12345")
        String sku,
        @Schema(description = "Ordered quantity", example = "5")
        Integer quantity
) {
}


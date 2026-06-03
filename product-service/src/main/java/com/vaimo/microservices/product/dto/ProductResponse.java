package com.vaimo.microservices.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "ProductResponse", description = "Product payload returned by the API")
public record ProductResponse(
        @Schema(description = "Unique product identifier", example = "66520a2e4593322f9548ea3f")
        String id,
        @Schema(description = "Product display name", example = "IPhone 18 Pro")
        String name,
        @Schema(description = "Short marketing description", example = "Flagship device with improved camera")
        String description,
        @Schema(description = "Stock keeping unit", example = "IP18PRO-256")
        String sku,
        @Schema(description = "Product price", example = "5699.99")
        BigDecimal price) {
}

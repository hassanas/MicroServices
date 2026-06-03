package com.vaimo.microservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Inventory response payload")
public record InventoryResponse(
        @Schema(description = "Inventory record ID", example = "1")
        Long id,
        @Schema(description = "MongoDB product ID", example = "507f1f77bcf86cd799439011")
        String productId,
        @Schema(description = "Current stock quantity", example = "100")
        Integer quantity,
        @Schema(description = "Record creation timestamp", example = "2026-05-28T10:30:00")
        LocalDateTime createdAt,
        @Schema(description = "Last update timestamp", example = "2026-05-28T15:45:30")
        LocalDateTime updatedAt
) {}


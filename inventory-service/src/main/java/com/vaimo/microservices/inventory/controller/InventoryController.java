package com.vaimo.microservices.inventory.controller;

import com.vaimo.microservices.inventory.dto.InventoryRequest;
import com.vaimo.microservices.inventory.dto.InventoryResponse;
import com.vaimo.microservices.inventory.exception.ApiErrorResponse;
import com.vaimo.microservices.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory", description = "Endpoints for managing product inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Check if product is in stock
     * POST /api/inventory
     * Request body: { "productId": "...", "quantity": 10 }
     */
    @PostMapping
    @Operation(
            summary = "Check product availability",
            description = "Check if a product is in stock with the required quantity"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Product is in stock",
                    content = @Content(mediaType = "application/json", schema = @Schema(
                            example = "{\"productId\": \"507f1f77bcf86cd799439011\", \"requiredQuantity\": 10, \"inStock\": true}"
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not in stock or insufficient quantity",
                    content = @Content(mediaType = "application/json", schema = @Schema(
                            example = "{\"productId\": \"507f1f77bcf86cd799439011\", \"requiredQuantity\": 10, \"inStock\": false}"
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Map<String, Object>> checkInventory(@RequestBody InventoryRequest request) {
        log.info("Received inventory check request for product: {}, quantity: {}",
                request.productId(), request.quantity());

        boolean inStock = inventoryService.isInStock(request.productId(), request.quantity());

        Map<String, Object> response = new HashMap<>();
        response.put("productId", request.productId());
        response.put("requiredQuantity", request.quantity());
        response.put("inStock", inStock);

        HttpStatus status = inStock ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return new ResponseEntity<>(response, status);
    }

    /**
     * Get inventory details by product ID
     * GET /api/inventory/{productId}
     */
    @GetMapping("/{productId}")
    @Operation(
            summary = "Get inventory details",
            description = "Retrieve detailed inventory information for a specific product"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Inventory details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product inventory not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String productId) {
        log.info("Retrieving inventory for product: {}", productId);

        return inventoryService.getInventoryByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Save or update inventory
     * PUT /api/inventory
     * Request body: { "productId": "...", "quantity": 100 }
     */
    @PutMapping
    @Operation(
            summary = "Update inventory level",
            description = "Create or update inventory record for a product with new quantity"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Inventory updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid inventory request or validation failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<InventoryResponse> updateInventory(@RequestBody InventoryRequest request) {
        log.info("Updating inventory for product: {}, quantity: {}",
                request.productId(), request.quantity());

        InventoryResponse response = inventoryService.saveInventory(request.productId(), request.quantity());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

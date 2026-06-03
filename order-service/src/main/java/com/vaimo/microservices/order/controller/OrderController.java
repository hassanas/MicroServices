package com.vaimo.microservices.order.controller;


import com.vaimo.microservices.order.dto.OrderRequest;
import com.vaimo.microservices.order.dto.OrderResponse;
import com.vaimo.microservices.order.exception.ApiErrorResponse;
import com.vaimo.microservices.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for managing customer orders")
public class OrderController {

    private final OrderService OrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Place a new order",
            description = "Create a new customer order with product details and quantity."
                    + " Before persisting the order, this operation performs an internal gRPC stock check"
                    + " against inventory-service.",
            externalDocs = @ExternalDocumentation(
                    description = "Internal dependency: inventory gRPC stock check",
                    url = "http://localhost:9000/v3/api-docs/inventory"
            ),
            extensions = {
                    @Extension(name = "x-inter-service-calls", properties = {
                            @ExtensionProperty(name = "protocol", value = "gRPC"),
                            @ExtensionProperty(name = "targetService", value = "inventory-service"),
                            @ExtensionProperty(name = "grpcMethod", value = "InventoryStockService.CheckStock"),
                            @ExtensionProperty(name = "transport", value = "inventory.grpc.host:inventory.grpc.port")
                    })
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Order created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order request or validation failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public OrderResponse placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return OrderService.placeOrder(orderRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "List all orders",
            description = "Retrieve a list of all orders in the system"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Orders retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public List<OrderResponse> listOrders() {
        return OrderService.listOrders();
    }



}

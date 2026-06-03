package com.vaimo.microservices.gateway.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GatewayFallbackController {

    @GetMapping(path = "/fallback/product", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> productFallback() {
        return fallbackResponse("product-service");
    }

    @GetMapping(path = "/fallback/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> ordersFallback() {
        return fallbackResponse("order-service");
    }

    @GetMapping(path = "/fallback/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> inventoryFallback() {
        return fallbackResponse("inventory-service");
    }

    private ResponseEntity<Map<String, String>> fallbackResponse(String serviceName) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Service temporarily unavailable",
                "service", serviceName,
                "message", "Please try again shortly"
            ));
    }
}


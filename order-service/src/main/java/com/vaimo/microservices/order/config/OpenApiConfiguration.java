package com.vaimo.microservices.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI openAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("REST API for managing customer orders and order fulfillment")
                        .version("v1")
                        .contact(new Contact()
                                .name("Vaimo Microservices")
                                .url("https://www.vaimo.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:9000")
                                .description("API Gateway"),
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local environment")));

        openAPI.addExtension("x-inter-service-calls", List.of(
                Map.of(
                        "sourceOperation", "POST /api/orders",
                        "targetService", "inventory-service",
                        "protocol", "gRPC",
                        "targetMethod", "InventoryStockService.CheckStock",
                        "transport", "inventory.grpc.host:inventory.grpc.port"
                )
        ));

        return openAPI;
    }
}


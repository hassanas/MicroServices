package com.vaimo.microservices.product.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI productServiceOpenApi() {
        Schema<?> apiErrorSchemaRef = new Schema<>().$ref("#/components/schemas/ApiErrorResponse");

        Components components = new Components()
                .addResponses("BadRequest", new ApiResponse()
                        .description("Invalid request payload")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(apiErrorSchemaRef))))
                .addResponses("InternalServerError", new ApiResponse()
                        .description("Unexpected server error")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(apiErrorSchemaRef))));

        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("REST API for creating and retrieving product catalog data")
                        .version("v1")
                        .contact(new Contact().name("Product Service Team").email("product-team@vaimo.com"))
                        .license(new License().name("Internal Use")))
                .servers(List.of(
                        new Server().url("http://localhost:9000").description("API Gateway"),
                        new Server().url("http://localhost:8080").description("Local environment")
                ))
                .components(components);
    }
}


package com.vaimo.microservices.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ProductServiceApplicationTests {

    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.5");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    static {
        mongoDBContainer.start();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        String requestBody = """
                {
                    "name": "Test Product",
                    "description": "This is a test product",
                    "sku": "TEST123",
                    "price": 19.99
                }
                """;
        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.sku").value("TEST123"));
    }

    @Test
    void shouldRejectInvalidProductPayload() throws Exception {
        String invalidRequestBody = """
                {
                    "name": "",
                    "description": "",
                    "sku": "",
                    "price": 0
                }
                """;

        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sku").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.price").isNotEmpty());
    }


    @Test
    void shouldGetAllProducts() throws Exception {
        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldExposeOpenApiContractWithReusableResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/product'].post.operationId").value("createProduct"))
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.components.responses.BadRequest.description").value("Invalid request payload"))
                .andExpect(jsonPath("$.components.responses.InternalServerError.description").value("Unexpected server error"));
    }
}
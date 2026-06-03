package com.vaimo.microservices.order.controller;

import com.vaimo.microservices.order.client.InventoryGrpcClient;
import com.vaimo.microservices.order.TestcontainersConfiguration;
import com.vaimo.microservices.order.dto.OrderResponse;
import com.vaimo.microservices.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private InventoryGrpcClient inventoryGrpcClient;

    @BeforeEach
    void cleanDb() {
        orderRepository.deleteAll();
        when(inventoryGrpcClient.isInStock(anyString(), anyInt())).thenReturn(true);
    }

    @Test
    void shouldCreateOrderAndListOrdersWithSameResponseShape() {
        String createRequest = """
                {
                  "productId": "6994fe07492f5def1d8865cd",
                  "price": 29.99,
                  "sku": "iphone_15",
                  "quantity": 2
                }
                """;
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "http://localhost:" + port + "/api/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(createRequest, headers);

        ResponseEntity<OrderResponse> createdResponse = restTemplate.postForEntity(baseUrl, requestEntity, OrderResponse.class);
        assertEquals(HttpStatus.CREATED, createdResponse.getStatusCode());
        assertNotNull(createdResponse.getBody());
        assertNotNull(createdResponse.getBody().id());
        assertNotNull(createdResponse.getBody().orderNumber());
        assertFalse(createdResponse.getBody().orderNumber().isBlank());
        assertEquals("6994fe07492f5def1d8865cd", createdResponse.getBody().productId());
        assertEquals("iphone_15", createdResponse.getBody().sku());
        assertEquals(2, createdResponse.getBody().quantity());

        ResponseEntity<List<OrderResponse>> listResponse = restTemplate.exchange(
                baseUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertFalse(listResponse.getBody().isEmpty());
        OrderResponse firstOrder = listResponse.getBody().getFirst();
        assertNotNull(firstOrder.id());
        assertNotNull(firstOrder.orderNumber());
        assertFalse(firstOrder.orderNumber().isBlank());
        assertEquals("6994fe07492f5def1d8865cd", firstOrder.productId());
        assertEquals("iphone_15", firstOrder.sku());
        assertEquals(2, firstOrder.quantity());
    }

    @Test
    void shouldRejectOrderWhenProductIdIsNot24HexCharacters() {
        String invalidRequest = """
                {
                  "productId": "6994",
                  "price": 29.99,
                  "sku": "iphone_15",
                  "quantity": 2
                }
                """;

        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "http://localhost:" + port + "/api/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(invalidRequest, headers);

        HttpClientErrorException.BadRequest exception = assertThrows(
                HttpClientErrorException.BadRequest.class,
                () -> restTemplate.postForEntity(baseUrl, requestEntity, String.class)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getResponseBodyAsString().contains("\"message\":\"Validation failed\""));
        assertTrue(exception.getResponseBodyAsString().contains("\"field\":\"productId\""));
        assertTrue(exception.getResponseBodyAsString().contains("24-character hex string"));
    }
}


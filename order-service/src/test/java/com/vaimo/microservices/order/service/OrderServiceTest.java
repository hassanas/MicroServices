package com.vaimo.microservices.order.service;

import com.vaimo.microservices.order.client.InventoryGrpcClient;
import com.vaimo.microservices.order.dto.OrderRequest;
import com.vaimo.microservices.order.dto.OrderResponse;
import com.vaimo.microservices.order.event.OrderPlacedEvent; // Added import
import com.vaimo.microservices.order.modal.Order;
import com.vaimo.microservices.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock // Matched exact types from your service definition
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldPlaceOrder() {
        // Arrange: prepare input request
        OrderRequest request = new OrderRequest(
                null,
                null,
                "101",
                new BigDecimal("29.99"),
                "iphone_15",
                2,
                "customer@example.com"
        );

        when(inventoryGrpcClient.isInStock(eq("101"), eq(2))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: call method under test
        OrderResponse response = orderService.placeOrder(request);

        // Assert: capture what was sent to repository.save(...)
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());

        Order savedOrder = captor.getValue();
        assertNotNull(response);
        assertNotNull(savedOrder.getOrderNumber());
        assertFalse(savedOrder.getOrderNumber().isBlank());
        assertEquals("101", savedOrder.getProductId());
        assertEquals(new BigDecimal("29.99"), savedOrder.getPrice());
        assertEquals("iphone_15", savedOrder.getSku());
        assertEquals(2, savedOrder.getQuantity());
        assertEquals("customer@example.com", savedOrder.getEmail());

        // Assert Kafka interaction: matches topic "order-place" and tracks event object
        verify(kafkaTemplate).send(eq("order-place"), any(OrderPlacedEvent.class));
    }

    @Test
    void shouldRejectOrderWhenOutOfStock() {
        OrderRequest request = new OrderRequest(
                null,
                null,
                "101",
                new BigDecimal("29.99"),
                "iphone_15",
                2,
                "customer@example.com"
        );

        when(inventoryGrpcClient.isInStock(eq("101"), eq(2))).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> orderService.placeOrder(request));
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaTemplate, never()).send(any(), any());
    }
}

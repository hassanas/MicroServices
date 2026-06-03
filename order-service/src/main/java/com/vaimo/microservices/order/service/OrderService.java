package com.vaimo.microservices.order.service;

import com.vaimo.microservices.order.client.InventoryGrpcClient;
import com.vaimo.microservices.order.dto.OrderRequest;
import com.vaimo.microservices.order.dto.OrderResponse;
import com.vaimo.microservices.order.event.OrderPlacedEvent;
import com.vaimo.microservices.order.modal.Order;
import com.vaimo.microservices.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;


    public OrderResponse placeOrder(OrderRequest orderRequest) {
        boolean inStock = inventoryGrpcClient.isInStock(orderRequest.productId(), orderRequest.quantity());
        if (!inStock) {
            throw new ResponseStatusException(CONFLICT, "Requested product quantity not available");
        }

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setProductId(orderRequest.productId());
        order.setPrice(orderRequest.price());
        order.setSku(orderRequest.sku());
        order.setQuantity(orderRequest.quantity());
        order.setEmail(orderRequest.email());
        Order savedOrder = orderRepository.save(order);
        // send message to kafka topic
        OrderPlacedEvent  orderPlacedEvent = new OrderPlacedEvent(order.getOrderNumber(), order.getEmail());
        // Corrected log statement with argument
        log.info("Start - sending OrderPlacement {} to kafka", orderPlacedEvent);
        kafkaTemplate.send("order-placed", orderPlacedEvent);
        log.info("End - sending OrderPlacement {} to kafka", orderPlacedEvent);



        return mapToOrderResponse(savedOrder);
    }

    public List<OrderResponse> listOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProductId(),
                order.getPrice(),
                order.getSku(),
                order.getQuantity()
        );
    }
}

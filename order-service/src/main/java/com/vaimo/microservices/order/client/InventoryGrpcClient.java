package com.vaimo.microservices.order.client;

import com.vaimo.microservices.order.grpc.InventoryStockServiceGrpc;
import com.vaimo.microservices.order.grpc.StockRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class InventoryGrpcClient {

    @Value("${inventory.grpc.host:localhost}")
    private String inventoryGrpcHost;

    @Value("${inventory.grpc.port:9095}")
    private int inventoryGrpcPort;

    private ManagedChannel channel;
    private InventoryStockServiceGrpc.InventoryStockServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(inventoryGrpcHost, inventoryGrpcPort)
                .usePlaintext()
                .build();
        blockingStub = InventoryStockServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isInStock(String productId, Integer quantity) {
        try {
            StockRequest request = StockRequest.newBuilder()
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .build();
            return blockingStub.checkStock(request).getInStock();
        } catch (StatusRuntimeException statusRuntimeException) {
            log.error("gRPC inventory check failed for productId={}, quantity={}", productId, quantity, statusRuntimeException);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable");
        }
    }
}


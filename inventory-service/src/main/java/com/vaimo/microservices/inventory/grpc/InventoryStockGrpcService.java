package com.vaimo.microservices.inventory.grpc;

import com.vaimo.microservices.inventory.service.InventoryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryStockGrpcService extends InventoryStockServiceGrpc.InventoryStockServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void checkStock(StockRequest request, StreamObserver<StockResponse> responseObserver) {
        log.info("gRPC CheckStock request received for productId={}, quantity={}", request.getProductId(), request.getQuantity());

        if (request.getQuantity() < 1) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("quantity must be greater than zero")
                            .asRuntimeException()
            );
            return;
        }

        boolean inStock = inventoryService.isInStock(request.getProductId(), request.getQuantity());

        StockResponse response = StockResponse.newBuilder()
                .setInStock(inStock)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}


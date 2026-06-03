package com.vaimo.microservices.inventory.config;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.vaimo.microservices.inventory.grpc.InventoryStockGrpcService;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GrpcServerConfiguration {

    private final InventoryStockGrpcService inventoryStockGrpcService;

    @Value("${grpc.server.port:9095}")
    private int grpcServerPort;

    private Server server;

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder.forPort(grpcServerPort)
                .addService(inventoryStockGrpcService)
                .build()
                .start();

        log.info("gRPC server started on port {}", grpcServerPort);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("Shutting down gRPC server on port {}", grpcServerPort);
            server.shutdown();
        }
    }
}


package com.orderproduct.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.orderproduct.inventoryservice.grpc.ReservationServiceGrpc;
import com.orderproduct.orderservice.service.InventoryReservationGrpcClientService;
import com.orderproduct.orderservice.service.InventoryReservationHttpService;
import com.orderproduct.orderservice.service.InventoryReservationService;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Slf4j
@Configuration
public class InventoryReservationConfig {

    @Bean
    @ConditionalOnProperty(name = "inventory.reservation.use-grpc", havingValue = "false", matchIfMissing = true)
    public InventoryReservationService httpInventoryReservationService(
            WebClient.Builder webClientBuilder,
            @Value("${inventory.api.base-url}") String inventoryApiBaseUrl) {
        log.info("Configuring HTTP-based inventory reservation service");
        return new InventoryReservationHttpService(webClientBuilder, inventoryApiBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "inventory.reservation.use-grpc", havingValue = "true")
    public InventoryReservationService grpcInventoryReservationService(
            @GrpcClient("inventory-reservation") ReservationServiceGrpc.ReservationServiceBlockingStub reservationServiceStub) {
        log.info("Configuring gRPC-based inventory reservation service");
        return new InventoryReservationGrpcClientService(reservationServiceStub);
    }
}

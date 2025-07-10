package com.orderproduct.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.orderproduct.orderservice.service.InventoryStatusService;

@Configuration
public class InventoryStatusServiceConfig {

    @Value("${inventory.api.base-url}")
    private String inventoryApiBaseUrl;

    @Bean
    public InventoryStatusService inventoryStatusService(WebClient.Builder webClientBuilder) {
        return new InventoryStatusService(webClientBuilder, inventoryApiBaseUrl);
    }
}

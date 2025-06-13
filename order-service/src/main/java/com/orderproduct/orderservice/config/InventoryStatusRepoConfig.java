package com.orderproduct.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.orderproduct.orderservice.repository.InventoryStatusRepository;

@Configuration
public class InventoryStatusRepoConfig {

    @Value("${inventory.api.base-url}")
    private String inventoryApiBaseUrl;

    @Bean
    public InventoryStatusRepository inventoryStatusRepository(WebClient.Builder webClientBuilder) {
        return new InventoryStatusRepository(webClientBuilder, inventoryApiBaseUrl);
    }
}

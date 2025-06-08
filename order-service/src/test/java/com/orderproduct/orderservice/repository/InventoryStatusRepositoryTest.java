package com.orderproduct.orderservice.repository;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InventoryStatusRepositoryTest {

    private InventoryStatusRepository inventoryStatusRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static MockWebServer mockWebServer;

    @BeforeEach
    void initialize() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        inventoryStatusRepository = new InventoryStatusRepository(
                WebClient.builder(),
                mockWebServer.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("`getInventoryAvailabilityFuture()` fetches list of `InventoryStockStatus` from inventory-service")
    public void getInventoryAvailabilityFutureTest() throws InternalServerException, JsonProcessingException, ExecutionException, InterruptedException {
        // Initialise
        final var mockStatuses = List.of(
                new InventoryStockStatus("sku1", true),
                new InventoryStockStatus("sku2", false)
        );
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockStatuses))
                .addHeader("Content-Type", "application/json"));

        // Call method
        final var responseStatuses = inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("sku1", "sku2")).get();

        // Assert value
        assertEquals(mockStatuses, responseStatuses);
    }

}

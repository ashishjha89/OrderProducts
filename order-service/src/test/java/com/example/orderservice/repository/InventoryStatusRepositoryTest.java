package com.example.orderservice.repository;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.dto.InventoryStockStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InventoryStatusRepositoryTest {

    private InventoryStatusRepository inventoryStatusRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static MockWebServer mockBackEnd;

    @BeforeAll
    static void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start(InventoryStatusRepository.PORT);
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @BeforeEach
    void initialize() {
        String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
        inventoryStatusRepository = new InventoryStatusRepository(WebClient.create(baseUrl));
    }

    @Test
    @DisplayName("retrieveStocksStatus() fetches InventoryStockStatus[] from inventory-service for passed sku-codes")
    public void retrieveStocksStatusTest() throws InternalServerException, JsonProcessingException {
        // Initialise
        final var mockStatuses = List.of(
                new InventoryStockStatus("sku1", true),
                new InventoryStockStatus("sku2", false)
        );
        mockBackEnd.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockStatuses))
                .addHeader("Content-Type", "application/json"));

        // Call method
        final var responseStatuses = inventoryStatusRepository.retrieveStocksStatus(List.of("sku1", "sku2"));

        // Assert value
        assertEquals(mockStatuses, responseStatuses);

    }

}

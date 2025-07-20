package com.orderproduct.orderservice.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.service.OrderService;

class OrderControllerTest {

    private WebTestClient webTestClient;
    private OrderService orderService;
    private ObjectMapper objectMapper;
    private static final String TEST_CONTENT = """
            {
                "orderLineItemsList": [
                    {
                        "skuCode": "skuCode",
                        "price": 1200,
                        "quantity": 10
                    }
                ]
            }
            """;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        OrderController orderController = new OrderController(orderService);

        webTestClient = WebTestClient.bindToController(orderController)
                .controllerAdvice(new ControllerExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("should return 201 when POST /api/order is called with valid request")
    void placeOrder_ValidRequest_Returns201() throws Exception {
        // Given
        OrderRequest orderRequest = objectMapper.readValue(TEST_CONTENT, OrderRequest.class);
        SavedOrder savedOrder = new SavedOrder("orderId", "orderNumber");
        when(orderService.placeOrder(orderRequest))
                .thenReturn(CompletableFuture.completedFuture(savedOrder));

        // When & Then
        webTestClient.post()
                .uri("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(TEST_CONTENT)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo("orderId")
                .jsonPath("$.orderNumber").isEqualTo("orderNumber");
    }

    @Test
    @DisplayName("should return 500 when POST /api/order fails due to internal error")
    void placeOrder_InternalError_Returns500() throws Exception {
        // Given
        OrderRequest orderRequest = objectMapper.readValue(TEST_CONTENT, OrderRequest.class);
        when(orderService.placeOrder(orderRequest))
                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));

        // When & Then
        webTestClient.post()
                .uri("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(TEST_CONTENT)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("SOMETHING_WENT_WRONG");
    }

    @Test
    @DisplayName("should return 409 when POST /api/order fails due to out of stock")
    void placeOrder_OutOfStock_Returns409() throws Exception {
        // Given
        OrderRequest orderRequest = objectMapper.readValue(TEST_CONTENT, OrderRequest.class);
        when(orderService.placeOrder(orderRequest))
                .thenReturn(CompletableFuture.failedFuture(new InventoryNotInStockException()));

        // When & Then
        webTestClient.post()
                .uri("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(TEST_CONTENT)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVENTORY_NOT_IN_STOCK");
    }

    @Test
    @DisplayName("should return 400 when POST /api/order is called with invalid request")
    void placeOrder_InvalidRequest_Returns400() throws Exception {
        // When & Then
        webTestClient.post()
                .uri("/api/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "orderLineItemsList": []
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST");
    }
}

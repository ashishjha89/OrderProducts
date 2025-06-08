package com.orderproduct.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {

    private MockMvc mockMvc;
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

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new ControllerExceptionHandler())
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

        // When
        MvcResult mvcResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEST_CONTENT))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value("orderId"))
                .andExpect(jsonPath("$.orderNumber").value("orderNumber"));
    }

    @Test
    @DisplayName("should return 500 when POST /api/order fails due to internal error")
    void placeOrder_InternalError_Returns500() throws Exception {
        // Given
        OrderRequest orderRequest = objectMapper.readValue(TEST_CONTENT, OrderRequest.class);
        when(orderService.placeOrder(orderRequest))
                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));

        // When
        MvcResult mvcResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEST_CONTENT))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("SOMETHING_WENT_WRONG"))
                .andExpect(jsonPath("$.errorMessage").value("Sorry, something went wrong."));
    }

    @Test
    @DisplayName("should return 400 when POST /api/order fails due to out of stock")
    void placeOrder_OutOfStock_Returns400() throws Exception {
        // Given
        OrderRequest orderRequest = objectMapper.readValue(TEST_CONTENT, OrderRequest.class);
        when(orderService.placeOrder(orderRequest))
                .thenReturn(CompletableFuture.failedFuture(new InventoryNotInStockException()));

        // When
        MvcResult mvcResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEST_CONTENT))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_NOT_IN_STOCK"))
                .andExpect(jsonPath("$.errorMessage").value("This product is not in stock."));
    }

    @Test
    @DisplayName("should return 400 when POST /api/order is called with invalid request")
    void placeOrder_InvalidRequest_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderLineItemsList": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value("This is an incorrect request-body."));
    }
}

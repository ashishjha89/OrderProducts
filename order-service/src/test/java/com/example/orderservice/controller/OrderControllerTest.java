package com.example.orderservice.controller;

import com.example.orderservice.common.BadRequestException;
import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.common.InventoryNotInStockException;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);

    private final OrderController orderController = new OrderController(orderService);

    @Test
    @DisplayName("placeOrder() calls OrderService.placeOrder() and retrieves `SavedOrder` from it")
    public void placeOrderTest() throws InternalServerException, BadRequestException, InventoryNotInStockException {
        // Initialise
        final var orderRequest = new OrderRequest(
                List.of(new OrderLineItemsDto("skuCode", BigDecimal.valueOf(1200), 10))
        );
        final var savedOrder = new SavedOrder("orderId", "orderNumber");
        when(orderService.placeOrder(orderRequest)).thenReturn(savedOrder);

        // Call method to test
        final var savedOrderFromService = orderController.placeOrder(orderRequest);

        // Assert value
        assertEquals(savedOrderFromService, savedOrder);
    }

    @Test
    @DisplayName("placeOrder() throws BadRequestException when null or empty request is passed")
    public void placeOrderBadRequestExceptionTest() {
        assertThrows(
                BadRequestException.class,
                () -> orderController.placeOrder(new OrderRequest(null)),
                "BadRequestException is expected when null OrderLineItemsList is passed"
        );

        assertThrows(
                BadRequestException.class,
                () -> orderController.placeOrder(new OrderRequest(new ArrayList<>())),
                "BadRequestException is expected when empty OrderLineItemsList is passed"
        );
    }

    @Test
    @DisplayName("placeOrder() forwards InternalServerException from OrderService.placeOrder")
    public void placeOrderInternalServerExceptionTest() throws InternalServerException, InventoryNotInStockException {
        final var orderRequest = new OrderRequest(
                List.of(new OrderLineItemsDto("skuCode", BigDecimal.valueOf(1200), 10))
        );
        when(orderService.placeOrder(orderRequest)).thenThrow(new InternalServerException());
        assertThrows(InternalServerException.class, () -> orderController.placeOrder(orderRequest));
    }


}

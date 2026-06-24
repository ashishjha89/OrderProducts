package com.orderproduct.orderservice.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.orderproduct.orderservice.common.BadRequestException;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.PlaceOrderInput;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class OrderGraphQLController {

    private final OrderService orderService;

    public OrderGraphQLController(OrderService orderService) {
        this.orderService = orderService;
    }

    @QueryMapping // HACK as GraphQL needs at least one "QueryMapping", so we created this dummy one.
    public String _service() {
        return "order-service";
    }

    @MutationMapping
    public CompletableFuture<SavedOrder> placeOrder(@Argument PlaceOrderInput input) {
        log.info("GraphQL mutation: placeOrder");
        if (input == null || input.orderLineItems() == null || input.orderLineItems().isEmpty()) {
            throw new BadRequestException();
        }
        OrderRequest orderRequest = new OrderRequest(
                input.orderLineItems().stream()
                        .map(item -> new OrderLineItemsDto(item.skuCode(), item.price(), item.quantity()))
                        .toList());
        return orderService.placeOrder(orderRequest);
    }
}

package com.example.orderservice.service;

import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);

    private final OrderService orderService = new OrderService(orderRepository);

    @Test
    public void placeOrder() {

    }
}

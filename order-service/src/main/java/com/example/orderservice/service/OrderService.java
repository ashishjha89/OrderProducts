package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.OrderRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public SavedOrder placeOrder(@NonNull OrderRequest orderRequest) throws InternalServerException {
        final var orderLineItemsList = orderRequest.getOrderLineItemsList().stream().map(this::mapToDto).toList();

        final var order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setOrderLineItemsList(orderLineItemsList);

        try {
            Order savedOrder = orderRepository.save(order);
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            throw new InternalServerException();
        }

    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        final var orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setSkuCode(orderLineItems.getSkuCode());
        orderLineItems.setQuantity(orderLineItems.getQuantity());
        return orderLineItems;
    }
}

package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.OrderRepository;
import lombok.NonNull;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public record OrderService(
        OrderRepository orderRepository,
        OrderNumberGenerator orderNumberGenerator
) {

    public SavedOrder placeOrder(@NonNull OrderRequest orderRequest) throws InternalServerException {
        final var orderLineItemsList = orderRequest.getOrderLineItemsList().stream().map(this::mapToDto).toList();

        final var order = new Order();
        order.setOrderNumber(orderNumberGenerator.getUniqueOrderNumber());
        order.setOrderLineItemsList(orderLineItemsList);

        try {
            final var savedOrder = orderRepository.save(order);
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            throw new InternalServerException();
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.getPrice())
                .skuCode(orderLineItemsDto.getSkuCode())
                .quantity(orderLineItemsDto.getQuantity())
                .build();
    }
}

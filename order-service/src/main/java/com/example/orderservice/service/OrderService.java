package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Transactional
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderNumberGenerator orderNumberGenerator;

    public SavedOrder placeOrder(@NonNull OrderRequest orderRequest) throws InternalServerException {
        final var orderLineItemsList = orderRequest.getOrderLineItemsList().stream().map(this::mapToDto).toList();

        final var order = new Order();
        order.setOrderNumber(orderNumberGenerator.getUniqueOrderNumber());
        order.setOrderLineItemsList(orderLineItemsList);

        try {
            final var savedOrder = orderRepository.save(order);
            log.info("Order is saved Id:" + savedOrder.getId() + " orderNumber:" + savedOrder.getOrderNumber());
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            log.error("Error when saving Order:" + e.getMessage());
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

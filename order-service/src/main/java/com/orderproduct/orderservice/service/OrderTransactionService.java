package com.orderproduct.orderservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.entity.Order;
import com.orderproduct.orderservice.entity.OrderLineItems;
import com.orderproduct.orderservice.entity.OutboxEvent;
import com.orderproduct.orderservice.event.OrderPlacedEvent;
import com.orderproduct.orderservice.repository.OrderRepository;
import com.orderproduct.orderservice.repository.OutboxEventRepository;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
class OrderTransactionService {

    private final OrderDataGenerator orderDataGenerator;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // TODO: Implement thread pool for order placement.

    @Transactional
    SavedOrder saveOrder(@NonNull String orderNumber, @NonNull OrderRequest orderRequest)
            throws InternalServerException {
        Order order = buildOrder(orderNumber, orderRequest);
        SavedOrder savedOrder = persistOrder(order);
        saveOrderPlacedEventToOutbox(savedOrder);
        return savedOrder;
    }

    private Order buildOrder(String orderNumber, OrderRequest orderRequest) {
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .build();
        List<OrderLineItems> orderLineItems = orderRequest.orderLineItemsList().stream()
                .map(dto -> toOrderLineItemEntity(dto, order))
                .toList();
        order.setOrderLineItemsList(orderLineItems);
        return order;
    }

    private SavedOrder persistOrder(Order order) throws InternalServerException {
        try {
            log.debug("Saving order with ID: {} and order number: {}", order.getId(), order.getOrderNumber());
            Order savedOrder = orderRepository.save(order);
            log.debug("Order saved successfully with ID: {} and order number: {}", savedOrder.getId(),
                    savedOrder.getOrderNumber());
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (Exception e) {
            log.error("Error saving order with order number: {}. Error: {}", order.getOrderNumber(), e.getMessage());
            throw new InternalServerException();
        }
    }

    private void saveOrderPlacedEventToOutbox(SavedOrder savedOrder) throws InternalServerException {
        try {
            OrderPlacedEvent event = new OrderPlacedEvent(savedOrder.orderNumber());
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(orderDataGenerator.getUniqueOutboxEventId())
                    .eventType("OrderPlacedEvent")
                    .aggregateType("Order")
                    .aggregateId(savedOrder.orderNumber())
                    .payload(payload)
                    .createdAt(orderDataGenerator.getCurrentTimestamp())
                    .build();
            log.debug("Saving OrderPlacedEvent to outbox for order: {}", savedOrder.orderNumber());
            outboxEventRepository.save(outboxEvent);
            log.debug("OrderPlacedEvent saved to outbox for order: {}", savedOrder.orderNumber());
        } catch (Exception ex) {
            log.error("Error saving OrderPlacedEvent to outbox for order: {}. Error: {}",
                    savedOrder.orderNumber(), ex.getMessage());
            throw new InternalServerException();
        }
    }

    private OrderLineItems toOrderLineItemEntity(OrderLineItemsDto orderLineItemsDto, Order order) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.price())
                .skuCode(orderLineItemsDto.skuCode())
                .quantity(orderLineItemsDto.quantity())
                .order(order)
                .build();
    }

}

package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.fasterxml.jackson.core.JsonProcessingException;
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

public class OrderTransactionServiceTest {

        private final OrderDataGenerator orderDataGenerator = mock(OrderDataGenerator.class);
        private final OrderRepository orderRepository = mock(OrderRepository.class);
        private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        private final ObjectMapper objectMapper = new ObjectMapper();

        private final OrderTransactionService orderTransactionService = new OrderTransactionService(
                        orderDataGenerator,
                        orderRepository,
                        outboxEventRepository,
                        objectMapper);

        private final String orderNumber = "TEST-ORDER-123";
        private final List<OrderLineItemsDto> lineItemDtoList = List.of(
                        new OrderLineItemsDto("skuCode1", BigDecimal.valueOf(1000), 10),
                        new OrderLineItemsDto("skuCode2", BigDecimal.valueOf(2000), 20));
        private final OrderRequest orderRequest = new OrderRequest(lineItemDtoList);

        @Test
        @DisplayName("`saveOrder()` successfully saves order and creates outbox event")
        public void saveOrder_SuccessfullySavesOrderAndCreatesOutboxEvent() throws Exception {
                // Setup order to save
                Long now = Instant.now().toEpochMilli();
                Order orderToSave = Order.builder().orderNumber(orderNumber).build();
                List<OrderLineItems> orderLineItems = orderRequest.orderLineItemsList().stream()
                                .map(dto -> toOrderLineItemEntity(dto, orderToSave))
                                .toList();
                orderToSave.setOrderLineItemsList(orderLineItems);

                Order orderEntity = Order.builder()
                                .id(1L)
                                .orderNumber(orderNumber)
                                .orderLineItemsList(orderLineItems)
                                .build();
                SavedOrder savedOrder = new SavedOrder(orderEntity.getId() + "", orderEntity.getOrderNumber());

                // Given
                when(orderDataGenerator.getUniqueOutboxEventId()).thenReturn("uniqueOutboxEventId");
                when(orderDataGenerator.getCurrentTimestamp()).thenReturn(now);

                when(orderRepository.save(orderToSave)).thenReturn(orderEntity);

                OutboxEvent outboxEvent = toOutboxEventEntity(savedOrder);
                // when(outboxEventRepository.save(savedOrder)).thenReturn(outboxEvent);

                // When
                SavedOrder result = orderTransactionService.saveOrder(orderNumber,
                                orderRequest);

                // Then
                assertNotNull(result);
                assertEquals("1", result.orderId());
                assertEquals(orderNumber, result.orderNumber());
                verify(orderRepository).save(orderToSave);
                verify(outboxEventRepository).save(outboxEvent);
        }

        @Test
        @DisplayName("`saveOrder()` throws InternalServerException when order save fails")
        public void saveOrder_ThrowsInternalServerException_WhenOrderSaveFails() {
                // Arrange
                when(orderRepository.save(any(Order.class))).thenThrow(mock(DataAccessException.class));

                // Act & Assert
                assertThrows(InternalServerException.class,
                                () -> orderTransactionService.saveOrder(orderNumber, orderRequest));
        }

        @Test
        @DisplayName("`saveOrder()` throws InternalServerException when outbox event save fails")
        public void saveOrder_ThrowsInternalServerException_WhenOutboxEventSaveFails()
                        throws Exception {
                // Arrange
                Order savedOrder = Order.builder()
                                .id(1L)
                                .orderNumber(orderNumber)
                                .build();
                when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
                when(outboxEventRepository.save(any(OutboxEvent.class)))
                                .thenThrow(new RuntimeException("Outbox save failed"));

                // Act & Assert
                assertThrows(InternalServerException.class,
                                () -> orderTransactionService.saveOrder(orderNumber, orderRequest));
        }

        @Test
        @DisplayName("`saveOrder()` throws InternalServerException when JSON serialization fails")
        public void saveOrder_ThrowsInternalServerException_WhenJsonSerializationFails()
                        throws Exception {
                // Arrange
                Order savedOrder = Order.builder()
                                .id(1L)
                                .orderNumber(orderNumber)
                                .build();
                when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
                ObjectMapper failingMapper = mock(ObjectMapper.class);
                when(failingMapper.writeValueAsString(any()))
                                .thenThrow(new RuntimeException("JSON serialization failed"));

                OrderTransactionService serviceWithFailingMapper = new OrderTransactionService(
                                orderDataGenerator,
                                orderRepository,
                                outboxEventRepository,
                                failingMapper);

                // Act & Assert
                assertThrows(InternalServerException.class,
                                () -> serviceWithFailingMapper.saveOrder(orderNumber, orderRequest));
        }

        private OrderLineItems toOrderLineItemEntity(OrderLineItemsDto orderLineItemsDto, Order order) {
                return OrderLineItems.builder()
                                .price(orderLineItemsDto.price())
                                .skuCode(orderLineItemsDto.skuCode())
                                .quantity(orderLineItemsDto.quantity())
                                .order(order)
                                .build();
        }

        private OutboxEvent toOutboxEventEntity(SavedOrder savedOrder) throws JsonProcessingException {
                OrderPlacedEvent event = new OrderPlacedEvent(savedOrder.orderNumber());
                String payload = objectMapper.writeValueAsString(event);

                return OutboxEvent.builder()
                                .eventId(orderDataGenerator.getUniqueOutboxEventId())
                                .eventType("OrderPlacedEvent")
                                .aggregateType("Order")
                                .aggregateId(savedOrder.orderNumber())
                                .payload(payload)
                                .createdAt(orderDataGenerator.getCurrentTimestamp())
                                .build();
        }
}
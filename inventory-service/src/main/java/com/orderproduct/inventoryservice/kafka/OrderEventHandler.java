package com.orderproduct.inventoryservice.kafka;

import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.common.exception.InvalidKafkaEventException;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.service.reservation.ReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "outbox.event.Order")
    public void handleOrderStatusChangedEvent(
            @Payload String payload,
            @Header("eventType") String eventType,
            @Header("aggregateType") String aggregateType,
            @Header("aggregateId") String aggregateId,
            @Header("eventId") String eventId) throws InvalidKafkaEventException {
        log.info("Received OrderStatusChangedEvent - EventId: {}, AggregateId: {}, EventType: {}, Raw payload: {}",
                eventId, aggregateId, eventType, payload);
        OrderStatusChangedEvent event = extractOrderStatusChangedEvent(payload);
        if (shouldProcessOrderStatus(event.status())) {
            ReservationState reservationState = mapOrderStatusToReservationState(event.status());
            reservationService.updateReservationState(
                    new ReservationStateUpdateRequest(event.orderNumber(), reservationState));
            log.debug("Successfully processed order status: {} for order: {}", event.status(), event.orderNumber());
        } else {
            log.debug("Ignoring order status: {} for order: {}", event.status(), event.orderNumber());
        }
    }

    private OrderStatusChangedEvent extractOrderStatusChangedEvent(String payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            String payloadJson = (String) map.get("payload");
            log.debug("Extracted payload: {}", payloadJson);
            return objectMapper.readValue(payloadJson, OrderStatusChangedEvent.class);
        } catch (Exception e) {
            log.error("Error parsing OrderStatusChangedEvent: {}", e.getMessage(), e);
            throw new InvalidKafkaEventException("Failed to parse OrderStatusChangedEvent");
        }
    }

    private boolean shouldProcessOrderStatus(String orderStatus) {
        if (orderStatus == null) {
            log.warn("Order status is null, ignoring event");
            return false;
        }
        String upperCaseStatus = orderStatus.toUpperCase();
        return "FULFILLED".equals(upperCaseStatus) || "CANCELLED".equals(upperCaseStatus);
    }

    private ReservationState mapOrderStatusToReservationState(String orderStatus) {
        return switch (orderStatus.toUpperCase()) {
            case "CANCELLED" -> ReservationState.CANCELLED;
            case "FULFILLED" -> ReservationState.FULFILLED;
            default -> {
                log.warn("Unexpected order status in mapping: {}", orderStatus);
                yield ReservationState.PENDING;
            }
        };
    }
}

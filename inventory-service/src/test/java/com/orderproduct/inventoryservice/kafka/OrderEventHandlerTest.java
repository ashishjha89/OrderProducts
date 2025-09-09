package com.orderproduct.inventoryservice.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.common.exception.InvalidKafkaEventException;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.service.reservation.ReservationService;

public class OrderEventHandlerTest {

    private final ReservationService reservationService = mock(ReservationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderEventHandler orderEventHandler = new OrderEventHandler(reservationService, objectMapper);

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should successfully process FULFILLED order status")
    public void handleOrderStatusChangedEvent_FulfilledStatus_ProcessesSuccessfully() throws Exception {
        // Given
        final var payload = createPayloadJson(new OrderStatusChangedEvent("ORDER-123", "FULFILLED"));
        final var expectedRequest = new ReservationStateUpdateRequest("ORDER-123", ReservationState.FULFILLED);

        // When
        orderEventHandler.handleOrderStatusChangedEvent(payload, "OrderStatusChangedEvent", "Order", "aggregate-123",
                "event-123");

        // Then
        verify(reservationService).updateReservationState(expectedRequest);
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should successfully process CANCELLED order status")
    public void handleOrderStatusChangedEvent_CancelledStatus_ProcessesSuccessfully() throws Exception {
        // Given
        final var payload = createPayloadJson(new OrderStatusChangedEvent("ORDER-456", "CANCELLED"));
        final var expectedRequest = new ReservationStateUpdateRequest("ORDER-456", ReservationState.CANCELLED);

        // When
        orderEventHandler.handleOrderStatusChangedEvent(payload, "OrderStatusChanged", "Order", "aggregate-456",
                "event-456");

        // Then
        verify(reservationService).updateReservationState(expectedRequest);
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should process FULFILLED status case-insensitively")
    public void handleOrderStatusChangedEvent_FulfilledStatusLowerCase_ProcessesSuccessfully() throws Exception {
        // Given
        final var payload = createPayloadJson(new OrderStatusChangedEvent("ORDER-789", "fulfilled"));
        final var expectedRequest = new ReservationStateUpdateRequest("ORDER-789", ReservationState.FULFILLED);

        // When
        orderEventHandler.handleOrderStatusChangedEvent(payload, "OrderStatusChanged", "Order", "aggregate-789",
                "event-789");

        // Then
        verify(reservationService).updateReservationState(expectedRequest);
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should ignore PENDING order status")
    public void handleOrderStatusChangedEvent_PendingStatus_IgnoresEvent() throws Exception {
        // Given
        final var payload = createPayloadJson(new OrderStatusChangedEvent("ORDER-202", "PENDING"));

        // When
        orderEventHandler.handleOrderStatusChangedEvent(payload, "OrderStatusChanged", "Order", "aggregate-202",
                "event-202");

        // Then
        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should ignore null order status")
    public void handleOrderStatusChangedEvent_NullStatus_IgnoresEvent() throws Exception {
        // Given
        final var payload = createPayloadJson(new OrderStatusChangedEvent("ORDER-404", null));

        // When
        orderEventHandler.handleOrderStatusChangedEvent(payload, "OrderStatusChanged", "Order", "aggregate-404",
                "event-404");

        // Then
        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should throw InvalidKafkaEventException when payload parsing fails")
    public void handleOrderStatusChangedEvent_InvalidPayload_ThrowsInvalidKafkaEventException() {
        // Given
        final var invalidPayload = "invalid-json";

        // When & Then
        assertThrows(InvalidKafkaEventException.class,
                () -> orderEventHandler.handleOrderStatusChangedEvent(invalidPayload,
                        "OrderStatusChanged", "Order", "aggregate-505", "event-505"));

        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should throw InvalidKafkaEventException when payload structure is invalid")
    public void handleOrderStatusChangedEvent_InvalidPayloadStructure_ThrowsInvalidKafkaEventException()
            throws Exception {
        // Given
        final var invalidPayload = "{\"invalid\": \"structure\"}";

        // When & Then
        assertThrows(InvalidKafkaEventException.class,
                () -> orderEventHandler.handleOrderStatusChangedEvent(invalidPayload,
                        "OrderStatusChanged", "Order", "aggregate-606", "event-606"));

        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should throw InvalidKafkaEventException when inner payload is invalid JSON")
    public void handleOrderStatusChangedEvent_InvalidInnerPayload_ThrowsInvalidKafkaEventException() throws Exception {
        // Given
        final var invalidInnerPayload = "{\"payload\": \"invalid-json\"}";

        // When & Then
        assertThrows(InvalidKafkaEventException.class,
                () -> orderEventHandler.handleOrderStatusChangedEvent(invalidInnerPayload,
                        "OrderStatusChanged", "Order", "aggregate-707", "event-707"));

        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should handle null payload field")
    public void handleOrderStatusChangedEvent_NullPayloadField_ThrowsInvalidKafkaEventException() {
        // Given
        final var payloadWithNullPayload = "{\"payload\": null}";

        // When & Then
        assertThrows(InvalidKafkaEventException.class,
                () -> orderEventHandler.handleOrderStatusChangedEvent(payloadWithNullPayload,
                        "OrderStatusChanged", "Order", "aggregate-null-payload", "event-null-payload"));

        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    @Test
    @DisplayName("`handleOrderStatusChangedEvent()` should handle empty payload field")
    public void handleOrderStatusChangedEvent_EmptyPayloadField_ThrowsInvalidKafkaEventException() {
        // Given
        final var payloadWithEmptyPayload = "{\"payload\": \"\"}";

        // When & Then
        assertThrows(InvalidKafkaEventException.class,
                () -> orderEventHandler.handleOrderStatusChangedEvent(payloadWithEmptyPayload,
                        "OrderStatusChanged", "Order", "aggregate-empty-payload", "event-empty-payload"));

        verify(reservationService, never()).updateReservationState(any(ReservationStateUpdateRequest.class));
    }

    private String createPayloadJson(OrderStatusChangedEvent event) throws JsonProcessingException {
        final var innerPayload = objectMapper.writeValueAsString(event);
        final Map<String, Object> outerPayload = new HashMap<>();
        outerPayload.put("payload", innerPayload);
        return objectMapper.writeValueAsString(outerPayload);
    }
}

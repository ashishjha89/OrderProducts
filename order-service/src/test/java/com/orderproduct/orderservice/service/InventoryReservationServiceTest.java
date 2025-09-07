package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.common.OrderReservationNotAllowedException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.ItemReservationRequest;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class InventoryReservationServiceTest {

        private InventoryReservationService inventoryReservationService;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private MockWebServer mockWebServer;

        @BeforeEach
        void initialize() throws IOException {
                mockWebServer = new MockWebServer();
                mockWebServer.start();

                inventoryReservationService = new InventoryReservationService(
                                WebClient.builder(),
                                mockWebServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
                mockWebServer.shutdown();
        }

        @Nested
        @DisplayName("Successful Operations")
        class SuccessfulOperations {

                @Test
                @DisplayName("reserveOrder should successfully reserve products and return InventoryAvailabilityStatus")
                void reserveOrderSuccessTest() throws Exception {
                        // Given
                        final var mockStatuses = List.of(
                                        new InventoryAvailabilityStatus("sku1", 10),
                                        new InventoryAvailabilityStatus("sku2", 5));
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(
                                                        new ItemReservationRequest("sku1", 5),
                                                        new ItemReservationRequest("sku2", 3)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setBody(objectMapper.writeValueAsString(mockStatuses))
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        final List<InventoryAvailabilityStatus> responseStatuses = inventoryReservationService
                                        .reserveOrder(orderReservationRequest).get();

                        // Then
                        assertEquals(mockStatuses, responseStatuses);
                }

                @Test
                @DisplayName("reserveOrder should handle multiple item reservations successfully")
                void reserveOrderShouldHandleMultipleItemReservationsSuccessfully() throws Exception {
                        // Given
                        final var mockStatuses = List.of(
                                        new InventoryAvailabilityStatus("sku1", 15),
                                        new InventoryAvailabilityStatus("sku2", 8),
                                        new InventoryAvailabilityStatus("sku3", 0));
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-456",
                                        List.of(
                                                        new ItemReservationRequest("sku1", 10),
                                                        new ItemReservationRequest("sku2", 5),
                                                        new ItemReservationRequest("sku3", 2)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setBody(objectMapper.writeValueAsString(mockStatuses))
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        final List<InventoryAvailabilityStatus> responseStatuses = inventoryReservationService
                                        .reserveOrder(orderReservationRequest).get();

                        // Then
                        assertEquals(mockStatuses, responseStatuses);
                }
        }

        @Nested
        @DisplayName("Input Validation")
        class InputValidation {

                @Test
                @DisplayName("reserveOrder should throw InvalidInputException when reservation request is empty")
                void reserveOrderShouldThrowInvalidInputExceptionWhenItemReservationRequestsEmpty() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest("ORDER-123", List.of());

                        // When & Then
                        assertThrows(
                                        InvalidInputException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest));
                }
        }

        @Nested
        @DisplayName("HTTP Error Handling")
        class HttpErrorHandling {

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns 4xx (not 429 and 409)")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenHttp4xx() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(404)
                                        .setBody("Not Found")
                                        .addHeader("Content-Type", "text/plain"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when server returns 429")
                void reserveOrderShouldThrowInternalServerExceptionWhenHttp429() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(429)
                                        .setBody("Too Many Requests")
                                        .addHeader("Content-Type", "text/plain"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when server returns HTTP 500 error")
                void reserveOrderShouldThrowInternalServerExceptionWhenHttpError() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(500)
                                        .setBody("Internal Server Error")
                                        .addHeader("Content-Type", "text/plain"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when connection fails")
                void reserveOrderShouldThrowInternalServerExceptionWhenConnectionFails() throws IOException {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        // Shutdown the server to simulate connection failure
                        mockWebServer.shutdown();

                        // When & Then
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }
        }

        @Nested
        @DisplayName("Response Validation")
        class ResponseValidation {

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns null response")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenResponseNull() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setBody("null")
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns empty array")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenResponseEmpty()
                                throws JsonProcessingException {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setBody(objectMapper.writeValueAsString(List.of()))
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns malformed JSON")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenMalformedJson() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setBody("{ malformed json }")
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }
        }

        @Nested
        @DisplayName("Conflict Error Handling (HTTP 409)")
        class ConflictErrorHandling {

                @Test
                @DisplayName("reserveOrder should throw InventoryNotInStockException when server returns 409 with NOT_ENOUGH_ITEM_ERROR_CODE")
                void reserveOrderShouldThrowInventoryNotInStockExceptionWhenHttp409WithNotEnoughItemError()
                                throws JsonProcessingException {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("iphone_12", 10)));

                        final var conflictResponse = """
                                        {
                                            "errorCode": "NOT_ENOUGH_ITEM_ERROR_CODE",
                                            "errorMessage": "Not enough stock for some products",
                                            "unavailableProducts": [
                                                {
                                                    "skuCode": "iphone_12",
                                                    "requestedQuantity": 10,
                                                    "availableQuantity": 5
                                                }
                                            ]
                                        }
                                        """;

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(409)
                                        .setBody(conflictResponse)
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InventoryNotInStockException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw OrderReservationNotAllowedException when server returns 409 with ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE")
                void reserveOrderShouldThrowOrderReservationNotAllowedExceptionWhenHttp409WithReservationNotAllowedError()
                                throws JsonProcessingException {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("iphone_12", 10)));

                        final var conflictResponse = """
                                        {
                                            "errorCode": "ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE",
                                            "errorMessage": "Cannot create reservations for order with non Pending states"
                                        }
                                        """;

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(409)
                                        .setBody(conflictResponse)
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(OrderReservationNotAllowedException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns 409 with unknown error code")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenHttp409WithUnknownErrorCode()
                                throws JsonProcessingException {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        final var conflictResponse = """
                                        {
                                            "errorCode": "SOME_OTHER_ERROR_CODE",
                                            "message": "Some other conflict error"
                                        }
                                        """;

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(409)
                                        .setBody(conflictResponse)
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns 409 with malformed JSON")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenHttp409WithMalformedJson() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(409)
                                        .setBody("{ invalid json structure")
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when server returns 409 with empty body")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenHttp409WithEmptyBody() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("sku1", 5)));

                        mockWebServer.enqueue(new MockResponse()
                                        .setResponseCode(409)
                                        .setBody("")
                                        .addHeader("Content-Type", "application/json"));

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> inventoryReservationService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, exception.getCause());
                }
        }

        @Nested
        @DisplayName("Resilience4j Configuration")
        class Resilience4jConfiguration {

                @Test
                @DisplayName("reserveOrder should have resilience4j annotations")
                void reserveOrderShouldHaveResilience4jAnnotations() throws NoSuchMethodException {
                        Method method = InventoryReservationService.class.getMethod(
                                        "reserveOrder", OrderReservationRequest.class);
                        assertNotNull(method.getAnnotation(CircuitBreaker.class), "@CircuitBreaker should be present");
                        assertNotNull(method.getAnnotation(TimeLimiter.class), "@TimeLimiter should be present");
                        assertNotNull(method.getAnnotation(Retry.class), "@Retry should be present");
                        assertEquals("inventory", method.getAnnotation(CircuitBreaker.class).name());
                        assertEquals("inventory", method.getAnnotation(TimeLimiter.class).name());
                        assertEquals("inventory", method.getAnnotation(Retry.class).name());
                }

                @Test
                @DisplayName("reserveOrder should trigger fallback and return failed future with InternalServerException")
                void reserveOrderShouldTriggerFallbackOnException() throws IOException {
                        // Given - Shutdown the server to simulate connection failure
                        mockWebServer.shutdown();

                        var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new ItemReservationRequest("test-sku-1", 5)));

                        // When
                        var future = inventoryReservationService.reserveOrder(orderReservationRequest);

                        // Then
                        var exception = assertThrows(Exception.class, future::get);
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }
        }
}
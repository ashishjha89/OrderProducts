package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Mono;

public class InventoryStatusServiceTest {

        private InventoryStatusService inventoryStatusService;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private MockWebServer mockWebServer;

        @BeforeEach
        void initialize() throws IOException {
                mockWebServer = new MockWebServer();
                mockWebServer.start();

                inventoryStatusService = new InventoryStatusService(
                                WebClient.builder(),
                                mockWebServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
                mockWebServer.shutdown();
        }

        @Test
        @DisplayName("should fetch list of `InventoryStockStatus` from inventory-service")
        void getInventoryAvailabilityFutureTest() throws Exception {
                // Given
                final var mockStatuses = List.of(
                                new InventoryStockStatus("sku1", 10),
                                new InventoryStockStatus("sku2", 0));
                mockWebServer.enqueue(new MockResponse()
                                .setBody(objectMapper.writeValueAsString(mockStatuses))
                                .addHeader("Content-Type", "application/json"));

                // When
                final List<InventoryStockStatus> responseStatuses = inventoryStatusService
                                .getInventoryAvailabilityFuture(List.of("sku1", "sku2")).get();

                // Then
                assertEquals(mockStatuses, responseStatuses);
        }

        @Test
        @DisplayName("should throw InvalidInputException when SKU codes list is empty")
        void shouldThrowInvalidInputExceptionWhenSkuCodesEmpty() {
                assertThrows(
                                InvalidInputException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of()));
        }

        @Test
        @DisplayName("should throw InvalidInventoryException when server returns 4xx (not 429)")
        void shouldThrowInvalidInventoryExceptionWhenHttp4xx() {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setResponseCode(404)
                                .setBody("Not Found")
                                .addHeader("Content-Type", "text/plain"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InvalidInventoryException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InternalServerException when server returns 429")
        void shouldThrowInternalServerExceptionWhenHttp429() {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setResponseCode(429)
                                .setBody("Too Many Requests")
                                .addHeader("Content-Type", "text/plain"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InternalServerException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InvalidInventoryException when server returns null response")
        void shouldThrowInvalidInventoryExceptionWhenResponseNull() {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setBody("null")
                                .addHeader("Content-Type", "application/json"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InvalidInventoryException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InvalidInventoryException when server returns empty array")
        void shouldThrowInvalidInventoryExceptionWhenResponseEmpty() throws JsonProcessingException {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setBody(objectMapper.writeValueAsString(List.of()))
                                .addHeader("Content-Type", "application/json"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InvalidInventoryException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InternalServerException when server returns HTTP 500 error")
        void shouldThrowInternalServerExceptionWhenHttpError() {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setResponseCode(500)
                                .setBody("Internal Server Error")
                                .addHeader("Content-Type", "text/plain"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InternalServerException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InternalServerException when connection fails")
        void shouldThrowInternalServerExceptionWhenConnectionFails() throws IOException {
                // Given - Shutdown the server to simulate connection failure
                mockWebServer.shutdown();

                // When & Then
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InternalServerException.class, exception.getCause());
        }

        @Test
        @DisplayName("should throw InvalidInventoryException when server returns malformed JSON")
        void shouldThrowInvalidInventoryExceptionWhenMalformedJson() {
                // Given
                mockWebServer.enqueue(new MockResponse()
                                .setBody("{ malformed json }")
                                .addHeader("Content-Type", "application/json"));

                // When
                ExecutionException exception = assertThrows(
                                ExecutionException.class,
                                () -> inventoryStatusService.getInventoryAvailabilityFuture(List.of("sku1")).get());

                // Then
                assertInstanceOf(InvalidInventoryException.class, exception.getCause());
        }

        @Test
        @DisplayName("should have resilience4j annotations on getInventoryAvailabilityFuture")
        void shouldHaveResilience4jAnnotations() throws NoSuchMethodException {
                Method method = InventoryStatusService.class.getMethod(
                                "getInventoryAvailabilityFuture", List.class);
                assertNotNull(method.getAnnotation(CircuitBreaker.class), "@CircuitBreaker should be present");
                assertNotNull(method.getAnnotation(TimeLimiter.class), "@TimeLimiter should be present");
                assertNotNull(method.getAnnotation(Retry.class), "@Retry should be present");
                assertEquals("inventory", method.getAnnotation(CircuitBreaker.class).name());
                assertEquals("inventory", method.getAnnotation(TimeLimiter.class).name());
                assertEquals("inventory", method.getAnnotation(Retry.class).name());
        }

        @Test
        @DisplayName("should trigger fallback and return failed future with InternalServerException")
        void shouldTriggerFallbackOnException() {
                // Initialize mocks
                var mockBuilder = mock(WebClient.Builder.class);
                var mockClient = mock(WebClient.class);
                var mockRequest = mock(WebClient.RequestHeadersUriSpec.class);
                var mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
                var mockResponseSpec = mock(WebClient.ResponseSpec.class);

                // Given
                when(mockBuilder.build()).thenReturn(mockClient);
                when(mockClient.get()).thenReturn(mockRequest);
                when(mockRequest.uri(any(URI.class))).thenReturn(mockHeadersSpec);
                when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
                // Mock-chained onStatus calls to return the same mockResponseSpec
                when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
                when(mockResponseSpec.bodyToMono(InventoryStockStatus[].class))
                                .thenReturn(Mono.error(new RuntimeException("Simulated failure"))); // Simulate an error

                var service = new InventoryStatusService(mockBuilder, "http://localhost");
                var skuCodes = List.of("test-sku-1");

                // When
                var future = service.getInventoryAvailabilityFuture(skuCodes);

                // Then
                var exception = assertThrows(Exception.class, future::get);
                assertInstanceOf(InternalServerException.class, exception.getCause());
        }
}
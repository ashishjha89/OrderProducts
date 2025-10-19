package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.orderproduct.inventoryservice.grpc.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.grpc.ReservationServiceGrpc;
import com.orderproduct.inventoryservice.grpc.ReserveProductsRequest;
import com.orderproduct.inventoryservice.grpc.ReserveProductsResponse;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.common.OrderReservationNotAllowedException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

@ExtendWith(MockitoExtension.class)
public class InventoryReservationGrpcClientServiceTest {

        @Mock
        private ReservationServiceGrpc.ReservationServiceBlockingStub reservationServiceStub;

        @InjectMocks
        private InventoryReservationGrpcClientService grpcClientService;

        @Nested
        @DisplayName("Successful Operations")
        class SuccessfulOperations {

                @Test
                @DisplayName("reserveOrder should successfully reserve products and return InventoryAvailabilityStatus")
                void reserveOrderSuccessTest() throws Exception {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(
                                                        new com.orderproduct.orderservice.dto.ItemReservationRequest(
                                                                        "sku1", 5),
                                                        new com.orderproduct.orderservice.dto.ItemReservationRequest(
                                                                        "sku2", 3)));

                        ReserveProductsResponse grpcResponse = ReserveProductsResponse.newBuilder()
                                        .addAvailableInventory(AvailableInventoryResponse.newBuilder()
                                                        .setSkuCode("sku1")
                                                        .setAvailableQuantity(10)
                                                        .build())
                                        .addAvailableInventory(AvailableInventoryResponse.newBuilder()
                                                        .setSkuCode("sku2")
                                                        .setAvailableQuantity(5)
                                                        .build())
                                        .build();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenReturn(grpcResponse);

                        // When
                        final List<InventoryAvailabilityStatus> responseStatuses = grpcClientService
                                        .reserveOrder(orderReservationRequest).get();

                        // Then
                        final var mockStatuses = List.of(
                                        new InventoryAvailabilityStatus("sku1", 10),
                                        new InventoryAvailabilityStatus("sku2", 5));
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
                                        () -> grpcClientService.reserveOrder(orderReservationRequest));
                }
        }

        @Nested
        @DisplayName("Response Validation")
        class ResponseValidation {

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when gRPC returns empty response")
                void reserveOrderShouldThrowInternalServerExceptionWhenResponseEmpty() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        ReserveProductsResponse emptyResponse = ReserveProductsResponse.newBuilder().build();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenReturn(emptyResponse);

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }
        }

        @Nested
        @DisplayName("gRPC Error Handling")
        class GrpcErrorHandling {

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when gRPC throws unexpected exception")
                void reserveOrderShouldThrowInternalServerExceptionWhenUnexpectedException() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(new RuntimeException("Unexpected error")); // Unexpected exception

                        // When
                        ExecutionException exception = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when gRPC returns INTERNAL with expected error code")
                void reserveOrderShouldThrowInternalServerExceptionWhenGrpcInternal() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                                        .setReason("SOMETHING_WENT_WRONG_ERROR_CODE")
                                        .build();

                        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.INTERNAL.value())
                                        .setMessage("Sorry, something went wrong.")
                                        .addDetails(Any.pack(errorInfo))
                                        .build();

                        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InternalServerException when INTERNAL without expected error code")
                void reserveOrderShouldThrowInternalServerExceptionWhenInternalWithoutErrorCode() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        StatusRuntimeException exception = Status.INTERNAL
                                        .withDescription("Internal error")
                                        .asRuntimeException();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InternalServerException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw OrderReservationNotAllowedException when gRPC returns FAILED_PRECONDITION with ORDER_RESERVATION_NOT_ALLOWED")
                void reserveOrderShouldThrowOrderReservationNotAllowedExceptionWhenFailedPreconditionWithReservationNotAllowed() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest(
                                                        "iphone_12", 10)));

                        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                                        .setReason("ORDER_RESERVATION_NOT_ALLOWED")
                                        .build();

                        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.FAILED_PRECONDITION.value())
                                        .setMessage("Cannot create reservations for order with non Pending states")
                                        .addDetails(Any.pack(errorInfo))
                                        .build();

                        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(OrderReservationNotAllowedException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when gRPC returns FAILED_PRECONDITION with unknown error code")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenFailedPreconditionWithUnknownErrorCode() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                                        .setReason("SOME_OTHER_ERROR_CODE")
                                        .build();

                        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.FAILED_PRECONDITION.value())
                                        .setMessage("Some other error")
                                        .addDetails(Any.pack(errorInfo))
                                        .build();

                        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when gRPC returns FAILED_PRECONDITION without ErrorInfo")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenFailedPreconditionWithoutErrorInfo() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        StatusRuntimeException exception = Status.FAILED_PRECONDITION
                                        .withDescription("Failed precondition")
                                        .asRuntimeException();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InventoryNotInStockException when gRPC returns RESOURCE_EXHAUSTED with NOT_ENOUGH_ITEM_ERROR_CODE")
                void reserveOrderShouldThrowInventoryNotInStockExceptionWhenResourceExhaustedWithNotEnoughItemError() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest(
                                                        "iphone_12", 10)));

                        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                                        .setReason("NOT_ENOUGH_ITEM_ERROR_CODE")
                                        .putMetadata("unavailable_products", "iphone_12")
                                        .putMetadata("unavailable_count", "1")
                                        .build();

                        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.RESOURCE_EXHAUSTED.value())
                                        .setMessage("Not enough stock for some products")
                                        .addDetails(Any.pack(errorInfo))
                                        .build();

                        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InventoryNotInStockException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when gRPC returns RESOURCE_EXHAUSTED with unknown error code")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenResourceExhaustedWithUnknownErrorCode() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                                        .setReason("SOME_OTHER_ERROR_CODE")
                                        .build();

                        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                                        .setCode(Status.Code.RESOURCE_EXHAUSTED.value())
                                        .setMessage("Some other error")
                                        .addDetails(Any.pack(errorInfo))
                                        .build();

                        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when gRPC returns RESOURCE_EXHAUSTED without ErrorInfo")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenResourceExhaustedWithoutErrorInfo() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        StatusRuntimeException exception = Status.RESOURCE_EXHAUSTED
                                        .withDescription("Resource exhausted")
                                        .asRuntimeException();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, executionException.getCause());
                }

                @Test
                @DisplayName("reserveOrder should throw InvalidInventoryException when gRPC returns unexpected status code")
                void reserveOrderShouldThrowInvalidInventoryExceptionWhenUnexpectedStatusCode() {
                        // Given
                        final var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest("sku1",
                                                        5)));

                        StatusRuntimeException exception = Status.PERMISSION_DENIED // Unexpected status code
                                        .withDescription("Permission denied")
                                        .asRuntimeException();

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(exception);

                        // When
                        ExecutionException executionException = assertThrows(
                                        ExecutionException.class,
                                        () -> grpcClientService.reserveOrder(orderReservationRequest).get());

                        // Then
                        assertInstanceOf(InvalidInventoryException.class, executionException.getCause());
                }

        }

        @Nested
        @DisplayName("Resilience4j Configuration")
        class Resilience4jConfiguration {

                @Test
                @DisplayName("reserveOrder should have resilience4j annotations")
                void reserveOrderShouldHaveResilience4jAnnotations() throws NoSuchMethodException {
                        Method method = InventoryReservationGrpcClientService.class.getMethod(
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
                void reserveOrderShouldTriggerFallbackOnException() {
                        // Given
                        var orderReservationRequest = new OrderReservationRequest(
                                        "ORDER-123",
                                        List.of(new com.orderproduct.orderservice.dto.ItemReservationRequest(
                                                        "test-sku-1", 5)));

                        when(reservationServiceStub.reserveProducts(toGrpcRequest(orderReservationRequest)))
                                        .thenThrow(new RuntimeException("Connection error"));

                        // When
                        var future = grpcClientService.reserveOrder(orderReservationRequest);

                        // Then
                        var exception = assertThrows(Exception.class, future::get);
                        assertInstanceOf(InternalServerException.class, exception.getCause());
                }
        }

        private ReserveProductsRequest toGrpcRequest(OrderReservationRequest request) {
                ReserveProductsRequest.Builder builder = ReserveProductsRequest.newBuilder()
                                .setOrderNumber(request.orderNumber());
                for (com.orderproduct.orderservice.dto.ItemReservationRequest item : request
                                .itemReservationRequests()) {
                        builder.addItemReservationRequests(
                                        com.orderproduct.inventoryservice.grpc.ItemReservationRequest.newBuilder()
                                                        .setSkuCode(item.skuCode())
                                                        .setQuantity(item.quantity())
                                                        .build());
                }
                return builder.build();
        }
}

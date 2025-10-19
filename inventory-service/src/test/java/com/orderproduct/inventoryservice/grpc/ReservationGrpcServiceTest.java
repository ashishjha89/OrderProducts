package com.orderproduct.inventoryservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.orderproduct.inventoryservice.common.exception.ErrorComponent;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.common.exception.OrderReservationNotAllowedException;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.ItemAvailability;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

@ExtendWith(MockitoExtension.class)
class ReservationGrpcServiceTest {

        @Mock
        private ReservationManagementService reservationManagementService;

        @Mock
        private StreamObserver<ReserveProductsResponse> responseObserver;

        @Captor
        private ArgumentCaptor<ReserveProductsResponse> responseCaptor;

        @Captor
        private ArgumentCaptor<StatusRuntimeException> errorCaptor;

        private ReservationGrpcService reservationGrpcService;

        @BeforeEach
        void setUp() {
                reservationGrpcService = new ReservationGrpcService(reservationManagementService);
        }

        @Test
        @DisplayName("Should reserve products successfully")
        void reserveProducts_Success() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-001")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(5)
                                                .build())
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode2")
                                                .setQuantity(10)
                                                .build())
                                .build();
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-001",
                                List.of(
                                                new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                                "skuCode1", 5),
                                                new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                                "skuCode2", 10)));

                List<AvailableInventoryResponse> expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 7),
                                new AvailableInventoryResponse("skuCode2", 5));

                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenReturn(expectedResponses);

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onNext(responseCaptor.capture());
                verify(responseObserver).onCompleted();

                ReserveProductsResponse capturedResponse = responseCaptor.getValue();
                assertEquals(2, capturedResponse.getAvailableInventoryCount());

                // Verify first item
                com.orderproduct.inventoryservice.grpc.AvailableInventoryResponse firstItem = capturedResponse
                                .getAvailableInventory(0);
                assertEquals("skuCode1", firstItem.getSkuCode());
                assertEquals(7, firstItem.getAvailableQuantity());

                // Verify second item
                com.orderproduct.inventoryservice.grpc.AvailableInventoryResponse secondItem = capturedResponse
                                .getAvailableInventory(1);
                assertEquals("skuCode2", secondItem.getSkuCode());
                assertEquals(5, secondItem.getAvailableQuantity());
        }

        @Test
        @DisplayName("Should return RESOURCE_EXHAUSTED when insufficient stock")
        void reserveProducts_InsufficientStock_ReturnsResourceExhausted() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-002")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(10)
                                                .build())
                                .build();
                List<ItemAvailability> unavailableProducts = List.of(
                                new ItemAvailability("skuCode1", 10, 5));
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-002",
                                List.of(new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                "skuCode1", 10)));
                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenThrow(new NotEnoughItemException(unavailableProducts));

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onError(errorCaptor.capture());

                StatusRuntimeException capturedError = errorCaptor.getValue();
                ErrorInfo errorInfo = extractErrorInfo(capturedError);

                assertEquals(Status.RESOURCE_EXHAUSTED.getCode(), capturedError.getStatus().getCode());
                assertEquals(ErrorComponent.notEnoughStockMsg, capturedError.getStatus().getDescription());
                assertEquals(ErrorComponent.NOT_ENOUGH_ITEM_ERROR_CODE, errorInfo.getReason());
        }

        @Test
        @DisplayName("Should return FAILED_PRECONDITION when reservation not allowed")
        void reserveProducts_ReservationNotAllowed_ReturnsFailedPrecondition() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-003")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(5)
                                                .build())
                                .build();
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-003",
                                List.of(new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                "skuCode1", 5)));
                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenThrow(new OrderReservationNotAllowedException("ORDER-003"));

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onError(errorCaptor.capture());

                StatusRuntimeException capturedError = errorCaptor.getValue();
                ErrorInfo errorInfo = extractErrorInfo(capturedError);

                assertEquals(Status.FAILED_PRECONDITION.getCode(), capturedError.getStatus().getCode());
                assertEquals(ErrorComponent.orderReservationNotAllowedMsg, capturedError.getStatus().getDescription());
                assertEquals(ErrorComponent.ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE, errorInfo.getReason());
        }

        @Test
        @DisplayName("Should return INTERNAL when internal server error")
        void reserveProducts_InternalServerError_ReturnsInternal() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-004")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(5)
                                                .build())
                                .build();
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-004",
                                List.of(new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                "skuCode1", 5)));
                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenThrow(new InternalServerException());

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onError(errorCaptor.capture());

                StatusRuntimeException capturedError = errorCaptor.getValue();
                ErrorInfo errorInfo = extractErrorInfo(capturedError);

                assertEquals(Status.INTERNAL.getCode(), capturedError.getStatus().getCode());
                assertEquals(ErrorComponent.somethingWentWrongMsg, capturedError.getStatus().getDescription());
                assertEquals(ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE, errorInfo.getReason());
        }

        @Test
        @DisplayName("Should handle empty item reservation requests")
        void reserveProducts_EmptyItemList_ShouldReturnEmptyResponse() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-005")
                                .build();
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-005",
                                List.of());
                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenReturn(List.of());

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onNext(responseCaptor.capture());
                verify(responseObserver).onCompleted();

                ReserveProductsResponse capturedResponse = responseCaptor.getValue();
                assertEquals(0, capturedResponse.getAvailableInventoryCount());
        }

        @Test
        @DisplayName("Should return INTERNAL for unexpected exceptions")
        void reserveProducts_UnexpectedException_ReturnsInternal() throws Exception {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-006")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(5)
                                                .build())
                                .build();
                OrderReservationRequest expectedRequest = new OrderReservationRequest(
                                "ORDER-006",
                                List.of(new com.orderproduct.inventoryservice.dto.request.ItemReservationRequest(
                                                "skuCode1", 5)));
                when(reservationManagementService.reserveProductsIfAvailable(expectedRequest))
                                .thenThrow(new RuntimeException("Unexpected error"));

                // When
                reservationGrpcService.reserveProducts(request, responseObserver);

                // Then
                verify(responseObserver).onError(errorCaptor.capture());

                StatusRuntimeException capturedError = errorCaptor.getValue();
                ErrorInfo errorInfo = extractErrorInfo(capturedError);

                assertEquals(Status.INTERNAL.getCode(), capturedError.getStatus().getCode());
                assertEquals(ErrorComponent.somethingWentWrongMsg, capturedError.getStatus().getDescription());
                assertEquals(ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE, errorInfo.getReason());
        }

        private ErrorInfo extractErrorInfo(StatusRuntimeException exception) {
                // Get the status from the exception
                io.grpc.Status grpcStatus = exception.getStatus();
                assertNotNull(grpcStatus, "gRPC Status should not be null");

                // Get the status details from the exception's trailers
                com.google.rpc.Status status = StatusProto.fromStatusAndTrailers(grpcStatus, exception.getTrailers());
                assertNotNull(status, "Status should not be null");

                for (Any detail : status.getDetailsList()) {
                        if (detail.is(ErrorInfo.class)) {
                                try {
                                        return detail.unpack(ErrorInfo.class);
                                } catch (Exception e) {
                                        // Continue to next detail
                                }
                        }
                }
                return null;
        }
}
package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.ItemReservationRequest;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.OrderReservationRequest;
import com.orderproduct.orderservice.dto.SavedOrder;

import io.micrometer.observation.ObservationRegistry;

public class OrderServiceTest {

        private final OrderTransactionService orderTransactionService = mock(OrderTransactionService.class);

        private final InventoryReservationService inventoryReservationService = mock(InventoryReservationService.class);

        private final ObservationRegistry observationRegistry = ObservationRegistry.create();

        private final OrderDataGenerator orderDataGenerator = mock(OrderDataGenerator.class);

        private final OrderService orderService = new OrderService(
                        orderTransactionService,
                        inventoryReservationService,
                        observationRegistry,
                        orderDataGenerator);

        private final String orderNumber = "ThisIsUniqueOrderNumber";

        private final List<OrderLineItemsDto> lineItemDtoList = List.of(
                        new OrderLineItemsDto("skuCode1", BigDecimal.valueOf(1000), 10),
                        new OrderLineItemsDto("skuCode2", BigDecimal.valueOf(2000), 20));

        private final OrderRequest orderRequest = new OrderRequest(lineItemDtoList);

        private final List<ItemReservationRequest> itemReservationRequests = List.of(
                        new ItemReservationRequest("skuCode1", 10),
                        new ItemReservationRequest("skuCode2", 20));

        private final OrderReservationRequest orderReservationRequest = new OrderReservationRequest(orderNumber,
                        itemReservationRequests);

        @BeforeEach
        public void setUp() {
                when(orderDataGenerator.getUniqueOrderNumber()).thenReturn(orderNumber);
        }

        @Test
        @DisplayName("`placeOrder()` converts OrderRequest to Order and saves it to repo when all items have sufficient available quantity")
        public void placeOrder_Succeeds_WhenAllItemsHaveSufficientAvailableQuantity() throws InternalServerException,
                        InventoryNotInStockException, ExecutionException, InterruptedException {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Available: 15, Requested: 10
                                                                new InventoryAvailabilityStatus("skuCode1", 15),
                                                                // Available: 25, Requested: 20
                                                                new InventoryAvailabilityStatus("skuCode2", 25))));

                final SavedOrder expectedSavedOrder = new SavedOrder("1", orderNumber);
                when(orderTransactionService.saveOrder(orderNumber, orderRequest)).thenReturn(expectedSavedOrder);

                // Call method to test
                final var savedOrder = orderService.placeOrder(orderRequest).get();

                // Assert value
                verify(orderTransactionService).saveOrder(orderNumber, orderRequest);
                assertEquals(orderNumber, savedOrder.orderNumber());
                assertEquals("1", savedOrder.orderId());
        }

        @Test
        @DisplayName("`placeOrder()` forwards InvalidInventoryException if product reservation fails")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenProductReservationFails()
                        throws InternalServerException {
                // Mock product reservation failure
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(CompletableFuture.failedFuture(new InvalidInventoryException()));

                // Assert
                assertInvalidInventoryExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InternalServerException from InventoryReservationService")
        public void placeOrder_ForwardsInternalServerException_FromInventoryReservationService()
                        throws InternalServerException {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));
                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InvalidInventoryException from InventoryReservationService")
        public void placeOrder_ForwardsInvalidInventoryException_FromInventoryReservationService()
                        throws InvalidInventoryException {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(CompletableFuture.failedFuture(new InvalidInventoryException()));
                // Assert
                assertInvalidInventoryExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InvalidInputException from InventoryReservationService")
        public void placeOrder_ForwardsInvalidInputException_FromInventoryReservationService()
                        throws InvalidInputException {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(CompletableFuture.failedFuture(new InvalidInputException()));
                // Assert
                assertInvalidInputExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InventoryNotInStockException from InventoryReservationService")
        public void placeOrder_ForwardsInventoryNotInStockException_FromInventoryReservationService()
                        throws InventoryNotInStockException {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(CompletableFuture.failedFuture(new InventoryNotInStockException()));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InternalServerException if reservation response is incomplete")
        public void placeOrder_ThrowsInternalServerException_WhenReservationResponseIsIncomplete()
                        throws InternalServerException {
                // Mock incomplete reservation response - missing skuCode1
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InternalServerException when OrderTransactionService throws InternalServerException")
        public void placeOrder_ThrowsInternalServerException_WhenOrderTransactionServiceThrowsError() {
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryAvailabilityStatus("skuCode1", 10),
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                when(orderTransactionService.saveOrder(orderNumber, orderRequest))
                                .thenThrow(new InternalServerException());

                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` calls saveOrderCancelledEvent when OrderTransactionService throws exception")
        public void placeOrder_CallsSaveOrderCancelledEvent_WhenOrderTransactionServiceThrowsException()
                        throws InternalServerException {
                // Given
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryAvailabilityStatus("skuCode1", 10),
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                InternalServerException expectedException = new InternalServerException();
                when(orderTransactionService.saveOrder(orderNumber, orderRequest))
                                .thenThrow(expectedException);

                // When
                assertInternalServerExceptionIsThrown();

                // Then - The exception will be wrapped in CompletionException in whenComplete
                verify(orderTransactionService).saveOrderCancelledEvent(eq(orderNumber), eq(orderRequest),
                                any(Throwable.class));
        }

        @Test
        @DisplayName("`placeOrder()` does not call saveOrderCancelledEvent when OrderTransactionService succeeds")
        public void placeOrder_DoesNotCallSaveOrderCancelledEvent_WhenOrderTransactionServiceSucceeds()
                        throws InternalServerException, ExecutionException, InterruptedException {
                // Given
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryAvailabilityStatus("skuCode1", 10),
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                final SavedOrder expectedSavedOrder = new SavedOrder("1", orderNumber);
                when(orderTransactionService.saveOrder(orderNumber, orderRequest)).thenReturn(expectedSavedOrder);

                // When
                final var savedOrder = orderService.placeOrder(orderRequest).get();

                // Then
                verify(orderTransactionService).saveOrder(orderNumber, orderRequest);
                verify(orderTransactionService, never()).saveOrderCancelledEvent(eq(orderNumber), eq(orderRequest),
                                any(Throwable.class));
                assertEquals(orderNumber, savedOrder.orderNumber());
        }

        @Test
        @DisplayName("`placeOrder()` handles saveOrderCancelledEvent failure gracefully")
        public void placeOrder_HandlesSaveOrderCancelledEventFailure_Gracefully()
                        throws InternalServerException {
                // Given
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryAvailabilityStatus("skuCode1", 10),
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                InternalServerException orderSaveException = new InternalServerException();
                when(orderTransactionService.saveOrder(orderNumber, orderRequest))
                                .thenThrow(orderSaveException);
                doThrow(new InternalServerException())
                                .when(orderTransactionService)
                                .saveOrderCancelledEvent(eq(orderNumber), eq(orderRequest), any(Throwable.class));

                assertInternalServerExceptionIsThrown();

                verify(orderTransactionService).saveOrderCancelledEvent(eq(orderNumber), eq(orderRequest),
                                any(Throwable.class));
        }

        @Test
        @DisplayName("`placeOrder()` throws InventoryNotInStockException when any item has zero available quantity")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenAnyItemHasZeroAvailableQuantity()
                        throws InternalServerException {
                // Mock reservation with zero quantity
                when(inventoryReservationService.reserveOrder(orderReservationRequest))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Available: 0, Requested: 10
                                                                new InventoryAvailabilityStatus("skuCode1", 0),
                                                                // Available: 20, Requested: 20
                                                                new InventoryAvailabilityStatus("skuCode2", 20))));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        private void assertInventoryNotInStockExceptionIsThrown() {
                ExecutionException executionException = assertThrows(
                                ExecutionException.class,
                                () -> orderService.placeOrder(orderRequest).get());
                Throwable cause = executionException.getCause();
                assertNotNull(cause);
                assertEquals(InventoryNotInStockException.class, cause.getClass());
        }

        private void assertInternalServerExceptionIsThrown() {
                ExecutionException executionException = assertThrows(
                                ExecutionException.class,
                                () -> orderService.placeOrder(orderRequest).get());
                Throwable cause = executionException.getCause();
                assertNotNull(cause);
                assertEquals(InternalServerException.class, cause.getClass());
        }

        private void assertInvalidInventoryExceptionIsThrown() {
                ExecutionException executionException = assertThrows(
                                ExecutionException.class,
                                () -> orderService.placeOrder(orderRequest).get());
                Throwable cause = executionException.getCause();
                assertNotNull(cause);
                assertEquals(InvalidInventoryException.class, cause.getClass());
        }

        private void assertInvalidInputExceptionIsThrown() {
                ExecutionException executionException = assertThrows(
                                ExecutionException.class,
                                () -> orderService.placeOrder(orderRequest).get());
                Throwable cause = executionException.getCause();
                assertNotNull(cause);
                assertEquals(InvalidInputException.class, cause.getClass());
        }
}

package com.orderproduct.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.repository.InventoryStatusRepository;

import io.micrometer.observation.ObservationRegistry;

public class OrderServiceTest {

        private final OrderTransactionService orderTransactionService = mock(OrderTransactionService.class);

        private final InventoryStatusRepository inventoryStatusRepository = mock(InventoryStatusRepository.class);

        private final ObservationRegistry observationRegistry = ObservationRegistry.create();

        private final OrderService orderService = new OrderService(
                        orderTransactionService,
                        inventoryStatusRepository,
                        observationRegistry);

        private final String orderNumber = "ThisIsUniqueOrderNumber";

        private final List<OrderLineItemsDto> lineItemDtoList = List.of(
                        new OrderLineItemsDto("skuCode1", BigDecimal.valueOf(1000), 10),
                        new OrderLineItemsDto("skuCode2", BigDecimal.valueOf(2000), 20));

        private final OrderRequest orderRequest = new OrderRequest(lineItemDtoList);

        @Test
        @DisplayName("`placeOrder()` converts OrderRequest to Order and saves it to repo if all lineItems are available")
        public void placeOrder_SavesOrderToRepo_WhenStockIsAvailable() throws InternalServerException,
                        InventoryNotInStockException, ExecutionException, InterruptedException {
                // Given
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryStockStatus("skuCode1", 10),
                                                                new InventoryStockStatus("skuCode2", 20))));

                final SavedOrder expectedSavedOrder = new SavedOrder("1", orderNumber);
                when(orderTransactionService.executeTransactionalOrderPlacement(orderRequest))
                                .thenReturn(expectedSavedOrder);

                // Call method to test
                final var savedOrder = orderService.placeOrder(orderRequest).get();

                // Assert value
                verify(orderTransactionService).executeTransactionalOrderPlacement(orderRequest);
                assertEquals(orderNumber, savedOrder.orderNumber());
                assertEquals("1", savedOrder.orderId());
        }

        @Test
        @DisplayName("`placeOrder()` throws InventoryNotInStockException if none of the stocks are found")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenNoneStockWasPresent()
                        throws InternalServerException {
                // Mock availability of stock
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Not available
                                                                new InventoryStockStatus("skuCode1", 0),
                                                                // Not available
                                                                new InventoryStockStatus("skuCode2", 0))));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InventoryNotInStockException if some of the stock was not available")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenSomeStockIsNotAvailable()
                        throws InternalServerException {
                // Mock availability of stock
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Not available
                                                                new InventoryStockStatus("skuCode1", 0),
                                                                // Available
                                                                new InventoryStockStatus("skuCode2", 20))));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InternalServerException if some of the stock's entry was not found")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenStatusForSomeStockIsMissing()
                        throws InternalServerException {
                // Mock availability of stock - Note entry for skuCode1 is missing
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryStockStatus("skuCode2", 20))));
                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InternalServerException from InventoryStatusRepository")
        public void placeOrder_ForwardsInternalServerException_FromInventoryStatusRepository()
                        throws InternalServerException {
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));
                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InvalidInventoryException from InventoryStatusRepository")
        public void placeOrder_ForwardsInvalidInventoryException_FromInventoryStatusRepository()
                        throws InvalidInventoryException {
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(CompletableFuture.failedFuture(new InvalidInventoryException()));
                // Assert
                assertInvalidInventoryExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` forwards InvalidInputException from InventoryStatusRepository")
        public void placeOrder_ForwardsInvalidInputException_FromInventoryStatusRepository()
                        throws InvalidInputException {
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(CompletableFuture.failedFuture(new InvalidInputException()));
                // Assert
                assertInvalidInputExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InternalServerException when OrderTransactionService throws InternalServerException")
        public void placeOrder_ThrowsInternalServerException_WhenOrderTransactionServiceThrowsError() {
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                new InventoryStockStatus("skuCode1", 10),
                                                                new InventoryStockStatus("skuCode2", 20))));
                when(orderTransactionService.executeTransactionalOrderPlacement(orderRequest))
                                .thenThrow(new InternalServerException());

                // Assert
                assertInternalServerExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InventoryNotInStockException when requested quantity is more than available quantity")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenRequestedQuantityExceedsAvailable()
                        throws InternalServerException {
                // Mock availability of stock with insufficient quantity
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Available: 3, Requested: 10
                                                                new InventoryStockStatus("skuCode1", 3),
                                                                // Available: 15, Requested: 20
                                                                new InventoryStockStatus("skuCode2", 15))));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` throws InventoryNotInStockException when any item has zero quantity")
        public void placeOrder_ThrowsInventoryNotInStockException_WhenAnyItemHasZeroQuantity()
                        throws InternalServerException {
                // Mock availability of stock with zero quantity
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Available:0, Requested: 10
                                                                new InventoryStockStatus("skuCode1", 0),
                                                                // Available:20, Requested: 20
                                                                new InventoryStockStatus("skuCode2", 20))));
                // Assert
                assertInventoryNotInStockExceptionIsThrown();
        }

        @Test
        @DisplayName("`placeOrder()` succeeds when all items have sufficient quantity")
        public void placeOrder_Succeeds_WhenAllItemsHaveSufficientQuantity() throws InternalServerException,
                        InventoryNotInStockException, ExecutionException, InterruptedException {
                when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                                .thenReturn(
                                                CompletableFuture.completedFuture(List.of(
                                                                // Available:20, Requested: 10
                                                                new InventoryStockStatus("skuCode1", 15),
                                                                // Available:25, Requested: 20
                                                                new InventoryStockStatus("skuCode2", 25))));

                final SavedOrder expectedSavedOrder = new SavedOrder("1", orderNumber);
                when(orderTransactionService.executeTransactionalOrderPlacement(orderRequest))
                                .thenReturn(expectedSavedOrder);

                // Call method to test
                final var savedOrder = orderService.placeOrder(orderRequest).get();

                // Assert value
                verify(orderTransactionService).executeTransactionalOrderPlacement(orderRequest);
                assertEquals(orderNumber, savedOrder.orderNumber());
                assertEquals("1", savedOrder.orderId());
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

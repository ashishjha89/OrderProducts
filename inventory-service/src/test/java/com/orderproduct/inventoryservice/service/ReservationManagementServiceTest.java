package com.orderproduct.inventoryservice.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.ItemAvailability;
import com.orderproduct.inventoryservice.dto.response.ReservationStateUpdateResponse;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.service.inventory.ItemOnHandService;
import com.orderproduct.inventoryservice.service.reservation.ReservationService;

public class ReservationManagementServiceTest {

        private final ItemOnHandService itemOnHandService = mock(ItemOnHandService.class);
        private final ReservationService reservationService = mock(ReservationService.class);
        private final ReservationManagementService reservationManagementService = new ReservationManagementService(
                        itemOnHandService, reservationService);

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should successfully reserve products when sufficient items are available")
        public void reserveProductsIfAvailable_SufficientItems_ReservesSuccessfully()
                        throws NotEnoughItemException, InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5), // requesting 5
                                new ItemReservationRequest("skuCode2", 10)); // requesting 10
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15), // 15 on hand
                                new ItemOnHandQuantity("skuCode2", 20)); // 20 on hand

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 3), // 3 reserved
                                new ReservedItemQuantity("skuCode2", 5)); // 5 reserved

                final var expectedResponses = List.of(
                                // 15 - 3 - 5 = 7 available after reservation
                                new AvailableInventoryResponse("skuCode1", 7),
                                // 20 - 5 - 10 = 5 available after reservation
                                new AvailableInventoryResponse("skuCode2", 5));

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);

                // When
                List<AvailableInventoryResponse> result = reservationManagementService
                                .reserveProductsIfAvailable(request);

                // Then
                assertEquals(expectedResponses, result);
                verify(reservationService).reserveProducts(request);
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should throw NotEnoughItemException when insufficient item is available")
        public void reserveProductsIfAvailable_InsufficientItem_ThrowsNotEnoughItemExceptionn() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 10), // requesting 10
                                new ItemReservationRequest("skuCode2", 20)); // requesting 20
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 8), // only 8 on hand
                                new ItemOnHandQuantity("skuCode2", 15)); // only 15 on hand

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 2), // 2 reserved
                                new ReservedItemQuantity("skuCode2", 3)); // 3 reserved

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);

                // Then
                assertThatThrownBy(() -> reservationManagementService.reserveProductsIfAvailable(request))
                                .isInstanceOf(NotEnoughItemException.class)
                                .satisfies(exception -> {
                                        NotEnoughItemException notEnoughItemException = (NotEnoughItemException) exception;
                                        List<ItemAvailability> unavailableProducts = notEnoughItemException
                                                        .getUnavailableProducts();
                                        assertEquals(2, unavailableProducts.size());

                                        // Check first unavailable product
                                        ItemAvailability first = unavailableProducts.get(0);
                                        assertEquals("skuCode1", first.skuCode());
                                        assertEquals(10, first.requestedQuantity());
                                        assertEquals(6, first.availableQuantity()); // 8 - 2 = 6

                                        // Check second unavailable product
                                        ItemAvailability second = unavailableProducts.get(1);
                                        assertEquals("skuCode2", second.skuCode());
                                        assertEquals(20, second.requestedQuantity());
                                        assertEquals(12, second.availableQuantity()); // 15 - 3 = 12
                                });
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should throw NotEnoughItemException when some products have insufficient item")
        public void reserveProductsIfAvailable_PartialInsufficientItem_ThrowsNotEnoughItemException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5), // requesting 5 (sufficient)
                                new ItemReservationRequest("skuCode2", 20)); // requesting 20 (insufficient)
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15), // 15 on hand
                                new ItemOnHandQuantity("skuCode2", 15)); // 15 on hand

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 3), // 3 reserved
                                new ReservedItemQuantity("skuCode2", 3)); // 3 reserved

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);

                // Then
                assertThatThrownBy(() -> reservationManagementService.reserveProductsIfAvailable(request))
                                .isInstanceOf(NotEnoughItemException.class)
                                .satisfies(exception -> {
                                        NotEnoughItemException notEnoughItemException = (NotEnoughItemException) exception;
                                        List<ItemAvailability> unavailableProducts = notEnoughItemException
                                                        .getUnavailableProducts();
                                        assertEquals(1, unavailableProducts.size());

                                        // Check the unavailable product
                                        ItemAvailability unavailable = unavailableProducts.get(0);
                                        assertEquals("skuCode2", unavailable.skuCode());
                                        assertEquals(20, unavailable.requestedQuantity());
                                        assertEquals(12, unavailable.availableQuantity()); // 15 - 3 = 12
                                });
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should throw InternalServerException when ItemOnHandService throws error while getting available inventory")
        public void reserveProductsIfAvailable_ItemOnHandServiceError_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5),
                                new ItemReservationRequest("skuCode2", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenThrow(new InternalServerException());

                // Then
                assertThrows(InternalServerException.class,
                                () -> reservationManagementService.reserveProductsIfAvailable(request));
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should throw InternalServerException when ReservationService throws error while getting reservations")
        public void reserveProductsIfAvailable_ReservationServiceFindPendingReservationError_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5),
                                new ItemReservationRequest("skuCode2", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15),
                                new ItemOnHandQuantity("skuCode2", 20));

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenThrow(new InternalServerException());

                // Then
                assertThrows(InternalServerException.class,
                                () -> reservationManagementService.reserveProductsIfAvailable(request));
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should throw InternalServerException when ReservationService throws error while reserving products")
        public void reserveProductsIfAvailable_ReservationServiceReserveProductsError_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5),
                                new ItemReservationRequest("skuCode2", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15),
                                new ItemOnHandQuantity("skuCode2", 20));

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 3),
                                new ReservedItemQuantity("skuCode2", 5));

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);
                doThrow(new InternalServerException()).when(reservationService).reserveProducts(request);

                // Then
                assertThrows(InternalServerException.class,
                                () -> reservationManagementService.reserveProductsIfAvailable(request));
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should handle edge case with zero quantities")
        public void reserveProductsIfAvailable_ZeroQuantities_HandlesCorrectly()
                        throws NotEnoughItemException, InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 0), // requesting 0
                                new ItemReservationRequest("skuCode2", 10)); // requesting 10
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 0), // 0 on hand
                                new ItemOnHandQuantity("skuCode2", 10)); // 10 on hand

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 0), // 0 reserved
                                new ReservedItemQuantity("skuCode2", 0)); // 0 reserved

                final var expectedResponses = List.of(
                                // 0 - 0 = 0 available after reservation
                                new AvailableInventoryResponse("skuCode1", 0),
                                // 10 - 10 = 0 available after
                                new AvailableInventoryResponse("skuCode2", 0));

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);

                // When
                List<AvailableInventoryResponse> result = reservationManagementService
                                .reserveProductsIfAvailable(request);

                // Then
                assertEquals(expectedResponses, result);
                verify(reservationService).reserveProducts(request);
        }

        @Test
        @DisplayName("`reserveProductsIfAvailable()` should handle case where all requested quantities are zero")
        public void reserveProductsIfAvailable_AllZeroQuantities_ReservesSuccessfully()
                        throws NotEnoughItemException, InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 0),
                                new ItemReservationRequest("skuCode2", 0));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 10), // 10 on hand
                                new ItemOnHandQuantity("skuCode2", 20)); // 20 on hand

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 5), // 5 reserved
                                new ReservedItemQuantity("skuCode2", 10)); // 10 reserved

                final var expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 5), // 10 - 5 = 5 available
                                new AvailableInventoryResponse("skuCode2", 10)); // 20 - 10 = 10 available

                when(itemOnHandService.itemAvailabilities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(List.of("skuCode1", "skuCode2")))
                                .thenReturn(reservedQuantities);

                // When
                List<AvailableInventoryResponse> result = reservationManagementService
                                .reserveProductsIfAvailable(request);

                // Then
                assertEquals(expectedResponses, result);
                verify(reservationService).reserveProducts(request);
        }

        @Test
        @DisplayName("`updateReservationState()` should successfully update reservation state and return response")
        public void updateReservationState_SuccessfulUpdate_ReturnsResponse() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                final var updatedReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(newState)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(newState)
                                                .build());

                final var expectedResponse = new ReservationStateUpdateResponse(
                                orderNumber,
                                newState,
                                List.of(
                                                new ReservationStateUpdateResponse.ReservationItemResponse("skuCode1",
                                                                5, newState),
                                                new ReservationStateUpdateResponse.ReservationItemResponse("skuCode2",
                                                                10, newState)));

                when(reservationService.updateReservationState(request)).thenReturn(updatedReservations);

                // When
                ReservationStateUpdateResponse result = reservationManagementService.updateReservationState(request);

                // Then
                assertEquals(expectedResponse, result);
                verify(reservationService).updateReservationState(request);
        }

        @Test
        @DisplayName("`updateReservationState()` should handle empty result when no reservations found")
        public void updateReservationState_NoReservationsFound_ReturnsEmptyResponse() throws InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                final var expectedResponse = new ReservationStateUpdateResponse(
                                orderNumber,
                                newState,
                                List.of());

                when(reservationService.updateReservationState(request)).thenReturn(List.of());

                // When
                ReservationStateUpdateResponse result = reservationManagementService.updateReservationState(request);

                // Then
                assertEquals(expectedResponse, result);
                verify(reservationService).updateReservationState(request);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when ReservationService throws error")
        public void updateReservationState_ReservationServiceError_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                when(reservationService.updateReservationState(request))
                                .thenThrow(new InternalServerException());

                // Then
                assertThrows(InternalServerException.class,
                                () -> reservationManagementService.updateReservationState(request));
        }
}
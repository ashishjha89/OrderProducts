package com.orderproduct.inventoryservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.service.inventory.ItemOnHandService;
import com.orderproduct.inventoryservice.service.reservation.ReservationService;

public class InventoryAvailabilityServiceTest {

        private final ItemOnHandService itemOnHandService = mock(ItemOnHandService.class);
        private final ReservationService reservationService = mock(ReservationService.class);
        private final InventoryAvailabilityService inventoryAvailabilityService = new InventoryAvailabilityService(
                        itemOnHandService, reservationService);

        @Test
        @DisplayName("`getAvailableInventory()` should return available inventory for given skuCodes")
        public void getAvailableInventory_ValidSkuCodes_ReturnsAvailableInventory() throws InternalServerException {
                // Given
                final var skuCodes = List.of("skuCode1", "skuCode2", "skuCode3");
                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15),
                                new ItemOnHandQuantity("skuCode2", 20),
                                new ItemOnHandQuantity("skuCode3", 0));

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 3),
                                new ReservedItemQuantity("skuCode2", 5));

                final var expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 12), // 15 - 3 = 12
                                new AvailableInventoryResponse("skuCode2", 15), // 20 - 5 = 15
                                new AvailableInventoryResponse("skuCode3", 0)); // 0 - 0 = 0

                when(itemOnHandService.itemAvailabilities(skuCodes))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(skuCodes))
                                .thenReturn(reservedQuantities);

                // When
                List<AvailableInventoryResponse> result = inventoryAvailabilityService.getAvailableInventory(skuCodes);

                // Then
                assertEquals(expectedResponses, result);
        }

        @Test
        @DisplayName("`getAvailableInventory()` should handle empty skuCodes list")
        public void getAvailableInventory_EmptySkuCodes_ReturnsEmptyList() throws InternalServerException {
                // Given
                final var skuCodes = List.<String>of();

                when(itemOnHandService.itemAvailabilities(skuCodes))
                                .thenReturn(List.of());
                when(reservationService.findPendingReservedQuantities(skuCodes))
                                .thenReturn(List.of());

                // When
                List<AvailableInventoryResponse> result = inventoryAvailabilityService.getAvailableInventory(skuCodes);

                // Then
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("`getAvailableInventory()` should throw InternalServerException when ItemOnHandService throws error")
        public void getAvailableInventory_ItemOnHandServiceError_ThrowsInternalServerException() {
                // Given
                final var skuCodes = List.of("skuCode1", "skuCode2");

                when(itemOnHandService.itemAvailabilities(skuCodes))
                                .thenThrow(new InternalServerException());

                // Then
                assertThrows(InternalServerException.class,
                                () -> inventoryAvailabilityService.getAvailableInventory(skuCodes));
        }

        @Test
        @DisplayName("`getAvailableInventory()` should handle when no reserved quantities exist")
        public void getAvailableInventory_NoReservedQuantities_ReturnsFullOnHandQuantities()
                        throws InternalServerException {
                // Given
                final var skuCodes = List.of("skuCode1", "skuCode2");
                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 15),
                                new ItemOnHandQuantity("skuCode2", 20));

                when(itemOnHandService.itemAvailabilities(skuCodes))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(skuCodes))
                                .thenReturn(List.of());

                final var expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 15), // 15 - 0 = 15
                                new AvailableInventoryResponse("skuCode2", 20)); // 20 - 0 = 20

                // When
                List<AvailableInventoryResponse> result = inventoryAvailabilityService.getAvailableInventory(skuCodes);

                // Then
                assertEquals(expectedResponses, result);
        }

        @Test
        @DisplayName("`getAvailableInventory()` should handle edge case with zero quantities")
        public void getAvailableInventory_ZeroQuantities_HandlesCorrectly() throws InternalServerException {
                // Given
                final var skuCodes = List.of("skuCode1", "skuCode2");
                final var itemOnHandQuantities = List.of(
                                new ItemOnHandQuantity("skuCode1", 0),
                                new ItemOnHandQuantity("skuCode2", 5));

                final var reservedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 0),
                                new ReservedItemQuantity("skuCode2", 10)); // More reserved than on hand

                final var expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 0), // 0 - 0 = 0
                                new AvailableInventoryResponse("skuCode2", 0)); // 5 - 10 = 0 (minimum 0)

                when(itemOnHandService.itemAvailabilities(skuCodes))
                                .thenReturn(itemOnHandQuantities);
                when(reservationService.findPendingReservedQuantities(skuCodes))
                                .thenReturn(reservedQuantities);

                // When
                List<AvailableInventoryResponse> result = inventoryAvailabilityService.getAvailableInventory(skuCodes);

                // Then
                assertEquals(expectedResponses, result);
        }
}
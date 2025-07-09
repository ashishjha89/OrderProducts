package com.orderproduct.inventoryservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.inventoryservice.common.exception.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.dto.request.CreateInventoryRequest;
import com.orderproduct.inventoryservice.dto.request.UpdateInventoryRequest;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UpdateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.service.inventory.ItemOnHandService;

public class InventoryManagementServiceTest {

        private final ItemOnHandService itemOnHandService = mock(ItemOnHandService.class);
        private final InventoryManagementService inventoryManagementService = new InventoryManagementService(
                        itemOnHandService);

        @Test
        @DisplayName("`createInventory()` should successfully create inventory")
        public void createInventory_ValidRequest_CreatesSuccessfully()
                        throws InternalServerException, DuplicateSkuCodeException {
                // Given
                final var request = new CreateInventoryRequest("skuCode1", 100);
                final var expectedResponse = CreateInventoryResponse.success("skuCode1");

                when(itemOnHandService.createInventory(any(Inventory.class)))
                                .thenReturn(expectedResponse);

                // When
                CreateInventoryResponse result = inventoryManagementService.createInventory(request);

                // Then
                assertEquals(expectedResponse, result);
                verify(itemOnHandService).createInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("`createInventory()` should throw DuplicateSkuCodeException when item on hand service throws it")
        public void createInventory_DuplicateSkuCode_ThrowsDuplicateSkuCodeException() {
                // Given
                final var request = new CreateInventoryRequest("skuCode1", 100);

                doThrow(new DuplicateSkuCodeException())
                                .when(itemOnHandService).createInventory(any(Inventory.class));

                // Then
                assertThrows(DuplicateSkuCodeException.class,
                                () -> inventoryManagementService.createInventory(request));
        }

        @Test
        @DisplayName("`createInventory()` should throw InternalServerException when item on hand service throws it")
        public void createInventory_ItemOnHandServiceError_ThrowsInternalServerException() {
                // Given
                final var request = new CreateInventoryRequest("skuCode1", 100);

                doThrow(new InternalServerException())
                                .when(itemOnHandService).createInventory(any(Inventory.class));

                // Then
                assertThrows(InternalServerException.class, () -> inventoryManagementService.createInventory(request));
        }

        @Test
        @DisplayName("`updateInventory()` should successfully update inventory")
        public void updateInventory_ValidRequest_UpdatesSuccessfully()
                        throws InternalServerException, NotFoundException {
                // Given
                final var request = new UpdateInventoryRequest("skuCode1", 150);
                final var expectedResponse = UpdateInventoryResponse.success("skuCode1", 150);

                when(itemOnHandService.updateInventory("skuCode1", 150))
                                .thenReturn(expectedResponse);

                // When
                UpdateInventoryResponse result = inventoryManagementService.updateInventory(request);

                // Then
                assertEquals(expectedResponse, result);
                verify(itemOnHandService).updateInventory("skuCode1", 150);
        }

        @Test
        @DisplayName("`updateInventory()` should throw NotFoundException when ItemOnHandService throws it")
        public void updateInventory_NonExistentSkuCode_ThrowsNotFoundException() {
                // Given
                final var request = new UpdateInventoryRequest("nonExistentSku", 50);

                doThrow(new NotFoundException())
                                .when(itemOnHandService).updateInventory("nonExistentSku", 50);

                // When & Then
                assertThrows(NotFoundException.class, () -> inventoryManagementService.updateInventory(request));
                verify(itemOnHandService).updateInventory("nonExistentSku", 50);
        }

        @Test
        @DisplayName("`updateInventory()` should throw InternalServerException when ItemOnHandService throws it")
        public void updateInventory_ItemOnHandServiceError_ThrowsInternalServerException() {
                // Given
                final var request = new UpdateInventoryRequest("skuCode1", 150);

                doThrow(new InternalServerException())
                                .when(itemOnHandService).updateInventory("skuCode1", 150);

                // Then
                assertThrows(InternalServerException.class, () -> inventoryManagementService.updateInventory(request));
        }

        @Test
        @DisplayName("`deleteInventory()` should successfully delete inventory")
        public void deleteInventory_ValidSkuCode_DeletesSuccessfully()
                        throws InternalServerException, NotFoundException {
                // Given
                final var skuCode = "skuCode1";

                // When
                inventoryManagementService.deleteInventory(skuCode);

                // Then
                verify(itemOnHandService).deleteInventory(skuCode);
        }

        @Test
        @DisplayName("`deleteInventory()` should throw NotFoundException when item on hand service throws it")
        public void deleteInventory_NonExistentSkuCode_ThrowsNotFoundException() {
                // Given
                final var skuCode = "nonExistentSku";

                doThrow(new NotFoundException())
                                .when(itemOnHandService).deleteInventory(skuCode);

                // Then
                assertThrows(NotFoundException.class, () -> inventoryManagementService.deleteInventory(skuCode));
        }

        @Test
        @DisplayName("`deleteInventory()` should throw InternalServerException when item on hand service throws it")
        public void deleteInventory_ItemOnHandServiceError_ThrowsInternalServerException() {
                // Given
                final var skuCode = "skuCode1";

                doThrow(new InternalServerException())
                                .when(itemOnHandService).deleteInventory(skuCode);

                // Then
                assertThrows(InternalServerException.class, () -> inventoryManagementService.deleteInventory(skuCode));
        }
}
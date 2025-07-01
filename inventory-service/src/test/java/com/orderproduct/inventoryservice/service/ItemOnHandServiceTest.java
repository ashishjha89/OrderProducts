package com.orderproduct.inventoryservice.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import com.orderproduct.inventoryservice.common.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.common.NotFoundException;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;

public class ItemOnHandServiceTest {

        private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);

        private final ItemOnHandService itemOnHandService = new ItemOnHandService(inventoryRepository);

        @Test
        @DisplayName("`itemAvailabilities()` should return `List<ItemOnHandQuantity>` for passed skuCodes with their respective quantities")
        public void itemAvailabilitiesTest() throws InternalServerException {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
                final var matchingInventories = List.of(
                                new Inventory(1L, "skuCode1", 10),
                                new Inventory(2L, "skuCode2", 0),
                                new Inventory(4L, "skuCode4", 15));
                when(inventoryRepository.findBySkuCodeIn(skuCodeList)).thenReturn(matchingInventories);
                final var expectedStatus = List.of(
                                new ItemOnHandQuantity("skuCode1", 10),
                                new ItemOnHandQuantity("skuCode2", 0),
                                new ItemOnHandQuantity("skuCode3", 0), // Note that skuCode3 is not in the
                                                                       // matchingInventories list
                                new ItemOnHandQuantity("skuCode4", 15));

                // Then
                assertEquals(expectedStatus, itemOnHandService.itemAvailabilities(skuCodeList));
        }

        @Test
        @DisplayName("`itemAvailabilities()` should throw InternalServerException when Repo throws DataAccessException")
        public void itemAvailabilities_WhenRepoThrowsError() {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
                when(inventoryRepository.findBySkuCodeIn(skuCodeList))
                                .thenThrow(new DataAccessResourceFailureException(
                                                "Child class of DataAccessException"));

                // Then
                assertThrows(InternalServerException.class, () -> itemOnHandService.itemAvailabilities(skuCodeList));
        }

        @Test
        @DisplayName("`createInventory()` should return CreateInventoryResponse when inventory is created")
        void createInventory_ValidInventory_ReturnsSuccessResponse() {
                // Given
                var inventory = Inventory.createInventory("SKU-123", 10);
                when(inventoryRepository.save(inventory)).thenReturn(inventory);

                // When
                CreateInventoryResponse response = itemOnHandService.createInventory(inventory);

                // Then
                assertThat(response.skuCode()).isEqualTo("SKU-123");
        }

        @Test
        @DisplayName("`createInventory()` should throw DuplicateSkuCodeException when repository throws DataIntegrityViolationException")
        void createInventory_DuplicateSkuCode_ThrowsDuplicateSkuCodeException() {
                // Given
                var inventory = Inventory.createInventory("SKU-123", 10);
                when(inventoryRepository.save(inventory))
                                .thenThrow(
                                                new DataIntegrityViolationException(
                                                                "some msg",
                                                                new ConstraintViolationException("", new SQLException(),
                                                                                "inventory_pkey")));

                // Then
                assertThatThrownBy(() -> itemOnHandService.createInventory(inventory))
                                .isInstanceOf(DuplicateSkuCodeException.class);
        }

        @Test
        @DisplayName("`createInventory()` should throw InternalServerException when repository throws DataAccessException")
        void createInventory_DatabaseError_ThrowsInternalServerException() {
                // Given
                var inventory = Inventory.createInventory("SKU-123", 10);
                when(inventoryRepository.save(inventory))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> itemOnHandService.createInventory(inventory))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`deleteInventory()` should succeed when inventory exists")
        void deleteInventory_ExistingSkuCode_Succeeds() throws Exception {
                // Given
                when(inventoryRepository.deleteBySkuCode("SKU-123")).thenReturn(1);

                // When & Then
                assertThatNoException()
                                .isThrownBy(() -> itemOnHandService.deleteInventory("SKU-123"));
        }

        @Test
        @DisplayName("`deleteInventory()` should throw NotFoundException when inventory does not exist")
        void deleteInventory_NonExistentSkuCode_ThrowsNotFoundException() {
                // Given
                when(inventoryRepository.deleteBySkuCode("NON-EXISTENT")).thenReturn(0);

                // Then
                assertThatThrownBy(() -> itemOnHandService.deleteInventory("NON-EXISTENT"))
                                .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("`deleteInventory()` should throw InternalServerException when repository throws DataAccessException")
        void deleteInventory_DatabaseError_ThrowsInternalServerException() {
                // Given
                when(inventoryRepository.deleteBySkuCode("SKU-123"))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> itemOnHandService.deleteInventory("SKU-123"))
                                .isInstanceOf(InternalServerException.class);
        }
}
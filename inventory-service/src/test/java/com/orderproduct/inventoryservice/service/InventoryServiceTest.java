package com.orderproduct.inventoryservice.service;

import com.orderproduct.inventoryservice.common.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryServiceTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);

    private final InventoryService inventoryService = new InventoryService(inventoryRepository);

    @Test
    @DisplayName("`inStock()` should return InventoryStockStatus(true) if Inventory is found")
    public void isInStock_WhenInventoryIsFound() throws InternalServerException {
        // Given
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.of(new Inventory(1L, "skuCode", 10)));

        // Then
        assertEquals(new InventoryStockStatus("skuCode", true), inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`inStock()` return InventoryStockStatus(false) if Inventory is NOT found")
    public void isInStock_WhenInventoryIsNotFound() throws InternalServerException {
        // Given
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.empty());

        // Then
        assertEquals(new InventoryStockStatus("skuCode", false), inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`inStock()` throws InternalServerException when Repo throws DataAccessException")
    public void isInStock_WhenRepoThrowsError() {
        // Given
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Then
        assertThrows(InternalServerException.class, () -> inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`stocksStatus()` should return `List<InventoryStockStatus>` for passed skuCodes, such that InventoryStockStatus.inStock=true if inventory is in DB and quantity>0")
    public void stocksStatusTest() throws InternalServerException {
        // Given
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        final var matchingInventories = List.of(
                new Inventory(1L, "skuCode1", 10),
                new Inventory(2L, "skuCode2", 0),
                new Inventory(4L, "skuCode4", 15)
        );
        when(inventoryRepository.findBySkuCodeIn(skuCodeList)).thenReturn(matchingInventories);
        final var expectedStatus = List.of(
                new InventoryStockStatus("skuCode1", true),
                new InventoryStockStatus("skuCode2", false),
                new InventoryStockStatus("skuCode3", false),
                new InventoryStockStatus("skuCode4", true)
        );

        // Then
        assertEquals(expectedStatus, inventoryService.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`stocksStatus()` should throw InternalServerException when repo returns null inventories")
    public void stocksStatus_WhenInventoryIsReceivedAsNullFromRepo() {
        // Given
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        when(inventoryRepository.findBySkuCodeIn(skuCodeList)).thenReturn(null);

        // Then
        assertThrows(InternalServerException.class, () -> inventoryService.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`stocksStatus()` should throw InternalServerException when Repo throws DataAccessException")
    public void stocksStatus_WhenRepoThrowsError() {
        // Given
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        when(inventoryRepository.findBySkuCodeIn(skuCodeList))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Then
        assertThrows(InternalServerException.class, () -> inventoryService.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`createInventory()` should return CreateInventoryResponse when inventory is created")
    void createInventory_ValidInventory_ReturnsSuccessResponse() {
        // Given
        var inventory = Inventory.builder()
                .skuCode("SKU-123")
                .quantity(10)
                .build();
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        // When
        CreateInventoryResponse response = inventoryService.createInventory(inventory);

        // Then
        assertThat(response.skuCode()).isEqualTo("SKU-123");
    }

    @Test
    @DisplayName("`createInventory()` should throw DuplicateSkuCodeException when repository throws DataIntegrityViolationException")
    void createInventory_DuplicateSkuCode_ThrowsDuplicateSkuCodeException() {
        // Given
        var inventory = Inventory.builder()
                .skuCode("SKU-123")
                .quantity(10)
                .build();
        when(inventoryRepository.save(inventory))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "some msg",
                                new ConstraintViolationException("", new SQLException(), "inventory_pkey")
                        )
                );

        // Then
        assertThatThrownBy(() -> inventoryService.createInventory(inventory))
                .isInstanceOf(DuplicateSkuCodeException.class);
    }

    @Test
    void createInventory_DatabaseError_ThrowsInternalServerException() {
        // Given
        var inventory = Inventory.builder()
                .skuCode("SKU-123")
                .quantity(10)
                .build();
        when(inventoryRepository.save(inventory))
                .thenThrow(new DataAccessException("Database connection failed") {
                });

        // Then
        assertThatThrownBy(() -> inventoryService.createInventory(inventory))
                .isInstanceOf(InternalServerException.class);
    }
}

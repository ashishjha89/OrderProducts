package com.orderproduct.inventoryservice.service;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryServiceTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);

    private final InventoryService inventoryService = new InventoryService(inventoryRepository);

    @Test
    @DisplayName("`isInStock()` should return InventoryStockStatus(true) if Inventory is found")
    public void isInStock_WhenInventoryIsFound() throws InternalServerException {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.of(new Inventory(1L, "skuCode", 10)));

        // Call method to test
        assertEquals(new InventoryStockStatus("skuCode", true), inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`isInStock()` return InventoryStockStatus(false) if Inventory is NOT found")
    public void isInStock_WhenInventoryIsNotFound() throws InternalServerException {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.empty());

        // Call method to test
        assertEquals(new InventoryStockStatus("skuCode", false), inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`isInStock()` throws InternalServerException when Repo throws DataAccessException")
    public void isInStock_WhenRepoThrowsError() {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Call method to test
        assertThrows(InternalServerException.class, () -> inventoryService.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`stocksStatus()` should return `List<InventoryStockStatus>` for passed skuCodes, such that InventoryStockStatus.isInStock=true if inventory is in DB and quantity>0")
    public void stocksStatusTest() throws InternalServerException {
        // Initialise
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        final var matchingInventories = List.of(
                new Inventory(1L, "skuCode1", 10),
                new Inventory(2L, "skuCode2", 0),
                new Inventory(3L, "skuCode4", 15)
        );
        when(inventoryRepository.findBySkuCodeIn(skuCodeList)).thenReturn(matchingInventories);
        final var expectedStatus = List.of(
                new InventoryStockStatus("skuCode1", true),
                new InventoryStockStatus("skuCode2", false),
                new InventoryStockStatus("skuCode3", false),
                new InventoryStockStatus("skuCode4", true)
        );

        // Call method to test
        assertEquals(expectedStatus, inventoryService.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`stocksStatus()` should throw InternalServerException when repo returns null inventories")
    public void stocksStatus_WhenInventoryIsReceivedAsNullFromRepo() {
        // Initialise
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        when(inventoryRepository.findBySkuCodeIn(skuCodeList)).thenReturn(null);

        // Call method to test
        assertThrows(InternalServerException.class, () -> inventoryService.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`stocksStatus()` should throw InternalServerException when Repo throws DataAccessException")
    public void stocksStatus_WhenRepoThrowsError() {
        // Initialise
        final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
        when(inventoryRepository.findBySkuCodeIn(skuCodeList))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Call method to test
        assertThrows(InternalServerException.class, () -> inventoryService.stocksStatus(skuCodeList));
    }
}

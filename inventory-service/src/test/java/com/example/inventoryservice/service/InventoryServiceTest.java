package com.example.inventoryservice.service;

import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryServiceTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);

    private final InventoryService inventoryService = new InventoryService(inventoryRepository);

    @Test
    @DisplayName("isInStock() return InventoryStockStatus(true) if Inventory is found")
    public void isInStock_WhenInventoryIsFound() throws InternalServerException {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.of(new Inventory(1L, "skuCode", 10)));

        // Call method to test
        assertEquals(new InventoryStockStatus(true), inventoryService.isInStock("skuCode"));

    }

    @Test
    @DisplayName("isInStock() return InventoryStockStatus(false) if Inventory is NOT found")
    public void isInStock_WhenInventoryIsNotFound() throws InternalServerException {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenReturn(Optional.empty());

        // Call method to test
        assertEquals(new InventoryStockStatus(false), inventoryService.isInStock("skuCode"));

    }

    @Test
    @DisplayName("isInStock throws InternalServerException when Repo throws DataAccessException")
    public void isInStock_WhenRepoThrowsError() {
        // Initialise
        when(inventoryRepository.findBySkuCode("skuCode"))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Call method to test
        assertThrows(InternalServerException.class, () -> inventoryService.isInStock("skuCode"));

    }
}

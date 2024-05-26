package com.example.inventoryservice.controller;

import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryControllerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);

    private final InventoryController inventoryController = new InventoryController(inventoryService);

    @Test
    @DisplayName("`isInStock()` retrieves `InventoryStockStatus` from `InventoryService.isInStock()`")
    public void isInStockTest() throws InternalServerException {
        // Initialise
        when(inventoryService.isInStock("skuCode")).thenReturn(new InventoryStockStatus("skuCode", true));

        // Call method and assert
        assertEquals(new InventoryStockStatus("skuCode", true), inventoryController.isInStock("skuCode"));
    }

    @Test
    @DisplayName("`isInStock()` forwards InternalServerException from InventoryService")
    public void isInStockInternalServerExceptionTest() throws InternalServerException {
        // Initialise
        when(inventoryService.isInStock("skuCode")).thenThrow(new InternalServerException());

        // Call method and assert
        assertThrows(
                InternalServerException.class,
                () -> inventoryController.isInStock("skuCode")
        );
    }

    @Test
    @DisplayName("`stocksStatus()` retrieves `List<InventoryStockStatus>` from `InventoryService.stocksStatus()`")
    public void stocksStatusTest() throws InternalServerException {
        // Initialise
        final var skuCodeList = List.of("skuCode1", "skuCode2");
        final var stocksStatus = List.of(
                new InventoryStockStatus("skuCode1", false),
                new InventoryStockStatus("skuCode2", true)
        );
        when(inventoryService.stocksStatus(skuCodeList)).thenReturn(stocksStatus);

        // Call method and assert
        assertEquals(stocksStatus, inventoryController.stocksStatus(skuCodeList));
    }

    @Test
    @DisplayName("`stocksStatus()` throws BadRequestException when null or empty List<skuCode> is passed")
    public void stocksStatusInternalServerExceptionTest() throws InternalServerException {
        // Initialise
        final var skuCodeList = List.of("skuCode1", "skuCode2");
        when(inventoryService.stocksStatus(skuCodeList)).thenThrow(new InternalServerException());

        // Call method and assert
        assertThrows(
                InternalServerException.class,
                () -> inventoryController.stocksStatus(skuCodeList)
        );
    }
}

package com.example.inventoryservice.controller;

import com.example.inventoryservice.common.BadRequestException;
import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryControllerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);

    private final InventoryController inventoryController = new InventoryController(inventoryService);

    @Test
    @DisplayName("isInStock() calls InventoryService.isInStock() and retrieves `InventoryStockStatus` from it")
    public void isInStockTest() throws BadRequestException, InternalServerException {
        // Initialise
        when(inventoryService.isInStock("skuCode")).thenReturn(new InventoryStockStatus(true));

        // Call method and assert
        assertEquals(new InventoryStockStatus(true), inventoryController.isInStock("skuCode"));
    }

    @Test
    @DisplayName("isInStock() throws BadRequestException when null or empty skuCode is passed")
    public void isInStockBadRequestExceptionTest() {
        // Call method and assert
        assertThrows(
                BadRequestException.class,
                () -> inventoryController.isInStock(" ")
        );
    }

    @Test
    @DisplayName("isInStock() forwards InternalServerException from InventoryService")
    public void isInStockInternalServerExceptionTest() throws BadRequestException, InternalServerException {
        // Initialise
        when(inventoryService.isInStock("skuCode")).thenThrow(new InternalServerException());

        // Call method and assert
        assertThrows(
                InternalServerException.class,
                () -> inventoryController.isInStock("skuCode")
        );
    }
}

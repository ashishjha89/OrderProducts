package com.orderproduct.orderservice.common;

import org.springframework.http.HttpStatus;

public class InventoryNotInStockException extends ApiException {

    public InventoryNotInStockException() {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE,
                ErrorComponent.inventoryNotInStockMsg
        );
    }
}

package com.orderproduct.orderservice.common;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.orderproduct.orderservice.dto.InventoryServiceErrorBody;

public class InventoryNotInStockException extends ApiException {
    private final List<InventoryServiceErrorBody.UnavailableProduct> unavailableProducts;

    public InventoryNotInStockException() {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE,
                ErrorComponent.inventoryNotInStockMsg);
        this.unavailableProducts = null;
    }

    public InventoryNotInStockException(String errorMessage,
            List<InventoryServiceErrorBody.UnavailableProduct> unavailableProducts) {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE,
                errorMessage != null ? errorMessage : ErrorComponent.inventoryNotInStockMsg);
        this.unavailableProducts = unavailableProducts;
    }

    public List<InventoryServiceErrorBody.UnavailableProduct> getUnavailableProducts() {
        return unavailableProducts;
    }
}

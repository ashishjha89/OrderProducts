package com.orderproduct.orderservice.dto;

public record InventoryStockStatus(String skuCode, int quantity) {

    public InventoryStockStatus {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}
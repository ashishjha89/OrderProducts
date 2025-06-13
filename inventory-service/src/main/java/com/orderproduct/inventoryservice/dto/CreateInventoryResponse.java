package com.orderproduct.inventoryservice.dto;

public record CreateInventoryResponse(
        String skuCode,
        String message) {

    public static CreateInventoryResponse success(String skuCode) {
        return new CreateInventoryResponse(skuCode, "Inventory created successfully");
    }
}

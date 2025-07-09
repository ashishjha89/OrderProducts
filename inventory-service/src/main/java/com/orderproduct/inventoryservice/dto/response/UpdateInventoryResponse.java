package com.orderproduct.inventoryservice.dto.response;

public record UpdateInventoryResponse(String skuCode, int quantity, String message) {

    public static UpdateInventoryResponse success(String skuCode, int quantity) {
        return new UpdateInventoryResponse(skuCode, quantity, "Inventory updated successfully");
    }
}
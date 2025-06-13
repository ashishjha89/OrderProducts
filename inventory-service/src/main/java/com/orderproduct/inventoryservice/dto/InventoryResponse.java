package com.orderproduct.inventoryservice.dto;

import com.orderproduct.inventoryservice.entity.Inventory;

public record InventoryResponse(String skuCode, int quantity) {

    public static InventoryResponse fromEntity(Inventory inventory) {
        return new InventoryResponse(inventory.getSkuCode(), inventory.getQuantity());
    }
}

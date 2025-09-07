package com.orderproduct.inventoryservice.dto.response;

public record ItemAvailability(String skuCode, int requestedQuantity, int availableQuantity) {
}

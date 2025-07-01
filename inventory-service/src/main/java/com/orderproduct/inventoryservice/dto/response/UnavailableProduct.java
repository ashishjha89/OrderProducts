package com.orderproduct.inventoryservice.dto.response;

public record UnavailableProduct(String skuCode, int requestedQuantity, int availableQuantity) {
}

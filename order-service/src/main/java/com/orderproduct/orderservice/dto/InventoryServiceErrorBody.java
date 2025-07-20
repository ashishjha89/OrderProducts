package com.orderproduct.orderservice.dto;

import java.util.List;

public record InventoryServiceErrorBody(
        String errorCode,
        String errorMessage,
        List<UnavailableProduct> unavailableProducts) {

    public record UnavailableProduct(String skuCode, int requestedQuantity, int availableQuantity) {
    }
}
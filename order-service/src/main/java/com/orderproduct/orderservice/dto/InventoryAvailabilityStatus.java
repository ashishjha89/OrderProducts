package com.orderproduct.orderservice.dto;

public record InventoryAvailabilityStatus(String skuCode, int availableQuantity) {

    public InventoryAvailabilityStatus {
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}
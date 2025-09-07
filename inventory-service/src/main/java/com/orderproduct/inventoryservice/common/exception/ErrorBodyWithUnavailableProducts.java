package com.orderproduct.inventoryservice.common.exception;

import java.util.List;

import com.orderproduct.inventoryservice.dto.response.ItemAvailability;

public record ErrorBodyWithUnavailableProducts(
        String errorCode,
        String errorMessage,
        List<ItemAvailability> unavailableProducts) {
}
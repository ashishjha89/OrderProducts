package com.orderproduct.inventoryservice.dto.request;

import java.util.List;

import com.orderproduct.inventoryservice.entity.ReservationState;

import lombok.NonNull;

public record ReservationStateUpdateRequest(
                @NonNull String orderNumber,
                @NonNull List<String> skuCodes,
                @NonNull ReservationState state) {
}
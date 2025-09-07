package com.orderproduct.inventoryservice.dto.request;

import com.orderproduct.inventoryservice.entity.ReservationState;

import lombok.NonNull;

public record ReservationStateUpdateRequest(@NonNull String orderNumber, @NonNull ReservationState state) {
}
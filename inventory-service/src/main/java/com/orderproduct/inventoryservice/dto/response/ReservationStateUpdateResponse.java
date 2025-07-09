package com.orderproduct.inventoryservice.dto.response;

import java.util.List;

import com.orderproduct.inventoryservice.entity.ReservationState;

import lombok.NonNull;

public record ReservationStateUpdateResponse(
                @NonNull String orderNumber,
                @NonNull ReservationState state,
                @NonNull List<ReservationItemResponse> updatedItems) {

        public record ReservationItemResponse(
                        @NonNull String skuCode,
                        int reservedQuantity,
                        @NonNull ReservationState status) {
        }

}
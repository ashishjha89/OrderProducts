package com.orderproduct.orderservice.dto;

import java.util.List;

import lombok.NonNull;

public record OrderReservationRequest(
        @NonNull String orderNumber,
        @NonNull List<ItemReservationRequest> itemReservationRequests) {
}

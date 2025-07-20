package com.orderproduct.orderservice.dto;

import lombok.NonNull;

public record ItemReservationRequest(@NonNull String skuCode, int quantity) {
}

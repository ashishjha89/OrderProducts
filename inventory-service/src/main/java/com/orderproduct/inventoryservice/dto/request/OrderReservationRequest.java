package com.orderproduct.inventoryservice.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record OrderReservationRequest(
                @NotBlank(message = "Order number cannot be blank.") String orderNumber,
                @NotEmpty(message = "Reservation requests cannot be empty.") @Valid List<ItemReservationRequest> itemReservationRequests) {
}

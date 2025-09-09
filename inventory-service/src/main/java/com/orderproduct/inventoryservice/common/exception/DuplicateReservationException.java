package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateReservationException extends ApiException {
    public DuplicateReservationException() {
        super(
                HttpStatus.CONFLICT,
                "DUPLICATE_RESERVATION_ERROR",
                "Duplicate reservation for the same order and SKU code");
    }
}

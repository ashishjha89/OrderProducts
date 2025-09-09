package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

public class NegativeQuantityException extends ApiException {
    public NegativeQuantityException() {
        super(
                HttpStatus.BAD_REQUEST,
                "NEGATIVE_QUANTITY_ERROR",
                "Quantity cannot be negative");
    }
}

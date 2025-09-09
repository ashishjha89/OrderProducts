package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientQuantityException extends ApiException {
    public InsufficientQuantityException() {
        super(
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_QUANTITY_ERROR",
                "Insufficient quantity available for deduction");
    }
}

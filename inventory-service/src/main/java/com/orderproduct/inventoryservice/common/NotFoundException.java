package com.orderproduct.inventoryservice.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException() {
        super(ErrorComponent.NOT_FOUND, HttpStatus.NOT_FOUND, ErrorComponent.notFoundMsg);
    }
}

package com.orderproduct.inventoryservice.common;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException() {
        super(ErrorComponent.BAD_REQUEST, HttpStatus.BAD_REQUEST, ErrorComponent.badRequestMsg);
    }
}
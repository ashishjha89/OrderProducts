package com.orderproduct.productservice.common;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException() {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorComponent.BAD_REQUEST_ERROR_CODE,
                ErrorComponent.badRequestMsg
        );
    }
}


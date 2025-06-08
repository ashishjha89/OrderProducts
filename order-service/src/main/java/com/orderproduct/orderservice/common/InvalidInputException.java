package com.orderproduct.orderservice.common;

import org.springframework.http.HttpStatus;

public class InvalidInputException extends ApiException {

    public InvalidInputException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorComponent.BAD_REQUEST_ERROR_CODE,
                message
        );
    }

    public InvalidInputException() {
        this(ErrorComponent.badRequestMsg);
    }
}

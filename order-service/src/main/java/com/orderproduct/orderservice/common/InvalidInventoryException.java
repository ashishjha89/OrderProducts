package com.orderproduct.orderservice.common;

import org.springframework.http.HttpStatus;

public class InvalidInventoryException extends ApiException {

    public InvalidInventoryException() {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE,
                ErrorComponent.somethingWentWrongMsg);
    }
}

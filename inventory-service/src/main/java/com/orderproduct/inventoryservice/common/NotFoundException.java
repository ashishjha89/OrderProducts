package com.orderproduct.inventoryservice.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                ErrorComponent.NOT_FOUND_ERROR_CODE,
                ErrorComponent.notFoundMsg
        );
    }
}

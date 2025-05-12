package com.example.productservice.common;

import org.springframework.http.HttpStatus;

public class InternalServerException extends ApiException {

    public InternalServerException() {
        super(ErrorComponent.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR, ErrorComponent.somethingWentWrongMsg);
    }
}

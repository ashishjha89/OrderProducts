package com.example.orderservice.common;

import org.springframework.http.HttpStatus;

public class InternalServerException extends ApiException {

    public InternalServerException() {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE,
                ErrorComponent.somethingWentWrongMsg
        );
    }
}

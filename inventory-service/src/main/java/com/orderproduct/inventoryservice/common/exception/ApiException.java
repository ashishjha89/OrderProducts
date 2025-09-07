package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.NonNull;

@Getter
public abstract class ApiException extends RuntimeException {

    @NonNull
    private final HttpStatus httpStatus;

    @NonNull
    private final String errorCode;

    @NonNull
    private final String errorMessage;

    public ApiException(@NonNull HttpStatus httpStatus, @NonNull String errorCode, @NonNull String errorMessage) {
        super(errorMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}

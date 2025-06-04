package com.orderproduct.inventoryservice.common;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ApiException extends RuntimeException {
    @NonNull
    private final String errorCode;

    @NonNull
    private final HttpStatus httpStatus;

    @NonNull
    private final String errorMessage;

    public ApiException(
            @NonNull String errorCode,
            @NonNull HttpStatus httpStatus,
            @NonNull String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }

}

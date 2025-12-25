package com.orderproduct.productservice.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;

import lombok.Getter;

@Getter
public abstract class ApiException extends RuntimeException {

    @NonNull
    private final HttpStatusCode httpStatus;

    @NonNull
    private final String errorCode;

    @NonNull
    private final String errorMessage;

    public ApiException(
            @NonNull HttpStatusCode httpStatus,
            @NonNull String errorCode,
            @NonNull String errorMessage) {
        super(errorMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}

package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public abstract class ApiException extends RuntimeException {

    @NonNull
    private final HttpStatus httpStatus;

    @NonNull
    private final String errorCode;

    @NonNull
    private final String errorMessage;

}

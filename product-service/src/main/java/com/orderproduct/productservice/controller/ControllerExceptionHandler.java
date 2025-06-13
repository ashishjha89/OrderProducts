package com.orderproduct.productservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orderproduct.productservice.common.ApiException;
import com.orderproduct.productservice.common.ErrorBody;

import io.swagger.v3.oas.annotations.Hidden;

@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
public class ControllerExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApiException(ApiException apiException) {
        return new ResponseEntity<>(
                new ErrorBody(apiException.getErrorCode(), apiException.getMessage()),
                apiException.getHttpStatus());
    }

}

package com.orderproduct.inventoryservice.controller;

import com.orderproduct.inventoryservice.common.ApiException;
import com.orderproduct.inventoryservice.common.ErrorBody;
import com.orderproduct.inventoryservice.common.ErrorComponent;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SuppressWarnings("unused")
@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
public class ControllerExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApiException(ApiException apiException) {
        return new ResponseEntity<>(
                new ErrorBody(apiException.getErrorCode(), apiException.getMessage()),
                apiException.getHttpStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGenericException(Exception exception) {
        return new ResponseEntity<>(
                new ErrorBody(ErrorComponent.SOMETHING_WENT_WRONG, ErrorComponent.somethingWentWrongMsg),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

}

package com.orderproduct.inventoryservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orderproduct.inventoryservice.common.ApiException;
import com.orderproduct.inventoryservice.common.ErrorBody;
import com.orderproduct.inventoryservice.common.ErrorComponent;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidationExceptions(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : ErrorComponent.badRequestMsg;
        return new ResponseEntity<>(
                new ErrorBody(ErrorComponent.BAD_REQUEST_ERROR_CODE, errorMessage),
                HttpStatus.BAD_REQUEST);
    }

}

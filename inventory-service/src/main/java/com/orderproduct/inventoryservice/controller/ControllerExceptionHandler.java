package com.orderproduct.inventoryservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orderproduct.inventoryservice.common.exception.ApiException;
import com.orderproduct.inventoryservice.common.exception.ErrorBody;
import com.orderproduct.inventoryservice.common.exception.ErrorBodyWithUnavailableProducts;
import com.orderproduct.inventoryservice.common.exception.ErrorComponent;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;

import io.swagger.v3.oas.annotations.Hidden;

@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
public class ControllerExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApiException(ApiException apiException) {
        return new ResponseEntity<>(
                new ErrorBody(apiException.getErrorCode(), apiException.getErrorMessage()),
                apiException.getHttpStatus());
    }

    @ExceptionHandler(NotEnoughItemException.class)
    public ResponseEntity<ErrorBodyWithUnavailableProducts> handleNotEnoughItemException(NotEnoughItemException ex) {
        return new ResponseEntity<>(
                new ErrorBodyWithUnavailableProducts(
                        ex.getErrorCode(),
                        ex.getErrorMessage(),
                        ex.getUnavailableProducts()),
                ex.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidationExceptions(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : ErrorComponent.badRequestMsg;
        return new ResponseEntity<>(
                new ErrorBody(ErrorComponent.BAD_REQUEST_ERROR_CODE, errorMessage),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(
                new ErrorBody(ErrorComponent.BAD_REQUEST_ERROR_CODE, ex.getMessage()),
                HttpStatus.BAD_REQUEST);
    }

}

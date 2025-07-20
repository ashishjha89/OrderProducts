package com.orderproduct.orderservice.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orderproduct.orderservice.common.ApiException;
import com.orderproduct.orderservice.common.ErrorBody;
import com.orderproduct.orderservice.common.ErrorComponent;
import com.orderproduct.orderservice.common.InventoryNotInStockException;

import io.swagger.v3.oas.annotations.Hidden;

@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
public class ControllerExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException apiException) {
        if (apiException instanceof InventoryNotInStockException insEx && insEx.getUnavailableProducts() != null) {
            Map<String, Object> body = new HashMap<>();
            body.put("errorCode", insEx.getErrorCode());
            body.put("errorMessage", insEx.getMessage());
            body.put("unavailableProducts", insEx.getUnavailableProducts());
            return new ResponseEntity<>(body, insEx.getHttpStatus());
        }
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

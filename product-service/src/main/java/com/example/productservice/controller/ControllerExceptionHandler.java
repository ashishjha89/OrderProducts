package com.example.productservice.controller;

import com.example.productservice.common.BadRequestException;
import com.example.productservice.common.ErrorBody;
import com.example.productservice.common.ErrorComponent;
import com.example.productservice.common.InternalServerException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SuppressWarnings("unused")
@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
public class ControllerExceptionHandler {

    @ExceptionHandler(InternalServerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorBody> internalServerException() {
        return new ResponseEntity<>(
                ErrorComponent.internalServerError,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorBody> badRequestException() {
        return new ResponseEntity<>(
                ErrorComponent.badRequestError,
                HttpStatus.BAD_REQUEST
        );
    }

}

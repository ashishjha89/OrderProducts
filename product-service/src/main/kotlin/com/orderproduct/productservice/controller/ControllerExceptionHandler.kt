package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.ApiException
import com.orderproduct.productservice.common.ErrorBody
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Hidden
class ControllerExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(apiException: ApiException): ResponseEntity<ErrorBody> =
        ResponseEntity(
            ErrorBody(apiException.errorCode, apiException.errorMessage),
            apiException.httpStatus
        )
}

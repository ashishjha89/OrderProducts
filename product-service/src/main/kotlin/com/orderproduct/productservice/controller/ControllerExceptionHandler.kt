package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.ApiException
import com.orderproduct.productservice.common.ErrorBody
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Suppress("unused")
@RestControllerAdvice
@Hidden // To hide it from Swagger! Controllers are specifying their exact errors.
class ControllerExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorBody> =
        ResponseEntity(ErrorBody(errorCode = ex.errorCode, errorMessage = ex.errorMessage), ex.httpStatus)
}

package com.orderproduct.productservice.common

import org.springframework.http.HttpStatus

class BadRequestException : ApiException(
    httpStatus = HttpStatus.BAD_REQUEST,
    errorCode = ErrorComponent.BAD_REQUEST_ERROR_CODE,
    errorMessage = ErrorComponent.badRequestMsg
)

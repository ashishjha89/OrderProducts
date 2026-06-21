package com.orderproduct.productservice.common

import org.springframework.http.HttpStatus

class InternalServerException : ApiException(
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    errorCode = ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE,
    errorMessage = ErrorComponent.somethingWentWrongMsg
)

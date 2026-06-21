package com.orderproduct.productservice.common

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode

const val SOMETHING_WENT_WRONG_ERROR_CODE = "SOMETHING_WENT_WRONG"
const val BAD_REQUEST_ERROR_CODE = "BAD_REQUEST"
const val SOMETHING_WENT_WRONG_MSG = "Sorry, something went wrong."
const val BAD_REQUEST_MSG = "This is an incorrect request-body"

sealed class ApiException(
    val httpStatus: HttpStatusCode,
    val errorCode: String,
    val errorMessage: String
) : RuntimeException(errorMessage)

class BadRequestException : ApiException(
    httpStatus = HttpStatus.BAD_REQUEST,
    errorCode = BAD_REQUEST_ERROR_CODE,
    errorMessage = BAD_REQUEST_MSG
)

class InternalServerException : ApiException(
    httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    errorCode = SOMETHING_WENT_WRONG_ERROR_CODE,
    errorMessage = SOMETHING_WENT_WRONG_MSG
)

package com.orderproduct.productservice.common

import org.springframework.http.HttpStatusCode

abstract class ApiException(
    val httpStatus: HttpStatusCode,
    val errorCode: String,
    val errorMessage: String
) : RuntimeException(errorMessage)

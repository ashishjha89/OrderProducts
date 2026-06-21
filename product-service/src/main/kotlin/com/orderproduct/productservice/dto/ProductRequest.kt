package com.orderproduct.productservice.dto

import java.math.BigDecimal

data class ProductRequest(
    val name: String?,
    val description: String?,
    val price: BigDecimal?
)

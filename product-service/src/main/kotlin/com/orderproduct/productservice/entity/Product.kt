package com.orderproduct.productservice.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal

@Document("product")
data class Product(
    @Id val id: String? = null,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val skuCode: String? = null
)

package com.orderproduct.productservice.repository

import com.orderproduct.productservice.entity.Product
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ProductRepository : CoroutineCrudRepository<Product, String> {

    suspend fun findBySkuCode(skuCode: String): Product?
}

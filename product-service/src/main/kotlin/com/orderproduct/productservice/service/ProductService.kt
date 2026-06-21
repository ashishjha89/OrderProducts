package com.orderproduct.productservice.service

import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductRequest
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.entity.Product
import com.orderproduct.productservice.repository.ProductRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class ProductService(private val productRepository: ProductRepository) {

    private val log = LoggerFactory.getLogger(ProductService::class.java)

    suspend fun createProduct(productRequest: ProductRequest): SavedProduct {
        val product = Product(
            name = productRequest.name!!,
            description = productRequest.description!!,
            price = productRequest.price!!
        )
        return try {
            val saved = productRepository.save(product)
            log.info("Product {} is saved", saved.id)
            SavedProduct(saved.id!!)
        } catch (e: DataAccessException) {
            log.error("Error when saving product: {}", e.message)
            throw InternalServerException()
        }
    }

    suspend fun getAllProducts(): List<ProductResponse> {
        return try {
            productRepository.findAll().map { it.toProductResponse() }.toList()
        } catch (e: DataAccessException) {
            log.error("Error when getting all products: {}", e.message)
            throw InternalServerException()
        }
    }

    private fun Product.toProductResponse() = ProductResponse(
        id = id!!,
        name = name,
        description = description,
        price = price
    )
}

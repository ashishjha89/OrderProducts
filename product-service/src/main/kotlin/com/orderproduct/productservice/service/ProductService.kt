package com.orderproduct.productservice.service

import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.entity.Product
import com.orderproduct.productservice.repository.ProductRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ProductService(private val productRepository: ProductRepository) {

    private val log = LoggerFactory.getLogger(ProductService::class.java)

    suspend fun createProduct(name: String, description: String, price: BigDecimal, skuCode: String): SavedProduct {
        val product = Product(name = name, description = description, price = price, skuCode = skuCode)
        return try {
            val saved = productRepository.save(product)
            log.info("Product {} is saved", saved.id)
            SavedProduct(requireNotNull(saved.id) { "MongoDB must assign an id after save" })
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

    suspend fun getProductById(id: String): ProductResponse? {
        return try {
            productRepository.findById(id)?.toProductResponse()
        } catch (e: DataAccessException) {
            log.error("Error when getting product by id {}: {}", id, e.message)
            throw InternalServerException()
        }
    }

    suspend fun getProductBySkuCode(skuCode: String): ProductResponse? {
        return try {
            productRepository.findBySkuCode(skuCode)?.toProductResponse()
        } catch (e: DataAccessException) {
            log.error("Error when getting product by skuCode {}: {}", skuCode, e.message)
            throw InternalServerException()
        }
    }

    private fun Product.toProductResponse() = ProductResponse(
        id = requireNotNull(id),
        name = name,
        description = description,
        price = price,
        skuCode = skuCode
    )
}

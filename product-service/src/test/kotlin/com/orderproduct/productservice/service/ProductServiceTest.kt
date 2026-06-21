package com.orderproduct.productservice.service

import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.entity.Product
import com.orderproduct.productservice.repository.ProductRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.math.BigDecimal

class ProductServiceTest {

    private val productRepository = mock<ProductRepository>()
    private val productService = ProductService(productRepository)

    @Test
    @DisplayName("createProduct() (i) transforms args to Product (ii) saves to repo (iii) returns SavedProduct")
    fun createProductHappyFlowTest() = runTest {
        val name = "Name"
        val description = "Description"
        val price = BigDecimal.valueOf(123)
        val productPassedToRepo = Product(name = name, description = description, price = price)
        val productReturnedFromRepo = Product(id = "id1", name = name, description = description, price = price)

        whenever(productRepository.save(productPassedToRepo)).thenReturn(productReturnedFromRepo)

        val result = productService.createProduct(name, description, price)

        verify(productRepository).save(productPassedToRepo)
        assertEquals(SavedProduct("id1"), result)
    }

    @Test
    @DisplayName("createProduct throws InternalServerException when repo throws DataAccessException")
    fun createProductWhenDBThrowsError() = runTest {
        val productPassedToRepo = Product(name = "Name", description = "Description", price = BigDecimal.valueOf(123))

        whenever(productRepository.save(productPassedToRepo))
            .thenThrow(DataAccessResourceFailureException("Child class of DataAccessException"))

        assertThrows(InternalServerException::class.java) {
            kotlinx.coroutines.runBlocking {
                productService.createProduct("Name", "Description", BigDecimal.valueOf(123))
            }
        }
    }

    @Test
    @DisplayName("getAllProducts() fetches Flow<Product> from repo and transforms them to List<ProductResponse>")
    fun getAllProductsTest() = runTest {
        val product1 = Product(id = "id1", name = "Name1", description = "Description1", price = BigDecimal.valueOf(123))
        val product2 = Product(id = "id2", name = "Name2", description = "Description2", price = BigDecimal.valueOf(456))

        whenever(productRepository.findAll()).thenReturn(flowOf(product1, product2))

        val result = productService.getAllProducts()

        assertEquals(
            listOf(
                ProductResponse("id1", "Name1", "Description1", BigDecimal.valueOf(123)),
                ProductResponse("id2", "Name2", "Description2", BigDecimal.valueOf(456))
            ),
            result
        )
    }

    @Test
    @DisplayName("getAllProducts throws InternalServerException when repo throws DataAccessException")
    fun getAllProductsWhenDBThrowsError() = runTest {
        whenever(productRepository.findAll())
            .thenThrow(DataAccessResourceFailureException("Child class of DataAccessException"))

        assertThrows(InternalServerException::class.java) {
            kotlinx.coroutines.runBlocking { productService.getAllProducts() }
        }
    }
}

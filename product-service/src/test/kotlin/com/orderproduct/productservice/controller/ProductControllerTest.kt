package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.service.ProductService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@WebFluxTest(controllers = [ProductController::class])
@Import(ProductControllerTest.MockedServiceConfig::class)
class ProductControllerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var productService: ProductService

    @TestConfiguration
    class MockedServiceConfig {
        @Bean
        fun productService(): ProductService = mock()
    }

    @BeforeEach
    fun setUp() {
        reset(productService)
    }

    @Test
    @DisplayName("should return SavedProduct when POST /api/products is called with valid request")
    fun createProductTest() {
        val savedProduct = SavedProduct("id1")
        runBlocking {
            whenever(productService.createProduct("name", "description", BigDecimal.valueOf(1000)))
                .thenReturn(savedProduct)
        }

        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"name","description":"description","price":1000}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.productId").isEqualTo(savedProduct.productId)
    }

    @Test
    @DisplayName("should return 400 when POST /api/products is called with empty name")
    fun createProductBadRequestEmptyName() {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"","description":"description","price":1000}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return 400 when POST /api/products is called with null name")
    fun createProductBadRequestNullName() {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":null,"description":"description","price":1000}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return 400 when POST /api/products is called with empty description")
    fun createProductBadRequestEmptyDescription() {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"name","description":"","price":1000}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return 400 when POST /api/products is called with null description")
    fun createProductBadRequestNullDescription() {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"name","description":null,"price":1000}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return 400 when POST /api/products is called with null price")
    fun createProductBadRequestNullPrice() {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"name","description":"description","price":null}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return 500 when POST /api/products and productService throws InternalServerException")
    fun createProductInternalServerError() {
        runBlocking {
            whenever(productService.createProduct("name", "description", BigDecimal.valueOf(1000)))
                .thenThrow(InternalServerException())
        }

        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"name","description":"description","price":1000}""")
            .exchange()
            .expectStatus().is5xxServerError
    }

    @Test
    @DisplayName("should return list of products when GET /api/products is called")
    fun getAllProductsTest() {
        val product1 = ProductResponse("id1", "name1", "description1", BigDecimal.valueOf(1000))
        val product2 = ProductResponse("id2", "name2", "description2", BigDecimal.valueOf(2000))
        runBlocking {
            whenever(productService.getAllProducts()).thenReturn(listOf(product1, product2))
        }

        webTestClient.get().uri("/api/products")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].id").isEqualTo(product1.id)
            .jsonPath("$[0].name").isEqualTo(product1.name)
            .jsonPath("$[0].description").isEqualTo(product1.description)
            .jsonPath("$[0].price").isEqualTo(product1.price)
            .jsonPath("$[1].id").isEqualTo(product2.id)
            .jsonPath("$[1].name").isEqualTo(product2.name)
            .jsonPath("$[1].description").isEqualTo(product2.description)
            .jsonPath("$[1].price").isEqualTo(product2.price)
    }
}

package com.orderproduct.productservice

import com.orderproduct.productservice.common.ErrorBody
import com.orderproduct.productservice.common.ErrorComponent
import com.orderproduct.productservice.dto.ProductRequest
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.entity.Product
import com.orderproduct.productservice.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class ProductServiceApplicationTests {

    companion object {
        @Container
        @JvmStatic
        val mongoDBContainer = MongoDBContainer("mongo:4.4.2")

        @DynamicPropertySource
        @JvmStatic
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl)
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var productRepository: ProductRepository

    @AfterEach
    fun cleanup() = runBlocking {
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("should return SavedProduct successfully when POST /api/products is called with valid request")
    fun postApiCall_ShouldInsertProductToDatabase() = runBlocking<Unit> {
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProductRequest("iPhone 13", "iPhone 13", BigDecimal.valueOf(1200)))
            .exchange()
            .expectStatus().isCreated

        assertEquals(1, productRepository.count())
    }

    @Test
    @DisplayName("should throw BadRequestException when POST /api/products is called with all-null fields")
    fun postApiCall_ShouldThrowBadRequestException_WhenNameOrDescriptionOrPriceIsEmptyOrMissing() {
        // All null fields
        val errorBody = webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProductRequest(null, null, null))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorBody::class.java)
            .returnResult().responseBody!!
        assertEquals(ErrorComponent.BAD_REQUEST_ERROR_CODE, errorBody.errorCode)
        assertEquals(ErrorComponent.badRequestMsg, errorBody.errorMessage)

        // Null name
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProductRequest(null, "Description", BigDecimal.valueOf(100)))
            .exchange()
            .expectStatus().isBadRequest

        // Null description
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProductRequest("name", null, BigDecimal.valueOf(100)))
            .exchange()
            .expectStatus().isBadRequest

        // Null price
        webTestClient.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ProductRequest("name", "Description", null))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("should return ProductResponse list when GET /api/products is called")
    fun getApiCallForProduct_ShouldReturnAllProductsFromDatabase() = runBlocking<Unit> {
        val uniqueName = System.currentTimeMillis().toString()
        productRepository.save(Product(name = uniqueName, description = "Description", price = BigDecimal.valueOf(12)))

        val responses = webTestClient.get().uri("/api/products")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ProductResponse::class.java)
            .returnResult().responseBody!!

        assertTrue(responses.any { it.name == uniqueName })
    }
}

package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.service.ProductService
import graphql.ErrorType
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import java.math.BigDecimal

@GraphQlTest(ProductGraphQLController::class)
@Import(ProductGraphQLControllerTest.MockedServiceConfig::class)
class ProductGraphQLControllerTest {

    @Autowired
    lateinit var graphQlTester: GraphQlTester

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
    @DisplayName("should return product list when products query is executed")
    fun productsQuery_returnsProductList() {
        val product1 = ProductResponse("id1", "name1", "description1", BigDecimal.valueOf(1000))
        val product2 = ProductResponse("id2", "name2", "description2", BigDecimal.valueOf(2000))
        runBlocking {
            whenever(productService.getAllProducts()).thenReturn(listOf(product1, product2))
        }

        graphQlTester.document(
            """
            query {
                products {
                    id
                    name
                    description
                    price
                }
            }
        """
        )
            .execute()
            .path("products").entityList(ProductResponse::class.java).hasSize(2)
            .path("products[0].id").entity(String::class.java).isEqualTo(product1.id)
            .path("products[0].name").entity(String::class.java).isEqualTo(product1.name)
            .path("products[0].description").entity(String::class.java).isEqualTo(product1.description)
            .path("products[1].id").entity(String::class.java).isEqualTo(product2.id)
    }

    @Test
    @DisplayName("should return productId when createProduct mutation is executed with valid input")
    fun createProductMutation_returnsProductId() {
        val savedProduct = SavedProduct("id1")
        runBlocking {
            whenever(productService.createProduct("name", "description", BigDecimal.valueOf(1000)))
                .thenReturn(savedProduct)
        }

        graphQlTester.document(
            """
            mutation {
                createProduct(input: {name: "name", description: "description", price: 1000}) {
                    productId
                }
            }
        """
        )
            .execute()
            .path("createProduct.productId").entity(String::class.java).isEqualTo(savedProduct.productId)
    }

    @Test
    @DisplayName("should return ValidationError with BAD_USER_INPUT code when createProduct mutation is called with blank name")
    fun createProductMutation_blankName_returnsValidationError() {
        graphQlTester.document(
            """
            mutation {
                createProduct(input: {name: "", description: "description", price: 1000}) {
                    productId
                }
            }
        """
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType).isEqualTo(ErrorType.ValidationError)
                assertThat(errors[0].extensions["code"]).isEqualTo("BAD_USER_INPUT")
            }
    }

    @Test
    @DisplayName("should return ValidationError with BAD_USER_INPUT code when createProduct mutation is called with blank description")
    fun createProductMutation_blankDescription_returnsValidationError() {
        graphQlTester.document(
            """
            mutation {
                createProduct(input: {name: "name", description: "", price: 1000}) {
                    productId
                }
            }
        """
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType).isEqualTo(ErrorType.ValidationError)
                assertThat(errors[0].extensions["code"]).isEqualTo("BAD_USER_INPUT")
            }
    }

    @Test
    @DisplayName("should return DataFetchingException with INTERNAL_SERVER_ERROR code when products query and productService throws InternalServerException")
    fun productsQuery_serviceThrowsInternalServerException_returnsDataFetchingException() {
        runBlocking {
            whenever(productService.getAllProducts()).thenThrow(InternalServerException())
        }

        graphQlTester.document(
            """
            query {
                products {
                    id
                    name
                }
            }
        """
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType).isEqualTo(ErrorType.DataFetchingException)
                assertThat(errors[0].extensions["code"]).isEqualTo("INTERNAL_SERVER_ERROR")
            }
    }

    @Test
    @DisplayName("should return DataFetchingException with INTERNAL_SERVER_ERROR code when createProduct mutation and productService throws InternalServerException")
    fun createProductMutation_serviceThrowsInternalServerException_returnsDataFetchingException() {
        runBlocking {
            whenever(productService.createProduct("name", "description", BigDecimal.valueOf(1000)))
                .thenThrow(InternalServerException())
        }

        graphQlTester.document(
            """
            mutation {
                createProduct(input: {name: "name", description: "description", price: 1000}) {
                    productId
                }
            }
        """
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType).isEqualTo(ErrorType.DataFetchingException)
                assertThat(errors[0].extensions["code"]).isEqualTo("INTERNAL_SERVER_ERROR")
            }
    }

    // Federation: _service { sdl }

    @Test
    @DisplayName("should return SDL string containing Product type and @key directive when _service query is executed")
    fun serviceQuery_returnsSdlWithProductKeyDirective() {
        graphQlTester.document(
            """
            query {
                _service {
                    sdl
                }
            }
        """
        )
            .execute()
            .path("_service.sdl").entity(String::class.java)
            .satisfies { sdl ->
                assertThat(sdl).contains("Product")
                assertThat(sdl).contains("@key")
                assertThat(sdl).contains("""@key(fields: "id")""")
            }
    }

    // Federation: _entities

    @Test
    @DisplayName("should return Product when _entities is called with a Product representation and product exists")
    fun entitiesQuery_productRepresentation_returnsProduct() {
        val product = ProductResponse("id1", "name1", "description1", BigDecimal.valueOf(1000))
        runBlocking {
            whenever(productService.getProductById("id1")).thenReturn(product)
        }

        graphQlTester.document(
            """
            query {
                _entities(representations: [{__typename: "Product", id: "id1"}]) {
                    ... on Product {
                        id
                        name
                        description
                        price
                    }
                }
            }
        """
        )
            .execute()
            .path("_entities[0].id").entity(String::class.java).isEqualTo("id1")
            .path("_entities[0].name").entity(String::class.java).isEqualTo("name1")
            .path("_entities[0].description").entity(String::class.java).isEqualTo("description1")
    }

    @Test
    @DisplayName("should return null when _entities is called with a Product representation but product does not exist")
    fun entitiesQuery_productNotFound_returnsNull() {
        runBlocking {
            whenever(productService.getProductById("unknown")).thenReturn(null)
        }

        graphQlTester.document(
            """
            query {
                _entities(representations: [{__typename: "Product", id: "unknown"}]) {
                    ... on Product {
                        id
                    }
                }
            }
        """
        )
            .execute()
            .path("_entities[0]").valueIsNull()
    }

    @Test
    @DisplayName("should resolve multiple representations in one call, returning each Product or null independently")
    fun entitiesQuery_multipleRepresentations_resolvesEachIndependently() {
        val product1 = ProductResponse("id1", "name1", "description1", BigDecimal.valueOf(1000))
        runBlocking {
            whenever(productService.getProductById("id1")).thenReturn(product1)
            whenever(productService.getProductById("id2")).thenReturn(null)
        }

        graphQlTester.document(
            """
            query {
                _entities(representations: [
                    {__typename: "Product", id: "id1"},
                    {__typename: "Product", id: "id2"}
                ]) {
                    ... on Product {
                        id
                        name
                    }
                }
            }
        """
        )
            .execute()
            .path("_entities[0].id").entity(String::class.java).isEqualTo("id1")
            .path("_entities[1]").valueIsNull()
    }

    @Test
    @DisplayName("should return DataFetchingException when _entities is called and productService throws InternalServerException")
    fun entitiesQuery_serviceThrowsInternalServerException_returnsDataFetchingException() {
        runBlocking {
            whenever(productService.getProductById("id1")).thenThrow(InternalServerException())
        }

        graphQlTester.document(
            """
            query {
                _entities(representations: [{__typename: "Product", id: "id1"}]) {
                    ... on Product {
                        id
                    }
                }
            }
        """
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].errorType).isEqualTo(ErrorType.DataFetchingException)
                assertThat(errors[0].extensions["code"]).isEqualTo("INTERNAL_SERVER_ERROR")
            }
    }
}

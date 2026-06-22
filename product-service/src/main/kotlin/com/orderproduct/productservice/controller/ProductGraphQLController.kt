package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.BadRequestException
import com.orderproduct.productservice.dto.CreateProductInput
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.service.ProductService
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class ProductGraphQLController(private val productService: ProductService) {

    private val log = LoggerFactory.getLogger(ProductGraphQLController::class.java)

    @QueryMapping
    suspend fun products(): List<ProductResponse> {
        log.info("GraphQL query: products")
        return productService.getAllProducts()
    }

    @MutationMapping
    suspend fun createProduct(@Argument input: CreateProductInput): SavedProduct {
        log.info("GraphQL mutation: createProduct")
        val name = input.name.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        val description = input.description.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        return productService.createProduct(name, description, input.price)
    }
}

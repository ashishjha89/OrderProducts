package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.BadRequestException
import com.orderproduct.productservice.common.ErrorBody
import com.orderproduct.productservice.common.ErrorComponent
import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.dto.ProductRequest
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.service.ProductService
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class ProductController(private val productService: ProductService) {

    private val log = LoggerFactory.getLogger(ProductController::class.java)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "OK",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = SavedProduct::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "errorCode:${ErrorComponent.BAD_REQUEST_ERROR_CODE} errorMessage:${ErrorComponent.badRequestMsg}",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            ),
            ApiResponse(
                responseCode = "500",
                description = "errorCode:${ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE} errorMessage:${ErrorComponent.somethingWentWrongMsg}",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            )
        ]
    )
    suspend fun createProduct(@RequestBody productRequest: ProductRequest): SavedProduct {
        log.info("POST:/api/products")
        if (productRequest.name.isNullOrBlank() ||
            productRequest.description.isNullOrBlank() ||
            productRequest.price == null
        ) {
            log.error("BadRequestException: POST:/api/products called with invalid body: {}", productRequest)
            throw BadRequestException()
        }
        return productService.createProduct(productRequest)
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "OK",
                content = [Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = ProductResponse::class))
                )]
            ),
            ApiResponse(
                responseCode = "500",
                description = "errorCode:${ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE} errorMessage:${ErrorComponent.somethingWentWrongMsg}",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            )
        ]
    )
    suspend fun getAllProducts(): List<ProductResponse> {
        log.info("GET:/api/products")
        return productService.getAllProducts()
    }
}

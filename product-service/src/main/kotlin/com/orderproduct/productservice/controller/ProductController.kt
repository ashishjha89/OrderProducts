package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.BAD_REQUEST_ERROR_CODE
import com.orderproduct.productservice.common.BAD_REQUEST_MSG
import com.orderproduct.productservice.common.BadRequestException
import com.orderproduct.productservice.common.ErrorBody
import com.orderproduct.productservice.common.SOMETHING_WENT_WRONG_ERROR_CODE
import com.orderproduct.productservice.common.SOMETHING_WENT_WRONG_MSG
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
                description = "errorCode:$BAD_REQUEST_ERROR_CODE errorMessage:$BAD_REQUEST_MSG",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            ),
            ApiResponse(
                responseCode = "500",
                description = "errorCode:$SOMETHING_WENT_WRONG_ERROR_CODE errorMessage:$SOMETHING_WENT_WRONG_MSG",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            )
        ]
    )
    suspend fun createProduct(@RequestBody productRequest: ProductRequest): SavedProduct {
        log.info("POST:/api/products")
        val name = productRequest.name?.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        val description = productRequest.description?.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        val price = productRequest.price ?: throw BadRequestException()
        return productService.createProduct(name, description, price)
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
                description = "errorCode:$SOMETHING_WENT_WRONG_ERROR_CODE errorMessage:$SOMETHING_WENT_WRONG_MSG",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorBody::class))]
            )
        ]
    )
    suspend fun getAllProducts(): List<ProductResponse> {
        log.info("GET:/api/products")
        return productService.getAllProducts()
    }
}

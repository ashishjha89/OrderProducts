package com.example.productservice.controller;

import com.example.productservice.common.BadRequestException;
import com.example.productservice.common.ErrorBody;
import com.example.productservice.common.ErrorComponent;
import com.example.productservice.common.InternalServerException;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.SavedProduct;
import com.example.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@SuppressWarnings("unused")
@Slf4j
public record ProductController(ProductService productService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuppressWarnings("unused")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "OK",
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = SavedProduct.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "errorCode:" + ErrorComponent.BAD_REQUEST + " errorMessage:" + ErrorComponent.badRequestMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "errorCode:" + ErrorComponent.SOMETHING_WENT_WRONG + " errorMessage:" + ErrorComponent.somethingWentWrongMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    )
            }
    )
    public SavedProduct createProduct(@RequestBody ProductRequest productRequest) throws BadRequestException, InternalServerException {
        log.info("POST:/api/product");
        if (productRequest == null
                || productRequest.getName() == null || productRequest.getName().isBlank()
                || productRequest.getDescription() == null || productRequest.getDescription().isBlank()
                || productRequest.getPrice() == null
        ) {
            log.error("BadRequestException because POST:/api/product is called with invalid ProductRequest productRequest:" + productRequest);
            throw new BadRequestException();
        }

        return productService.createProduct(productRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @SuppressWarnings("unused")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OK",
                            content = {
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = ProductResponse.class))
                                    )
                            }
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "errorCode:" + ErrorComponent.SOMETHING_WENT_WRONG + " errorMessage:" + ErrorComponent.somethingWentWrongMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    )
            }
    )
    public List<ProductResponse> getAllProducts() throws InternalServerException {
        log.info("GET:/api/product");
        return productService.getAllProducts();
    }
}

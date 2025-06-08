package com.orderproduct.productservice.controller;

import com.orderproduct.productservice.common.BadRequestException;
import com.orderproduct.productservice.common.ErrorBody;
import com.orderproduct.productservice.common.ErrorComponent;
import com.orderproduct.productservice.common.InternalServerException;
import com.orderproduct.productservice.dto.ProductRequest;
import com.orderproduct.productservice.dto.ProductResponse;
import com.orderproduct.productservice.dto.SavedProduct;
import com.orderproduct.productservice.service.ProductService;
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
@RequestMapping("/api/products")
@SuppressWarnings("unused")
@Slf4j
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

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
                            description = "errorCode:" + ErrorComponent.BAD_REQUEST_ERROR_CODE + " errorMessage:" + ErrorComponent.badRequestMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "errorCode:" + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:" + ErrorComponent.somethingWentWrongMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    )
            }
    )
    public SavedProduct createProduct(@RequestBody ProductRequest productRequest) throws BadRequestException, InternalServerException {
        log.info("POST:/api/products");
        if (productRequest == null
                || productRequest.getName() == null || productRequest.getName().isBlank()
                || productRequest.getDescription() == null || productRequest.getDescription().isBlank()
                || productRequest.getPrice() == null
        ) {
            log.error("BadRequestException because POST:/api/product is called with invalid ProductRequest productRequest:{}", productRequest);
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
                            description = "errorCode:" + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:" + ErrorComponent.somethingWentWrongMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    )
            }
    )
    public List<ProductResponse> getAllProducts() throws InternalServerException {
        log.info("GET:/api/products");
        return productService.getAllProducts();
    }
}

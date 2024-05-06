package com.example.productservice.controller;

import com.example.productservice.common.BadRequestException;
import com.example.productservice.common.InternalServerException;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.SavedProduct;
import com.example.productservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);

    private final ProductController productController = new ProductController(productService);

    @Test
    @DisplayName("createProduct() calls productService.createProduct() and retrieves `SavedProduct` from it")
    public void createProductTest() throws InternalServerException, BadRequestException {
        // Initialise
        final var productRequest = new ProductRequest("name", "description", BigDecimal.valueOf(1000));
        final var savedProduct = new SavedProduct("id1");
        when(productService.createProduct(productRequest)).thenReturn(savedProduct);

        // Verify
        assertEquals(savedProduct, productController.createProduct(productRequest));
    }

    @Test
    @DisplayName("createProduct() throws BadRequestException when null fields are passed")
    public void createProductTes1t() {
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(null),
                "BadRequestException is expected when null ProductRequest is passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest()),
                "BadRequestException is expected when null fields are passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest("", "description", BigDecimal.valueOf(1))),
                "BadRequestException is expected when empty or null name is passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest(null, "description", BigDecimal.valueOf(1))),
                "BadRequestException is expected when empty or null name is passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest("name", "", BigDecimal.valueOf(1))),
                "BadRequestException is expected when empty or null description is passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest("name", null, BigDecimal.valueOf(1))),
                "BadRequestException is expected when empty or null description is passed"
        );
        assertThrows(
                BadRequestException.class,
                () -> productController.createProduct(new ProductRequest("name", "description", null)),
                "BadRequestException is expected when empty or null price is passed"
        );
    }

    @Test
    @DisplayName("createProduct() forwards InternalServerException from OrderService.placeOrder")
    public void createProductForwardsInternalServerExceptionTest() throws InternalServerException {
        final var productRequest = new ProductRequest("name", "description", BigDecimal.valueOf(1000));
        when(productService.createProduct(productRequest)).thenThrow(new InternalServerException());
        assertThrows(InternalServerException.class, () -> productController.createProduct(productRequest));
    }

    @Test
    @DisplayName("getAllProducts() should return products returned by productService.getAllProducts()")
    public void getAllProductsHappyFlowTest() throws InternalServerException {
        // Mock
        ProductResponse productResponse = mock(ProductResponse.class);
        final var productResponseList = List.of(productResponse);
        when(productService.getAllProducts()).thenReturn(productResponseList);

        // Call method
        productController.getAllProducts();

        // Verify
        assertEquals(productResponseList, productService.getAllProducts());
    }

    @Test
    @DisplayName("createProduct() forwards InternalServerException from OrderService.placeOrder")
    public void getAllProductsForwardsInternalServerExceptionTest() throws InternalServerException {
        when(productService.getAllProducts()).thenThrow(new InternalServerException());
        assertThrows(InternalServerException.class, productController::getAllProducts);
    }

}

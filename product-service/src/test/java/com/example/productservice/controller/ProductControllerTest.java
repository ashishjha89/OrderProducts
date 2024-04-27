package com.example.productservice.controller;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);

    private final ProductController productController = new ProductController(productService);

    @Test
    @DisplayName("createProduct(productRequest) should call productService.createProduct(productRequest)")
    public void createProductTest() {
        // Initialise
        final var productRequest = mock(ProductRequest.class);

        // Call method
        productController.createProduct(productRequest);

        // Verify
        verify(productService).createProduct(productRequest);
    }

    @Test
    @DisplayName("getAllProducts() should return products returned by productService.getAllProducts()")
    public void getAllProductsTest() {
        // Mock
        ProductResponse productResponse = mock(ProductResponse.class);
        final var productResponseList = List.of(productResponse);
        when(productService.getAllProducts()).thenReturn(productResponseList);

        // Call method
        productController.getAllProducts();

        // Verify
        assertEquals(productResponseList, productService.getAllProducts());
    }

}

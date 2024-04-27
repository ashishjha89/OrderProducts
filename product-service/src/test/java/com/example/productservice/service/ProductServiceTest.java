package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);

    private final ProductService productService = new ProductService(productRepository);

    @Test
    @DisplayName("createProduct() should transform ProductRequest(dto) -> Product(model), and then save Product to repo")
    public void createProduct() {
        // Initialise
        final var name = "Name";
        final var description = "Description";
        final var price = BigDecimal.valueOf(123);
        final var productRequest = new ProductRequest(name, description, price);
        final var product = Product.builder().name(name).description(description).price(price).build();

        // Call method to test
        productService.createProduct(productRequest);

        // Verify
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("getAllProducts() fetches List<Product> from repo and transforms them to List<ProductResponse>")
    public void getAllProductsTest() {
        // Initialise
        final var id1 = "id1";
        final var name1 = "Name1";
        final var description1 = "Description1";
        final var price1 = BigDecimal.valueOf(123);
        final var product1 = Product.builder().id(id1).name(name1).description(description1).price(price1).build();
        final var productResponse1 = new ProductResponse(id1, name1, description1, price1);

        final var id2 = "id1";
        final var name2 = "Name";
        final var description2 = "Description";
        final var price2 = BigDecimal.valueOf(123);
        final var product2 = Product.builder().id(id2).name(name2).description(description2).price(price2).build();
        final var productResponse2 = new ProductResponse(id2, name2, description2, price2);

        final var productList = List.of(product1, product2);
        // List<ProductResponse> productResponseList = List.of(productResponse1, productResponse2);

        // Mock
        when(productRepository.findAll()).thenReturn(productList);

        // Call method to test
        final var productResponseList = productService.getAllProducts();

        // Assert
        assertEquals(productResponseList, List.of(productResponse1, productResponse2));
    }

}

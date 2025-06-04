package com.orderproduct.productservice.service;

import com.orderproduct.productservice.common.InternalServerException;
import com.orderproduct.productservice.dto.ProductRequest;
import com.orderproduct.productservice.dto.ProductResponse;
import com.orderproduct.productservice.dto.SavedProduct;
import com.orderproduct.productservice.entity.Product;
import com.orderproduct.productservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);

    private final ProductService productService = new ProductService(productRepository);

    @Test
    @DisplayName("createProduct()  (i) transform `ProductRequest`to `Product` (ii) save `Product` to repo (iii) return `SavedProduct`")
    public void createProductHappyFlowTest() throws InternalServerException {
        // Initialise
        final var name = "Name";
        final var description = "Description";
        final var price = BigDecimal.valueOf(123);
        final var productRequest = new ProductRequest(name, description, price);
        final var productPassedToRepo = Product.builder().name(name).description(description).price(price).build();

        final var productReturnedFromRepo = Product.builder().id("id1").name(name).description(description).price(price).build();
        when(productRepository.save(productPassedToRepo)).thenReturn(productReturnedFromRepo);

        // Call method to test
        final var productSaved = productService.createProduct(productRequest);

        // Verify
        verify(productRepository).save(productPassedToRepo);
        assertEquals(new SavedProduct("id1"), productSaved);
    }

    @Test
    @DisplayName("createProduct throws InternalServerException when Repo throws DataAccessException")
    public void createProductWhenDBThrowsError() {
        // Initialise
        final var name = "Name";
        final var description = "Description";
        final var price = BigDecimal.valueOf(123);
        final var productRequest = new ProductRequest(name, description, price);
        final var productPassedToRepo = Product.builder().name(name).description(description).price(price).build();

        // Mock throwing of exception (one of the child of DataAccessException) from repo
        when(productRepository.save(productPassedToRepo))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Assert
        assertThrows(InternalServerException.class, () -> productService.createProduct(productRequest));
    }

    @Test
    @DisplayName("getAllProducts() fetches List<Product> from repo and transforms them to List<ProductResponse>")
    public void getAllProductsTest() throws InternalServerException {
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

    @Test
    @DisplayName("createProduct throws InternalServerException when Repo throws DataAccessException")
    public void getAllProductsWhenDBThrowsError() {
        // Mock throwing of exception (one of the child of DataAccessException) from repo
        when(productRepository.findAll())
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));

        // Assert
        assertThrows(InternalServerException.class, productService::getAllProducts);
    }

}

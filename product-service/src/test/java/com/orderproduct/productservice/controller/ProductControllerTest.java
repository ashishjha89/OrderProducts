package com.orderproduct.productservice.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.orderproduct.productservice.common.InternalServerException;
import com.orderproduct.productservice.dto.ProductRequest;
import com.orderproduct.productservice.dto.ProductResponse;
import com.orderproduct.productservice.dto.SavedProduct;
import com.orderproduct.productservice.service.ProductService;

@WebMvcTest(controllers = { ProductController.class })
@Import({ ProductControllerTest.MockedServiceConfig.class })
public class ProductControllerTest {

    @Autowired
    public MockMvc mockMvc;

    @Autowired
    public ProductService productService;

    @TestConfiguration
    static class MockedServiceConfig {
        @Bean
        public ProductService productService() {
            return mock(ProductService.class);
        }
    }

    @BeforeEach
    public void setUp() {
        Mockito.reset(productService);
    }

    @Test
    @DisplayName("should return `SavedProduct` successfully when `POST api/products` is called with valid request")
    public void createProductTest() throws Exception {
        // Initialise
        final var productRequest = new ProductRequest("name", "description", BigDecimal.valueOf(1000));
        final var savedProduct = new SavedProduct("id1");
        when(productService.createProduct(productRequest)).thenReturn(savedProduct);

        // Call method
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "name",
                            "description": "description",
                            "price": 1000
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(savedProduct.productId()));
    }

    @Test
    @DisplayName("should return `BadRequestException` when `POST api/products` is called with invalid request: empty name field")
    public void createProductBadRequestTest() throws Exception {
        // Empty name
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "",
                            "description": "description",
                            "price": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        // Null name
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": null,
                            "description": "description",
                            "price": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        // Empty description
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "name",
                            "description": "",
                            "price": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        // Null description
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "name",
                            "description": null,
                            "price": 1000
                        }
                        """))
                .andExpect(status().isBadRequest());

        // Empty price
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "name",
                            "description": "",
                            "price": null
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return `InternalServerError` when `POST api/products` is called & productService throws InternalServerException")
    public void placeOrderTest() throws Exception {
        // Initialise
        final var productRequest = new ProductRequest("name", "description", BigDecimal.valueOf(1000));
        when(productService.createProduct(productRequest))
                .thenThrow(new InternalServerException());
        // Call method
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "name",
                            "description": "description",
                            "price": 1000
                        }
                        """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("should return list of products when `GET api/products` is called")
    public void getAllProductsTest() throws Exception {
        // Initialise
        final var product1 = new ProductResponse("id1", "name1", "description1", BigDecimal.valueOf(1000));
        final var product2 = new ProductResponse("id2", "name2", "description2", BigDecimal.valueOf(2000));
        when(productService.getAllProducts()).thenReturn(List.of(product1, product2));

        // Call method
        mockMvc.perform(get("/api/products")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(product1.getId()))
                .andExpect(jsonPath("$[0].name").value(product1.getName()))
                .andExpect(jsonPath("$[0].description").value(product1.getDescription()))
                .andExpect(jsonPath("$[0].price").value(product1.getPrice()))
                .andExpect(jsonPath("$[1].id").value(product2.getId()))
                .andExpect(jsonPath("$[1].name").value(product2.getName()))
                .andExpect(jsonPath("$[1].description").value(product2.getDescription()))
                .andExpect(jsonPath("$[1].price").value(product2.getPrice()));
    }

}

package com.example.productservice;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class ProductServiceApplicationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry dymDynamicPropertyRegistry) {
        dymDynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("POST call to /api/product will insert Product to Db")
    void postApiCallShouldInsertProductToDatabase() throws Exception {
        var productRequest = getProductRequest();
        var productRequestStr = objectMapper.writeValueAsString(productRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr))
                .andExpect(status().isCreated());
        assertEquals(1, productRepository.findAll().size());
    }

    @Test
    @DisplayName("GET call to /api/product will get all Products from Db")
    void getApiCallForProductShouldReturnAllProductsFromDatabase() throws Exception {
        // Insert a product
        var uniqueName = System.currentTimeMillis() + "";
        var product = Product.builder().name(uniqueName).description("Description").price(BigDecimal.valueOf(12)).build();
        productRepository.insert(product);

        // Make Api call
        MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        var jsonStr = result.getResponse().getContentAsString();
        var responses = objectMapper.readValue(jsonStr, ProductResponse[].class);
        Optional<ProductResponse> productOpt = Arrays.stream(responses)
                .filter(productRes -> productRes.getName().equals(uniqueName))
                .findAny();

        // Assert that api returns inserted item
        assertTrue(productOpt.isPresent());
    }

    private ProductRequest getProductRequest() {
        return ProductRequest.builder()
                .name("iPhone 13")
                .description("iPhone 13")
                .price(BigDecimal.valueOf(1200))
                .build();
    }

}

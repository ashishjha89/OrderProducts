package com.orderproduct.productservice;

import com.orderproduct.productservice.common.ErrorBody;
import com.orderproduct.productservice.common.ErrorComponent;
import com.orderproduct.productservice.dto.ProductRequest;
import com.orderproduct.productservice.dto.ProductResponse;
import com.orderproduct.productservice.entity.Product;
import com.orderproduct.productservice.repository.ProductRepository;
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
@SuppressWarnings("unused")
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
    @DisplayName("should return `SavedProduct` successfully when `POST api/products` is called with valid request")
    void postApiCall_ShouldInsertProductToDatabase() throws Exception {
        // Initialise
        var productRequest = ProductRequest.builder()
                .name("iPhone 13")
                .description("iPhone 13")
                .price(BigDecimal.valueOf(1200))
                .build();
        var productRequestStr = objectMapper.writeValueAsString(productRequest);

        // Make Api call
        mockMvc.perform(MockMvcRequestBuilders.post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr))
                .andExpect(status().isCreated());

        // Assert item is inserted
        assertEquals(1, productRepository.findAll().size());
    }

    @Test
    @DisplayName("should throw `BadRequestException` when `POST api/products` is called with invalid request")
    void postApiCall_ShouldThrowBadRequestException_WhenNameOrDescriptionOrPriceIsEmptyOrMissing() throws Exception {
        // Initialise 1
        var productRequestWithAllNullFields = ProductRequest.builder().build();
        var productRequestStr1 = objectMapper.writeValueAsString(productRequestWithAllNullFields);
        // Make Api call and expect BadRequest
        MvcResult result1 = mockMvc.perform(MockMvcRequestBuilders.post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr1))
                .andExpect(status().isBadRequest())
                .andReturn();
        // Process response
        final var jsonStr = result1.getResponse().getContentAsString();
        final var errorBody = objectMapper.readValue(jsonStr, ErrorBody.class);
        // Assert
        assertEquals(ErrorComponent.BAD_REQUEST_ERROR_CODE, errorBody.errorCode());
        assertEquals(ErrorComponent.badRequestMsg, errorBody.errorMessage());

        // Initialise 2
        var productRequestWithNameAsNull = ProductRequest.builder().name(null).price(BigDecimal.valueOf(100)).description("Description").build();
        var productRequestStr2 = objectMapper.writeValueAsString(productRequestWithNameAsNull);
        // Make Api call and expect BadRequest
        mockMvc.perform(MockMvcRequestBuilders.post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr2))
                .andExpect(status().isBadRequest());

        // Initialise 3
        var productRequestWithDescriptionAsNull = ProductRequest.builder().name("name").price(BigDecimal.valueOf(100)).description(null).build();
        var productRequestStr3 = objectMapper.writeValueAsString(productRequestWithDescriptionAsNull);
        // Make Api call and expect BadRequest
        mockMvc.perform(MockMvcRequestBuilders.post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr3))
                .andExpect(status().isBadRequest());

        // Initialise 4
        var productRequestWithPriceAsNull = ProductRequest.builder().name("name").price(null).description("Description").build();
        var productRequestStr4 = objectMapper.writeValueAsString(productRequestWithPriceAsNull);
        // Make Api call and expect BadRequest
        mockMvc.perform(MockMvcRequestBuilders.post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestStr4))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return `ProductResponse` successfully when `GET api/products` is called")
    void getApiCallForProduct_ShouldReturnAllProductsFromDatabase() throws Exception {
        // Insert a product
        var uniqueName = System.currentTimeMillis() + "";
        var product = Product.builder().name(uniqueName).description("Description").price(BigDecimal.valueOf(12)).build();
        productRepository.insert(product);

        // Make Api call
        MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var responses = objectMapper.readValue(jsonStr, ProductResponse[].class);
        Optional<ProductResponse> productOpt = Arrays.stream(responses)
                .filter(productRes -> productRes.getName().equals(uniqueName))
                .findAny();

        // Assert that api returns inserted item
        assertTrue(productOpt.isPresent());
    }

}

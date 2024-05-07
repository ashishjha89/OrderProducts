package com.example.inventoryservice;

import com.example.inventoryservice.common.ErrorBody;
import com.example.inventoryservice.common.ErrorComponent;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class InventoryServiceApplicationTests {

    @Container
    static final MySQLContainer mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:5.7.34"));

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Inventory inventory1 = Inventory.builder().quantity(10).skuCode("skuCode1").build();
    private final Inventory inventory2 = Inventory.builder().quantity(10).skuCode("skuCode2").build();

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @BeforeEach
    void setupInventory() {
        inventoryRepository.save(inventory1);
        inventoryRepository.save(inventory2);
    }

    @AfterEach
    void cleanup() {
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("/api/inventory/skuCode1 returns {isInStock:true} when Inventory with skuCode1 is available")
    void isInStock_WhenInventoryIsPresent() throws Exception {
        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/skuCode1"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var stockStatus = objectMapper.readValue(jsonStr, InventoryStockStatus.class);

        // Assert
        assertTrue(stockStatus.isInStock());
    }

    @Test
    @DisplayName("/api/inventory/skuCodeRandom returns {isInStock:false} when Inventory with skuCode1 is NOT available")
    void isInStock_WhenInventoryIsAbsent() throws Exception {
        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/skuCodeRandom"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var stockStatus = objectMapper.readValue(jsonStr, InventoryStockStatus.class);

        // Assert
        assertFalse(stockStatus.isInStock());
    }

    @Test
    @DisplayName("/api/inventory/ returns BadRequestException when empty string/space is passed as skuCode")
    void isInStockThrowsBadRequestException_WhenEmptySkuCodeIsPassed() throws Exception {
        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/ "))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var errorBody = objectMapper.readValue(jsonStr, ErrorBody.class);

        // Assert
        assertEquals(ErrorComponent.BAD_REQUEST, errorBody.errorCode());
        assertEquals(ErrorComponent.badRequestMsg, errorBody.errorMessage());
    }

}

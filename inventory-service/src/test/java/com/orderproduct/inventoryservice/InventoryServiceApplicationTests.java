package com.orderproduct.inventoryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@SuppressWarnings("unused")
class InventoryServiceApplicationTests {

    @Container
    static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Inventory inventory1 = Inventory.builder().quantity(10).skuCode("skuCode1").build();
    private final Inventory inventory2 = Inventory.builder().quantity(20).skuCode("skuCode2").build();
    private final Inventory inventory3 = Inventory.builder().quantity(0).skuCode("skuCode3").build();
    private final Inventory inventory4 = Inventory.builder().quantity(40).skuCode("skuCode4").build();

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
        inventoryRepository.save(inventory3);
        inventoryRepository.save(inventory4);
    }

    @AfterEach
    void cleanup() {
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("GET:/api/inventory should return inStock=true if skuCode is not available")
    void isInStock_WhenInventoryIsPresent() throws Exception {
        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/skuCode1"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var stockStatus = objectMapper.readValue(jsonStr, InventoryStockStatus.class);

        // Assert
        assertTrue(stockStatus.inStock());
    }

    @Test
    @DisplayName("GET:/api/inventory should return inStock=false if skuCode is not available")
    void isInStock_WhenInventoryIsAbsent() throws Exception {
        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/skuCodeRandom"))
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var stockStatus = objectMapper.readValue(jsonStr, InventoryStockStatus.class);

        // Assert
        assertFalse(stockStatus.inStock());
    }

    @Test
    @DisplayName("GET:/api/inventory should return List<InventoryStockStatus> for skuCodes passed in queryParams")
    void stocksStatusTest() throws Exception {
        final var expectedStockStatus = List.of(
                new InventoryStockStatus("skuCode1", true),
                new InventoryStockStatus("skuCode2", true),
                new InventoryStockStatus("skuCode3", false), // Note: skuCode3 has quantity=0
                new InventoryStockStatus("random", false)
        );

        // Make Api call
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders
                                .get("/api/inventory?skuCode=skuCode1&skuCode=skuCode2&skuCode=skuCode3&skuCode=random")
                )
                .andExpect(status().isOk())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var stocksStatus = Arrays.stream(objectMapper.readValue(jsonStr, InventoryStockStatus[].class)).toList();

        // Assert
        assertEquals(expectedStockStatus, stocksStatus);
    }

    @Test
    @DisplayName("POST:/api/inventory should return 201 when creating new inventory")
    void createInventory_Success() throws Exception {
        // Given
        final var newInventory = """
                {
                    "skuCode": "newSkuCode",
                    "quantity": 50
                }
                """;

        // Make Api call
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders
                                .post("/api/inventory")
                                .contentType("application/json")
                                .content(newInventory)
                )
                .andExpect(status().isCreated())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var response = objectMapper.readValue(jsonStr, Map.class);

        // Assert
        assertEquals("newSkuCode", response.get("skuCode"));
        assertEquals("Inventory created successfully", response.get("message"));
        assertTrue(Objects.requireNonNull(result.getResponse().getHeader("Location"))
                .endsWith("/api/inventory/newSkuCode"));

        // Verify in database
        final var savedInventory = inventoryRepository.findBySkuCode("newSkuCode");
        assertTrue(savedInventory.isPresent());
        assertEquals(50, savedInventory.get().getQuantity());
    }

    @Test
    @DisplayName("DELETE:/api/inventory/{sku-code} should return 204 when deleting existing inventory")
    void deleteInventory_Success() throws Exception {
        // Given - we already have inventory1 from setup

        // Make Api call
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .delete("/api/inventory/skuCode1")
                )
                .andExpect(status().isNoContent());

        // Verify deletion in database
        final var deletedInventory = inventoryRepository.findBySkuCode("skuCode1");
        assertTrue(deletedInventory.isEmpty());
    }
}

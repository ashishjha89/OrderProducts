package com.orderproduct.inventoryservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UpdateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class InventoryIntegrationTests {

        @Container
        static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

        @Autowired
        private InventoryRepository inventoryRepository;

        @Autowired
        private ReservationRepository reservationRepository;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private final Inventory inventory1 = Inventory.createInventory("skuCode1", 10);
        private final Inventory inventory2 = Inventory.createInventory("skuCode2", 5);
        private final Inventory inventory3 = Inventory.createInventory("skuCode3", 0);
        private final Reservation inventory1Reserved = Reservation.builder()
                        .skuCode("skuCode1")
                        .reservedQuantity(1)
                        .orderNumber("orderNumber1")
                        .reservedAt(java.time.LocalDateTime.now())
                        .status(ReservationState.PENDING)
                        .build();
        private final Reservation inventory2Reserved = Reservation.builder()
                        .skuCode("skuCode2")
                        .reservedQuantity(2)
                        .orderNumber("orderNumber2")
                        .reservedAt(java.time.LocalDateTime.now())
                        .status(ReservationState.FULFILLED) // Only Pending reservations are considered for available
                        .build();

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
                reservationRepository.save(inventory1Reserved);
                reservationRepository.save(inventory2Reserved);
        }

        @AfterEach
        void cleanup() {
                inventoryRepository.deleteAll();
                reservationRepository.deleteAll();
        }

        @Test
        @DisplayName("GET:/api/inventory should return List<AvailableInventoryResponse> for skuCodes passed in queryParams")
        void availableInventoryTest() throws Exception {
                final var expectedAvailableInventoryResponse = List.of(
                                // Note: skuCode1 has 10 onHands and 1 in pending reservation
                                new AvailableInventoryResponse("skuCode1", 9),
                                // Note: skuCode2 has quantity=5
                                new AvailableInventoryResponse("skuCode2", 5),
                                // Note: skuCode3 has quantity=0
                                new AvailableInventoryResponse("skuCode3", 0),
                                // Note: random is not in the database
                                new AvailableInventoryResponse("random", 0));

                // Make Api call with skuCode1, skuCode2, skuCode3, random
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .get("/api/inventory?skuCode=skuCode1&skuCode=skuCode2&skuCode=skuCode3&skuCode=random"))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var actualAvailableInventoryResponse = Arrays
                                .stream(objectMapper.readValue(jsonStr, AvailableInventoryResponse[].class))
                                .toList();

                // Assert
                assertEquals(expectedAvailableInventoryResponse, actualAvailableInventoryResponse);
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
                                                .content(newInventory))
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
                assertEquals(50, savedInventory.get().getOnHandQuantity());
        }

        @Test
        @DisplayName("PUT:/api/inventory/{sku-code} should return 200 when updating existing inventory")
        void updateInventory_Success() throws Exception {
                // Given - we already have inventory1 from setup with quantity=10
                final var updateRequest = """
                                {
                                    "skuCode": "skuCode1",
                                    "quantity": 25
                                }
                                """;

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .put("/api/inventory/skuCode1")
                                                .contentType("application/json")
                                                .content(updateRequest))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, UpdateInventoryResponse.class);

                // Assert response structure
                assertEquals("skuCode1", response.skuCode());
                assertEquals(25, response.quantity());
                assertEquals("Inventory updated successfully", response.message());

                // Verify inventory was updated in database
                final var updatedInventory = inventoryRepository.findBySkuCode("skuCode1");
                assertTrue(updatedInventory.isPresent());
                assertEquals(25, updatedInventory.get().getOnHandQuantity());
        }

        @Test
        @DisplayName("PUT:/api/inventory/{sku-code} should return 404 when updating non-existent inventory")
        void updateInventory_NonExistentInventory() throws Exception {
                // Given - SKU code that doesn't exist
                final var updateRequest = """
                                {
                                    "skuCode": "nonExistentSku",
                                    "quantity": 50
                                }
                                """;

                // Make Api call
                mockMvc.perform(
                                MockMvcRequestBuilders
                                                .put("/api/inventory/nonExistentSku")
                                                .contentType("application/json")
                                                .content(updateRequest))
                                .andExpect(status().isNotFound());

                // Verify no inventory was created in database
                final var nonExistentInventory = inventoryRepository.findBySkuCode("nonExistentSku");
                assertTrue(nonExistentInventory.isEmpty());
        }

        @Test
        @DisplayName("DELETE:/api/inventory/{sku-code} should return 204 when deleting existing inventory")
        void deleteInventory_Success() throws Exception {
                // Given - we already have inventory1 from setup

                // Make Api call
                mockMvc.perform(
                                MockMvcRequestBuilders
                                                .delete("/api/inventory/skuCode1"))
                                .andExpect(status().isNoContent());

                // Verify deletion in database
                final var deletedInventory = inventoryRepository.findBySkuCode("skuCode1");
                assertTrue(deletedInventory.isEmpty());
        }
}
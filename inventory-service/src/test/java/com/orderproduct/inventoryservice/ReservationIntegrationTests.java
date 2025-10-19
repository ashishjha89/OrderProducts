package com.orderproduct.inventoryservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;

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
import com.orderproduct.inventoryservice.common.exception.ErrorBody;
import com.orderproduct.inventoryservice.common.exception.ErrorBodyWithUnavailableProducts;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.ReservationStateUpdateResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ReservationIntegrationTests {

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
                        .reservedAt(LocalDateTime.now())
                        .status(ReservationState.PENDING)
                        .build();
        private final Reservation inventory2Reserved = Reservation.builder()
                        .skuCode("skuCode2")
                        .reservedQuantity(2)
                        .orderNumber("orderNumber2")
                        .reservedAt(LocalDateTime.now())
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
        @DisplayName("POST:/api/reservations should return 200 when reserving products successfully")
        void reserveProducts_Success() throws Exception {
                // Given
                final var reservationRequest = """
                                {
                                    "orderNumber": "ORDER-123",
                                    "itemReservationRequests": [
                                        {
                                            "skuCode": "skuCode1",
                                            "quantity": 5
                                        },
                                        {
                                            "skuCode": "skuCode2",
                                            "quantity": 3
                                        }
                                    ]
                                }
                                """;

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations")
                                                .contentType("application/json")
                                                .content(reservationRequest))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = Arrays
                                .stream(objectMapper.readValue(jsonStr, AvailableInventoryResponse[].class))
                                .toList();

                // Assert response contains available quantities before reservation
                assertEquals(2, response.size());

                // skuCode1: onHands=10, reserved=1, requested=5, so available=4
                var skuCode1Response = response.stream()
                                .filter(r -> "skuCode1".equals(r.skuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(4, skuCode1Response.availableQuantity());

                // skuCode2: onHands=5, reserved=0, requested=3, so available=2
                var skuCode2Response = response.stream()
                                .filter(r -> "skuCode2".equals(r.skuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(2, skuCode2Response.availableQuantity());

                // Verify reservations were created in database
                final var newReservations = reservationRepository.findByOrderNumber("ORDER-123");
                assertEquals(2, newReservations.size());

                var skuCode1Reservation = newReservations.stream()
                                .filter(r -> "skuCode1".equals(r.getSkuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(5, skuCode1Reservation.getReservedQuantity());
                assertEquals(ReservationState.PENDING, skuCode1Reservation.getStatus());

                var skuCode2Reservation = newReservations.stream()
                                .filter(r -> "skuCode2".equals(r.getSkuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(3, skuCode2Reservation.getReservedQuantity());
                assertEquals(ReservationState.PENDING, skuCode2Reservation.getStatus());
        }

        @Test
        @DisplayName("POST:/api/reservations should return 409 when insufficient available items")
        void reserveProducts_InsufficientItems() throws Exception {
                // Given - onHands=10, reserved=1, so available=9
                // But we're requesting 15 which exceeds available quantity
                final var reservationRequest = """
                                {
                                    "orderNumber": "ORDER-456",
                                    "itemReservationRequests": [
                                        {
                                            "skuCode": "skuCode1",
                                            "quantity": 15
                                        }
                                    ]
                                }
                                """;

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations")
                                                .contentType("application/json")
                                                .content(reservationRequest))
                                .andExpect(status().isConflict())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, ErrorBodyWithUnavailableProducts.class);

                // Assert error response
                assertEquals("NOT_ENOUGH_ITEM_ERROR_CODE", response.errorCode());

                // Assert unavailable products details
                var unavailableProducts = response.unavailableProducts();
                assertEquals(1, unavailableProducts.size());

                var unavailableProduct = unavailableProducts.get(0);
                assertEquals("skuCode1", unavailableProduct.skuCode());
                assertEquals(15, unavailableProduct.requestedQuantity());
                assertEquals(9, unavailableProduct.availableQuantity()); // 10 onHands - 1 existing pending

                // Verify no reservations were created in database
                final var newReservations = reservationRepository.findByOrderNumber("ORDER-456");
                assertEquals(0, newReservations.size());
        }

        @Test
        @DisplayName("POST:/api/reservations should return 409 when reservation not allowed for order with non-pending state")
        void reserveProducts_OrderReservationNotAllowed() throws Exception {
                // Given - orderNumber2 already has FULFILLED reservations
                // Attempting to reserve again should fail
                final var reservationRequest = """
                                {
                                    "orderNumber": "orderNumber2",
                                    "itemReservationRequests": [
                                        {
                                            "skuCode": "skuCode2",
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """;

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations")
                                                .contentType("application/json")
                                                .content(reservationRequest))
                                .andExpect(status().isConflict())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, ErrorBody.class);

                // Assert error response
                assertEquals("ORDER_RESERVATION_NOT_ALLOWED", response.errorCode());

                // Verify no new reservations were created in database
                final var reservations = reservationRepository.findByOrderNumber("orderNumber2");
                assertEquals(1, reservations.size()); // Only the existing FULFILLED reservation
                assertEquals(ReservationState.FULFILLED, reservations.get(0).getStatus());
        }

        @Test
        @DisplayName("POST:/api/reservations/{orderNumber}/fulfill should return 200 when fulfilling order successfully")
        void fulfillOrder_Success() throws Exception {
                // Given - we have existing reservations from setup
                final var orderNumber = "orderNumber1";

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations/{orderNumber}/fulfill", orderNumber))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, ReservationStateUpdateResponse.class);

                // Assert response structure
                assertEquals(orderNumber, response.orderNumber());
                assertEquals(ReservationState.FULFILLED, response.state());
                assertEquals(1, response.updatedItems().size());

                // Assert updated item details
                var updatedItem = response.updatedItems().get(0);
                assertEquals("skuCode1", updatedItem.skuCode());
                assertEquals(1, updatedItem.reservedQuantity());
                assertEquals(ReservationState.FULFILLED, updatedItem.status());

                // Verify reservation was updated in database
                final var updatedReservations = reservationRepository.findByOrderNumber(orderNumber);
                assertEquals(1, updatedReservations.size());

                var updatedReservation = updatedReservations.get(0);
                assertEquals(ReservationState.FULFILLED, updatedReservation.getStatus());
                assertEquals(1, updatedReservation.getReservedQuantity());
                assertEquals("skuCode1", updatedReservation.getSkuCode());
        }

        @Test
        @DisplayName("POST:/api/reservations/{orderNumber}/fulfill should return 200 when no reservations found")
        void fulfillOrder_NoReservationsFound() throws Exception {
                // Given - order number that doesn't exist
                final var orderNumber = "nonExistentOrder";

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations/{orderNumber}/fulfill", orderNumber))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, ReservationStateUpdateResponse.class);

                // Assert response structure
                assertEquals(orderNumber, response.orderNumber());
                assertEquals(ReservationState.FULFILLED, response.state());
                assertEquals(0, response.updatedItems().size()); // No items updated
        }

        @Test
        @DisplayName("POST:/api/reservations/{orderNumber}/cancel should return 200 when cancelling order successfully")
        void cancelOrder_Success() throws Exception {
                // Given - we have existing reservations from setup
                final var orderNumber = "orderNumber1";

                // Make Api call
                MvcResult result = mockMvc.perform(
                                MockMvcRequestBuilders
                                                .post("/api/reservations/{orderNumber}/cancel", orderNumber))
                                .andExpect(status().isOk())
                                .andReturn();

                // Process response
                final var jsonStr = result.getResponse().getContentAsString();
                final var response = objectMapper.readValue(jsonStr, ReservationStateUpdateResponse.class);

                // Assert response structure
                assertEquals(orderNumber, response.orderNumber());
                assertEquals(ReservationState.CANCELLED, response.state());
                assertEquals(1, response.updatedItems().size());

                // Assert updated item details
                var updatedItem = response.updatedItems().get(0);
                assertEquals("skuCode1", updatedItem.skuCode());
                assertEquals(1, updatedItem.reservedQuantity());
                assertEquals(ReservationState.CANCELLED, updatedItem.status());

                // Verify reservation was updated in database
                final var updatedReservations = reservationRepository.findByOrderNumber(orderNumber);
                assertEquals(1, updatedReservations.size());

                var updatedReservation = updatedReservations.get(0);
                assertEquals(ReservationState.CANCELLED, updatedReservation.getStatus());
                assertEquals(1, updatedReservation.getReservedQuantity());
                assertEquals("skuCode1", updatedReservation.getSkuCode());
        }
}
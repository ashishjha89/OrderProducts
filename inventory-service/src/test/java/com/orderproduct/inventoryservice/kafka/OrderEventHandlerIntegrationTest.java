package com.orderproduct.inventoryservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

@SpringBootTest
@Testcontainers
class OrderEventHandlerIntegrationTest {

    @Container
    static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderEventHandler orderEventHandler;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean up data before each test
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up data after each test
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("FULFILLED order should update reservations to FULFILLED and deduct inventory quantities")
    void handleOrderStatusChangedEvent_FulfilledOrder_UpdatesReservationsAndDeductsInventory() throws Exception {
        // Given - Setup inventory and reservations
        String orderNumber = "ORDER-FULFILL-001";
        String skuCode1 = "SKU-001";
        String skuCode2 = "SKU-002";

        // Create inventory with initial quantities
        Inventory inventory1 = Inventory.createInventory(skuCode1, 100);
        Inventory inventory2 = Inventory.createInventory(skuCode2, 50);
        inventoryRepository.saveAll(List.of(inventory1, inventory2));

        // Create pending reservations
        Reservation reservation1 = Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode1)
                .reservedQuantity(30)
                .reservedAt(LocalDateTime.now())
                .status(ReservationState.PENDING)
                .build();
        Reservation reservation2 = Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode2)
                .reservedQuantity(20)
                .reservedAt(LocalDateTime.now())
                .status(ReservationState.PENDING)
                .build();
        reservationRepository.saveAll(List.of(reservation1, reservation2));

        // Create FULFILLED order event payload
        String payload = createOrderStatusChangedEventPayload(orderNumber, "FULFILLED");

        // When - Process the FULFILLED order event
        orderEventHandler.handleOrderStatusChangedEvent(
                payload,
                "OrderStatusChangedEvent",
                "Order",
                "aggregate-" + orderNumber,
                "event-" + orderNumber);

        // Then - Verify reservations are updated to FULFILLED
        List<Reservation> updatedReservations = reservationRepository.findByOrderNumber(orderNumber);
        assertThat(updatedReservations).hasSize(2);
        assertThat(updatedReservations).allMatch(r -> r.getStatus() == ReservationState.FULFILLED);

        // Verify inventory quantities are deducted
        Inventory updatedInventory1 = inventoryRepository.findBySkuCode(skuCode1).orElseThrow();
        Inventory updatedInventory2 = inventoryRepository.findBySkuCode(skuCode2).orElseThrow();

        assertThat(updatedInventory1.getOnHandQuantity()).isEqualTo(70); // 100 - 30
        assertThat(updatedInventory2.getOnHandQuantity()).isEqualTo(30); // 50 - 20
    }

    @Test
    @DisplayName("CANCELLED order should update reservations to CANCELLED without deducting inventory")
    void handleOrderStatusChangedEvent_CancelledOrder_UpdatesReservationsWithoutDeductingInventory() throws Exception {
        // Given - Setup inventory and reservations
        String orderNumber = "ORDER-CANCEL-001";
        String skuCode1 = "SKU-001";
        String skuCode2 = "SKU-002";

        // Create inventory with initial quantities
        Inventory inventory1 = Inventory.createInventory(skuCode1, 100);
        Inventory inventory2 = Inventory.createInventory(skuCode2, 50);
        inventoryRepository.saveAll(List.of(inventory1, inventory2));

        // Create pending reservations
        Reservation reservation1 = Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode1)
                .reservedQuantity(30)
                .reservedAt(LocalDateTime.now())
                .status(ReservationState.PENDING)
                .build();
        Reservation reservation2 = Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode2)
                .reservedQuantity(20)
                .reservedAt(LocalDateTime.now())
                .status(ReservationState.PENDING)
                .build();
        reservationRepository.saveAll(List.of(reservation1, reservation2));

        // Create CANCELLED order event payload
        String payload = createOrderStatusChangedEventPayload(orderNumber, "CANCELLED");

        // When - Process the CANCELLED order event
        orderEventHandler.handleOrderStatusChangedEvent(
                payload,
                "OrderStatusChangedEvent",
                "Order",
                "aggregate-" + orderNumber,
                "event-" + orderNumber);

        // Then - Verify reservations are updated to CANCELLED
        List<Reservation> updatedReservations = reservationRepository.findByOrderNumber(orderNumber);
        assertThat(updatedReservations).hasSize(2);
        assertThat(updatedReservations).allMatch(r -> r.getStatus() == ReservationState.CANCELLED);

        // Verify inventory quantities remain unchanged
        Inventory updatedInventory1 = inventoryRepository.findBySkuCode(skuCode1).orElseThrow();
        Inventory updatedInventory2 = inventoryRepository.findBySkuCode(skuCode2).orElseThrow();

        assertThat(updatedInventory1.getOnHandQuantity()).isEqualTo(100); // Unchanged
        assertThat(updatedInventory2.getOnHandQuantity()).isEqualTo(50); // Unchanged
    }

    @Test
    @DisplayName("PENDING order status should be ignored")
    void handleOrderStatusChangedEvent_PendingOrderStatus_IgnoresEvent() throws Exception {
        // Given - Setup inventory and reservations
        String orderNumber = "ORDER-PENDING-001";
        String skuCode = "SKU-001";

        Inventory inventory = Inventory.createInventory(skuCode, 100);
        inventoryRepository.save(inventory);

        Reservation reservation = Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode)
                .reservedQuantity(25)
                .reservedAt(LocalDateTime.now())
                .status(ReservationState.PENDING)
                .build();
        reservationRepository.save(reservation);

        // Create PENDING order event payload
        String payload = createOrderStatusChangedEventPayload(orderNumber, "PENDING");

        // When - Process the PENDING order event
        orderEventHandler.handleOrderStatusChangedEvent(
                payload,
                "OrderStatusChangedEvent",
                "Order",
                "aggregate-" + orderNumber,
                "event-" + orderNumber);

        // Then - Verify reservations remain unchanged
        List<Reservation> reservations = reservationRepository.findByOrderNumber(orderNumber);
        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getStatus()).isEqualTo(ReservationState.PENDING);

        // Verify inventory quantity remains unchanged
        Inventory updatedInventory = inventoryRepository.findBySkuCode(skuCode).orElseThrow();
        assertThat(updatedInventory.getOnHandQuantity()).isEqualTo(100); // Unchanged
    }

    private String createOrderStatusChangedEventPayload(String orderNumber, String status) throws Exception {
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(orderNumber, status);
        String innerPayload = objectMapper.writeValueAsString(event);

        Map<String, Object> outerPayload = new HashMap<>();
        outerPayload.put("payload", innerPayload);

        return objectMapper.writeValueAsString(outerPayload);
    }
}

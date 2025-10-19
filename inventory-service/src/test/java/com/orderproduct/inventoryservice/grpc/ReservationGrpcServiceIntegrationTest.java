package com.orderproduct.inventoryservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
                "grpc.server.inProcessName=test",
                "grpc.server.port=-1",
                "grpc.client.reservation-service.address=in-process:test"
})
@DirtiesContext
@Testcontainers
class ReservationGrpcServiceIntegrationTest {

        @Container
        static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

        @Autowired
        private InventoryRepository inventoryRepository;

        @Autowired
        private ReservationRepository reservationRepository;

        @GrpcClient("reservation-service")
        private ReservationServiceGrpc.ReservationServiceBlockingStub reservationServiceStub;

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
        @DisplayName("Should successfully reserve products via gRPC")
        void reserveProducts_Success() {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-123")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(5)
                                                .build())
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode2")
                                                .setQuantity(3)
                                                .build())
                                .build();

                // When
                ReserveProductsResponse response = reservationServiceStub.reserveProducts(request);

                // Then
                assertNotNull(response);
                assertEquals(2, response.getAvailableInventoryCount());

                // skuCode1: onHands=10, reserved=1, requested=5, so available=4
                AvailableInventoryResponse skuCode1Response = response.getAvailableInventoryList().stream()
                                .filter(r -> "skuCode1".equals(r.getSkuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(4, skuCode1Response.getAvailableQuantity());

                // skuCode2: onHands=5, reserved=0, requested=3, so available=2
                AvailableInventoryResponse skuCode2Response = response.getAvailableInventoryList().stream()
                                .filter(r -> "skuCode2".equals(r.getSkuCode()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(2, skuCode2Response.getAvailableQuantity());

                // Verify reservations were created in database
                var newReservations = reservationRepository.findByOrderNumber("ORDER-123");
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
        @DisplayName("Should return RESOURCE_EXHAUSTED when insufficient available items via gRPC")
        void reserveProducts_InsufficientItems() {
                // Given - onHands=10, reserved=1, so available=9
                // But we're requesting 15 which exceeds available quantity
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-456")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode1")
                                                .setQuantity(15)
                                                .build())
                                .build();

                // When & Then
                StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
                        reservationServiceStub.reserveProducts(request);
                });

                assertEquals(Status.RESOURCE_EXHAUSTED.getCode(), exception.getStatus().getCode());

                // Verify no reservations were created in database
                var newReservations = reservationRepository.findByOrderNumber("ORDER-456");
                assertEquals(0, newReservations.size());
        }

        @Test
        @DisplayName("Should return FAILED_PRECONDITION when reservation not allowed for order with non-pending state via gRPC")
        void reserveProducts_OrderReservationNotAllowed() {
                // Given - orderNumber2 already has FULFILLED reservations
                // Attempting to reserve again should fail
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("orderNumber2")
                                .addItemReservationRequests(ItemReservationRequest.newBuilder()
                                                .setSkuCode("skuCode2")
                                                .setQuantity(1)
                                                .build())
                                .build();

                // When & Then
                StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
                        reservationServiceStub.reserveProducts(request);
                });

                assertEquals(Status.FAILED_PRECONDITION.getCode(), exception.getStatus().getCode());

                // Verify no new reservations were created in database
                var reservations = reservationRepository.findByOrderNumber("orderNumber2");
                assertEquals(1, reservations.size()); // Only the existing FULFILLED reservation
                assertEquals(ReservationState.FULFILLED, reservations.get(0).getStatus());
        }

        @Test
        @DisplayName("Should handle empty request via gRPC")
        void reserveProducts_EmptyRequest() {
                // Given
                ReserveProductsRequest request = ReserveProductsRequest.newBuilder()
                                .setOrderNumber("ORDER-EMPTY")
                                .build();

                // When
                ReserveProductsResponse response = reservationServiceStub.reserveProducts(request);

                // Then
                assertNotNull(response);
                assertEquals(0, response.getAvailableInventoryCount());
        }

}
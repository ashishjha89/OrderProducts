package com.orderproduct.inventoryservice.service.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.InventoryExceptionHandler;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;
import com.orderproduct.inventoryservice.service.inventory.InventoryDeductionService;

import jakarta.persistence.PersistenceException;

public class ReservationServiceFindPendingReservationsTest {

        private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
        private final InventoryExceptionHandler inventoryExceptionHandler = new InventoryExceptionHandler();
        private final ReservationRepositoryWrapper reservationRepositoryWrapper = new ReservationRepositoryWrapper(
                        reservationRepository, inventoryExceptionHandler);
        private final TimeProvider timeProvider = mock(TimeProvider.class);
        private final InventoryDeductionService inventoryDeductionService = mock(InventoryDeductionService.class);

        private final ReservedQuantityService reservedQuantityService = new ReservedQuantityService(
                        reservationRepositoryWrapper);
        private final ReservationOrchestrator reservationBuilder = new ReservationOrchestrator(
                        reservationRepositoryWrapper,
                        timeProvider);
        private final ReservationStateManager reservationStateManager = new ReservationStateManager(
                        reservationRepositoryWrapper, inventoryDeductionService);

        private final ReservationService reservationService = new ReservationService(reservationRepositoryWrapper,
                        reservedQuantityService, reservationBuilder, reservationStateManager);

        @Test
        @DisplayName("`findPendingReservedQuantities()` should return `List<ReservedItemQuantity>` for passed skuCodes")
        public void findPendingReservedQuantitiesTest() throws InternalServerException {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2", "skuCode3");
                final var currentTime = LocalDateTime.now();
                final var matchingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber("ORDER-001")
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber("ORDER-002")
                                                .skuCode("skuCode1")
                                                .reservedQuantity(3)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(3L)
                                                .orderNumber("ORDER-003")
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                // Expected: Individual ReservedItemQuantity objects for each reservation
                final var expectedReservedItemQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 5),
                                new ReservedItemQuantity("skuCode1", 3),
                                new ReservedItemQuantity("skuCode2", 10));

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenReturn(matchingReservations);

                // When
                List<ReservedItemQuantity> result = reservationService.findPendingReservedQuantities(skuCodeList);

                // Then
                assertEquals(expectedReservedItemQuantities, result);
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should return empty list when no pending reservations exist")
        public void findPendingReservedQuantities_NoPendingReservations_ReturnsEmptyList()
                        throws InternalServerException {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2");

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenReturn(List.of());

                // When
                List<ReservedItemQuantity> result = reservationService.findPendingReservedQuantities(skuCodeList);

                // Then
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should handle empty SKU code list")
        public void findPendingReservedQuantities_EmptySkuCodeList_ReturnsEmptyList() throws InternalServerException {
                // Given
                final var skuCodeList = List.<String>of();

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenReturn(List.of());

                // When
                List<ReservedItemQuantity> result = reservationService.findPendingReservedQuantities(skuCodeList);

                // Then
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should throw InternalServerException when DataAccessException occurs")
        public void findPendingReservedQuantities_DataAccessException_ThrowsInternalServerException() {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2");

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenThrow(new DataAccessResourceFailureException("Database connection failed"));

                // When & Then
                assertThrows(InternalServerException.class,
                                () -> reservationService.findPendingReservedQuantities(skuCodeList));
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should throw InternalServerException when PersistenceException occurs")
        public void findPendingReservedQuantities_PersistenceException_ThrowsInternalServerException() {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2");

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenThrow(new PersistenceException("Database constraint violation"));

                // When & Then
                assertThrows(InternalServerException.class,
                                () -> reservationService.findPendingReservedQuantities(skuCodeList));
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should throw InternalServerException when Exception occurs")
        public void findPendingReservedQuantities_Exception_ThrowsInternalServerException() {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2");

                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenThrow(new RuntimeException("Unexpected error"));

                // When & Then
                assertThrows(InternalServerException.class,
                                () -> reservationService.findPendingReservedQuantities(skuCodeList));
        }
}
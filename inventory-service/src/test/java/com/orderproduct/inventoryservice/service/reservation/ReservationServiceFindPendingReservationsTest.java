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
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

public class ReservationServiceFindPendingReservationsTest {

        private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
        private final TimeProvider timeProvider = mock(TimeProvider.class);

        private final ReservedQuantityService reservedQuantityService = new ReservedQuantityService(
                        reservationRepository);
        private final ReservationOrchestrator reservationBuilder = new ReservationOrchestrator(reservationRepository,
                        timeProvider);
        private final ReservationStateManager reservationStateManager = new ReservationStateManager(
                        reservationRepository);

        private final ReservationService reservationService = new ReservationService(reservationRepository,
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
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());
                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenReturn(matchingReservations);
                final var expectedQuantities = List.of(
                                new ReservedItemQuantity("skuCode1", 5),
                                new ReservedItemQuantity("skuCode2", 10));

                // When
                List<ReservedItemQuantity> result = reservationService.findPendingReservedQuantities(skuCodeList);

                // Then
                assertEquals(expectedQuantities, result);
        }

        @Test
        @DisplayName("`findPendingReservedQuantities()` should return empty list when no pending reservations exist")
        public void findPendingReservedQuantities_NoReservations_ReturnsEmptyList() throws InternalServerException {
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
        @DisplayName("`findPendingReservedQuantities()` should throw InternalServerException when Repo throws DataAccessException")
        public void findPendingReservedQuantities_WhenRepoThrowsError() {
                // Given
                final var skuCodeList = List.of("skuCode1", "skuCode2");
                when(reservationRepository.findBySkuCodeInAndStatus(skuCodeList, ReservationState.PENDING))
                                .thenThrow(new DataAccessResourceFailureException("Database connection failed"));

                // Then
                assertThrows(InternalServerException.class,
                                () -> reservationService.findPendingReservedQuantities(skuCodeList));
        }
}
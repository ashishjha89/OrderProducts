package com.orderproduct.inventoryservice.service.reservation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

public class ReservationServiceUpdateReservationStateTest {

        private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
        private final TimeProvider timeProvider = mock(TimeProvider.class);

        private final ReservedQuantityService reservedQuantityService = new ReservedQuantityService(
                        reservationRepository);
        private final ReservationBuilder reservationBuilder = new ReservationBuilder(reservationRepository,
                        timeProvider);
        private final ReservationStateManager reservationStateManager = new ReservationStateManager(
                        reservationRepository);

        private final ReservationService reservationService = new ReservationService(
                        reservationRepository, reservedQuantityService, reservationBuilder, reservationStateManager);

        /**
         * Helper method to create a copy of a reservation with updated state.
         * This simulates the transformation logic in ReservationStateManager.
         */
        private Reservation copy(Reservation reservation, ReservationState newState) {
                return reservation.toBuilder()
                                .status(newState)
                                .build();
        }

        @Test
        @DisplayName("`updateReservationState()` should update reservation state successfully when reservations exist")
        public void updateReservationState_ExistingReservations_UpdatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1", "skuCode2");
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = existingReservations.stream()
                                .map(reservation -> copy(reservation, newState))
                                .toList();

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
        }

        @Test
        @DisplayName("`updateReservationState()` should return empty list when no reservations exist for the order and SKUs")
        public void updateReservationState_NoReservations_ReturnsEmptyList() throws InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1", "skuCode2");
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var expectedReservations = List.<Reservation>of();

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes)).thenReturn(List.of());
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("`updateReservationState()` should handle single SKU code update")
        public void updateReservationState_SingleSkuCode_UpdatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1");
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var existingReservation = Reservation.builder()
                                .id(1L)
                                .orderNumber(orderNumber)
                                .skuCode("skuCode1")
                                .reservedQuantity(5)
                                .reservedAt(currentTime.minusHours(1))
                                .status(ReservationState.PENDING)
                                .build();

                final var expectedReservations = List.of(copy(existingReservation, newState));

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenReturn(List.of(existingReservation));
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
        }

        @Test
        @DisplayName("`updateReservationState()` should handle multiple SKU codes with partial matches")
        public void updateReservationState_PartialMatches_UpdatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1", "skuCode2", "skuCode3");
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode3")
                                                .reservedQuantity(15)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = existingReservations.stream()
                                .map(reservation -> copy(reservation, newState))
                                .toList();

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when finding reservations throws DataAccessException")
        public void updateReservationState_FindingReservationsThrowsError() {
                // Given
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1", "skuCode2");
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when saving reservations throws DataAccessException")
        public void updateReservationState_SavingReservationsThrowsError() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1");
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var existingReservation = Reservation.builder()
                                .id(1L)
                                .orderNumber(orderNumber)
                                .skuCode("skuCode1")
                                .reservedQuantity(5)
                                .reservedAt(currentTime.minusHours(1))
                                .status(ReservationState.PENDING)
                                .build();

                final var expectedReservations = List.of(copy(existingReservation, newState));

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenReturn(List.of(existingReservation));
                when(reservationRepository.saveAll(expectedReservations))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`updateReservationState()` should handle all reservation states (PENDING, FULFILLED, CANCELLED, EXPIRED)")
        public void updateReservationState_AllStates_UpdatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var skuCodes = List.of("skuCode1", "skuCode2", "skuCode3", "skuCode4");
                final var newState = ReservationState.EXPIRED;
                final var request = new ReservationStateUpdateRequest(orderNumber, skuCodes, newState);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.FULFILLED)
                                                .build(),
                                Reservation.builder()
                                                .id(3L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode3")
                                                .reservedQuantity(15)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.CANCELLED)
                                                .build(),
                                Reservation.builder()
                                                .id(4L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode4")
                                                .reservedQuantity(20)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.EXPIRED)
                                                .build());

                final var expectedReservations = existingReservations.stream()
                                .map(reservation -> copy(reservation, newState))
                                .toList();

                when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes))
                                .thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
        }
}
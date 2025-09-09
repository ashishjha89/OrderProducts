package com.orderproduct.inventoryservice.service.reservation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import com.orderproduct.inventoryservice.common.exception.DuplicateReservationException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.InventoryExceptionHandler;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;
import com.orderproduct.inventoryservice.service.inventory.InventoryDeductionService;

import jakarta.persistence.PersistenceException;

public class ReservationServiceUpdateReservationStateTest {

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

        private final ReservationService reservationService = new ReservationService(
                        reservationRepositoryWrapper, reservedQuantityService, reservationBuilder,
                        reservationStateManager);

        private Reservation copy(Reservation reservation, ReservationState newState) {
                return reservation.toBuilder()
                                .status(newState)
                                .build();
        }

        @Test
        @DisplayName("`updateReservationState()` should deduct inventory for FULFILLED reservations")
        public void updateReservationState_FulfilledState_DeductsInventory() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

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

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
                verify(inventoryDeductionService).deductInventoryForFulfilledOrder(expectedReservations);
        }

        @Test
        @DisplayName("`updateReservationState()` should not deduct inventory for non-FULFILLED reservations")
        public void updateReservationState_NonFulfilledState_DoesNotDeductInventory() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = existingReservations.stream()
                                .map(reservation -> copy(reservation, newState))
                                .toList();

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertEquals(expectedReservations, result);
                verifyNoInteractions(inventoryDeductionService);
        }

        @Test
        @DisplayName("`updateReservationState()` should handle empty reservation list")
        public void updateReservationState_NoReservations_ReturnsEmptyList() throws InternalServerException {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                final var expectedReservations = List.<Reservation>of();

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(List.of());
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.updateReservationState(request);

                // Then
                assertTrue(result.isEmpty());
                verify(inventoryDeductionService).deductInventoryForFulfilledOrder(expectedReservations);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when DataAccessException occurs")
        public void updateReservationState_DataAccessException_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // When & Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
                verifyNoInteractions(inventoryDeductionService);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when PersistenceException occurs")
        public void updateReservationState_PersistenceException_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenThrow(new PersistenceException("Database constraint violation"));

                // When & Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
                verifyNoInteractions(inventoryDeductionService);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw InternalServerException when Exception occurs")
        public void updateReservationState_Exception_ThrowsInternalServerException() {
                // Given
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.CANCELLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenThrow(new RuntimeException("Unexpected error"));

                // When & Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
                verifyNoInteractions(inventoryDeductionService);
        }

        @Test
        @DisplayName("`updateReservationState()` should propagate InternalServerException from inventory deduction")
        public void updateReservationState_InventoryDeductionFails_PropagatesException()
                        throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var newState = ReservationState.FULFILLED;
                final var request = new ReservationStateUpdateRequest(orderNumber, newState);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = existingReservations.stream()
                                .map(reservation -> copy(reservation, newState))
                                .toList();

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(reservationRepository.saveAll(expectedReservations)).thenReturn(expectedReservations);
                doThrow(new InternalServerException())
                                .when(inventoryDeductionService).deductInventoryForFulfilledOrder(expectedReservations);

                // When & Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(InternalServerException.class);
                verify(inventoryDeductionService).deductInventoryForFulfilledOrder(expectedReservations);
        }

        @Test
        @DisplayName("`updateReservationState()` should throw DuplicateReservationException when repository throws ConstraintViolationException")
        void updateReservationState_ConstraintViolation_ThrowsDuplicateReservationException() {
                // Given
                String orderNumber = "ORDER-123";
                ReservationStateUpdateRequest request = new ReservationStateUpdateRequest(orderNumber,
                                ReservationState.FULFILLED);

                List<Reservation> existingReservations = List.of(
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(LocalDateTime.now())
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(reservationRepository.saveAll(anyList()))
                                .thenThrow(new DataIntegrityViolationException("Constraint violation",
                                                new ConstraintViolationException("Duplicate reservation", null,
                                                                "inventory_reservation")));

                // When & Then
                assertThatThrownBy(() -> reservationService.updateReservationState(request))
                                .isInstanceOf(DuplicateReservationException.class);
        }
}
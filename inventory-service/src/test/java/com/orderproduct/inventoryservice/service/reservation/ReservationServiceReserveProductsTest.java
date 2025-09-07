package com.orderproduct.inventoryservice.service.reservation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.OrderReservationNotAllowedException;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

import jakarta.persistence.PersistenceException;

public class ReservationServiceReserveProductsTest {

        private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
        private final TimeProvider timeProvider = mock(TimeProvider.class);

        private final ReservedQuantityService reservedQuantityService = new ReservedQuantityService(
                        reservationRepository);
        private final ReservationOrchestrator reservationBuilder = new ReservationOrchestrator(reservationRepository,
                        timeProvider);
        private final ReservationStateManager reservationStateManager = new ReservationStateManager(
                        reservationRepository);

        private final ReservationService reservationService = new ReservationService(
                        reservationRepository, reservedQuantityService, reservationBuilder, reservationStateManager);

        @Test
        @DisplayName("`reserveProducts()` should create new reservations when no existing reservations exist")
        public void reserveProducts_NewReservations_CreatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5),
                                new ItemReservationRequest("skuCode2", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var expectedReservations = List.of(
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(List.of());
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(anyList())).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.reserveProducts(request);

                // Then
                assertEquals(expectedReservations, result);
        }

        @Test
        @DisplayName("`reserveProducts()` should update existing reservations when they already exist")
        public void reserveProducts_ExistingReservations_UpdatesSuccessfully() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 15),
                                new ItemReservationRequest("skuCode2", 20));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedUpdatedReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(15)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(20)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(anyList())).thenReturn(expectedUpdatedReservations);

                // When
                List<Reservation> result = reservationService.reserveProducts(request);

                // Then
                assertEquals(expectedUpdatedReservations, result);
        }

        @Test
        @DisplayName("`reserveProducts()` should handle comprehensive mixed scenario with additions, updates, and deletions")
        public void reserveProducts_ComprehensiveMixedScenario_HandlesCorrectly() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 15), // existing - should update
                                new ItemReservationRequest("skuCode3", 20)); // new - should create
                // skuCode2 should be deleted (was in existing but not in request)
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

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
                                                .reservedQuantity(3)
                                                .reservedAt(currentTime.minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(15)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode3")
                                                .reservedQuantity(20)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(anyList())).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.reserveProducts(request);

                // Then
                assertEquals(expectedReservations, result);
                verify(reservationRepository).deleteByOrderNumberAndSkuCodeIn(eq(orderNumber), eq(List.of("skuCode2")));
        }

        @Test
        @DisplayName("`reserveProducts()` should allow modification when order has only PENDING reservations")
        public void reserveProducts_OrderWithOnlyPendingReservations_AllowsModification()
                        throws InternalServerException {
                // Given
                final var orderNumber = "ORDER-004";
                final var currentTime = LocalDateTime.now();
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(LocalDateTime.now().minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                final var expectedReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(10)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(anyList())).thenReturn(expectedReservations);

                // When
                List<Reservation> result = reservationService.reserveProducts(request);

                // Then
                assertEquals(expectedReservations, result);
                verify(reservationRepository).saveAll(anyList());
        }

        @ParameterizedTest
        @MethodSource("nonPendingStatesProvider")
        @DisplayName("`reserveProducts()` should throw OrderReservationNotAllowedException for non-PENDING order states")
        public void reserveProducts_NonPendingOrderStates_ThrowsOrderReservationNotAllowedException(
                        ReservationState state, String orderNumber) {
                // Given
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(3)
                                                .reservedAt(LocalDateTime.now().minusHours(1))
                                                .status(state)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(OrderReservationNotAllowedException.class);
        }

        private static Stream<Arguments> nonPendingStatesProvider() {
                return Stream.of(
                                Arguments.of(ReservationState.FULFILLED, "ORDER-001"),
                                Arguments.of(ReservationState.CANCELLED, "ORDER-002"),
                                Arguments.of(ReservationState.EXPIRED, "ORDER-003"));
        }

        @Test
        @DisplayName("`reserveProducts()` should throw OrderReservationNotAllowedException when order has mixed states")
        public void reserveProducts_OrderWithMixedStates_ThrowsOrderReservationNotAllowedException() {
                // Given
                final var orderNumber = "ORDER-005";
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(LocalDateTime.now().minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build(),
                                Reservation.builder()
                                                .id(2L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(3)
                                                .reservedAt(LocalDateTime.now().minusHours(2))
                                                .status(ReservationState.CANCELLED)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(OrderReservationNotAllowedException.class);
        }

        @Test
        @DisplayName("`reserveProducts()` should throw InternalServerException when finding existing reservations throws DataAccessException")
        public void reserveProducts_FindingExistingReservationsThrowsError() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`reserveProducts()` should throw InternalServerException when saving reservations throws DataAccessException")
        public void reserveProducts_SavingReservationsThrowsError() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);
                final var reservationsToSave = List.of(
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(5)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenReturn(List.of()); // there is no existing reservation
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(reservationsToSave))
                                .thenThrow(new DataAccessException("Database connection failed") {
                                });

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`reserveProducts()` should throw InternalServerException when saving reservations throws PersistenceException")
        public void reserveProducts_SavingReservationsThrowsPersistenceException() throws InternalServerException {
                // Given
                final var currentTime = LocalDateTime.now();
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", -5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);
                final var reservationsToSave = List.of(
                                Reservation.builder()
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode1")
                                                .reservedQuantity(-5)
                                                .reservedAt(currentTime)
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber))
                                .thenReturn(List.of());
                when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
                when(reservationRepository.saveAll(reservationsToSave))
                                .thenThrow(new PersistenceException(
                                                "Constraint violation: reserved_quantity cannot be negative"));

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(InternalServerException.class);
        }

        @Test
        @DisplayName("`reserveProducts()` should throw InternalServerException when deleting reservations throws DataAccessException")
        public void reserveProducts_DeletingReservationsThrowsError() {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var existingReservations = List.of(
                                Reservation.builder()
                                                .id(1L)
                                                .orderNumber(orderNumber)
                                                .skuCode("skuCode2")
                                                .reservedQuantity(3)
                                                .reservedAt(LocalDateTime.now().minusHours(1))
                                                .status(ReservationState.PENDING)
                                                .build());

                when(reservationRepository.findByOrderNumber(orderNumber)).thenReturn(existingReservations);
                doThrow(new DataAccessException("Database connection failed") {
                }).when(reservationRepository).deleteByOrderNumberAndSkuCodeIn(anyString(), anyList());

                // Then
                assertThatThrownBy(() -> reservationService.reserveProducts(request))
                                .isInstanceOf(InternalServerException.class);
        }
}
package com.orderproduct.inventoryservice.service.reservation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
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
    private final ReservationBuilder reservationBuilder = new ReservationBuilder(reservationRepository,
            timeProvider);

    private final ReservationService reservationService = new ReservationService(reservationRepository,
            reservedQuantityService, reservationBuilder);

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

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of("skuCode1", "skuCode2")))
                .thenReturn(List.of());
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

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of("skuCode1", "skuCode2")))
                .thenReturn(existingReservations);
        when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
        when(reservationRepository.saveAll(anyList())).thenReturn(expectedUpdatedReservations);

        // When
        List<Reservation> result = reservationService.reserveProducts(request);

        // Then
        assertEquals(expectedUpdatedReservations, result);
    }

    @Test
    @DisplayName("`reserveProducts()` should throw InternalServerException when finding existing reservations throws DataAccessException")
    public void reserveProducts_FindingExistingReservationsThrowsError() {
        // Given
        final var orderNumber = "ORDER-001";
        final var itemRequests = List.of(new ItemReservationRequest("skuCode1", 5));
        final var request = new OrderReservationRequest(orderNumber, itemRequests);

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of("skuCode1")))
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

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of("skuCode1")))
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

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of("skuCode1")))
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
    @DisplayName("`reserveProducts()` should handle mixed scenario with existing and new reservations")
    public void reserveProducts_MixedScenario_HandlesCorrectly() throws InternalServerException {
        // Given
        final var currentTime = LocalDateTime.now();
        final var orderNumber = "ORDER-001";
        final var itemRequests = List.of(
                new ItemReservationRequest("skuCode1", 15), // existing - should update
                new ItemReservationRequest("skuCode2", 10), // new - should create
                new ItemReservationRequest("skuCode3", 20)); // new - should create
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
                        .skuCode("skuCode2")
                        .reservedQuantity(10)
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

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber,
                List.of("skuCode1", "skuCode2", "skuCode3")))
                .thenReturn(existingReservations);
        when(timeProvider.getCurrentTimestamp()).thenReturn(currentTime);
        when(reservationRepository.saveAll(anyList())).thenReturn(expectedReservations);

        // When
        List<Reservation> result = reservationService.reserveProducts(request);

        // Then
        assertEquals(expectedReservations, result);
    }

    @Test
    @DisplayName("`reserveProducts()` should handle empty item requests list")
    public void reserveProducts_EmptyItemRequests_ReturnsEmptyList() throws InternalServerException {
        // Given
        final var orderNumber = "ORDER-001";
        final var request = new OrderReservationRequest(orderNumber, List.of());

        when(reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, List.of()))
                .thenReturn(List.of());
        when(reservationRepository.saveAll(anyList())).thenReturn(List.of());

        // When
        List<Reservation> result = reservationService.reserveProducts(request);

        // Then
        assertTrue(result.isEmpty());
    }
}
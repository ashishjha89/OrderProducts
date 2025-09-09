package com.orderproduct.inventoryservice.service.reservation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;
import com.orderproduct.inventoryservice.service.inventory.InventoryDeductionService;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal service for managing reservation state updates.
 * Should only be used by other services, not controllers.
 */
@Service
@Slf4j
@AllArgsConstructor
public class ReservationStateManager {
    private final ReservationRepositoryWrapper reservationRepository;
    private final InventoryDeductionService inventoryDeductionService;

    @NonNull
    public List<Reservation> updateReservationState(@NonNull ReservationStateUpdateRequest request)
            throws InternalServerException {
        List<Reservation> reservations = findReservations(request);
        List<Reservation> updatedReservations = updateReservationsState(reservations, request.state());

        if (request.state() == ReservationState.FULFILLED) {
            inventoryDeductionService.deductInventoryForFulfilledOrder(updatedReservations);
        }

        return updatedReservations;
    }

    @NonNull
    private List<Reservation> findReservations(@NonNull ReservationStateUpdateRequest request)
            throws InternalServerException {
        List<Reservation> result = reservationRepository.findByOrderNumber(request.orderNumber());
        log.debug("Found {} reservations for order: {}", result.size(), request.orderNumber());
        return result;
    }

    @NonNull
    private List<Reservation> updateReservationsState(
            @NonNull List<Reservation> reservations,
            @NonNull ReservationState newState) {
        log.debug("Transforming {} reservations to state: {}", reservations.size(), newState);
        List<Reservation> updatedReservations = reservations.stream()
                .map(reservation -> reservation.toBuilder()
                        .status(newState)
                        .build())
                .toList();
        log.debug("Successfully transformed {} reservations to state: {}", updatedReservations.size(), newState);
        return updatedReservations;
    }
}
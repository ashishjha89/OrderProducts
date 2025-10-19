package com.orderproduct.inventoryservice.service.reservation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.DuplicateReservationException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.OrderReservationNotAllowedException;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal service for reservation operations.
 * Should only be used by other services, not controllers.
 */
@Service
@Slf4j
@AllArgsConstructor
public class ReservationService {

    private final ReservationRepositoryWrapper reservationRepository;
    private final ReservedQuantityService reservedQuantityService;
    private final ReservationOrchestrator reservationOrchestrator;
    private final ReservationStateManager reservationStateManager;

    @NonNull
    public List<ReservedItemQuantity> findPendingReservedQuantities(@NonNull List<String> skuCodes)
            throws InternalServerException {
        log.debug("Finding pending reserved quantities for {} SKU codes", skuCodes.size());

        List<ReservedItemQuantity> result = reservedQuantityService.findPendingReservedQuantities(skuCodes);

        log.debug("Found {} pending reservations for {} SKU codes", result.size(), skuCodes.size());
        return result;
    }

    @NonNull
    public List<Reservation> reserveProducts(@NonNull OrderReservationRequest request)
            throws InternalServerException, DuplicateReservationException, OrderReservationNotAllowedException {
        log.debug("Reserving products for order: {} with {} items",
                request.orderNumber(), request.itemReservationRequests().size());

        List<Reservation> reservationsToSave = reservationOrchestrator.buildReservationsToSave(request);
        List<Reservation> result = saveItems(reservationsToSave);

        log.debug("Successfully reserved {} items for order: {}", result.size(), request.orderNumber());
        return result;
    }

    @NonNull
    public List<Reservation> updateReservationState(@NonNull ReservationStateUpdateRequest request)
            throws InternalServerException, DuplicateReservationException {
        log.debug("Updating reservation state to {} for order: {}", request.state(), request.orderNumber());

        List<Reservation> updatedReservations = reservationStateManager.updateReservationState(request);
        List<Reservation> result = saveItems(updatedReservations);

        log.debug("Successfully updated reservations to state: {} for order: {}",
                request.state(), request.orderNumber());
        return result;
    }

    @NonNull
    private List<Reservation> saveItems(List<Reservation> reservationsToSave)
            throws InternalServerException, DuplicateReservationException {
        log.debug("Saving {} reservations", reservationsToSave.size());
        List<Reservation> result = reservationRepository.saveAll(reservationsToSave);
        log.debug("Successfully saved {} reservations", result.size());
        return result;
    }

}
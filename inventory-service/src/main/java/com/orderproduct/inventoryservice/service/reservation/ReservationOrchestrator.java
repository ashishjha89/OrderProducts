package com.orderproduct.inventoryservice.service.reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.OrderReservationNotAllowedException;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.domain.PendingReservationItem;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReservationOrchestrator {

    private final ReservationRepositoryWrapper reservationRepository;
    private final TimeProvider timeProvider;

    public ReservationOrchestrator(ReservationRepositoryWrapper reservationRepository,
            TimeProvider timeProvider) {
        this.reservationRepository = reservationRepository;
        this.timeProvider = timeProvider;
    }

    /**
     * Builds reservations to save based on the order reservation request.
     * Handles idempotency by updating existing (pendingreservations.
     * It's not allowed to modify other states of reservations.
     * Also deletes reservations for SKU codes that are no longer in the request.
     */
    @NonNull
    List<Reservation> buildReservationsToSave(@NonNull OrderReservationRequest request)
            throws InternalServerException, OrderReservationNotAllowedException {
        String orderNumber = request.orderNumber();
        List<Reservation> allExistingReservations = getAllExistingReservationsForOrder(orderNumber);

        if (!orderReservationAllowed(orderNumber, allExistingReservations))
            throw new OrderReservationNotAllowedException(orderNumber);

        Set<PendingReservationItem> existingPendingReservations = convertToPendingReservationItems(
                allExistingReservations);

        // Delete reservations for SKU codes that are no longer in the request
        deleteRemovedSkuReservations(orderNumber, request, existingPendingReservations);

        return request.itemReservationRequests().stream()
                .map(reservationRequest -> {
                    String skuCode = reservationRequest.skuCode();
                    int quantity = reservationRequest.quantity();
                    Reservation existingReservation = findExistingReservationForSku(skuCode,
                            existingPendingReservations);
                    return createOrUpdateReservation(orderNumber, skuCode, quantity, existingReservation);
                })
                .toList();
    }

    private void deleteRemovedSkuReservations(@NonNull String orderNumber, @NonNull OrderReservationRequest request,
            @NonNull Set<PendingReservationItem> existingReservations) throws InternalServerException {
        Set<String> requestedSkuCodes = extractSkuCodesFromRequest(request);
        Set<String> existingSkuCodes = extractSkuCodesFromAllReservations(existingReservations);

        Set<String> skuCodesToDelete = existingSkuCodes.stream()
                .filter(skuCode -> !requestedSkuCodes.contains(skuCode))
                .collect(Collectors.toSet());

        if (!skuCodesToDelete.isEmpty()) {
            deleteReservationsForSkuCodes(orderNumber, skuCodesToDelete);
        }
    }

    private boolean orderReservationAllowed(String orderNumber, List<Reservation> existingReservations) {
        if (existingReservations.isEmpty()) {
            return true; // No existing reservations, allow creation
        }
        return existingReservations.stream()
                .allMatch(reservation -> reservation.getStatus() == ReservationState.PENDING);
    }

    @NonNull
    private Set<PendingReservationItem> convertToPendingReservationItems(List<Reservation> allReservations) {
        return allReservations.stream()
                .map(reservation -> new PendingReservationItem(reservation.getSkuCode(), reservation))
                .collect(Collectors.toSet());
    }

    @NonNull
    private List<Reservation> getAllExistingReservationsForOrder(@NonNull String orderNumber)
            throws InternalServerException {
        return reservationRepository.findByOrderNumber(orderNumber);
    }

    @NonNull
    private Set<String> extractSkuCodesFromRequest(@NonNull OrderReservationRequest request) {
        return request.itemReservationRequests().stream()
                .map(ItemReservationRequest::skuCode)
                .collect(Collectors.toSet());
    }

    @NonNull
    private Set<String> extractSkuCodesFromAllReservations(@NonNull Set<PendingReservationItem> reservations) {
        return reservations.stream()
                .map(PendingReservationItem::skuCode)
                .collect(Collectors.toSet());
    }

    private void deleteReservationsForSkuCodes(@NonNull String orderNumber, @NonNull Set<String> skuCodesToDelete)
            throws InternalServerException {
        reservationRepository.deleteByOrderNumberAndSkuCodeIn(orderNumber, List.copyOf(skuCodesToDelete));
    }

    private Reservation findExistingReservationForSku(@NonNull String skuCode,
            @NonNull Set<PendingReservationItem> existingReservations) {
        return existingReservations.stream()
                .filter(item -> item.skuCode().equals(skuCode))
                .map(PendingReservationItem::reservation)
                .findFirst()
                .orElse(null);
    }

    @NonNull
    private Reservation createOrUpdateReservation(@NonNull String orderNumber, @NonNull String skuCode,
            int quantity, Reservation existingReservation) {
        LocalDateTime currentTime = timeProvider.getCurrentTimestamp();

        if (existingReservation != null) {
            return existingReservation.toBuilder()
                    .reservedQuantity(quantity)
                    .reservedAt(currentTime)
                    .build();
        } else {
            return Reservation.builder()
                    .orderNumber(orderNumber)
                    .skuCode(skuCode)
                    .reservedQuantity(quantity)
                    .reservedAt(currentTime)
                    .status(ReservationState.PENDING)
                    .build();
        }
    }
}
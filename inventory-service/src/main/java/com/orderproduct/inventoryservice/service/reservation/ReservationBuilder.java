package com.orderproduct.inventoryservice.service.reservation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.util.TimeProvider;
import com.orderproduct.inventoryservice.domain.PendingReservationItem;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
class ReservationBuilder {
    private final ReservationRepository reservationRepository;
    private final TimeProvider timeProvider;

    @NonNull
    List<Reservation> buildReservationsToSave(@NonNull OrderReservationRequest request)
            throws InternalServerException {
        String orderNumber = request.orderNumber();
        List<String> skuCodes = extractSkuCodes(request);
        Set<PendingReservationItem> pendingReservationItems = fetchPendingReservationItems(orderNumber, skuCodes);

        return request.itemReservationRequests().stream()
                .map(reservationRequest -> {
                    String skuCode = reservationRequest.skuCode();
                    int quantity = reservationRequest.quantity();
                    Reservation pendingReservation = getPendingReservation(skuCode, pendingReservationItems);
                    return createOrUpdateReservation(orderNumber, skuCode, quantity, pendingReservation);
                })
                .toList();
    }

    @NonNull
    private Set<PendingReservationItem> fetchPendingReservationItems(String orderNumber, List<String> skuCodes)
            throws InternalServerException {
        return getPendingReservationsForOrder(orderNumber, skuCodes).stream()
                .map(reservation -> new PendingReservationItem(reservation.getSkuCode(), reservation))
                .collect(Collectors.toSet());
    }

    @NonNull
    private List<Reservation> getPendingReservationsForOrder(String orderNumber, List<String> skuCodes)
            throws InternalServerException {
        try {
            log.debug("Finding existing reservations for order: {} with {} SKUs", orderNumber, skuCodes.size());
            List<Reservation> result = reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes);
            log.debug("Found {} existing reservations for order: {}", result.size(), orderNumber);
            return result;
        } catch (DataAccessException e) {
            log.error(
                    "DataAccessException when finding existing reservations for orderNumber:{} and skuCodes:{} and errorMsg:{}",
                    orderNumber, skuCodes, e.getMessage());
            throw new InternalServerException();
        }
    }

    @NonNull
    private List<String> extractSkuCodes(@NonNull OrderReservationRequest request) {
        return request.itemReservationRequests().stream()
                .map(requestedItem -> requestedItem.skuCode())
                .toList();
    }

    @NonNull
    private Reservation createOrUpdateReservation(
            String orderNumber,
            String skuCode,
            int quantityToReserve,
            @Nullable Reservation reservation) {
        if (reservation == null) {
            log.debug("Creating new reservation for SKU: {} in order: {}", skuCode, orderNumber);
            return Reservation.builder()
                    .orderNumber(orderNumber)
                    .skuCode(skuCode)
                    .reservedQuantity(quantityToReserve)
                    .reservedAt(timeProvider.getCurrentTimestamp())
                    .status(ReservationState.PENDING)
                    .build();
        } else {
            log.debug("Updating existing reservation for SKU: {} in order: {}", skuCode, orderNumber);
            return reservation.toBuilder()
                    .reservedQuantity(quantityToReserve)
                    .reservedAt(timeProvider.getCurrentTimestamp())
                    .status(ReservationState.PENDING)
                    .build();
        }
    }

    @Nullable
    private Reservation getPendingReservation(String skuCode, Set<PendingReservationItem> pendingReservationItems) {
        return pendingReservationItems.stream()
                .filter(item -> item.skuCode().equals(skuCode))
                .map(pendingReservationItem -> pendingReservationItem.reservation())
                .findFirst()
                .orElse(null);
    }
}
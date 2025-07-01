package com.orderproduct.inventoryservice.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.common.TimeProvider;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepository;

import jakarta.persistence.PersistenceException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
class ReservationService {
    private final ReservationRepository reservationRepository;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    @NonNull
    List<ReservedItemQuantity> findPendingReservedQuantities(@NonNull List<String> skuCodes)
            throws InternalServerException {
        log.debug("Finding pending reserved quantities for {} SKU codes", skuCodes.size());

        List<ReservedItemQuantity> result = reservedItems(skuCodes).stream()
                .map(ReservedItemQuantity::fromReservation)
                .toList();

        log.debug("Found {} pending reservations for {} SKU codes", result.size(), skuCodes.size());
        return result;
    }

    @Transactional
    @NonNull
    List<Reservation> reserveProducts(@NonNull OrderReservationRequest request) throws InternalServerException {
        log.debug("Reserving products for order: {} with {} items",
                request.orderNumber(), request.itemReservationRequests().size());

        List<Reservation> result = saveItems(getReservationsToSave(request));

        log.debug("Successfully reserved {} items for order: {}", result.size(), request.orderNumber());
        return result;
    }

    private List<Reservation> reservedItems(List<String> skuCodes) throws InternalServerException {
        try {
            log.debug("Querying reservations for {} SKU codes", skuCodes.size());
            List<Reservation> result = reservationRepository.findBySkuCodeInAndStatus(skuCodes,
                    ReservationState.PENDING);
            log.debug("Found {} pending reservations", result.size());
            return result;
        } catch (DataAccessException e) {
            log.error("DataAccessException when finding reservations for skuCodes:{} and errorMsg:{}", skuCodes,
                    e.getMessage());
            throw new InternalServerException();
        }
    }

    private List<Reservation> saveItems(List<Reservation> reservationsToSave) {
        try {
            log.debug("Saving {} reservations", reservationsToSave.size());
            List<Reservation> result = reservationRepository.saveAll(reservationsToSave);
            log.debug("Successfully saved {} reservations", result.size());
            return result;
        } catch (DataAccessException e) {
            log.error("DataAccessException when saving reservations:{} and errorMsg:{}", reservationsToSave,
                    e.getMessage());
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error("PersistenceException when saving reservations:{} and errorMsg:{}", reservationsToSave,
                    e.getMessage());
            throw new InternalServerException();
        }
    }

    private List<Reservation> getReservationsToSave(@NonNull OrderReservationRequest request)
            throws InternalServerException {
        return calculateReservationsToSave(
                request,
                skuToExistingReservationMap(request.orderNumber(), extractSkuCodes(request)));
    }

    private List<Reservation> calculateReservationsToSave(
            @NonNull OrderReservationRequest request,
            @NonNull Map<String, Reservation> skuToExistingReservationMap) {
        log.debug("Calculating reservations to save for order: {}", request.orderNumber());
        List<Reservation> result = request.itemReservationRequests().stream()
                .map(reservationRequest -> createOrUpdateReservation(request.orderNumber(), reservationRequest,
                        skuToExistingReservationMap))
                .toList();
        log.debug("Calculated {} reservations to save", result.size());
        return result;
    }

    private Map<String, Reservation> skuToExistingReservationMap(String orderNumber, List<String> skuCodes)
            throws InternalServerException {
        log.debug("Creating SKU to existing reservation map for order: {} with {} SKUs", orderNumber, skuCodes.size());
        Map<String, Reservation> result = orderPendingReservations(orderNumber, skuCodes).stream()
                .collect(Collectors.toMap(Reservation::getSkuCode, reservation -> reservation));
        log.debug("Created SKU to reservation map with {} entries", result.size());
        return result;
    }

    private List<Reservation> orderPendingReservations(String orderNumber, List<String> skuCodes)
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

    private List<String> extractSkuCodes(@NonNull OrderReservationRequest request) {
        return request.itemReservationRequests().stream()
                .map(stockStatus -> stockStatus.skuCode())
                .toList();
    }

    private Reservation createOrUpdateReservation(
            String orderNumber,
            @NonNull ItemReservationRequest itemReservationRequest,
            @NonNull Map<String, Reservation> existingReservationsBySku) {
        Reservation reservation = existingReservationsBySku.getOrDefault(itemReservationRequest.skuCode(), null);
        if (reservation == null) {
            log.debug("Creating new reservation for SKU: {} in order: {}",
                    itemReservationRequest.skuCode(), orderNumber);
            reservation = Reservation.builder()
                    .orderNumber(orderNumber)
                    .skuCode(itemReservationRequest.skuCode())
                    .build();
        } else {
            log.debug("Updating existing reservation for SKU: {} in order: {}",
                    itemReservationRequest.skuCode(), orderNumber);
        }
        reservation.setReservedQuantity(itemReservationRequest.quantity());
        reservation.setReservedAt(timeProvider.getCurrentTimestamp());
        reservation.setStatus(ReservationState.PENDING);
        return reservation;
    }

}
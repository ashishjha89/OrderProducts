package com.orderproduct.inventoryservice.service.reservation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.repository.ReservationRepositoryWrapper;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
class ReservedQuantityService {

    private final ReservationRepositoryWrapper reservationRepository;

    @NonNull
    List<ReservedItemQuantity> findPendingReservedQuantities(@NonNull List<String> skuCodes)
            throws InternalServerException {
        List<Reservation> reservedItems = getReservedItems(skuCodes);
        return reservedItems.stream()
                .map(ReservedItemQuantity::fromReservation)
                .toList();
    }

    @NonNull
    private List<Reservation> getReservedItems(List<String> skuCodes) throws InternalServerException {
        log.debug("Querying reservations for {} SKU codes", skuCodes.size());
        List<Reservation> result = reservationRepository.findBySkuCodeInAndStatus(skuCodes,
                ReservationState.PENDING);
        log.debug("Found {} pending reservations", result.size());
        return result;
    }
}
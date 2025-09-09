package com.orderproduct.inventoryservice.service.inventory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.entity.Reservation;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling inventory deduction operations when orders are
 * fulfilled.
 * This service orchestrates the deduction of inventory quantities for multiple
 * reservations.
 */
@Service
@Slf4j
@AllArgsConstructor
public class InventoryDeductionService {

    private final ItemOnHandService itemOnHandService;

    /**
     * Deducts inventory quantities for a list of fulfilled reservations.
     * This method is called when an order is fulfilled and the reserved quantities
     * need to be deducted from the on-hand inventory.
     * 
     * @param fulfilledReservations List of reservations that have been fulfilled
     * @throws InternalServerException if inventory deduction fails
     */
    @Transactional
    public void deductInventoryForFulfilledOrder(@NonNull List<Reservation> fulfilledReservations)
            throws InternalServerException {
        log.debug("Processing inventory deduction for {} reservations", fulfilledReservations.size());
        if (fulfilledReservations.isEmpty()) {
            return;
        }
        String orderNumber = fulfilledReservations.get(0).getOrderNumber();
        for (Reservation reservation : fulfilledReservations) {
            try {
                itemOnHandService.deductInventoryQuantity(
                        reservation.getSkuCode(),
                        reservation.getReservedQuantity());
            } catch (NotFoundException e) {
                log.warn("SKU {} not found for order {} - skipping deduction",
                        reservation.getSkuCode(), orderNumber);
            } catch (InternalServerException e) {
                log.error("Failed to deduct inventory for SKU {} in order {} - transaction will rollback",
                        reservation.getSkuCode(), orderNumber);
                throw e;
            }
        }
    }
}

package com.orderproduct.inventoryservice.repository;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.orderproduct.inventoryservice.common.exception.DuplicateReservationException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.InventoryExceptionHandler;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReservationRepositoryWrapper {

    private final ReservationRepository reservationRepository;
    private final InventoryExceptionHandler exceptionHandler;

    public ReservationRepositoryWrapper(ReservationRepository reservationRepository,
            InventoryExceptionHandler exceptionHandler) {
        this.reservationRepository = reservationRepository;
        this.exceptionHandler = exceptionHandler;
    }

    public List<Reservation> findBySkuCodeInAndStatus(List<String> skuCodes, ReservationState status)
            throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> reservationRepository.findBySkuCodeInAndStatus(skuCodes, status),
                "finding reservations by SKU codes and status",
                "skuCodes", skuCodes, "status", status);
    }

    public List<Reservation> findByOrderNumber(String orderNumber) throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> reservationRepository.findByOrderNumber(orderNumber),
                "finding reservations by order number",
                "orderNumber", orderNumber);
    }

    public List<Reservation> findByOrderNumberAndSkuCodeIn(String orderNumber, List<String> skuCodes)
            throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> reservationRepository.findByOrderNumberAndSkuCodeIn(orderNumber, skuCodes),
                "finding reservations by order number and SKU codes",
                "orderNumber", orderNumber, "skuCodes", skuCodes);
    }

    public void deleteByOrderNumberAndSkuCodeIn(String orderNumber, List<String> skuCodes)
            throws InternalServerException {
        exceptionHandler.executeInventoryOperation(
                () -> {
                    reservationRepository.deleteByOrderNumberAndSkuCodeIn(orderNumber, skuCodes);
                    return null;
                },
                "deleting reservations by order number and SKU codes",
                "orderNumber", orderNumber, "skuCodes", skuCodes);
    }

    public List<Reservation> saveAll(List<Reservation> reservations)
            throws InternalServerException, DuplicateReservationException {
        try {
            return reservationRepository.saveAll(reservations);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Duplicate reservation attempted for order and SKU combination");
                throw new DuplicateReservationException();
            }
            log.error("DataAccessException when saving reservations - Context: reservationCount, {} - Error: {}",
                    reservations.size(), e.getMessage(), e);
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error("PersistenceException when saving reservations - Context: reservationCount, {} - Error: {}",
                    reservations.size(), e.getMessage(), e);
            throw new InternalServerException();
        } catch (Exception e) {
            log.error("Exception when saving reservations - Context: reservationCount, {} - Error: {}",
                    reservations.size(), e.getMessage(), e);
            throw new InternalServerException();
        }
    }
}

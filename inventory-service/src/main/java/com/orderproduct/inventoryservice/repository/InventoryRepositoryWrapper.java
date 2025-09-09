package com.orderproduct.inventoryservice.repository;

import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.orderproduct.inventoryservice.common.exception.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.exception.InsufficientQuantityException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.InventoryExceptionHandler;
import com.orderproduct.inventoryservice.common.exception.NegativeQuantityException;
import com.orderproduct.inventoryservice.entity.Inventory;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InventoryRepositoryWrapper {

    private final InventoryRepository inventoryRepository;
    private final InventoryExceptionHandler exceptionHandler;

    public InventoryRepositoryWrapper(InventoryRepository inventoryRepository,
            InventoryExceptionHandler exceptionHandler) {
        this.inventoryRepository = inventoryRepository;
        this.exceptionHandler = exceptionHandler;
    }

    public Optional<Inventory> findBySkuCode(String skuCode) throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> inventoryRepository.findBySkuCode(skuCode),
                "finding inventory by SKU code",
                "skuCode", skuCode);
    }

    public List<Inventory> findBySkuCodeIn(List<String> skuCodes) throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> inventoryRepository.findBySkuCodeIn(skuCodes),
                "finding inventory by SKU codes",
                "skuCodes", skuCodes);
    }

    public int updateQuantityBySkuCode(String skuCode, int quantity)
            throws InternalServerException, NegativeQuantityException {
        try {
            return inventoryRepository.updateQuantityBySkuCode(skuCode, quantity);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Negative quantity attempted for SKU: {} with quantity: {}", skuCode, quantity);
                throw new NegativeQuantityException();
            }
            log.error(
                    "DataAccessException when updating inventory quantity - Context: skuCode, {}, quantity, {} - Error: {}",
                    skuCode, quantity, e.getMessage(), e);
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error(
                    "PersistenceException when updating inventory quantity - Context: skuCode, {}, quantity, {} - Error: {}",
                    skuCode, quantity, e.getMessage(), e);
            throw new InternalServerException();
        } catch (Exception e) {
            log.error("Exception when updating inventory quantity - Context: skuCode, {}, quantity, {} - Error: {}",
                    skuCode, quantity, e.getMessage(), e);
            throw new InternalServerException();
        }
    }

    public int deductQuantityBySkuCode(String skuCode, int deductionQuantity)
            throws InternalServerException, InsufficientQuantityException {
        try {
            return inventoryRepository.deductQuantityBySkuCode(skuCode, deductionQuantity);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Insufficient quantity for deduction - SKU: {}, deductionQuantity: {}", skuCode,
                        deductionQuantity);
                throw new InsufficientQuantityException();
            }
            log.error(
                    "DataAccessException when deducting inventory quantity - Context: skuCode, {}, deductionQuantity, {} - Error: {}",
                    skuCode, deductionQuantity, e.getMessage(), e);
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error(
                    "PersistenceException when deducting inventory quantity - Context: skuCode, {}, deductionQuantity, {} - Error: {}",
                    skuCode, deductionQuantity, e.getMessage(), e);
            throw new InternalServerException();
        } catch (Exception e) {
            log.error(
                    "Exception when deducting inventory quantity - Context: skuCode, {}, deductionQuantity, {} - Error: {}",
                    skuCode, deductionQuantity, e.getMessage(), e);
            throw new InternalServerException();
        }
    }

    public int deleteBySkuCode(String skuCode) throws InternalServerException {
        return exceptionHandler.executeInventoryOperation(
                () -> inventoryRepository.deleteBySkuCode(skuCode),
                "deleting inventory by SKU code",
                "skuCode", skuCode);
    }

    public Inventory save(Inventory inventory) throws InternalServerException, DuplicateSkuCodeException {
        try {
            return inventoryRepository.save(inventory);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Duplicate SKU code attempted: {}", inventory.getSkuCode());
                throw new DuplicateSkuCodeException();
            }
            log.error("DataAccessException when saving inventory - Context: skuCode, {} - Error: {}",
                    inventory.getSkuCode(), e.getMessage(), e);
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error("PersistenceException when saving inventory - Context: skuCode, {} - Error: {}",
                    inventory.getSkuCode(), e.getMessage(), e);
            throw new InternalServerException();
        } catch (Exception e) {
            log.error("Exception when saving inventory - Context: skuCode, {} - Error: {}",
                    inventory.getSkuCode(), e.getMessage(), e);
            throw new InternalServerException();
        }
    }
}

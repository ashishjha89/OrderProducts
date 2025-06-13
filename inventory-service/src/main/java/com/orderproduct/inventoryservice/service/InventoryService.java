package com.orderproduct.inventoryservice.service;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.common.NotFoundException;
import com.orderproduct.inventoryservice.dto.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    @NonNull
    public List<InventoryStockStatus> stocksStatus(@NonNull List<String> skuCodes) throws InternalServerException {
        try {
            final var inventoryList = inventoryRepository.findBySkuCodeIn(skuCodes);
            if (inventoryList == null) {
                log.error("Null Inventory-list fetched from Repo for skuCodes:{}", skuCodes);
                throw new InternalServerException();
            }
            return skuCodes
                    .stream()
                    .map(skuCode -> new InventoryStockStatus(skuCode,
                            inventoryList.stream()
                                    .filter(inv -> inv.getSkuCode().equals(skuCode))
                                    .findFirst()
                                    .map(Inventory::getQuantity)
                                    .orElse(0)))
                    .toList();
        } catch (DataAccessException e) {
            log.error("Error when finding stocksStatus for skuCodes:{} and errorMsg:{}", skuCodes, e.getMessage());
            throw new InternalServerException();
        }
    }

    @Transactional
    @NonNull
    public CreateInventoryResponse createInventory(@NonNull Inventory inventory)
            throws InternalServerException, DuplicateSkuCodeException {
        try {
            inventoryRepository.save(inventory);
            return CreateInventoryResponse.success(inventory.getSkuCode());
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw new DuplicateSkuCodeException();
            }
            log.error("Error when creating inventory with skuCode:{} and errorMsg:{}", inventory.getSkuCode(),
                    e.getMessage());
            throw new InternalServerException();
        }
    }

    @Transactional
    public void deleteInventory(@NonNull String skuCode) throws InternalServerException, NotFoundException {
        try {
            int deletedCount = inventoryRepository.deleteBySkuCode(skuCode);
            if (deletedCount == 0) {
                throw new NotFoundException();
            }
        } catch (DataAccessException e) {
            log.error("Error when deleting inventory with skuCode:{} and errorMsg:{}", skuCode, e.getMessage());
            throw new InternalServerException();
        }
    }
}

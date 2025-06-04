package com.orderproduct.inventoryservice.service;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    @NonNull
    public InventoryStockStatus isInStock(@NonNull String skuCode) throws InternalServerException {
        try {
            return new InventoryStockStatus(skuCode, inventoryRepository.findBySkuCode(skuCode).isPresent());
        } catch (DataAccessException e) {
            log.error("Error when finding inventory by skuCode:{} and errorMsg:{}", skuCode, e.getMessage());
            throw new InternalServerException();
        }
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<InventoryStockStatus> stocksStatus(@NonNull List<String> skuCodes) throws InternalServerException {
        try {
            final var inventoryList = inventoryRepository.findBySkuCodeIn(skuCodes);
            if (inventoryList == null) {
                log.error("Null Inventory-list fetched from Repo for skuCodes:{}", skuCodes);
                throw new InternalServerException();
            }
            final var inventoriesFound = inventoryList.stream().filter(inventory -> inventory.getQuantity() > 0).toList();
            return skuCodes
                    .stream()
                    .map(skuCode -> new InventoryStockStatus(skuCode, isInventoryPresent(skuCode, inventoriesFound)))
                    .toList();
        } catch (DataAccessException e) {
            log.error("Error when finding stocksStatus for skuCodes:{} and errorMsg:{}", skuCodes, e.getMessage());
            throw new InternalServerException();
        }
    }

    private boolean isInventoryPresent(String skuCode, List<Inventory> inventoryList) {
        return inventoryList.stream().anyMatch(inventory -> inventory.getSkuCode().equals(skuCode));
    }
}

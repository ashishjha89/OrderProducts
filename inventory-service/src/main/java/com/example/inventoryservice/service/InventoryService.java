package com.example.inventoryservice.service;

import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.repository.InventoryRepository;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public InventoryStockStatus isInStock(@NonNull String skuCode) throws InternalServerException {
        try {
            return new InventoryStockStatus(inventoryRepository.findBySkuCode(skuCode).isPresent());
        } catch (DataAccessException e) {
            log.error("Error when finding inventory by skuCode:" + e.getMessage());
            throw new InternalServerException();
        }
    }
}

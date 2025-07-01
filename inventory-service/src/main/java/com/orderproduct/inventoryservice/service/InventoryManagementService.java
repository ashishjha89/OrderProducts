package com.orderproduct.inventoryservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.common.NotFoundException;
import com.orderproduct.inventoryservice.dto.request.CreateInventoryRequest;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class InventoryManagementService {

    private final ItemOnHandService itemOnHandService;

    @Transactional
    @NonNull
    public CreateInventoryResponse createInventory(@NonNull CreateInventoryRequest createInventoryRequest)
            throws InternalServerException, DuplicateSkuCodeException {
        log.info("Creating new inventory for SKU: {} with quantity: {}",
                createInventoryRequest.skuCode(), createInventoryRequest.quantity());

        CreateInventoryResponse response = itemOnHandService.createInventory(
                Inventory.createInventory(createInventoryRequest.skuCode(), createInventoryRequest.quantity()));

        log.debug("Successfully created inventory for SKU: {}", response.skuCode());
        return response;
    }

    @Transactional
    public void deleteInventory(@NonNull String skuCode) throws InternalServerException, NotFoundException {
        log.info("Deleting inventory for SKU: {}", skuCode);
        itemOnHandService.deleteInventory(skuCode);
    }
}
package com.orderproduct.inventoryservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.exception.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.dto.request.CreateInventoryRequest;
import com.orderproduct.inventoryservice.dto.request.UpdateInventoryRequest;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UpdateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.service.inventory.ItemOnHandService;

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
                                Inventory.createInventory(createInventoryRequest.skuCode(),
                                                createInventoryRequest.quantity()));

                log.debug("Successfully created inventory for SKU: {}", response.skuCode());
                return response;
        }

        @Transactional
        @NonNull
        public UpdateInventoryResponse updateInventory(@NonNull UpdateInventoryRequest updateInventoryRequest)
                        throws InternalServerException, NotFoundException {
                log.info("Updating inventory for SKU: {} with new quantity: {}",
                                updateInventoryRequest.skuCode(), updateInventoryRequest.quantity());

                UpdateInventoryResponse response = itemOnHandService.updateInventory(
                                updateInventoryRequest.skuCode(), updateInventoryRequest.quantity());

                log.debug("Successfully updated inventory for SKU: {}", response.skuCode());
                return response;
        }

        @Transactional
        public void deleteInventory(@NonNull String skuCode) throws InternalServerException, NotFoundException {
                log.info("Deleting inventory for SKU: {}", skuCode);
                itemOnHandService.deleteInventory(skuCode);
                log.debug("Successfully deleted inventory for SKU: {}", skuCode);
        }
}
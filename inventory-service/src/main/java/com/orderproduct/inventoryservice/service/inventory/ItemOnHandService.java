package com.orderproduct.inventoryservice.service.inventory;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.orderproduct.inventoryservice.common.exception.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.repository.InventoryRepository;

import jakarta.persistence.PersistenceException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class ItemOnHandService {

    private final InventoryRepository inventoryRepository;

    @NonNull
    public List<ItemOnHandQuantity> itemAvailabilities(@NonNull List<String> skuCodes) throws InternalServerException {
        log.debug("Fetching item availabilities for {} SKU codes", skuCodes.size());

        List<Inventory> availableInventories = getAvailableInventories(skuCodes);
        List<ItemOnHandQuantity> result = getItemOnHandQuantity(skuCodes, availableInventories);

        log.debug("Retrieved availabilities for {} SKUs", result.size());
        return result;
    }

    @NonNull
    public CreateInventoryResponse createInventory(@NonNull Inventory inventory)
            throws InternalServerException, DuplicateSkuCodeException {
        log.debug("Creating inventory for SKU: {} with quantity: {}",
                inventory.getSkuCode(), inventory.getOnHandQuantity());

        saveInventory(inventory);
        CreateInventoryResponse response = CreateInventoryResponse.success(inventory.getSkuCode());

        log.debug("Successfully created inventory for SKU: {}", response.skuCode());
        return response;
    }

    public void deleteInventory(@NonNull String skuCode) throws InternalServerException, NotFoundException {
        log.debug("Deleting inventory for SKU: {}", skuCode);

        int deletedCount = deleteItem(skuCode);
        if (deletedCount == 0) {
            log.warn("No inventory found to delete for SKU: {}", skuCode);
            throw new NotFoundException();
        }

        log.debug("Successfully deleted inventory for SKU: {}", skuCode);
    }

    private List<Inventory> getAvailableInventories(List<String> skuCodes) throws InternalServerException {
        try {
            log.debug("Querying inventory for {} SKU codes", skuCodes.size());
            List<Inventory> result = inventoryRepository.findBySkuCodeIn(skuCodes);
            log.debug("Found {} inventory records", result.size());
            return result;
        } catch (DataAccessException e) {
            log.error("DataAccessException when finding onHands Item Availabilities for skuCodes:{} and errorMsg:{}",
                    skuCodes,
                    e.getMessage());
            throw new InternalServerException();
        }
    }

    private void saveInventory(@NonNull Inventory inventory) throws InternalServerException, DuplicateSkuCodeException {
        try {
            log.debug("Saving inventory for SKU: {}", inventory.getSkuCode());
            inventoryRepository.save(inventory);
            log.debug("Successfully saved inventory for SKU: {}", inventory.getSkuCode());
        } catch (DataAccessException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("Duplicate SKU code attempted: {}", inventory.getSkuCode());
                throw new DuplicateSkuCodeException();
            }
            log.error("Error when creating inventory with skuCode:{} and errorMsg:{}", inventory.getSkuCode(),
                    e.getMessage());
            throw new InternalServerException();
        } catch (PersistenceException e) {
            log.error("PersistenceException when creating inventory with skuCode:{} and errorMsg:{}",
                    inventory.getSkuCode(),
                    e.getMessage());

            throw new InternalServerException();
        }
    }

    private int deleteItem(@NonNull String skuCode) throws InternalServerException, NotFoundException {
        try {
            log.debug("Deleting inventory for SKU: {}", skuCode);
            int result = inventoryRepository.deleteBySkuCode(skuCode);
            log.debug("Deleted {} inventory records for SKU: {}", result, skuCode);
            return result;
        } catch (DataAccessException e) {
            log.error("Error when deleting inventory with skuCode:{} and errorMsg:{}", skuCode, e.getMessage());
            throw new InternalServerException();
        }
    }

    private List<ItemOnHandQuantity> getItemOnHandQuantity(List<String> skuCodes, List<Inventory> inventoryList)
            throws InternalServerException {
        log.debug("Mapping {} SKU codes to on-hand quantities", skuCodes.size());
        List<ItemOnHandQuantity> result = skuCodes.stream()
                .map(skuCode -> new ItemOnHandQuantity(skuCode,
                        inventoryList.stream()
                                .filter(inv -> inv.getSkuCode().equals(skuCode))
                                .findFirst()
                                .map(Inventory::getOnHandQuantity)
                                .orElse(0)))
                .toList();
        log.debug("Mapped {} SKU codes to on-hand quantities", result.size());
        return result;
    }
}
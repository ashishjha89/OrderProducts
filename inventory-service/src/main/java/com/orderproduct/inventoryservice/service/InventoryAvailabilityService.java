package com.orderproduct.inventoryservice.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.util.InventoryCalculationUtils;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class InventoryAvailabilityService {

    private final ItemOnHandService itemOnHandService;
    private final ReservationService reservationService;

    @Transactional(readOnly = true)
    @NonNull
    public List<AvailableInventoryResponse> getAvailableInventory(@NonNull List<String> skuCodes)
            throws InternalServerException {
        log.info("Calculating available inventory for {} SKU codes: {}", skuCodes.size(), skuCodes);

        // Create maps for efficient lookup
        Map<String, Integer> inventoryQuantityMap = skuCodeToOnHandsQuantityMap(skuCodes);
        Map<String, Integer> reservedQuantityMap = skuCodeToReservedQuantityMap(skuCodes);

        List<AvailableInventoryResponse> result = skuCodes.stream()
                .map(skuCode -> {
                    int availableQuantity = InventoryCalculationUtils.calculateAvailableQuantity(skuCode,
                            inventoryQuantityMap,
                            reservedQuantityMap);
                    return new AvailableInventoryResponse(skuCode, availableQuantity);
                })
                .toList();

        log.debug("Calculated available inventory for {} SKUs", result.size());
        return result;
    }

    private Map<String, Integer> skuCodeToOnHandsQuantityMap(List<String> skuCodes)
            throws InternalServerException {
        List<ItemOnHandQuantity> itemOnHandQuantityList = itemOnHandService.itemAvailabilities(skuCodes);
        Map<String, Integer> result = InventoryCalculationUtils
                .createSkuCodeToOnHandsQuantityMap(itemOnHandQuantityList);
        return result;
    }

    private Map<String, Integer> skuCodeToReservedQuantityMap(List<String> skuCodes) {
        List<ReservedItemQuantity> reservedQuantityList = reservationService.findPendingReservedQuantities(skuCodes);
        Map<String, Integer> result = InventoryCalculationUtils
                .createSkuCodeToReservedQuantityMap(reservedQuantityList);
        return result;
    }
}
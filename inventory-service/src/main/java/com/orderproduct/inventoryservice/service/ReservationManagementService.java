package com.orderproduct.inventoryservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.common.InventoryCalculationUtils;
import com.orderproduct.inventoryservice.common.NotEnoughStockException;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class ReservationManagementService {

    private final ItemOnHandService itemOnHandService;
    private final ReservationService reservationService;

    @Transactional
    public List<AvailableInventoryResponse> reserveProductsIfAvailable(OrderReservationRequest request)
            throws NotEnoughStockException, InternalServerException {
        log.info("Attempting to reserve products for order: {} with {} items",
                request.orderNumber(), request.itemReservationRequests().size());

        List<String> skuCodes = extractSkuCodes(request);
        List<UnavailableProduct> unavailableStocks = new ArrayList<>();

        // Create maps for efficient lookup
        Map<String, Integer> skuCodeToOnHandsQuantityMap = createSkuCodeToOnHandsQuantityMap(skuCodes);
        Map<String, Integer> skuCodeToReservedQuantityMap = createSkuCodeToReservedQuantityMap(skuCodes);

        // Check each requested SKU for availability
        for (ItemReservationRequest requestedItem : request.itemReservationRequests()) {
            String skuCode = requestedItem.skuCode();
            int requestedQuantity = requestedItem.quantity();
            int availableQuantity = InventoryCalculationUtils.calculateAvailableQuantity(skuCode,
                    skuCodeToOnHandsQuantityMap,
                    skuCodeToReservedQuantityMap);

            if (requestedQuantity > availableQuantity) {
                unavailableStocks.add(new UnavailableProduct(skuCode, requestedQuantity, availableQuantity));
            }
        }

        // If there are any unavailable products, throw NotEnoughStockException
        if (!unavailableStocks.isEmpty()) {
            log.warn("Insufficient stock for order: {}. Unavailable products: {}",
                    request.orderNumber(), unavailableStocks);
            throw new NotEnoughStockException(unavailableStocks);
        }

        // Reserve products
        reservationService.reserveProducts(request);

        // Return available inventory for each SKU
        List<AvailableInventoryResponse> result = request.itemReservationRequests().stream()
                .map(requestedStock -> {
                    String skuCode = requestedStock.skuCode();
                    int availableQuantity = InventoryCalculationUtils.calculateAvailableQuantity(skuCode,
                            skuCodeToOnHandsQuantityMap,
                            skuCodeToReservedQuantityMap);
                    return new AvailableInventoryResponse(skuCode, availableQuantity);
                })
                .toList();

        log.debug("Returning updated availability for {} SKUs after reservation", result.size());
        return result;
    }

    private List<String> extractSkuCodes(OrderReservationRequest request) {
        return request.itemReservationRequests().stream()
                .map(stockStatus -> stockStatus.skuCode())
                .toList();
    }

    private Map<String, Integer> createSkuCodeToOnHandsQuantityMap(List<String> skuCodes)
            throws InternalServerException {
        List<ItemOnHandQuantity> itemOnHandQuantityList = itemOnHandService.itemAvailabilities(skuCodes);
        Map<String, Integer> result = InventoryCalculationUtils
                .createSkuCodeToOnHandsQuantityMap(itemOnHandQuantityList);
        return result;
    }

    private Map<String, Integer> createSkuCodeToReservedQuantityMap(List<String> skuCodes) {
        List<ReservedItemQuantity> reservedQuantityList = reservationService.findPendingReservedQuantities(skuCodes);
        Map<String, Integer> result = InventoryCalculationUtils
                .createSkuCodeToReservedQuantityMap(reservedQuantityList);
        return result;
    }
}
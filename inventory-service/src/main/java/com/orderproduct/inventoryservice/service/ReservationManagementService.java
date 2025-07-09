package com.orderproduct.inventoryservice.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.common.util.InventoryCalculationUtils;
import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;
import com.orderproduct.inventoryservice.service.inventory.ItemOnHandService;
import com.orderproduct.inventoryservice.service.reservation.ReservationService;

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
                        throws NotEnoughItemException, InternalServerException {
                log.info("Attempting to reserve products for order: {} with {} items",
                                request.orderNumber(), request.itemReservationRequests().size());

                List<String> skuCodes = extractSkuCodes(request);

                // Create maps for efficient lookup
                Map<String, Integer> skuCodeToOnHandsQuantityMap = skuCodeToOnHandsQuantityMap(skuCodes);
                Map<String, Integer> skuCodeToReservedQuantityMap = skuCodeToReservedQuantityMap(skuCodes);

                // Find unavailable products
                List<UnavailableProduct> unavailableItems = request.itemReservationRequests().stream()
                                .map(requestedItem -> {
                                        String skuCode = requestedItem.skuCode();
                                        int requestedQuantity = requestedItem.quantity();
                                        int availableQuantity = InventoryCalculationUtils.calculateAvailableQuantity(
                                                        skuCode,
                                                        skuCodeToOnHandsQuantityMap,
                                                        skuCodeToReservedQuantityMap);
                                        return new UnavailableProduct(skuCode, requestedQuantity, availableQuantity);
                                })
                                .filter(unavailableProduct -> unavailableProduct
                                                .requestedQuantity() > unavailableProduct
                                                                .availableQuantity())
                                .toList();

                // If there are any unavailable products, throw NotEnoughItemException
                if (!unavailableItems.isEmpty()) {
                        log.warn("Insufficient item for order: {}. Unavailable products: {}",
                                        request.orderNumber(), unavailableItems);
                        throw new NotEnoughItemException(unavailableItems);
                }

                // Reserve products
                reservationService.reserveProducts(request);

                // Return available inventory for each SKU with updated quantities
                List<AvailableInventoryResponse> result = request.itemReservationRequests().stream()
                                .map(requestedItem -> {
                                        String skuCode = requestedItem.skuCode();
                                        int requestedQuantity = requestedItem.quantity();
                                        int originalAvailableQuantity = InventoryCalculationUtils
                                                        .calculateAvailableQuantity(skuCode,
                                                                        skuCodeToOnHandsQuantityMap,
                                                                        skuCodeToReservedQuantityMap);
                                        int updatedAvailableQuantity = originalAvailableQuantity - requestedQuantity;
                                        return new AvailableInventoryResponse(skuCode, updatedAvailableQuantity);
                                })
                                .toList();

                log.debug("Returning updated availability for {} SKUs after reservation", result.size());
                return result;
        }

        private List<String> extractSkuCodes(OrderReservationRequest request) {
                return request.itemReservationRequests().stream()
                                .map(requestedItem -> requestedItem.skuCode())
                                .toList();
        }

        private Map<String, Integer> skuCodeToOnHandsQuantityMap(List<String> skuCodes)
                        throws InternalServerException {
                List<ItemOnHandQuantity> itemOnHandQuantityList = itemOnHandService.itemAvailabilities(skuCodes);
                Map<String, Integer> result = InventoryCalculationUtils
                                .createSkuCodeToOnHandsQuantityMap(itemOnHandQuantityList);
                return result;
        }

        private Map<String, Integer> skuCodeToReservedQuantityMap(List<String> skuCodes) {
                List<ReservedItemQuantity> reservedQuantityList = reservationService
                                .findPendingReservedQuantities(skuCodes);
                Map<String, Integer> result = InventoryCalculationUtils
                                .createSkuCodeToReservedQuantityMap(reservedQuantityList);
                return result;
        }
}
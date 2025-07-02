package com.orderproduct.inventoryservice.common.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;

/**
 * Utility class for inventory calculations to reduce code duplication.
 */
public final class InventoryCalculationUtils {

    private InventoryCalculationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a map of SKU codes to on-hand quantities for efficient lookup.
     * 
     * @param itemOnHandQuantityList list of item on-hand quantities
     * @return map of SKU code to on-hand quantity
     */
    public static Map<String, Integer> createSkuCodeToOnHandsQuantityMap(
            List<ItemOnHandQuantity> itemOnHandQuantityList) {
        return itemOnHandQuantityList.stream()
                .collect(Collectors.toMap(ItemOnHandQuantity::skuCode, ItemOnHandQuantity::quantity));
    }

    /**
     * Creates a map of SKU codes to reserved quantities for efficient lookup.
     * 
     * @param reservedQuantityList list of reserved item quantities
     * @return map of SKU code to reserved quantity
     */
    public static Map<String, Integer> createSkuCodeToReservedQuantityMap(
            List<ReservedItemQuantity> reservedQuantityList) {
        return reservedQuantityList.stream()
                .collect(Collectors.toMap(ReservedItemQuantity::skuCode, ReservedItemQuantity::quantity));
    }

    /**
     * Calculates the available quantity for a given SKU.
     * Available quantity = on-hand quantity - reserved quantity (minimum 0)
     * 
     * @param skuCode                 the SKU code
     * @param onHandsItemQuantityMap  map of SKU codes to on-hand quantities (can be
     *                                null)
     * @param reservedItemQuantityMap map of SKU codes to reserved quantities (can
     *                                be null)
     * @return the available quantity (never negative)
     */
    public static int calculateAvailableQuantity(
            String skuCode,
            Map<String, Integer> onHandsItemQuantityMap,
            Map<String, Integer> reservedItemQuantityMap) {
        int onHandsItemQuantity = (onHandsItemQuantityMap != null) ? onHandsItemQuantityMap.getOrDefault(skuCode, 0)
                : 0;
        int reservedItemQuantity = (reservedItemQuantityMap != null) ? reservedItemQuantityMap.getOrDefault(skuCode, 0)
                : 0;
        return Math.max(0, onHandsItemQuantity - reservedItemQuantity);
    }
}
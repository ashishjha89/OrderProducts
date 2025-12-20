package com.orderproduct.inventoryservice.common.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;

import jakarta.annotation.Nullable;

public final class InventoryCalculationUtils {

        private InventoryCalculationUtils() {
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
                                .collect(Collectors.groupingBy(
                                                ReservedItemQuantity::skuCode,
                                                Collectors.summingInt(ReservedItemQuantity::quantity)));
        }

        /**
         * Calculates the available quantity for a given SKU.
         * Available quantity = on-hand quantity - reserved quantity (minimum 0)
         * 
         * @param skuCode                 the SKU code
         * @param onHandsItemQuantityMap  map of SKU codes to on-hand quantities
         * @param reservedItemQuantityMap map of SKU codes to reserved quantities
         * @return the available quantity (never negative)
         */
        public static int calculateAvailableQuantity(
                        String skuCode,
                        @Nullable Map<String, Integer> onHandsItemQuantityMap,
                        @Nullable Map<String, Integer> reservedItemQuantityMap) {
                int onHandsItemQuantity = getQuantity(onHandsItemQuantityMap, skuCode);
                int reservedItemQuantity = getQuantity(reservedItemQuantityMap, skuCode);
                return Math.max(0, onHandsItemQuantity - reservedItemQuantity);
        }

        private static int getQuantity(Map<String, Integer> quantityMap, String skuCode) {
                return quantityMap != null ? quantityMap.getOrDefault(skuCode, 0) : 0;
        }
}
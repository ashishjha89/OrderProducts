package com.orderproduct.inventoryservice.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.inventoryservice.domain.ItemOnHandQuantity;
import com.orderproduct.inventoryservice.domain.ReservedItemQuantity;

class InventoryCalculationUtilsTest {

    @Test
    @DisplayName("`createSkuCodeToOnHandsQuantityMap()` should create map from list of ItemOnHandQuantity")
    void createSkuCodeToOnHandsQuantityMap_ValidList_ReturnsCorrectMap() {
        // Given
        List<ItemOnHandQuantity> itemOnHandQuantities = List.of(
                new ItemOnHandQuantity("SKU-001", 10),
                new ItemOnHandQuantity("SKU-002", 25),
                new ItemOnHandQuantity("SKU-003", 0));

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToOnHandsQuantityMap(itemOnHandQuantities);

        // Then
        assertEquals(3, result.size());
        assertEquals(10, result.get("SKU-001"));
        assertEquals(25, result.get("SKU-002"));
        assertEquals(0, result.get("SKU-003"));
    }

    @Test
    @DisplayName("`createSkuCodeToOnHandsQuantityMap()` should handle empty list")
    void createSkuCodeToOnHandsQuantityMap_EmptyList_ReturnsEmptyMap() {
        // Given
        List<ItemOnHandQuantity> itemOnHandQuantities = List.of();

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToOnHandsQuantityMap(itemOnHandQuantities);

        // Then
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("`createSkuCodeToOnHandsQuantityMap()` should handle single item")
    void createSkuCodeToOnHandsQuantityMap_SingleItem_ReturnsCorrectMap() {
        // Given
        List<ItemOnHandQuantity> itemOnHandQuantities = List.of(
                new ItemOnHandQuantity("SKU-001", 15));

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToOnHandsQuantityMap(itemOnHandQuantities);

        // Then
        assertEquals(1, result.size());
        assertEquals(15, result.get("SKU-001"));
    }

    @Test
    @DisplayName("`createSkuCodeToOnHandsQuantityMap()` should throw exception for duplicate SKU codes")
    void createSkuCodeToOnHandsQuantityMap_DuplicateSkuCodes_ThrowsException() {
        // Given
        List<ItemOnHandQuantity> itemOnHandQuantities = List.of(
                new ItemOnHandQuantity("SKU-001", 10),
                new ItemOnHandQuantity("SKU-001", 20)); // Duplicate SKU code

        // Then
        assertThrows(IllegalStateException.class,
                () -> InventoryCalculationUtils.createSkuCodeToOnHandsQuantityMap(itemOnHandQuantities));
    }

    @Test
    @DisplayName("`createSkuCodeToReservedQuantityMap()` should create map from list of ReservedItemQuantity")
    void createSkuCodeToReservedQuantityMap_ValidList_ReturnsCorrectMap() {
        // Given
        List<ReservedItemQuantity> reservedQuantities = List.of(
                new ReservedItemQuantity("SKU-001", 3),
                new ReservedItemQuantity("SKU-002", 8),
                new ReservedItemQuantity("SKU-003", 0));

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToReservedQuantityMap(reservedQuantities);

        // Then
        assertEquals(3, result.size());
        assertEquals(3, result.get("SKU-001"));
        assertEquals(8, result.get("SKU-002"));
        assertEquals(0, result.get("SKU-003"));
    }

    @Test
    @DisplayName("`createSkuCodeToReservedQuantityMap()` should handle empty list")
    void createSkuCodeToReservedQuantityMap_EmptyList_ReturnsEmptyMap() {
        // Given
        List<ReservedItemQuantity> reservedQuantities = List.of();

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToReservedQuantityMap(reservedQuantities);

        // Then
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("`createSkuCodeToReservedQuantityMap()` should handle single item")
    void createSkuCodeToReservedQuantityMap_SingleItem_ReturnsCorrectMap() {
        // Given
        List<ReservedItemQuantity> reservedQuantities = List.of(
                new ReservedItemQuantity("SKU-001", 5));

        // When
        Map<String, Integer> result = InventoryCalculationUtils.createSkuCodeToReservedQuantityMap(reservedQuantities);

        // Then
        assertEquals(1, result.size());
        assertEquals(5, result.get("SKU-001"));
    }

    @Test
    @DisplayName("`createSkuCodeToReservedQuantityMap()` should throw exception for duplicate SKU codes")
    void createSkuCodeToReservedQuantityMap_DuplicateSkuCodes_ThrowsException() {
        // Given
        List<ReservedItemQuantity> reservedQuantities = List.of(
                new ReservedItemQuantity("SKU-001", 3),
                new ReservedItemQuantity("SKU-001", 7)); // Duplicate SKU code

        // Then
        assertThrows(IllegalStateException.class,
                () -> InventoryCalculationUtils.createSkuCodeToReservedQuantityMap(reservedQuantities));
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should calculate correct available quantity when on-hand > reserved")
    void calculateAvailableQuantity_OnHandGreaterThanReserved_ReturnsCorrectQuantity() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 15);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-001", 5);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(10, result); // 15 - 5 = 10
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should return 0 when on-hand equals reserved")
    void calculateAvailableQuantity_OnHandEqualsReserved_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 10);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-001", 10);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // 10 - 10 = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should return 0 when on-hand < reserved")
    void calculateAvailableQuantity_OnHandLessThanReserved_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 5);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-001", 10);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 5 - 10) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle SKU not in on-hand map")
    void calculateAvailableQuantity_SkuNotInOnHandMap_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-002", 15);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-001", 5);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 0 - 5) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle SKU not in reserved map")
    void calculateAvailableQuantity_SkuNotInReservedMap_ReturnsOnHandQuantity() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 15);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-002", 5);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(15, result); // Math.max(0, 15 - 0) = 15
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle SKU not in either map")
    void calculateAvailableQuantity_SkuNotInEitherMap_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-002", 15);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-003", 5);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 0 - 0) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle zero quantities")
    void calculateAvailableQuantity_ZeroQuantities_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 0);
        Map<String, Integer> reservedItemQuantityMap = Map.of("SKU-001", 0);

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 0 - 0) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle empty maps")
    void calculateAvailableQuantity_EmptyMaps_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of();
        Map<String, Integer> reservedItemQuantityMap = Map.of();

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 0 - 0) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle null maps")
    void calculateAvailableQuantity_NullMaps_ReturnsZero() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = null;
        Map<String, Integer> reservedItemQuantityMap = null;

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(0, result); // Math.max(0, 0 - 0) = 0
    }

    @Test
    @DisplayName("`calculateAvailableQuantity()` should handle mixed null and valid maps")
    void calculateAvailableQuantity_MixedNullAndValidMaps_ReturnsCorrectQuantity() {
        // Given
        Map<String, Integer> onHandsItemQuantityMap = Map.of("SKU-001", 20);
        Map<String, Integer> reservedItemQuantityMap = null;

        // When
        int result = InventoryCalculationUtils.calculateAvailableQuantity("SKU-001", onHandsItemQuantityMap,
                reservedItemQuantityMap);

        // Then
        assertEquals(20, result); // Math.max(0, 20 - 0) = 20
    }
}
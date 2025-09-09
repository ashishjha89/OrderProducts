package com.orderproduct.inventoryservice.service.inventory;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.entity.Reservation;
import com.orderproduct.inventoryservice.entity.ReservationState;

public class InventoryDeductionServiceTest {

    private final ItemOnHandService itemOnHandService = mock(ItemOnHandService.class);
    private final InventoryDeductionService inventoryDeductionService = new InventoryDeductionService(
            itemOnHandService);

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should successfully deduct inventory for single reservation")
    public void deductInventoryForFulfilledOrder_SingleReservation_Succeeds() throws Exception {
        // Given
        final var reservation = createReservation("ORDER-123", "SKU-001", 5, ReservationState.FULFILLED);
        final var fulfilledReservations = List.of(reservation);

        // When & Then
        assertThatNoException()
                .isThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations));

        verify(itemOnHandService).deductInventoryQuantity("SKU-001", 5);
    }

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should handle mixed scenarios with some NotFoundException and some success")
    public void deductInventoryForFulfilledOrder_MixedScenarios_Succeeds() throws Exception {
        // Given
        final var reservation1 = createReservation("ORDER-123", "SKU-001", 5, ReservationState.FULFILLED);
        final var reservation2 = createReservation("ORDER-123", "NON-EXISTENT-SKU", 3, ReservationState.FULFILLED);
        final var reservation3 = createReservation("ORDER-123", "SKU-003", 10, ReservationState.FULFILLED);
        final var fulfilledReservations = List.of(reservation1, reservation2, reservation3);

        doThrow(new NotFoundException())
                .when(itemOnHandService).deductInventoryQuantity("NON-EXISTENT-SKU", 3);

        // When & Then
        assertThatNoException()
                .isThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations));

        verify(itemOnHandService).deductInventoryQuantity("SKU-001", 5);
        verify(itemOnHandService).deductInventoryQuantity("NON-EXISTENT-SKU", 3);
        verify(itemOnHandService).deductInventoryQuantity("SKU-003", 10);
    }

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should successfully handle empty reservation list")
    public void deductInventoryForFulfilledOrder_EmptyReservationList_Succeeds() throws Exception {
        // Given
        final var fulfilledReservations = List.<Reservation>of();

        // When & Then
        assertThatNoException()
                .isThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations));
    }

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should continue processing when inventory not found")
    public void deductInventoryForFulfilledOrder_InventoryNotFound_ContinuesProcessing() throws Exception {
        // Given
        final var reservation = createReservation("ORDER-123", "NON-EXISTENT-SKU", 5, ReservationState.FULFILLED);
        final var fulfilledReservations = List.of(reservation);

        doThrow(new NotFoundException())
                .when(itemOnHandService).deductInventoryQuantity("NON-EXISTENT-SKU", 5);

        // When & Then
        assertThatNoException()
                .isThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations));

        verify(itemOnHandService).deductInventoryQuantity("NON-EXISTENT-SKU", 5);
    }

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should throw InternalServerException when ItemOnHandService throws InternalServerException")
    public void deductInventoryForFulfilledOrder_ItemOnHandServiceError_ThrowsInternalServerException()
            throws Exception {
        // Given
        final var reservation = createReservation("ORDER-123", "SKU-001", 5, ReservationState.FULFILLED);
        final var fulfilledReservations = List.of(reservation);

        doThrow(new InternalServerException())
                .when(itemOnHandService).deductInventoryQuantity("SKU-001", 5);

        // When & Then
        assertThatThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations))
                .isInstanceOf(InternalServerException.class);

        verify(itemOnHandService).deductInventoryQuantity("SKU-001", 5);
    }

    @Test
    @DisplayName("`deductInventoryForFulfilledOrder()` should stop processing when first reservation throws InternalServerException")
    public void deductInventoryForFulfilledOrder_FirstReservationInternalServerError_StopsProcessing()
            throws Exception {
        // Given
        final var reservation1 = createReservation("ORDER-123", "SKU-001", 5, ReservationState.FULFILLED);
        final var reservation2 = createReservation("ORDER-123", "SKU-002", 3, ReservationState.FULFILLED);
        final var fulfilledReservations = List.of(reservation1, reservation2);

        doThrow(new InternalServerException())
                .when(itemOnHandService).deductInventoryQuantity("SKU-001", 5);

        // When & Then
        assertThatThrownBy(() -> inventoryDeductionService.deductInventoryForFulfilledOrder(fulfilledReservations))
                .isInstanceOf(InternalServerException.class);

        verify(itemOnHandService).deductInventoryQuantity("SKU-001", 5);
        // Note: The second reservation should not be processed due to early exception
    }

    private Reservation createReservation(String orderNumber, String skuCode, int reservedQuantity,
            ReservationState status) {
        return Reservation.builder()
                .orderNumber(orderNumber)
                .skuCode(skuCode)
                .reservedQuantity(reservedQuantity)
                .reservedAt(LocalDateTime.now())
                .status(status)
                .build();
    }
}

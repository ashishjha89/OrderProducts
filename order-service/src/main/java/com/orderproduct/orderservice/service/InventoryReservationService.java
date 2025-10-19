package com.orderproduct.orderservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import lombok.NonNull;

public interface InventoryReservationService {
    @NonNull
    CompletableFuture<List<InventoryAvailabilityStatus>> reserveOrder(
            @NonNull OrderReservationRequest orderReservationRequest)
            throws InternalServerException, InvalidInventoryException,
            InvalidInputException, InventoryNotInStockException;
}

package com.orderproduct.orderservice.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.ItemReservationRequest;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.OrderReservationRequest;
import com.orderproduct.orderservice.dto.ProductStub;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.dto.SavedOrderLineItem;
import com.orderproduct.orderservice.repository.OrderRepository;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
public class OrderService {

    private final OrderTransactionService orderTransactionService;
    private final InventoryReservationService inventoryReservationService;
    private final ObservationRegistry observationRegistry;
    private final OrderDataGenerator orderDataGenerator;
    private final OrderRepository orderRepository;

    @NonNull
    public CompletableFuture<SavedOrder> placeOrder(
            @NonNull OrderRequest orderRequest) throws InternalServerException, InventoryNotInStockException {
        String orderNumber = orderDataGenerator.getUniqueOrderNumber();
        return attemptProductReservation(orderNumber, orderRequest).thenCompose(reservationFailed -> {
            if (reservationFailed) {
                log.info("Inventory not in stock for order request: {}", orderRequest);
                return CompletableFuture.failedFuture(new InventoryNotInStockException());
            }
            return CompletableFuture
                    .supplyAsync(() -> orderTransactionService.saveOrder(orderNumber, orderRequest))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Order save failed for order: {}", orderNumber, throwable);
                            try {
                                orderTransactionService.saveOrderCancelledEvent(orderNumber, orderRequest, throwable);
                            } catch (InternalServerException e) {
                                log.error("Failed to save OrderCancelledEvent for order: {}", orderNumber, e);
                            }
                        }
                    });
        });
    }

    private CompletableFuture<Boolean> attemptProductReservation(
            @NonNull String orderNumber,
            @NonNull OrderRequest orderRequest)
            throws InternalServerException, InvalidInventoryException,
            InvalidInputException, InventoryNotInStockException {
        List<ItemReservationRequest> reservationRequests = orderRequest.orderLineItemsList().stream()
                .map(item -> new ItemReservationRequest(item.skuCode(), item.quantity()))
                .toList();
        OrderReservationRequest orderReservationRequest = new OrderReservationRequest(orderNumber, reservationRequests);

        return inventoryServiceObservation()
                .observe(() -> inventoryReservationService.reserveOrder(orderReservationRequest)
                        .thenApply(availableStocks -> {
                            if (availableStocks == null) {
                                log.error("Received null response from inventory service for order: {}", orderNumber);
                                throw new InternalServerException();
                            }
                            if (!isReservationResponseComplete(reservationRequests, availableStocks)) {
                                log.error("Reservation response incomplete for all line items in order: {}",
                                        orderNumber);
                                throw new InternalServerException();
                            }
                            if (!isReservationSuccessfulForAllItems(reservationRequests, availableStocks)) {
                                log.info(
                                        "Reservation failed due to insufficient quantities for all line items in order: {}",
                                        orderNumber);
                                return true;
                            }
                            log.info("Product reservation successful for order: {}", orderNumber);
                            return false;
                        }));
    }

    private boolean isReservationResponseComplete(
            @NonNull List<ItemReservationRequest> requestedItems,
            @NonNull List<InventoryAvailabilityStatus> availableStocks) {
        return requestedItems.stream().allMatch(requestedItem -> {
            String skuCode = requestedItem.skuCode();
            InventoryAvailabilityStatus reservedStock = getFirstMatchingStock(skuCode, availableStocks);
            return reservedStock != null;
        });
    }

    private boolean isReservationSuccessfulForAllItems(
            @NonNull List<ItemReservationRequest> requestedItems,
            @NonNull List<InventoryAvailabilityStatus> availableStocks) {
        return requestedItems.stream().allMatch(requestedItem -> {
            String skuCode = requestedItem.skuCode();
            InventoryAvailabilityStatus reservedStock = getFirstMatchingStock(skuCode, availableStocks);
            return reservedStock != null && reservedStock.availableQuantity() > 0;
        });
    }

    @Nullable
    private InventoryAvailabilityStatus getFirstMatchingStock(String skuCode,
                                                              List<InventoryAvailabilityStatus> availableStocks) {
        return availableStocks.stream()
                .filter(stockStatus -> stockStatus.skuCode().equals(skuCode))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public Optional<SavedOrder> getOrderByOrderNumber(@NonNull String orderNumber) {
        try {
            return orderRepository.findByOrderNumber(orderNumber)
                    .map(order -> new SavedOrder(order.getId() + "", order.getOrderNumber()));
        } catch (DataAccessException e) {
            log.error("Error when getting order by orderNumber {}: {}", orderNumber, e.getMessage());
            throw new InternalServerException();
        }
    }

    @NonNull
    public List<SavedOrderLineItem> getLineItemsByOrderNumber(@NonNull String orderNumber) {
        try {
            return orderRepository.findByOrderNumberWithLineItems(orderNumber)
                    .map(order -> order.getOrderLineItemsList().stream()
                            .map(item -> new SavedOrderLineItem(
                                    item.getSkuCode(), item.getPrice(), item.getQuantity(),
                                    new ProductStub(item.getSkuCode())))
                            .toList())
                    .orElse(List.of());
        } catch (DataAccessException e) {
            log.error("Error when getting line items for orderNumber {}: {}", orderNumber, e.getMessage());
            throw new InternalServerException();
        }
    }

    private Observation inventoryServiceObservation() {
        final Observation inventoryServiceObservation = Observation.createNotStarted(
                "inventory-service-reservation",
                this.observationRegistry);
        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");
        return inventoryServiceObservation;
    }

}
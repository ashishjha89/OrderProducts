package com.orderproduct.orderservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.repository.InventoryStatusRepository;

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
    private final InventoryStatusRepository inventoryStatusRepository;
    private final ObservationRegistry observationRegistry;

    @NonNull
    public CompletableFuture<SavedOrder> placeOrder(
            @NonNull OrderRequest orderRequest) throws InternalServerException, InventoryNotInStockException {
        return isAnyLineItemMissing(orderRequest).thenCompose(isAnyLineItemMissing -> {
            if (isAnyLineItemMissing) {
                log.info("InventoryNotInStock orderRequest:{}", orderRequest);
                return CompletableFuture.failedFuture(new InventoryNotInStockException());
            }
            return CompletableFuture
                    .supplyAsync(() -> orderTransactionService.executeTransactionalOrderPlacement(orderRequest));
        });
    }

    private CompletableFuture<Boolean> isAnyLineItemMissing(
            @NonNull OrderRequest orderRequest) throws InternalServerException {
        final List<String> skuCodesInOrder = orderRequest.orderLineItemsList().stream().map(OrderLineItemsDto::skuCode)
                .toList();
        final Observation inventoryServiceObservation = Observation.createNotStarted(
                "inventory-service-lookup",
                this.observationRegistry);
        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");
        return inventoryServiceObservation.observe(() -> inventoryStatusRepository
                .getInventoryAvailabilityFuture(skuCodesInOrder)
                .handle((stocksStatus, exception) -> {
                    if (exception instanceof RuntimeException)
                        throw (RuntimeException) exception;
                    if (!isStockAvailableForAllSkuCodes(skuCodesInOrder, stocksStatus)) {
                        log.error("StockStatus not returned for all LineItems in Order");
                        throw new InternalServerException();
                    }
                    return orderRequest.orderLineItemsList().stream()
                            .anyMatch(orderRequestItem -> {
                                InventoryStockStatus stockStatus = stocksStatus.stream()
                                        .filter(status -> status.skuCode().equals(orderRequestItem.skuCode()))
                                        .findFirst()
                                        .orElseThrow(() -> new InternalServerException());
                                boolean insufficientQuantity = stockStatus.quantity() < orderRequestItem.quantity();
                                if (insufficientQuantity) {
                                    log.info("Insufficient quantity for skuCode:{} - Available:{}, Requested:{}",
                                            orderRequestItem.skuCode(), stockStatus.quantity(),
                                            orderRequestItem.quantity());
                                }
                                return insufficientQuantity;
                            });
                }));
    }

    private boolean isStockAvailableForAllSkuCodes(List<String> skuCodeList,
            List<InventoryStockStatus> stockStatusList) {
        return skuCodeList.stream().allMatch(skuCode -> isStockStatusAvailable(skuCode, stockStatusList));
    }

    private boolean isStockStatusAvailable(String skuCode, List<InventoryStockStatus> stockStatusList) {
        return stockStatusList.stream().anyMatch(stockStatus -> stockStatus.skuCode().equals(skuCode));
    }

}

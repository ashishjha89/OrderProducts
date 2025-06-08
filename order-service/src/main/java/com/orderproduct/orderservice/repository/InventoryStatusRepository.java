package com.orderproduct.orderservice.repository;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
@Slf4j
@AllArgsConstructor
public class InventoryStatusRepository {

    private static final String INVENTORY_API_PATH = "/api/inventory";

    private static final String SKU_CODE_QUERY_PARAM = "skuCode";

    private final WebClient.Builder webClientBuilder;

    private final String inventoryApiBaseUrl;

    @NonNull
    public CompletableFuture<List<InventoryStockStatus>> getInventoryAvailabilityFuture(
            List<String> skuCodes
    ) throws InternalServerException {
        return webClientBuilder.baseUrl(inventoryApiBaseUrl).build().get()
                .uri(uriBuilder -> uriBuilder.path(INVENTORY_API_PATH).queryParam(SKU_CODE_QUERY_PARAM, skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryStockStatus[].class)
                .toFuture()
                .handle((stockStatus, exception) -> {
                    if (exception != null) {
                        log.error("Error when retrieving stockStatus from inventory-service:{}", exception.getMessage());
                        throw new InternalServerException();
                    }
                    if (stockStatus == null) {
                        log.error("Null value of stockStatus returned from inventory-service");
                        throw new InternalServerException();
                    }
                    return Arrays.stream(stockStatus).toList();
                });
    }
}

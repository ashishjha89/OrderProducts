package com.example.orderservice.repository;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.dto.InventoryStockStatus;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;

@Repository
@Slf4j
@AllArgsConstructor
public class InventoryStatusRepository {

    static final String HOST_NAME = "http://localhost";

    static final int PORT = 8082;

    private static final String INVENTORY_API_URI = HOST_NAME + ":" + PORT + "/api/inventory";

    static final String SKU_CODE_QUERY_PARAM = "skuCode";

    private final WebClient webClient;

    @NonNull
    public List<InventoryStockStatus> retrieveStocksStatus(List<String> skuCodes) throws InternalServerException {
        try {
            final var stockStatus = webClient.get()
                    .uri(INVENTORY_API_URI, uriBuilder -> uriBuilder.queryParam(SKU_CODE_QUERY_PARAM, skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryStockStatus[].class)
                    .block();
            if (stockStatus == null) {
                log.error("Null value of stockStatus returned from inventory-service");
                throw new InternalServerException();
            }
            return Arrays.stream(stockStatus).toList();
        } catch (Exception e) {
            log.error("Error when retrieving stockStatus from inventory-service:" + e.getMessage());
            throw new InternalServerException();
        }
    }
}

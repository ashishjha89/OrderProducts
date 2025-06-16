package com.orderproduct.orderservice.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
@Slf4j
@AllArgsConstructor
public class InventoryStatusRepository {

    private static final String API_PATH = "api/inventory";
    private static final String PARAM_SKU_CODE = "skuCode";

    private final WebClient.Builder webClientBuilder;
    private final String inventoryApiBaseUrl;

    private URI buildInventoryUri(List<String> skuCodes) {
        return UriComponentsBuilder.fromHttpUrl(inventoryApiBaseUrl)
                .pathSegment(API_PATH)
                .queryParam(PARAM_SKU_CODE, skuCodes)
                .build()
                .toUri();
    }

    /**
     * Circuit breaking and retry will only be triggered for
     * {@link InternalServerException}.
     * <ul>
     * <li>{@code InternalServerException} is thrown for HTTP 5xx and 429 errors
     * (server errors and rate limiting).</li>
     * <li>{@code InvalidInventoryException} is thrown for all other HTTP errors
     * (4xx except 429), as well as for null/empty inventory responses.</li>
     * <li>{@code InvalidInputException} is thrown for empty input.</li>
     * <li>Only {@code InternalServerException} participates in circuit breaker and
     * retry logic. {@code InvalidInventoryException} and
     * {@code InvalidInputException} are fast-fail and will not affect circuit
     * breaker state or trigger retries.</li>
     * </ul>
     * <p>
     * This behavior is enforced by both the code and the resilience4j configuration.
     */
    @CircuitBreaker(name = "inventory", fallbackMethod = "onInventoryServiceFailure")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    @NonNull
    public CompletableFuture<List<InventoryStockStatus>> getInventoryAvailabilityFuture(
            @NonNull List<String> skuCodes) throws InternalServerException {

        if (skuCodes.isEmpty()) {
            log.warn("Attempted to check inventory with empty SKU codes list");
            throw new InvalidInputException("SKU codes list cannot be empty");
        }
        return webClientBuilder
                .build()
                .get()
                .uri(buildInventoryUri(skuCodes))
                .retrieve()
                .onStatus(
                        status -> status.is5xxServerError() || status.value() == 429,
                        response -> {
                            log.error("Received {} from inventory service (5xx or 429)", response.statusCode());
                            return Mono.error(new InternalServerException());
                        })
                .onStatus(
                        status -> status.is4xxClientError() && status.value() != 429,
                        response -> {
                            log.error("Received {} from inventory service (4xx, not 429)", response.statusCode());
                            return Mono.error(new InvalidInventoryException());
                        })
                .bodyToMono(InventoryStockStatus[].class)
                .switchIfEmpty(
                        Mono.error(new InvalidInventoryException()))
                .flatMap(stockStatus -> {
                    if (stockStatus == null) {
                        log.error("Received null response from inventory service. SKUs: {}", skuCodes);
                        return Mono.error(new InvalidInventoryException());
                    }
                    if (stockStatus.length == 0) {
                        log.error("Received empty response from inventory service. SKUs: {}", skuCodes);
                        return Mono.error(new InvalidInventoryException());
                    }
                    return Mono.just(Arrays.stream(stockStatus).toList());
                })
                .onErrorMap(ex -> {
                    log.error("Error fetching inventory status for SKUs: {}: {}", skuCodes, ex.toString());
                    if (ex instanceof InternalServerException
                            || ex instanceof InvalidInputException
                            || ex instanceof InvalidInventoryException) {
                        return ex;
                    }
                    if (ex instanceof JsonProcessingException || ex instanceof DecodingException) {
                        return new InvalidInventoryException();
                    }
                    // For all other errors (e.g., connection failures), treat as
                    // InternalServerException
                    return new InternalServerException();
                })
                .toFuture();
    }

    @SuppressWarnings("unused")
    private CompletableFuture<List<InventoryStockStatus>> onInventoryServiceFailure(
            @NonNull List<String> skuCodes,
            RuntimeException runtimeException) {
        log.error("Circuit breaker fallback for skuCodes: {}. Error: {}", skuCodes, runtimeException.getMessage());
        CompletableFuture<List<InventoryStockStatus>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new InternalServerException());
        return failedFuture;
    }
}

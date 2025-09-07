package com.orderproduct.orderservice.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.orderproduct.orderservice.common.ApiException;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.common.OrderReservationNotAllowedException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.InventoryServiceErrorBody;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@AllArgsConstructor
public class InventoryReservationService {

    private static final String RESERVATIONS_API_PATH = "api/reservations";

    private final WebClient.Builder webClientBuilder;
    private final String inventoryApiBaseUrl;

    private URI buildReservationsUri() {
        return UriComponentsBuilder.fromHttpUrl(inventoryApiBaseUrl)
                .pathSegment(RESERVATIONS_API_PATH)
                .build()
                .toUri();
    }

    private WebClient.RequestHeadersSpec<?> buildOrderReservationWebClient(
            OrderReservationRequest orderReservationRequest) {
        return webClientBuilder
                .build()
                .post()
                .uri(buildReservationsUri())
                .bodyValue(orderReservationRequest);
    }

    /**
     * Circuit breaking and retry will only be triggered for
     * {@link InternalServerException}.
     * <ul>
     * <li>{@code InternalServerException} is thrown for HTTP 5xx and 429 errors
     * (server errors and rate limiting).</li>
     * <li>{@code InventoryNotInStockException} is thrown for HTTP 409 conflicts
     * with error code 'NOT_ENOUGH_ITEM_ERROR_CODE' indicating insufficient
     * inventory.</li>
     * <li>{@code OrderReservationNotAllowedException} is thrown for HTTP 409
     * conflicts
     * with error code 'ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE' indicating that
     * the
     * order reservation is not allowed.</li>
     * <li>{@code InvalidInventoryException} is thrown for all other HTTP errors
     * (4xx except 429 and 409), as well as for null/empty inventory responses and
     * 409 conflicts without the expected error code.</li>
     * <li>{@code InvalidInputException} is thrown for empty input.</li>
     * <li>Only {@code InternalServerException} participates in circuit breaker and
     * retry logic. {@code InventoryNotInStockException},
     * {@code InvalidInventoryException} and
     * {@code InvalidInputException} are fast-fail and will not affect circuit
     * breaker state or trigger retries.</li>
     * </ul>
     * <p>
     * This behavior is enforced by both the code and the resilience4j
     * configuration.
     */
    @CircuitBreaker(name = "inventory", fallbackMethod = "onReserveOrderFailure")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    @NonNull
    public CompletableFuture<List<InventoryAvailabilityStatus>> reserveOrder(
            @NonNull OrderReservationRequest orderReservationRequest)
            throws InternalServerException, InvalidInventoryException, InvalidInputException,
            InventoryNotInStockException {
        validateOrderReservationRequest(orderReservationRequest);
        return buildOrderReservationWebClient(orderReservationRequest)
                .retrieve()
                .onStatus(this::isServerError,
                        response -> handleServerError(orderReservationRequest.orderNumber()))
                .onStatus(status -> status.value() == 409,
                        response -> handleConflictError(response, orderReservationRequest.orderNumber()))
                .onStatus(this::isClientError,
                        response -> handleClientError(orderReservationRequest.orderNumber()))
                .bodyToMono(InventoryAvailabilityStatus[].class)
                .switchIfEmpty(Mono.error(new InvalidInventoryException()))
                .flatMap(
                        stockStatus -> validateAndTransformReserveOrderResponse(stockStatus,
                                orderReservationRequest.orderNumber()))
                .onErrorMap(ex -> mapToAppropriateException(ex, orderReservationRequest.orderNumber()))
                .toFuture();
    }

    private void validateOrderReservationRequest(OrderReservationRequest orderReservationRequest) {
        if (orderReservationRequest.itemReservationRequests().isEmpty()) {
            log.warn("Attempted to reserve products with empty item reservation requests for order: {}",
                    orderReservationRequest.orderNumber());
            throw new InvalidInputException("Item reservation requests cannot be empty");
        }
    }

    private Mono<List<InventoryAvailabilityStatus>> validateAndTransformReserveOrderResponse(
            InventoryAvailabilityStatus[] stockStatus, String orderNumber) {
        if (stockStatus == null) {
            log.error("Received null response from inventory service for order: {}", orderNumber);
            return Mono.error(new InvalidInventoryException());
        }
        if (stockStatus.length == 0) {
            log.error("Received empty response from inventory service for order: {}", orderNumber);
            return Mono.error(new InvalidInventoryException());
        }
        return Mono.just(Arrays.stream(stockStatus).toList());
    }

    @SuppressWarnings("unused")
    private CompletableFuture<List<InventoryAvailabilityStatus>> onReserveOrderFailure(
            @NonNull OrderReservationRequest orderReservationRequest,
            Throwable throwable) {
        log.error("Circuit breaker fallback triggered for order: {}. Error: {}", orderReservationRequest.orderNumber(),
                throwable.getMessage());
        CompletableFuture<List<InventoryAvailabilityStatus>> failedFuture = new CompletableFuture<>();
        if (throwable instanceof ApiException) {
            failedFuture.completeExceptionally(throwable);
        } else {
            failedFuture.completeExceptionally(new InternalServerException());
        }
        return failedFuture;
    }

    private boolean isServerError(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }

    private boolean isClientError(HttpStatusCode status) {
        return status.is4xxClientError() && status.value() != 429 && status.value() != 409;
    }

    private Mono<? extends Throwable> handleServerError(String orderNumber) {
        log.error("Received server error (5xx or 429) from inventory service for order: {}", orderNumber);
        return Mono.error(new InternalServerException());
    }

    private Mono<? extends Throwable> handleConflictError(ClientResponse response, String orderNumber) {
        log.error("Received conflict (409) from inventory service for order: {}", orderNumber);
        return response.bodyToMono(InventoryServiceErrorBody.class)
                .<Throwable>flatMap(errorBody -> {
                    if (errorBody != null && "NOT_ENOUGH_ITEM_ERROR_CODE".equals(errorBody.errorCode())) {
                        log.error("Inventory not in stock for order: {}", orderNumber);
                        return Mono.<Throwable>error(new InventoryNotInStockException(
                                errorBody.errorMessage(),
                                errorBody.unavailableProducts()));
                    } else if (errorBody != null
                            && "ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE".equals(errorBody.errorCode())) {
                        log.error("Order reservation not allowed for order: {}", orderNumber);
                        return Mono.<Throwable>error(new OrderReservationNotAllowedException());
                    } else {
                        log.error("Received unknown conflict error for order: {}", orderNumber);
                        return Mono.<Throwable>error(new InvalidInventoryException());
                    }
                })
                .onErrorResume(ex -> {
                    if (ex instanceof InventoryNotInStockException || ex instanceof InvalidInventoryException
                            || ex instanceof OrderReservationNotAllowedException) {
                        return Mono.error(ex);
                    }
                    log.error("Failed to parse conflict response body for order: {}", orderNumber);
                    return Mono.error(new InvalidInventoryException());
                });
    }

    private Mono<? extends Throwable> handleClientError(String orderNumber) {
        log.error("Received client error (4xx, not 429 or 409) from inventory service for order: {}", orderNumber);
        return Mono.error(new InvalidInventoryException());
    }

    private Throwable mapToAppropriateException(Throwable ex, String orderNumber) {
        log.error("Error reserving products for order: {}: {}", orderNumber, ex.toString());

        if (ex instanceof InternalServerException
                || ex instanceof InvalidInputException
                || ex instanceof InvalidInventoryException
                || ex instanceof InventoryNotInStockException
                || ex instanceof OrderReservationNotAllowedException) {
            return ex;
        }
        if (ex instanceof JsonProcessingException || ex instanceof DecodingException) {
            return new InvalidInventoryException();
        }
        // For other errors (e.g., connection failures), return InternalServerException
        return new InternalServerException();
    }
}
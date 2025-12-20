package com.orderproduct.orderservice.service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.orderproduct.inventoryservice.grpc.ItemReservationRequest;
import com.orderproduct.inventoryservice.grpc.ReservationServiceGrpc;
import com.orderproduct.inventoryservice.grpc.ReserveProductsRequest;
import com.orderproduct.inventoryservice.grpc.ReserveProductsResponse;
import com.orderproduct.orderservice.common.ApiException;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.common.OrderReservationNotAllowedException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class InventoryReservationGrpcClientService implements InventoryReservationService {

    private final ReservationServiceGrpc.ReservationServiceBlockingStub reservationServiceStub;

    @CircuitBreaker(name = "inventory", fallbackMethod = "onReserveOrderFailure")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    @NonNull
    @Override
    public CompletableFuture<List<InventoryAvailabilityStatus>> reserveOrder(
            @NonNull OrderReservationRequest orderReservationRequest)
            throws InternalServerException, InvalidInventoryException, InvalidInputException,
            InventoryNotInStockException {

        validateOrderReservationRequest(orderReservationRequest);

        return CompletableFuture.supplyAsync(() -> {
            try {
                ReserveProductsRequest grpcRequest = convertToGrpcRequest(orderReservationRequest);
                ReserveProductsResponse grpcResponse = reservationServiceStub.reserveProducts(grpcRequest);

                List<InventoryAvailabilityStatus> result = convertFromGrpcResponse(grpcResponse);

                log.info("gRPC:ReserveOrder - Successfully reserved {} items for order: {}",
                        result.size(), orderReservationRequest.orderNumber());

                return result;
            } catch (StatusRuntimeException e) {
                log.error("gRPC:ReserveOrder - Error for order: {}: {}",
                        orderReservationRequest.orderNumber(), e.getStatus());
                throw handleGrpcException(e, orderReservationRequest.orderNumber());
            } catch (Exception e) {
                log.error("gRPC:ReserveOrder - Unexpected error for order: {}: {}",
                        orderReservationRequest.orderNumber(), e.getMessage());
                throw new InternalServerException();
            }
        });
    }

    private void validateOrderReservationRequest(OrderReservationRequest orderReservationRequest) {
        if (orderReservationRequest.itemReservationRequests().isEmpty()) {
            log.warn("Attempted to reserve products with empty item reservation requests for order: {}",
                    orderReservationRequest.orderNumber());
            throw new InvalidInputException("Item reservation requests cannot be empty");
        }
    }

    private ReserveProductsRequest convertToGrpcRequest(OrderReservationRequest request) {
        ReserveProductsRequest.Builder builder = ReserveProductsRequest.newBuilder()
                .setOrderNumber(request.orderNumber());
        for (com.orderproduct.orderservice.dto.ItemReservationRequest item : request.itemReservationRequests()) {
            builder.addItemReservationRequests(
                    ItemReservationRequest.newBuilder()
                            .setSkuCode(item.skuCode())
                            .setQuantity(item.quantity())
                            .build());
        }
        return builder.build();
    }

    private List<InventoryAvailabilityStatus> convertFromGrpcResponse(@Nullable ReserveProductsResponse response) {
        if (response == null) {
            log.error("Received null response from gRPC inventory service");
            throw new InvalidInventoryException();
        }
        if (response.getAvailableInventoryCount() == 0) {
            log.warn("Received empty response from gRPC inventory service");
            throw new InvalidInventoryException();
        }
        return response.getAvailableInventoryList().stream()
                .map(item -> new InventoryAvailabilityStatus(
                        item.getSkuCode(),
                        item.getAvailableQuantity()))
                .collect(Collectors.toList());
    }

    private RuntimeException handleGrpcException(StatusRuntimeException e, String orderNumber) {
        Status.Code code = e.getStatus().getCode();

        if (code == Status.Code.RESOURCE_EXHAUSTED) {
            return handleResourceExhausted(e, orderNumber);
        } else if (code == Status.Code.FAILED_PRECONDITION) {
            return handleFailedPrecondition(e, orderNumber);
        } else if (code == Status.Code.INTERNAL) {
            return handleInternalError(e, orderNumber);
        } else {
            log.error("Received unexpected gRPC error code {} for order: {}", code, orderNumber);
            return new InvalidInventoryException();
        }
    }

    private RuntimeException handleResourceExhausted(StatusRuntimeException e, String orderNumber) {
        ErrorInfo errorInfo = extractErrorInfo(e);

        if (errorInfo != null && "NOT_ENOUGH_ITEM_ERROR_CODE".equals(errorInfo.getReason())) {
            log.error("Inventory not in stock for order: {}", orderNumber);
            String unavailableProductsStr = errorInfo.getMetadataMap()
                    .getOrDefault("unavailable_products", "");

            List<com.orderproduct.orderservice.dto.InventoryServiceErrorBody.UnavailableProduct> unavailableProducts = unavailableProductsStr
                    .isEmpty()
                            ? List.of()
                            : Arrays.stream(unavailableProductsStr.split(","))
                                    .map(skuCode -> new com.orderproduct.orderservice.dto.InventoryServiceErrorBody.UnavailableProduct(
                                            skuCode.trim(), 0, 0))
                                    .collect(Collectors.toList());

            return new InventoryNotInStockException(
                    e.getStatus().getDescription() != null
                            ? e.getStatus().getDescription()
                            : "Not enough stock for some products",
                    unavailableProducts);
        }

        log.error("Received RESOURCE_EXHAUSTED without expected error code for order: {}", orderNumber);
        return new InvalidInventoryException();
    }

    private RuntimeException handleFailedPrecondition(StatusRuntimeException e, String orderNumber) {
        ErrorInfo errorInfo = extractErrorInfo(e);
        if (errorInfo != null && "ORDER_RESERVATION_NOT_ALLOWED".equals(errorInfo.getReason())) {
            log.error("Order reservation not allowed for order: {}", orderNumber);
            return new OrderReservationNotAllowedException();
        }
        log.error("Received FAILED_PRECONDITION without expected error code for order: {}", orderNumber);
        return new InvalidInventoryException();
    }

    private RuntimeException handleInternalError(StatusRuntimeException e, String orderNumber) {
        ErrorInfo errorInfo = extractErrorInfo(e);
        if (errorInfo != null && "SOMETHING_WENT_WRONG_ERROR_CODE".equals(errorInfo.getReason())) {
            log.error("Internal server error from gRPC inventory service for order: {}", orderNumber);
            return new InternalServerException();
        }
        log.error("Received INTERNAL error without expected error code for order: {}", orderNumber);
        return new InternalServerException();
    }

    private ErrorInfo extractErrorInfo(StatusRuntimeException exception) {
        try {
            io.grpc.Status grpcStatus = exception.getStatus();
            com.google.rpc.Status status = StatusProto.fromStatusAndTrailers(grpcStatus, exception.getTrailers());
            if (status != null) {
                for (Any detail : status.getDetailsList()) {
                    if (detail.is(ErrorInfo.class)) {
                        try {
                            return detail.unpack(ErrorInfo.class);
                        } catch (Exception e) {
                            log.warn("Failed to unpack ErrorInfo: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract error info from gRPC exception: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unused")
    private CompletableFuture<List<InventoryAvailabilityStatus>> onReserveOrderFailure(
            @NonNull OrderReservationRequest orderReservationRequest,
            Throwable throwable) {
        log.error("Circuit breaker fallback triggered for order: {}. Error: {}",
                orderReservationRequest.orderNumber(), throwable.getMessage());

        CompletableFuture<List<InventoryAvailabilityStatus>> failedFuture = new CompletableFuture<>();
        if (throwable instanceof ApiException) {
            failedFuture.completeExceptionally(throwable);
        } else {
            failedFuture.completeExceptionally(new InternalServerException());
        }
        return failedFuture;
    }
}

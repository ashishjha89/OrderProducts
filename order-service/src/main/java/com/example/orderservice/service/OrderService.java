package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.common.InventoryNotInStockException;
import com.example.orderservice.dto.InventoryStockStatus;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.event.OrderPlacedEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.InventoryStatusRepository;
import com.example.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@AllArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final InventoryStatusRepository inventoryStatusRepository;

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private final OrderNumberGenerator orderNumberGenerator;

    private final ObservationRegistry observationRegistry;

    @NonNull
    public CompletableFuture<SavedOrder> placeOrder(
            @NonNull OrderRequest orderRequest
    ) throws InternalServerException, InventoryNotInStockException {
        return isAnyLineItemMissing(orderRequest).thenCompose(isAnyLineItemMissing -> {
            if (isAnyLineItemMissing) {
                log.info("InventoryNotInStock orderRequest:" + orderRequest);
                throw new InventoryNotInStockException();
            }
            return CompletableFuture.supplyAsync(() -> {
                SavedOrder savedOrder = saveOrder(getOrder(orderRequest));
                sendOrderPlacedEventToNotificationTopic(savedOrder);
                return savedOrder;
            });
        });
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackStockStatus")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    private CompletableFuture<Boolean> isAnyLineItemMissing(
            @NonNull OrderRequest orderRequest
    ) throws InternalServerException {
        final var skuCodesInOrder =
                orderRequest.getOrderLineItemsList().stream().map(OrderLineItemsDto::getSkuCode).toList();
        Observation inventoryServiceObservation = Observation.createNotStarted(
                "inventory-service-lookup",
                this.observationRegistry
        );
        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");
        return inventoryServiceObservation.observe(() ->
                inventoryStatusRepository
                        .getInventoryAvailabilityFuture(skuCodesInOrder)
                        .handle((stocksStatus, exception) -> {
                            if (exception instanceof RuntimeException) throw (RuntimeException) exception;
                            if (!isStockAvailableForAllSkuCodes(skuCodesInOrder, stocksStatus)) {
                                log.error("StockStatus not returned for all LineItems in Order");
                                throw new InternalServerException();
                            }
                            return !stocksStatus.stream().allMatch(InventoryStockStatus::isInStock);
                        })
        );
    }

    @SuppressWarnings("unused")
    private CompletableFuture<SavedOrder> fallbackStockStatus(
            @NonNull OrderRequest orderRequest,
            RuntimeException runtimeException
    ) throws InternalServerException, InventoryNotInStockException {
        log.error("Exception thrown by CircuitBreaker " + runtimeException.getMessage());
        if (runtimeException.getCause() instanceof InventoryNotInStockException)
            throw new InventoryNotInStockException();
        else
            throw new InternalServerException();
    }

    private SavedOrder saveOrder(Order order) throws InternalServerException {
        try {
            final var savedOrder = orderRepository.save(order);
            log.info("Order is saved Id:" + savedOrder.getId() + " orderNumber:" + savedOrder.getOrderNumber());
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            log.error("Error when saving Order:" + e.getMessage());
            throw new InternalServerException();
        }
    }

    private void sendOrderPlacedEventToNotificationTopic(SavedOrder savedOrder) {
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(savedOrder.orderNumber()));
    }

    private boolean isStockAvailableForAllSkuCodes(List<String> skuCodeList, List<InventoryStockStatus> stockStatusList) {
        return skuCodeList.stream().allMatch(skuCode -> isStockStatusAvailable(skuCode, stockStatusList));
    }

    private boolean isStockStatusAvailable(String skuCode, List<InventoryStockStatus> stockStatusList) {
        return stockStatusList.stream().anyMatch(stockStatus -> stockStatus.skuCode().equals(skuCode));
    }

    private Order getOrder(OrderRequest orderRequest) {
        return Order.builder()
                .orderNumber(orderNumberGenerator.getUniqueOrderNumber())
                .orderLineItemsList(orderRequest.getOrderLineItemsList().stream().map(this::mapToDto).toList())
                .build();
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.getPrice())
                .skuCode(orderLineItemsDto.getSkuCode())
                .quantity(orderLineItemsDto.getQuantity())
                .build();
    }
}

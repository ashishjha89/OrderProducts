package com.orderproduct.orderservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.entity.Order;
import com.orderproduct.orderservice.entity.OrderLineItems;
import com.orderproduct.orderservice.event.OrderPlacedEvent;
import com.orderproduct.orderservice.repository.InventoryStatusRepository;
import com.orderproduct.orderservice.repository.OrderRepository;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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
            @NonNull OrderRequest orderRequest) throws InternalServerException, InventoryNotInStockException {
        return isAnyLineItemMissing(orderRequest).thenCompose(isAnyLineItemMissing -> {
            if (isAnyLineItemMissing) {
                log.info("InventoryNotInStock orderRequest:{}", orderRequest);
                throw new InventoryNotInStockException();
            }
            return CompletableFuture.supplyAsync(() -> {
                // TODO: Implement outbox pattern here
                SavedOrder savedOrder = saveOrder(getOrder(orderRequest));
                sendOrderPlacedEventToNotificationTopic(savedOrder);
                return savedOrder;
            });
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

    private SavedOrder saveOrder(Order order) throws InternalServerException {
        try {
            final var savedOrder = orderRepository.save(order);
            log.info("Order is saved Id:{} orderNumber:{}", savedOrder.getId(), savedOrder.getOrderNumber());
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            log.error("Error when saving Order:{}", e.getMessage());
            throw new InternalServerException();
        }
    }

    private void sendOrderPlacedEventToNotificationTopic(SavedOrder savedOrder) {
        try {
            kafkaTemplate
                    .send("notificationTopic", new OrderPlacedEvent(savedOrder.orderNumber()))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send OrderPlacedEvent to Kafka for orderNumber:{} - {}",
                                    savedOrder.orderNumber(), ex.getMessage());
                        } else {
                            log.info("OrderPlacedEvent sent to Kafka for orderNumber:{}", savedOrder.orderNumber());
                        }
                    });
        } catch (Exception ex) {
            log.error("Exception while sending OrderPlacedEvent to Kafka for orderNumber:{} - {}",
                    savedOrder.orderNumber(), ex.getMessage());
        }
    }

    private boolean isStockAvailableForAllSkuCodes(List<String> skuCodeList,
            List<InventoryStockStatus> stockStatusList) {
        return skuCodeList.stream().allMatch(skuCode -> isStockStatusAvailable(skuCode, stockStatusList));
    }

    private boolean isStockStatusAvailable(String skuCode, List<InventoryStockStatus> stockStatusList) {
        return stockStatusList.stream().anyMatch(stockStatus -> stockStatus.skuCode().equals(skuCode));
    }

    private Order getOrder(OrderRequest orderRequest) {
        return Order.builder()
                .orderNumber(orderNumberGenerator.getUniqueOrderNumber())
                .orderLineItemsList(orderRequest.orderLineItemsList().stream().map(this::mapToDto).toList())
                .build();
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.price())
                .skuCode(orderLineItemsDto.skuCode())
                .quantity(orderLineItemsDto.quantity())
                .build();
    }
}

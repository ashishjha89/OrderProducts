package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.common.InventoryNotInStockException;
import com.example.orderservice.dto.InventoryStockStatus;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.InventoryStatusRepository;
import com.example.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Transactional
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final InventoryStatusRepository inventoryStatusRepository;

    private final OrderNumberGenerator orderNumberGenerator;

    @NonNull
    public SavedOrder placeOrder(@NonNull OrderRequest orderRequest) throws InternalServerException, InventoryNotInStockException {
        if (!areAllOrderLineItemsInStock(orderRequest)) throw new InventoryNotInStockException();

        final var order = Order.builder()
                .orderNumber(orderNumberGenerator.getUniqueOrderNumber())
                .orderLineItemsList(orderRequest.getOrderLineItemsList().stream().map(this::mapToDto).toList())
                .build();
        try {
            final var savedOrder = orderRepository.save(order);
            log.info("Order is saved Id:" + savedOrder.getId() + " orderNumber:" + savedOrder.getOrderNumber());
            return new SavedOrder(savedOrder.getId() + "", savedOrder.getOrderNumber());
        } catch (DataAccessException e) {
            log.error("Error when saving Order:" + e.getMessage());
            throw new InternalServerException();
        }
    }

    private boolean areAllOrderLineItemsInStock(@NonNull OrderRequest orderRequest) throws InternalServerException {
        final var skuCodesInOrder = orderRequest.getOrderLineItemsList().stream().map(OrderLineItemsDto::getSkuCode).toList();
        final var stocksStatus = inventoryStatusRepository.retrieveStocksStatus(skuCodesInOrder);
        if (!isStockStatusAvailableForAllSkuCodes(skuCodesInOrder, stocksStatus)) {
            log.error("StockStatus not returned for all LineItems in Order");
            throw new InternalServerException();
        }
        return stocksStatus.stream().allMatch(InventoryStockStatus::isInStock);
    }

    private boolean isStockStatusAvailableForAllSkuCodes(List<String> skuCodeList, List<InventoryStockStatus> stockStatusList) {
        return skuCodeList.stream().allMatch(skuCode -> isStockStatusAvailable(skuCode, stockStatusList));
    }

    private boolean isStockStatusAvailable(String skuCode, List<InventoryStockStatus> stockStatusList) {
        return stockStatusList.stream().anyMatch(stockStatus -> stockStatus.skuCode().equals(skuCode));
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        return OrderLineItems.builder()
                .price(orderLineItemsDto.getPrice())
                .skuCode(orderLineItemsDto.getSkuCode())
                .quantity(orderLineItemsDto.getQuantity())
                .build();
    }
}

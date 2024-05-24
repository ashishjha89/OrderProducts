package com.example.orderservice.service;

import com.example.orderservice.common.InternalServerException;
import com.example.orderservice.common.InventoryNotInStockException;
import com.example.orderservice.dto.InventoryStockStatus;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItems;
import com.example.orderservice.repository.InventoryStatusRepository;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);

    private final InventoryStatusRepository inventoryStatusRepository = mock(InventoryStatusRepository.class);

    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);

    private final OrderService orderService = new OrderService(orderRepository, inventoryStatusRepository, orderNumberGenerator);

    private final String orderNumber = "ThisIsUniqueOrderNumber";

    private final List<OrderLineItemsDto> lineItemDtoList = List.of(
            new OrderLineItemsDto("skuCode1", BigDecimal.valueOf(1000), 10),
            new OrderLineItemsDto("skuCode2", BigDecimal.valueOf(2000), 20)
    );

    private final OrderRequest orderRequest = new OrderRequest(lineItemDtoList);

    private final Order orderThatWillBePassedToRepoToSave = Order.builder()
            .orderNumber(orderNumber)
            .orderLineItemsList(getOrderLineItemsListFromDtoList(lineItemDtoList))
            .build();

    @BeforeEach
    public void setup() {
        when(orderNumberGenerator.getUniqueOrderNumber()).thenReturn(orderNumber);
    }

    @Test
    @DisplayName("placeOrder receives `OrderRequest` and converts it to `Order` and saves it to repo if all lineItems are available")
    public void placeOrder_SavesOrderToRepo_WhenStockIsAvailable() throws InternalServerException, InventoryNotInStockException, ExecutionException, InterruptedException {
        // Mock availability of stock
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("skuCode1", "skuCode2")))
                .thenReturn(List.of(
                        new InventoryStockStatus("skuCode1", true),
                        new InventoryStockStatus("skuCode2", true)
                ));

        // Initialise Order that will be returned by Repo (after saving)
        final var orderReturnedFromRepo = Order.builder()
                .id(1L)
                .orderNumber(orderThatWillBePassedToRepoToSave.getOrderNumber())
                .orderLineItemsList(orderThatWillBePassedToRepoToSave.getOrderLineItemsList())
                .build();
        when(orderRepository.save(orderThatWillBePassedToRepoToSave)).thenReturn(orderReturnedFromRepo);

        // Call method to test
        final var savedOrder = orderService.placeOrder(orderRequest).get();

        // Assert value
        verify(orderRepository).save(orderThatWillBePassedToRepoToSave);
        assertEquals("ThisIsUniqueOrderNumber", savedOrder.orderNumber());
        assertEquals("1", savedOrder.orderId());
    }

    @Test
    @DisplayName("placeOrder doesn't save order to repo if none of the stocks are found and throws InventoryNotInStockException")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenNoneStockWasPresent() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("skuCode1", "skuCode2")))
                .thenReturn(List.of(
                        new InventoryStockStatus("skuCode1", false), // Not available
                        new InventoryStockStatus("skuCode2", false) // Not available
                ));
        // Assert
        assertThrows(InventoryNotInStockException.class, () -> orderService.placeOrder(orderRequest));
    }

    @Test
    @DisplayName("placeOrder doesn't save order to repo if some of the stock was not found and throws InventoryNotInStockException")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenSomeStockIsNotAvailable() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("skuCode1", "skuCode2")))
                .thenReturn(List.of(
                        new InventoryStockStatus("skuCode1", false), // Not available
                        new InventoryStockStatus("skuCode2", true)  // Available
                ));
        // Assert
        assertThrows(InventoryNotInStockException.class, () -> orderService.placeOrder(orderRequest));
    }

    @Test
    @DisplayName("placeOrder doesn't save order to repo if some of the stock was not found and throws InternalServerException")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenStatusForSomeStockIsMissing() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("skuCode1", "skuCode2")))
                .thenReturn(List.of(
                        new InventoryStockStatus("skuCode2", true) // Note entry for skuCode1 is missing
                ));
        // Assert
        assertThrows(InternalServerException.class, () -> orderService.placeOrder(orderRequest));
    }

    @Test
    @DisplayName("placeOrder forwards InternalServerException from InventoryStatusRepository")
    public void placeOrder_ForwardsInternalServerException_FromInventoryStatusRepository() throws InternalServerException {
        // Mock throwing of exception (one of the child of DataAccessException) from repo
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("skuCode1", "skuCode2")))
                .thenThrow(new InternalServerException());
        // Assert
        assertThrows(InternalServerException.class, () -> orderService.placeOrder(orderRequest));
    }

    @Test
    @DisplayName("placeOrder throws InternalServerException when Repo throws DataAccessException")
    public void placeOrder_ThrowsInternalServerException_WhenDBThrowsError() {
        // Mock throwing of exception (one of the child of DataAccessException) from repo
        when(orderRepository.save(orderThatWillBePassedToRepoToSave))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));
        // Assert
        assertThrows(InternalServerException.class, () -> orderService.placeOrder(orderRequest));
    }

    private List<OrderLineItems> getOrderLineItemsListFromDtoList(List<OrderLineItemsDto> lineItemsDtoList) {
        return lineItemsDtoList.stream().map(orderLineItemsDto ->
                OrderLineItems.builder()
                        .skuCode(orderLineItemsDto.getSkuCode())
                        .price(orderLineItemsDto.getPrice())
                        .quantity(orderLineItemsDto.getQuantity())
                        .build()
        ).toList();
    }
}

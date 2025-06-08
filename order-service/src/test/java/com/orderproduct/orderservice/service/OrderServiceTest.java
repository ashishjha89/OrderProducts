package com.orderproduct.orderservice.service;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryStockStatus;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.event.OrderPlacedEvent;
import com.orderproduct.orderservice.entity.Order;
import com.orderproduct.orderservice.entity.OrderLineItems;
import com.orderproduct.orderservice.repository.InventoryStatusRepository;
import com.orderproduct.orderservice.repository.OrderRepository;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);

    private final InventoryStatusRepository inventoryStatusRepository = mock(InventoryStatusRepository.class);

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate = mock(KafkaTemplate.class);

    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);

    private final ObservationRegistry observationRegistry = ObservationRegistry.create();

    private final OrderService orderService = new OrderService(
            orderRepository,
            inventoryStatusRepository,
            kafkaTemplate,
            orderNumberGenerator,
            observationRegistry
    );

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
    @DisplayName("`placeOrder()` converts OrderRequest to Order and saves it to repo if all lineItems are available")
    public void placeOrder_SavesOrderToRepo_WhenStockIsAvailable() throws InternalServerException, InventoryNotInStockException, ExecutionException, InterruptedException {
        // Mock availability of stock
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(
                        CompletableFuture.completedFuture(List.of(
                                new InventoryStockStatus("skuCode1", true),
                                new InventoryStockStatus("skuCode2", true)
                        )));

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
        verify(kafkaTemplate).send("notificationTopic", new OrderPlacedEvent("ThisIsUniqueOrderNumber"));
    }

    @Test
    @DisplayName("`placeOrder()` throws InventoryNotInStockException if none of the stocks are found")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenNoneStockWasPresent() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(
                        CompletableFuture.completedFuture(List.of(
                                new InventoryStockStatus("skuCode1", false), // Not available
                                new InventoryStockStatus("skuCode2", false) // Not available
                        )));
        // Assert
        assertInventoryNotInStockExceptionIsThrown();
    }

    @Test
    @DisplayName("`placeOrder()` throws InventoryNotInStockException if some of the stock was not available")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenSomeStockIsNotAvailable() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(
                        CompletableFuture.completedFuture(List.of(
                                new InventoryStockStatus("skuCode1", false), // Not available
                                new InventoryStockStatus("skuCode2", true)  // Available
                        )));
        // Assert
        assertInventoryNotInStockExceptionIsThrown();
    }

    @Test
    @DisplayName("`placeOrder()` throws InternalServerException if some of the stock's entry was not found")
    public void placeOrder_ThrowsInventoryNotInStockException_WhenStatusForSomeStockIsMissing() throws InternalServerException {
        // Mock availability of stock
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(
                        CompletableFuture.completedFuture(List.of(
                                new InventoryStockStatus("skuCode2", true) // Note entry for skuCode1 is missing
                        )));
        // Assert
        assertInternalServerExceptionIsThrown();
    }

    @Test
    @DisplayName("`placeOrder()` forwards InternalServerException from InventoryStatusRepository")
    public void placeOrder_ForwardsInternalServerException_FromInventoryStatusRepository() throws InternalServerException {
        // Mock throwing of exception (one of the children of DataAccessException) from repo
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));
        // Assert
        assertInternalServerExceptionIsThrown();
    }

    @Test
    @DisplayName("`placeOrder()` throws InternalServerException when Repo throws DataAccessException")
    public void placeOrder_ThrowsInternalServerException_WhenDBThrowsError() {
        // Mock availability of stock
        when(inventoryStatusRepository.getInventoryAvailabilityFuture(List.of("skuCode1", "skuCode2")))
                .thenReturn(
                        CompletableFuture.completedFuture(List.of(
                                new InventoryStockStatus("skuCode1", true),
                                new InventoryStockStatus("skuCode2", true)
                        )));
        // Mock throwing of exception (one of the child of DataAccessException) from repo
        when(orderRepository.save(orderThatWillBePassedToRepoToSave))
                .thenThrow(new DataAccessResourceFailureException("Child class of DataAccessException"));
        // Assert
        assertInternalServerExceptionIsThrown();
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

    private void assertInventoryNotInStockExceptionIsThrown() {
        ExecutionException executionException = assertThrows(
                ExecutionException.class,
                () -> orderService.placeOrder(orderRequest).get()
        );
        Throwable cause = executionException.getCause();
        assertNotNull(cause);
        assertEquals(InventoryNotInStockException.class, cause.getClass());
    }

    private void assertInternalServerExceptionIsThrown() {
        ExecutionException executionException = assertThrows(
                ExecutionException.class,
                () -> orderService.placeOrder(orderRequest).get()
        );
        Throwable cause = executionException.getCause();
        assertNotNull(cause);
        assertEquals(InternalServerException.class, cause.getClass());
    }
}

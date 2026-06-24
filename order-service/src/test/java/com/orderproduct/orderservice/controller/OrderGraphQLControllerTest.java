package com.orderproduct.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.config.GraphQLConfig;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.service.OrderService;

import graphql.ErrorType;

@GraphQlTest(OrderGraphQLController.class)
@Import({OrderGraphQLControllerTest.MockedServiceConfig.class, GraphQLConfig.class, GraphQLExceptionHandler.class})
class OrderGraphQLControllerTest {

    @Autowired
    GraphQlTester graphQlTester;

    @Autowired
    OrderService orderService;

    @TestConfiguration
    static class MockedServiceConfig {
        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }
    }

    @BeforeEach
    void setUp() {
        reset(orderService);
    }

    private static final String PLACE_ORDER_MUTATION = """
            mutation {
                placeOrder(input: {
                    orderLineItems: [{skuCode: "SKU-1", price: 1200, quantity: 2}]
                }) {
                    orderId
                    orderNumber
                }
            }
            """;

    @Test
    @DisplayName("should return orderId and orderNumber when placeOrder mutation is executed with valid input")
    void placeOrder_returnsPlacedOrder() {
        SavedOrder savedOrder = new SavedOrder("order-id-1", "ORD-001");
        OrderRequest expectedRequest = new OrderRequest(
                List.of(new OrderLineItemsDto("SKU-1", BigDecimal.valueOf(1200), 2)));
        when(orderService.placeOrder(expectedRequest))
                .thenReturn(CompletableFuture.completedFuture(savedOrder));

        graphQlTester.document(PLACE_ORDER_MUTATION)
                .execute()
                .path("placeOrder.orderId").entity(String.class).isEqualTo("order-id-1")
                .path("placeOrder.orderNumber").entity(String.class).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("should return ValidationError with BAD_USER_INPUT when placeOrder mutation is called with empty orderLineItems")
    void placeOrder_emptyLineItems_returnsValidationError() {
        graphQlTester.document("""
                        mutation {
                            placeOrder(input: { orderLineItems: [] }) {
                                orderId
                                orderNumber
                            }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.ValidationError);
                    assertThat(errors.getFirst().getExtensions().get("code")).isEqualTo("BAD_USER_INPUT");
                });
    }

    @Test
    @DisplayName("should return ValidationError with INVENTORY_NOT_IN_STOCK when service throws InventoryNotInStockException")
    void placeOrder_inventoryNotInStock_returnsValidationError() {
        when(orderService.placeOrder(any()))
                .thenReturn(CompletableFuture.failedFuture(new InventoryNotInStockException()));

        graphQlTester.document(PLACE_ORDER_MUTATION)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.ValidationError);
                    assertThat(errors.getFirst().getExtensions().get("code"))
                            .isEqualTo("INVENTORY_NOT_IN_STOCK");
                });
    }

    @Test
    @DisplayName("should return DataFetchingException with INTERNAL_SERVER_ERROR when service throws InternalServerException")
    void placeOrder_serviceThrows_returnsInternalServerError() {
        when(orderService.placeOrder(any()))
                .thenReturn(CompletableFuture.failedFuture(new InternalServerException()));

        graphQlTester.document(PLACE_ORDER_MUTATION)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.DataFetchingException);
                    assertThat(errors.getFirst().getExtensions().get("code"))
                            .isEqualTo("INTERNAL_SERVER_ERROR");
                });
    }
}

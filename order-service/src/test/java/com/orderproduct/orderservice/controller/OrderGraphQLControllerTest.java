package com.orderproduct.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.orderproduct.orderservice.dto.ProductStub;
import com.orderproduct.orderservice.dto.SavedOrderLineItem;

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

    // Federation: _service { sdl }

    @Test
    @DisplayName("should return SDL containing PlacedOrder type and @key directive when _service query is executed")
    void serviceQuery_returnsSdlWithPlacedOrderKeyDirective() {
        graphQlTester.document("""
                        query {
                            _service {
                                sdl
                            }
                        }
                        """)
                .execute()
                .path("_service.sdl").entity(String.class)
                .satisfies(sdl -> {
                    assertThat(sdl).contains("PlacedOrder");
                    assertThat(sdl).contains("@key");
                    assertThat(sdl).contains("@key(fields: \"orderNumber\")");
                });
    }

    // Federation: _entities

    @Test
    @DisplayName("should return PlacedOrder when _entities is called with a PlacedOrder representation and order exists")
    void entitiesQuery_placedOrderRepresentation_returnsPlacedOrder() {
        SavedOrder savedOrder = new SavedOrder("1", "ORD-001");
        when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(Optional.of(savedOrder));

        graphQlTester.document("""
                        query {
                            _entities(representations: [{__typename: "PlacedOrder", orderNumber: "ORD-001"}]) {
                                ... on PlacedOrder {
                                    orderId
                                    orderNumber
                                }
                            }
                        }
                        """)
                .execute()
                .path("_entities[0].orderId").entity(String.class).isEqualTo("1")
                .path("_entities[0].orderNumber").entity(String.class).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("should return null when _entities is called with a PlacedOrder representation but order does not exist")
    void entitiesQuery_orderNotFound_returnsNull() {
        when(orderService.getOrderByOrderNumber("UNKNOWN")).thenReturn(Optional.empty());

        graphQlTester.document("""
                        query {
                            _entities(representations: [{__typename: "PlacedOrder", orderNumber: "UNKNOWN"}]) {
                                ... on PlacedOrder {
                                    orderId
                                }
                            }
                        }
                        """)
                .execute()
                .path("_entities[0]").valueIsNull();
    }

    @Test
    @DisplayName("should resolve multiple representations independently, returning each PlacedOrder or null")
    void entitiesQuery_multipleRepresentations_resolvesEachIndependently() {
        SavedOrder savedOrder = new SavedOrder("1", "ORD-001");
        when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(Optional.of(savedOrder));
        when(orderService.getOrderByOrderNumber("UNKNOWN")).thenReturn(Optional.empty());

        graphQlTester.document("""
                        query {
                            _entities(representations: [
                                {__typename: "PlacedOrder", orderNumber: "ORD-001"},
                                {__typename: "PlacedOrder", orderNumber: "UNKNOWN"}
                            ]) {
                                ... on PlacedOrder {
                                    orderId
                                    orderNumber
                                }
                            }
                        }
                        """)
                .execute()
                .path("_entities[0].orderNumber").entity(String.class).isEqualTo("ORD-001")
                .path("_entities[1]").valueIsNull();
    }

    @Test
    @DisplayName("should return DataFetchingException with INTERNAL_SERVER_ERROR when _entities query and service throws InternalServerException")
    void entitiesQuery_serviceThrows_returnsInternalServerError() {
        when(orderService.getOrderByOrderNumber("ORD-001")).thenThrow(new InternalServerException());

        graphQlTester.document("""
                        query {
                            _entities(representations: [{__typename: "PlacedOrder", orderNumber: "ORD-001"}]) {
                                ... on PlacedOrder {
                                    orderId
                                }
                            }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.DataFetchingException);
                    assertThat(errors.getFirst().getExtensions().get("code"))
                            .isEqualTo("INTERNAL_SERVER_ERROR");
                });
    }

    // lineItems field resolver (@SchemaMapping on PlacedOrder.lineItems)

    @Test
    @DisplayName("should return lineItems with product stubs when placeOrder mutation includes lineItems")
    void placeOrder_withLineItems_returnsLineItemsWithProductStub() {
        SavedOrder savedOrder = new SavedOrder("1", "ORD-001");
        when(orderService.placeOrder(any())).thenReturn(CompletableFuture.completedFuture(savedOrder));
        when(orderService.getLineItemsByOrderNumber("ORD-001")).thenReturn(List.of(
                new SavedOrderLineItem("samsung-s10", BigDecimal.valueOf(100), 1, new ProductStub("samsung-s10"))));

        graphQlTester.document("""
                        mutation {
                            placeOrder(input: {
                                orderLineItems: [{skuCode: "samsung-s10", price: 100, quantity: 1}]
                            }) {
                                orderId
                                orderNumber
                                lineItems {
                                    skuCode
                                    price
                                    quantity
                                    product {
                                        skuCode
                                    }
                                }
                            }
                        }
                        """)
                .execute()
                .path("placeOrder.lineItems[0].skuCode").entity(String.class).isEqualTo("samsung-s10")
                .path("placeOrder.lineItems[0].price").entity(BigDecimal.class).isEqualTo(BigDecimal.valueOf(100))
                .path("placeOrder.lineItems[0].quantity").entity(Integer.class).isEqualTo(1)
                .path("placeOrder.lineItems[0].product.skuCode").entity(String.class).isEqualTo("samsung-s10");
    }

    @Test
    @DisplayName("should return empty lineItems list when order has no line items")
    void placeOrder_orderWithNoLineItems_returnsEmptyLineItemsList() {
        SavedOrder savedOrder = new SavedOrder("1", "ORD-001");
        when(orderService.placeOrder(any())).thenReturn(CompletableFuture.completedFuture(savedOrder));
        when(orderService.getLineItemsByOrderNumber("ORD-001")).thenReturn(List.of());

        graphQlTester.document("""
                        mutation {
                            placeOrder(input: {
                                orderLineItems: [{skuCode: "SKU-1", price: 100, quantity: 1}]
                            }) {
                                lineItems {
                                    skuCode
                                }
                            }
                        }
                        """)
                .execute()
                .path("placeOrder.lineItems").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("should return DataFetchingException when lineItems resolver throws InternalServerException")
    void placeOrder_lineItemsServiceThrows_returnsInternalServerError() {
        SavedOrder savedOrder = new SavedOrder("1", "ORD-001");
        when(orderService.placeOrder(any())).thenReturn(CompletableFuture.completedFuture(savedOrder));
        when(orderService.getLineItemsByOrderNumber("ORD-001")).thenThrow(new InternalServerException());

        graphQlTester.document("""
                        mutation {
                            placeOrder(input: {
                                orderLineItems: [{skuCode: "SKU-1", price: 100, quantity: 1}]
                            }) {
                                lineItems {
                                    skuCode
                                }
                            }
                        }
                        """)
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

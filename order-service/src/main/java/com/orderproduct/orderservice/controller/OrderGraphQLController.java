package com.orderproduct.orderservice.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.graphql.data.method.annotation.SchemaMapping;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.orderproduct.orderservice.common.BadRequestException;
import com.orderproduct.orderservice.dto.FederationServiceSdl;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.PlaceOrderInput;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.dto.SavedOrderLineItem;
import com.orderproduct.orderservice.service.OrderService;

import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class OrderGraphQLController {

    private final OrderService orderService;

    public OrderGraphQLController(OrderService orderService) {
        this.orderService = orderService;
    }

    @MutationMapping
    public CompletableFuture<SavedOrder> placeOrder(@Argument PlaceOrderInput input) {
        log.info("GraphQL mutation: placeOrder");
        if (input == null || input.orderLineItems() == null || input.orderLineItems().isEmpty()) {
            throw new BadRequestException();
        }
        OrderRequest orderRequest = new OrderRequest(
                input.orderLineItems().stream()
                        .map(item -> new OrderLineItemsDto(item.skuCode(), item.price(), item.quantity()))
                        .toList());
        return orderService.placeOrder(orderRequest);
    }

    // Resolves lineItems on PlacedOrder — Spring calls this only when lineItems is in the query.
    // The source SavedOrder carries the orderNumber needed to load the line items from the DB.
    @SchemaMapping(typeName = "PlacedOrder", field = "lineItems")
    public List<SavedOrderLineItem> lineItems(SavedOrder savedOrder) {
        log.info("Resolving lineItems for order: {}", savedOrder.orderNumber());
        return orderService.getLineItemsByOrderNumber(savedOrder.orderNumber());
    }

    // Federation: the router calls _service { sdl } at startup to discover this subgraph's schema.
    @QueryMapping(name = "_service")
    public FederationServiceSdl service() {
        return new FederationServiceSdl(SUBGRAPH_SDL);
    }

    // Federation: the router calls _entities with a list of representations (e.g. [{__typename:"PlacedOrder", orderNumber:"ORD-001"}])
    // to hydrate order stubs referenced by other subgraphs. We resolve each by its key field.
    @SuppressWarnings("unchecked")
    @QueryMapping(name = "_entities")
    public List<Object> entities(DataFetchingEnvironment env) {
        List<Map<String, Object>> representations = env.getArgument("representations");
        if (representations == null) return List.of();
        log.info("GraphQL query: _entities — resolving {} representations", representations.size());
        List<Object> results = new ArrayList<>(representations.size());
        for (Map<String, Object> representation : representations) {
            if ("PlacedOrder".equals(representation.get("__typename"))) {
                String orderNumber = (String) representation.get("orderNumber");
                if (orderNumber != null) {
                    results.add(orderService.getOrderByOrderNumber(orderNumber).orElse(null));
                } else {
                    results.add(null);
                }
            } else {
                results.add(null);
            }
        }
        return results;
    }

    // The SDL advertised to the router — read from schema.graphqls (the single source of truth).
    // Excludes federation built-ins (_entities, _service, _Entity, _Service, _Any) which live in
    // federation.graphqls and are loaded by Spring at runtime but must not be sent to the router.
    private static final String SUBGRAPH_SDL = readSubgraphSdl();

    private static String readSubgraphSdl() {
        try (var stream = OrderGraphQLController.class.getResourceAsStream("/graphql/schema.graphqls")) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read schema.graphqls", e);
        }
    }
}

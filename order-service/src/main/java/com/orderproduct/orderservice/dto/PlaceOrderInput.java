package com.orderproduct.orderservice.dto;

import java.util.List;

public record PlaceOrderInput(List<OrderLineItemInput> orderLineItems) {
}

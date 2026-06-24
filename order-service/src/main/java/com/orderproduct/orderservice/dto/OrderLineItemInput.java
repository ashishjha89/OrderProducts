package com.orderproduct.orderservice.dto;

import java.math.BigDecimal;

public record OrderLineItemInput(String skuCode, BigDecimal price, Integer quantity) {
}

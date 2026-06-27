package com.orderproduct.orderservice.dto;

import java.math.BigDecimal;

public record SavedOrderLineItem(String skuCode, BigDecimal price, Integer quantity, ProductStub product) {}

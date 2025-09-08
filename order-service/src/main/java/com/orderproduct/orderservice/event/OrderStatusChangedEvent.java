package com.orderproduct.orderservice.event;

public record OrderStatusChangedEvent(String orderNumber, String status) {

}


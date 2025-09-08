package com.orderproduct.inventoryservice.kafka;

public record OrderStatusChangedEvent(String orderNumber, String status) {

}

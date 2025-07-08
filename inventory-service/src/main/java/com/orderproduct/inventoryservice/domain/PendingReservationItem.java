package com.orderproduct.inventoryservice.domain;

import com.orderproduct.inventoryservice.entity.Reservation;

public record PendingReservationItem(String skuCode, Reservation reservation) {

}

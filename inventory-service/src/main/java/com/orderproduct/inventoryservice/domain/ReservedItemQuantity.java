package com.orderproduct.inventoryservice.domain;

import com.orderproduct.inventoryservice.entity.Reservation;

public record ReservedItemQuantity(String skuCode, int quantity) {

    public static ReservedItemQuantity fromReservation(Reservation reservation) {
        return new ReservedItemQuantity(reservation.getSkuCode(), reservation.getReservedQuantity());
    }

}

package com.orderproduct.inventoryservice.common.exception;

import org.springframework.http.HttpStatus;

public class OrderReservationNotAllowedException extends ApiException {

    public OrderReservationNotAllowedException(String orderNumbere) {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE,
                ErrorComponent.orderReservationNotAllowedMsg);
    }
}

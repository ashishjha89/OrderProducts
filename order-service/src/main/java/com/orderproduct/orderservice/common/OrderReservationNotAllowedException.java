package com.orderproduct.orderservice.common;

import org.springframework.http.HttpStatus;

public class OrderReservationNotAllowedException extends ApiException {

    public OrderReservationNotAllowedException() {
        super(HttpStatus.CONFLICT,
                ErrorComponent.ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE,
                ErrorComponent.orderReservationNotAllowedMsg);
    }
}

package com.orderproduct.orderservice.common;

public class ErrorComponent {

    public static final String SOMETHING_WENT_WRONG_ERROR_CODE = "SOMETHING_WENT_WRONG";

    public static final String BAD_REQUEST_ERROR_CODE = "BAD_REQUEST";

    public static final String INVENTORY_NOT_IN_STOCK_ERROR_CODE = "INVENTORY_NOT_IN_STOCK";

    public static final String ORDER_RESERVATION_NOT_ALLOWED_ERROR_CODE = "ORDER_RESERVATION_NOT_ALLOWED";

    public static final String somethingWentWrongMsg = "Sorry, something went wrong.";

    public static final String badRequestMsg = "This is an incorrect request-body.";

    public static final String inventoryNotInStockMsg = "This product is not in stock.";

    public static final String orderReservationNotAllowedMsg = "Reservation is not allowed for this order, as the order is not in pending state.";

}

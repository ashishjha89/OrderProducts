package com.example.orderservice.common;

public class ErrorComponent {

    public static final String SOMETHING_WENT_WRONG = "SOMETHING_WENT_WRONG";

    public static final String BAD_REQUEST = "BAD_REQUEST";

    public static final String INVENTORY_NOT_IN_STOCK = "INVENTORY_NOT_IN_STOCK";

    public static final String somethingWentWrongMsg = "Sorry, something went wrong. Try again after some time";

    public static final String badRequestMsg = "This is an incorrect request-body";

    public static final String inventoryNotInStockMsg = "This product is not in stock";

    public static final ErrorBody internalServerError = new ErrorBody(
            SOMETHING_WENT_WRONG,
            somethingWentWrongMsg
    );

    public static final ErrorBody badRequestError = new ErrorBody(
            BAD_REQUEST,
            badRequestMsg
    );

    public static final ErrorBody inventoryNotInStockError = new ErrorBody(
            INVENTORY_NOT_IN_STOCK,
            inventoryNotInStockMsg
    );

}

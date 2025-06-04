package com.orderproduct.inventoryservice.common;

public class ErrorComponent {

    public static final String SOMETHING_WENT_WRONG = "SOMETHING_WENT_WRONG";

    public static final String BAD_REQUEST = "BAD_REQUEST";

    public static final String somethingWentWrongMsg = "Sorry, something went wrong.";

    public static final String badRequestMsg = "This is an incorrect request-body";

    public static final ErrorBody internalServerError = new ErrorBody(
            SOMETHING_WENT_WRONG,
            somethingWentWrongMsg
    );

    public static final ErrorBody badRequestError = new ErrorBody(
            BAD_REQUEST,
            badRequestMsg
    );

}

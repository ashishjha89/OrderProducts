package com.orderproduct.inventoryservice.common.exception;

public class ErrorComponent {

    public static final String SOMETHING_WENT_WRONG_ERROR_CODE = "SOMETHING_WENT_WRONG";

    public static final String BAD_REQUEST_ERROR_CODE = "BAD_REQUEST";

    public static final String NOT_FOUND_ERROR_CODE = "NOT_FOUND";

    public static final String DUPLICATE_SKU_ERROR_CODE = "DUPLICATE_SKU_CODE";

    public static final String NOT_ENOUGH_ITEM_ERROR_CODE = "NOT_ENOUGH_ITEM_ERROR_CODE";

    public static final String somethingWentWrongMsg = "Sorry, something went wrong.";

    public static final String badRequestMsg = "This is an incorrect request-body.";

    public static final String duplicateSkuCodeMsg = "Inventory with this SKU code already exists.";

    public static final String notFoundMsg = "Resource not found.";

    public static final String notEnoughStockMsg = "Not enough stock for some products";

}
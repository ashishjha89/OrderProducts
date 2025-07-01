package com.orderproduct.inventoryservice.common;

import java.util.List;

import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;

public class ErrorBodyWithUnavailableProducts {
    private final String errorCode;
    private final String errorMessage;
    private final List<UnavailableProduct> unavailableProducts;

    public ErrorBodyWithUnavailableProducts(String errorCode, String message,
            List<UnavailableProduct> unavailableProducts) {
        this.errorCode = errorCode;
        this.errorMessage = message;
        this.unavailableProducts = unavailableProducts;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<UnavailableProduct> getUnavailableProducts() {
        return unavailableProducts;
    }
}
package com.orderproduct.inventoryservice.common;

import org.springframework.http.HttpStatus;

public class DuplicateSkuCodeException extends ApiException {

    public DuplicateSkuCodeException() {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.DUPLICATE_SKU_ERROR_CODE,
                ErrorComponent.duplicateSkuCodeMsg);
    }
}

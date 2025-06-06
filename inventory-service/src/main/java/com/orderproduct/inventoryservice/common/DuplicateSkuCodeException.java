package com.orderproduct.inventoryservice.common;

import org.springframework.http.HttpStatus;

public class DuplicateSkuCodeException extends ApiException {

    public DuplicateSkuCodeException() {
        super(ErrorComponent.DUPLICATE_SKU_CODE, HttpStatus.CONFLICT, ErrorComponent.duplicateSkuCodeMsg);
    }
}

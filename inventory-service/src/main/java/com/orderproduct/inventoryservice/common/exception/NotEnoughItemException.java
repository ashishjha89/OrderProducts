package com.orderproduct.inventoryservice.common.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class NotEnoughItemException extends ApiException {

    @NonNull
    private final List<UnavailableProduct> unavailableProducts;

    public NotEnoughItemException(List<UnavailableProduct> unavailableProducts) {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.NOT_ENOUGH_ITEM_ERROR_CODE,
                ErrorComponent.notEnoughStockMsg);
        this.unavailableProducts = unavailableProducts;
    }
}

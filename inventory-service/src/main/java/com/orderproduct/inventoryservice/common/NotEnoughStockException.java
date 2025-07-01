package com.orderproduct.inventoryservice.common;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class NotEnoughStockException extends ApiException {

    @NonNull
    private final List<UnavailableProduct> unavailableProducts;

    public NotEnoughStockException(List<UnavailableProduct> unavailableProducts) {
        super(
                HttpStatus.CONFLICT,
                ErrorComponent.NOT_ENOUGH_STOCK_ERROR_CODE,
                ErrorComponent.notEnoughStockMsg);
        this.unavailableProducts = unavailableProducts;
    }
}

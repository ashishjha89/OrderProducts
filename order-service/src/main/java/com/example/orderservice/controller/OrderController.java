package com.example.orderservice.controller;

import com.example.orderservice.common.*;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.SavedOrder;
import com.example.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/order")
@Slf4j
@SuppressWarnings("unused")
public record OrderController(OrderService orderService) {

    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "OK",
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = SavedOrder.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "errorCode:" + ErrorComponent.BAD_REQUEST + " errorMessage:" + ErrorComponent.badRequestMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "errorCode:" + ErrorComponent.INVENTORY_NOT_IN_STOCK + " errorMessage:" + ErrorComponent.inventoryNotInStockMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "errorCode:" + ErrorComponent.SOMETHING_WENT_WRONG + " errorMessage:" + ErrorComponent.somethingWentWrongMsg,
                            content = {
                                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                            }
                    )
            }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuppressWarnings("unused")
    public CompletableFuture<SavedOrder> placeOrder(
            @RequestBody OrderRequest orderRequest
    ) throws BadRequestException, InternalServerException, InventoryNotInStockException {
        log.info("POST:/api/order");
        if (orderRequest == null
                || orderRequest.getOrderLineItemsList() == null
                || orderRequest.getOrderLineItemsList().isEmpty()
        ) {
            log.error("BadRequestException because POST:/api/order is called with invalid OrderRequest orderRequest:" + orderRequest);
            throw new BadRequestException();
        }
        return orderService.placeOrder(orderRequest);
    }
}

package com.orderproduct.orderservice.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orderproduct.orderservice.common.BadRequestException;
import com.orderproduct.orderservice.common.ErrorBody;
import com.orderproduct.orderservice.common.ErrorComponent;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.dto.SavedOrder;
import com.orderproduct.orderservice.service.OrderService;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/order")
@Slf4j
public class OrderController {

        private final OrderService orderService;

        public OrderController(OrderService orderService) {
                this.orderService = orderService;
        }

        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "OK", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = SavedOrder.class))
                        }),
                        @ApiResponse(responseCode = "400", description = "errorCode:"
                                        + ErrorComponent.BAD_REQUEST_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.badRequestMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        }),
                        @ApiResponse(responseCode = "400", description = "errorCode:"
                                        + ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.inventoryNotInStockMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public CompletableFuture<SavedOrder> placeOrder(
                        @RequestBody OrderRequest orderRequest)
                        throws BadRequestException, InternalServerException, InventoryNotInStockException {
                log.info("POST:/api/order");
                if (orderRequest == null
                                || orderRequest.orderLineItemsList() == null
                                || orderRequest.orderLineItemsList().isEmpty()) {
                        log.warn("Bad request: Invalid order request received. Order request: {}", orderRequest);
                        throw new BadRequestException();
                }
                return orderService.placeOrder(orderRequest);
        }
}

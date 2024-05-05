package com.example.orderservice.controller;

import com.example.orderservice.common.BadRequestException;
import com.example.orderservice.common.ErrorBody;
import com.example.orderservice.common.ErrorComponent;
import com.example.orderservice.common.InternalServerException;
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
    public SavedOrder placeOrder(@RequestBody OrderRequest orderRequest) throws BadRequestException, InternalServerException {
        if (orderRequest == null
                || orderRequest.getOrderLineItemsList() == null
                || orderRequest.getOrderLineItemsList().isEmpty()
        ) throw new BadRequestException();

        return orderService.placeOrder(orderRequest);
    }
}

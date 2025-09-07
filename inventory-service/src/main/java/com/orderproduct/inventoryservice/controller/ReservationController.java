package com.orderproduct.inventoryservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orderproduct.inventoryservice.common.exception.ErrorBody;
import com.orderproduct.inventoryservice.common.exception.ErrorBodyWithUnavailableProducts;
import com.orderproduct.inventoryservice.common.exception.ErrorComponent;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.ReservationStateUpdateResponse;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Handles reservation-related endpoints: reserving products for orders
@RestController
@RequestMapping("/api/reservations")
@Slf4j
@AllArgsConstructor
public class ReservationController {

        private final ReservationManagementService reservationManagementService;

        /**
         * Reserve products for an order if available.
         * Endpoint: POST /api/reservations
         */
        @PostMapping
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK - Products reserved successfully", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AvailableInventoryResponse.class)))
                        }),
                        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        }),
                        @ApiResponse(responseCode = "409", description = "errorCode:"
                                        + ErrorComponent.NOT_ENOUGH_ITEM_ERROR_CODE
                                        + " errorMessage:" + ErrorComponent.notEnoughStockMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBodyWithUnavailableProducts.class))
                                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public List<AvailableInventoryResponse> reserveProducts(@Valid @RequestBody OrderReservationRequest request)
                        throws InternalServerException {
                log.info("POST:/api/reservations - Reserving products for order: {}", request.orderNumber());
                return reservationManagementService.reserveProductsIfAvailable(request);
        }

        /**
         * Fulfill an order by updating all its reservations to FULFILLED state.
         * Endpoint: POST /api/reservations/{orderNumber}/fulfill
         */
        @PostMapping("/{orderNumber}/fulfill")
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK - Order fulfilled successfully", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationStateUpdateResponse.class))
                        }),
                        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        }),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        })
        })
        public ReservationStateUpdateResponse fulfillOrder(@PathVariable @NotBlank String orderNumber)
                        throws InternalServerException {
                log.info("POST:/api/reservations/{}/fulfill - Fulfilling order", orderNumber);
                ReservationStateUpdateRequest request = new ReservationStateUpdateRequest(
                                orderNumber,
                                ReservationState.FULFILLED);
                return reservationManagementService.updateReservationState(request);
        }

        /**
         * Fulfill an order by updating all its reservations to CANCELLED state.
         * Endpoint: POST /api/reservations/{orderNumber}/cancel
         */
        @PostMapping("/{orderNumber}/cancel")
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK - Order fulfilled successfully", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationStateUpdateResponse.class))
                        }),
                        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        }),
                        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        })
        })
        public ReservationStateUpdateResponse cancelOrder(@PathVariable @NotBlank String orderNumber)
                        throws InternalServerException {
                log.info("POST:/api/reservations/{}/cancel - Cancelling order", orderNumber);
                ReservationStateUpdateRequest request = new ReservationStateUpdateRequest(
                                orderNumber,
                                ReservationState.CANCELLED);
                return reservationManagementService.updateReservationState(request);
        }
}
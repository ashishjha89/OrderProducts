package com.orderproduct.inventoryservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.orderproduct.inventoryservice.common.ErrorBody;
import com.orderproduct.inventoryservice.common.ErrorComponent;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.request.CreateInventoryRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.service.InventoryAvailabilityService;
import com.orderproduct.inventoryservice.service.InventoryManagementService;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/inventory")
@Slf4j
public class InventoryController {

        private final InventoryAvailabilityService inventoryAvailabilityService;
        private final InventoryManagementService inventoryManagementService;
        private final ReservationManagementService reservationManagementService;

        public InventoryController(
                        InventoryAvailabilityService inventoryAvailabilityService,
                        InventoryManagementService inventoryManagementService,
                        ReservationManagementService reservationManagementService) {
                this.inventoryAvailabilityService = inventoryAvailabilityService;
                this.inventoryManagementService = inventoryManagementService;
                this.reservationManagementService = reservationManagementService;
        }

        @GetMapping
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AvailableInventoryResponse.class)))
                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public List<AvailableInventoryResponse> inventoryAvailabilities(@RequestParam List<String> skuCode)
                        throws InternalServerException {
                log.info("GET:/api/inventory?skuCode=<code1>&skuCode=<code2>");
                return inventoryAvailabilityService.getAvailableInventory(skuCode);
        }

        @PostMapping("/reserve")
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK - Products reserved successfully", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AvailableInventoryResponse.class)))
                        }),
                        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        }),
                        @ApiResponse(responseCode = "409", description = "errorCode:"
                                        + ErrorComponent.NOT_ENOUGH_STOCK_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.notEnoughStockMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public List<AvailableInventoryResponse> reserveProducts(@Valid @RequestBody OrderReservationRequest request)
                        throws InternalServerException {
                log.info("POST:/api/inventory/reserve - Reserving products for order: {}", request.orderNumber());
                return reservationManagementService.reserveProductsIfAvailable(request);
        }

        @PostMapping
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Created", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = CreateInventoryResponse.class))
                        }),
                        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input", content = {
                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                        }),
                        @ApiResponse(responseCode = "409", description = "errorCode:"
                                        + ErrorComponent.DUPLICATE_SKU_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.duplicateSkuCodeMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public ResponseEntity<CreateInventoryResponse> createInventory(
                        @Valid @RequestBody CreateInventoryRequest request)
                        throws InternalServerException {
                log.info("POST:/api/inventory - Creating new inventory for skuCode: {}", request.skuCode());
                CreateInventoryResponse response = inventoryManagementService.createInventory(request);
                final var location = ServletUriComponentsBuilder
                                .fromCurrentRequest()
                                .path("/{sku-code}")
                                .buildAndExpand(response.skuCode())
                                .toUri();
                return ResponseEntity
                                .created(location)
                                .body(response);
        }

        @DeleteMapping("/{sku-code}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "No Content - Deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "errorCode:"
                                        + ErrorComponent.NOT_FOUND_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.notFoundMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public void deleteInventory(@PathVariable("sku-code") String skuCode) throws InternalServerException {
                log.info("DELETE:/api/inventory/{}", skuCode);
                inventoryManagementService.deleteInventory(skuCode);
        }
}
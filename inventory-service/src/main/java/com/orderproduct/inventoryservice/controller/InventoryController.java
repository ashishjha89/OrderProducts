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
import com.orderproduct.inventoryservice.dto.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.InventoryRequest;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.service.InventoryService;

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

        private final InventoryService inventoryService;

        public InventoryController(InventoryService inventoryService) {
                this.inventoryService = inventoryService;
        }

        @GetMapping
        @ResponseStatus(HttpStatus.OK)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "OK", content = {
                                        @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InventoryStockStatus.class)))
                        }),
                        @ApiResponse(responseCode = "500", description = "errorCode:"
                                        + ErrorComponent.SOMETHING_WENT_WRONG_ERROR_CODE + " errorMessage:"
                                        + ErrorComponent.somethingWentWrongMsg, content = {
                                                        @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class))
                                        })
        })
        public List<InventoryStockStatus> stocksStatus(@RequestParam List<String> skuCode)
                        throws InternalServerException {
                log.info("GET:/api/inventory?skuCode=<code1>&skuCode=<code2>");
                return inventoryService.stocksStatus(skuCode);
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
        public ResponseEntity<CreateInventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request)
                        throws InternalServerException {
                log.info("POST:/api/inventory - Creating new inventory for skuCode: {}", request.skuCode());
                CreateInventoryResponse response = inventoryService.createInventory(
                                Inventory.builder()
                                                .skuCode(request.skuCode())
                                                .quantity(request.quantity())
                                                .build());
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
                inventoryService.deleteInventory(skuCode);
        }
}
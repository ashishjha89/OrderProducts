package com.example.inventoryservice.controller;

import com.example.inventoryservice.common.ErrorBody;
import com.example.inventoryservice.common.ErrorComponent;
import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Slf4j
@SuppressWarnings("unused")
public record InventoryController(InventoryService inventoryService) {

    @GetMapping("/{sku-code}")
    @ResponseStatus(HttpStatus.OK)
    @SuppressWarnings("unused")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OK",
                            content = {
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = InventoryStockStatus.class)
                                    )
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
    public InventoryStockStatus isInStock(@PathVariable("sku-code") String skuCode) throws InternalServerException {
        log.info("GET:/api/inventory");
        return inventoryService.isInStock(skuCode);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @SuppressWarnings("unused")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OK",
                            content = {
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = InventoryStockStatus.class))
                                    )
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
    public List<InventoryStockStatus> stocksStatus(@RequestParam List<String> skuCode) throws InternalServerException {
        log.info("GET:/api/inventory?skuCode=<code1>&skuCode=<code2>");
        return inventoryService.stocksStatus(skuCode);
    }

}

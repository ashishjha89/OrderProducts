package com.orderproduct.inventoryservice.controller;

import com.orderproduct.inventoryservice.common.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.entity.Inventory;
import com.orderproduct.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {InventoryController.class})
@Import({InventoryControllerTest.MockedServiceConfig.class})
public class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryService inventoryService;

    @TestConfiguration
    static class MockedServiceConfig {
        @Bean
        public InventoryService productService() {
            return mock(InventoryService.class);
        }
    }

    @BeforeEach
    public void setUp() {
        when(inventoryService.isInStock(anyString())).thenReturn(new InventoryStockStatus("test-sku", true));
    }

    @Test
    @DisplayName("should return InventoryStockStatus when GET /inventory/{sku-id} is called and product exists")
    public void isInStock_WhenProductExists_ReturnsSuccess() throws Exception {
        InventoryStockStatus status = new InventoryStockStatus("test-sku", true);
        when(inventoryService.isInStock("test-sku")).thenReturn(status);

        mockMvc.perform(get("/api/inventory/test-sku"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.skuCode").value("test-sku"))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    @DisplayName("should return 500 when GET /inventory/{sku-id} is called and service throws InternalServerException")
    public void isInStock_WhenInternalError_ReturnsInternalServerError() throws Exception {
        when(inventoryService.isInStock("test-sku")).thenThrow(new InternalServerException());

        mockMvc.perform(get("/api/inventory/test-sku"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("should return List<InventoryStockStatus> when GET /inventory?skuCode={id1,id2} is called and products exist")
    public void stocksStatus_WhenProductsExist_ReturnsSuccess() throws Exception {
        List<InventoryStockStatus> statuses = List.of(
                new InventoryStockStatus("sku1", true),
                new InventoryStockStatus("sku2", false)
        );
        when(inventoryService.stocksStatus(any())).thenReturn(statuses);

        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "sku1", "sku2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].skuCode").value("sku1"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].skuCode").value("sku2"))
                .andExpect(jsonPath("$[1].inStock").value(false));
    }

    @Test
    @DisplayName("should return 500 when GET /inventory?skuCode={id1,id2} is called and service throws InternalServerException")
    public void stocksStatus_WhenInternalError_ReturnsInternalServerError() throws Exception {
        when(inventoryService.stocksStatus(any())).thenThrow(new InternalServerException());

        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "sku1", "sku2"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("should return 201 when POST /inventory is called with valid request")
    void createInventory_ValidRequest_Returns201() throws Exception {
        // Given
        var response = CreateInventoryResponse.success("SKU-123");
        when(inventoryService.createInventory(any(Inventory.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "SKU-123",
                                    "quantity": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/inventory/SKU-123"))
                .andExpect(jsonPath("$.skuCode").value("SKU-123"))
                .andExpect(jsonPath("$.message").value("Inventory created successfully"));
    }

    @Test
    @DisplayName("should return 409 when POST /inventory is called with duplicate SKU code")
    void createInventory_DuplicateSkuCode_Returns409() throws Exception {
        // Given
        when(inventoryService.createInventory(any(Inventory.class)))
                .thenThrow(new DuplicateSkuCodeException());

        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "SKU-123",
                                    "quantity": 10
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_SKU_CODE"))
                .andExpect(jsonPath("$.errorMessage").value("Inventory with this SKU code already exists."));
    }

    @Test
    @DisplayName("should return 500 when POST /inventory fails due to internal error")
    void createInventory_DatabaseError_Returns500() throws Exception {
        // Given
        when(inventoryService.createInventory(any(Inventory.class)))
                .thenThrow(new InternalServerException());

        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "SKU-123",
                                    "quantity": 10
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SOMETHING_WENT_WRONG"))
                .andExpect(jsonPath("$.errorMessage").value("Sorry, something went wrong."));
    }

    @Test
    @DisplayName("should return 400 when POST /inventory is called with blank SKU code")
    void createInventory_BlankSkuCode_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "",
                                    "quantity": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value("SKU code cannot be blank."));
    }

    @Test
    @DisplayName("should return 400 when POST /inventory is called with invalid SKU code characters")
    void createInventory_InvalidSkuCodeCharacters_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "SKU#123",
                                    "quantity": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value("SKU code can only contain alphanumeric characters, hyphens, and underscores."));
    }

    @Test
    @DisplayName("should return 400 when POST /inventory is called with SKU code exceeding length limit.")
    void createInventory_SkuCodeTooLong_Returns400() throws Exception {
        // Given
        String longSkuCode = "a".repeat(101);

        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                    "skuCode": "%s",
                                    "quantity": 10
                                }
                                """, longSkuCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value("SKU code length must be less than 100 characters."));
    }

    @Test
    @DisplayName("should return 400 when POST /inventory is called with negative quantity")
    void createInventory_NegativeQuantity_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "skuCode": "SKU-123",
                                    "quantity": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value("Quantity must be non-negative."));
    }
}

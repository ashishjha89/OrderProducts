package com.orderproduct.inventoryservice.controller;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
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
}

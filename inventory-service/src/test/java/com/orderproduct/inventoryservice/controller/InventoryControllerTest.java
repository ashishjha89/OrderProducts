package com.orderproduct.inventoryservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.orderproduct.inventoryservice.common.exception.DuplicateSkuCodeException;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotFoundException;
import com.orderproduct.inventoryservice.dto.request.CreateInventoryRequest;
import com.orderproduct.inventoryservice.dto.request.UpdateInventoryRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.CreateInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UpdateInventoryResponse;
import com.orderproduct.inventoryservice.service.InventoryAvailabilityService;
import com.orderproduct.inventoryservice.service.InventoryManagementService;

@WebMvcTest(controllers = { InventoryController.class })
@Import({ InventoryControllerTest.MockedServiceConfig.class })
public class InventoryControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private InventoryAvailabilityService inventoryAvailabilityService;

        @Autowired
        private InventoryManagementService inventoryManagementService;

        @BeforeEach
        void setUp() {
                reset(inventoryAvailabilityService, inventoryManagementService);
        }

        @TestConfiguration
        static class MockedServiceConfig {
                @Bean
                public InventoryAvailabilityService inventoryAvailabilityService() {
                        return mock(InventoryAvailabilityService.class);
                }

                @Bean
                public InventoryManagementService inventoryManagementService() {
                        return mock(InventoryManagementService.class);
                }

        }

        @Test
        @DisplayName("should return List<AvailableInventoryResponse> when GET /inventory?skuCode={id1,id2} is called")
        public void inventoryAvailabilities_WhenProductsExist_ReturnsSuccess() throws Exception {
                List<AvailableInventoryResponse> statuses = List.of(
                                new AvailableInventoryResponse("sku1", 10),
                                new AvailableInventoryResponse("sku2", 0));
                when(inventoryAvailabilityService.getAvailableInventory(List.of("sku1", "sku2"))).thenReturn(statuses);

                mockMvc.perform(get("/api/inventory")
                                .param("skuCode", "sku1", "sku2"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].skuCode").value("sku1"))
                                .andExpect(jsonPath("$[0].availableQuantity").value(10))
                                .andExpect(jsonPath("$[1].skuCode").value("sku2"))
                                .andExpect(jsonPath("$[1].availableQuantity").value(0));
        }

        @Test
        @DisplayName("should return 500 when GET /inventory?skuCode={id1,id2} is called and service throws InternalServerException")
        public void inventoryAvailabilities_WhenInternalError_ReturnsInternalServerError() throws Exception {
                when(inventoryAvailabilityService.getAvailableInventory(List.of("sku1", "sku2")))
                                .thenThrow(new InternalServerException());

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
                when(inventoryManagementService.createInventory(new CreateInventoryRequest("SKU-123", 10)))
                                .thenReturn(response);

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
                when(inventoryManagementService.createInventory(new CreateInventoryRequest("SKU-123", 10)))
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
                                .andExpect(jsonPath("$.errorMessage")
                                                .value("Inventory with this SKU code already exists."));
        }

        @Test
        @DisplayName("should return 500 when POST /inventory fails due to internal error")
        void createInventory_DatabaseError_Returns500() throws Exception {
                // Given
                when(inventoryManagementService.createInventory(new CreateInventoryRequest("SKU-123", 10)))
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
                                .andExpect(jsonPath("$.errorMessage")
                                                .value("SKU code can only contain alphanumeric characters, hyphens, and underscores."));
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
                                .andExpect(jsonPath("$.errorMessage")
                                                .value("SKU code length must be less than 100 characters."));
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

        @Test
        @DisplayName("should return 204 when DELETE /inventory/{sku-code} successfully deletes inventory")
        void deleteInventory_ExistingSkuCode_Returns204() throws Exception {
                // Given
                doNothing().when(inventoryManagementService).deleteInventory("SKU-123");

                // When & Then
                mockMvc.perform(delete("/api/inventory/SKU-123"))
                                .andExpect(status().isNoContent())
                                .andExpect(content().string(""));
        }

        @Test
        @DisplayName("should return 404 when DELETE /inventory/{sku-code} is called with non-existent SKU code")
        void deleteInventory_NonExistentSkuCode_Returns404() throws Exception {
                // Given
                doThrow(new NotFoundException()).when(inventoryManagementService).deleteInventory("NON-EXISTENT");

                // When & Then
                mockMvc.perform(delete("/api/inventory/NON-EXISTENT"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                                .andExpect(jsonPath("$.errorMessage").value("Resource not found."));
        }

        @Test
        @DisplayName("should return 500 when DELETE /inventory/{sku-code} fails due to internal error")
        void deleteInventory_InternalError_Returns500() throws Exception {
                // Given
                doThrow(new InternalServerException()).when(inventoryManagementService).deleteInventory("SKU-123");

                // When & Then
                mockMvc.perform(delete("/api/inventory/SKU-123"))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.errorCode").value("SOMETHING_WENT_WRONG"))
                                .andExpect(jsonPath("$.errorMessage").value("Sorry, something went wrong."));
        }

        @Test
        @DisplayName("should return 200 when PUT /inventory/{sku-code} is called with valid request")
        void updateInventory_ValidRequest_Returns200() throws Exception {
                // Given
                String skuCode = "SKU-123";
                int newQuantity = 75;
                UpdateInventoryResponse expectedResponse = UpdateInventoryResponse.success(skuCode, newQuantity);

                when(inventoryManagementService.updateInventory(any(UpdateInventoryRequest.class)))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(put("/api/inventory/{sku-code}", skuCode)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("""
                                                {
                                                    "skuCode": "%s",
                                                    "quantity": %d
                                                }
                                                """, skuCode, newQuantity)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.skuCode").value(skuCode))
                                .andExpect(jsonPath("$.quantity").value(newQuantity))
                                .andExpect(jsonPath("$.message").value("Inventory updated successfully"));
        }

        @Test
        @DisplayName("should return 404 when PUT /inventory/{sku-code} is called with non-existent SKU code")
        void updateInventory_NonExistentSkuCode_Returns404() throws Exception {
                // Given
                String skuCode = "NON-EXISTENT";
                int newQuantity = 50;

                when(inventoryManagementService.updateInventory(any(UpdateInventoryRequest.class)))
                                .thenThrow(new NotFoundException());

                // When & Then
                mockMvc.perform(put("/api/inventory/{sku-code}", skuCode)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("""
                                                {
                                                    "skuCode": "%s",
                                                    "quantity": %d
                                                }
                                                """, skuCode, newQuantity)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                                .andExpect(jsonPath("$.errorMessage").value("Resource not found."));
        }

        @Test
        @DisplayName("should return 400 when PUT /inventory/{sku-code} is called with blank SKU code")
        void updateInventory_BlankSkuCode_Returns400() throws Exception {
                // When & Then
                mockMvc.perform(put("/api/inventory/SKU-123")
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
        @DisplayName("should return 400 when PUT /inventory/{sku-code} is called with invalid SKU code characters")
        void updateInventory_InvalidSkuCodeCharacters_Returns400() throws Exception {
                // When & Then
                mockMvc.perform(put("/api/inventory/SKU-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                    "skuCode": "SKU#123",
                                                    "quantity": 10
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.errorMessage")
                                                .value("SKU code can only contain alphanumeric characters, hyphens, and underscores."));
        }

        @Test
        @DisplayName("should return 400 when PUT /inventory/{sku-code} is called with SKU code exceeding length limit")
        void updateInventory_SkuCodeTooLong_Returns400() throws Exception {
                // Given
                String longSkuCode = "a".repeat(101);

                // When & Then
                mockMvc.perform(put("/api/inventory/SKU-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("""
                                                {
                                                    "skuCode": "%s",
                                                    "quantity": 10
                                                }
                                                """, longSkuCode)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.errorMessage")
                                                .value("SKU code length must be less than 100 characters."));
        }

        @Test
        @DisplayName("should return 400 when PUT /inventory/{sku-code} is called with negative quantity")
        void updateInventory_NegativeQuantity_Returns400() throws Exception {
                // When & Then
                mockMvc.perform(put("/api/inventory/SKU-123")
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

        @Test
        @DisplayName("should return 500 when PUT /inventory/{sku-code} fails due to internal error")
        void updateInventory_InternalError_Returns500() throws Exception {
                // Given
                String skuCode = "SKU-123";
                int newQuantity = 75;

                when(inventoryManagementService.updateInventory(any(UpdateInventoryRequest.class)))
                                .thenThrow(new InternalServerException());

                // When & Then
                mockMvc.perform(put("/api/inventory/{sku-code}", skuCode)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("""
                                                {
                                                    "skuCode": "%s",
                                                    "quantity": %d
                                                }
                                                """, skuCode, newQuantity)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.errorCode").value("SOMETHING_WENT_WRONG"))
                                .andExpect(jsonPath("$.errorMessage").value("Sorry, something went wrong."));
        }
}

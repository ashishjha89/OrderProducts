package com.orderproduct.inventoryservice;

import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.orderproduct.inventoryservice.common.InternalServerException;
import com.orderproduct.inventoryservice.controller.InventoryController;
import com.orderproduct.inventoryservice.dto.InventoryStockStatus;
import com.orderproduct.inventoryservice.service.InventoryService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

@WebMvcTest(InventoryController.class)
@ActiveProfiles("contracts")
public abstract class CdcBaseClass {

        @Autowired
        private InventoryController inventoryController;

        @MockBean
        private InventoryService inventoryService;

        @BeforeEach
        public void setup() throws InternalServerException {
                RestAssuredMockMvc.standaloneSetup(inventoryController);
                // iPhone12 = 5, iPhone13 = 10, iPhone14 = 0
                when(inventoryService.stocksStatus(List.of("iphone_12", "iphone_13")))
                                .thenReturn(List.of(
                                                new InventoryStockStatus("iphone_12", 5),
                                                new InventoryStockStatus("iphone_13", 10)));
                when(inventoryService.stocksStatus(List.of("iphone_13", "iphone_14")))
                                .thenReturn(List.of(
                                                new InventoryStockStatus("iphone_13", 10),
                                                new InventoryStockStatus("iphone_14", 0)));
        }

}

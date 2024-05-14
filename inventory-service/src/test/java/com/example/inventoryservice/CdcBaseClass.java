package com.example.inventoryservice;

import com.example.inventoryservice.common.InternalServerException;
import com.example.inventoryservice.controller.InventoryController;
import com.example.inventoryservice.dto.InventoryStockStatus;
import com.example.inventoryservice.service.InventoryService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.Mockito.when;

@SpringBootTest(classes = InventoryServiceApplication.class)
public abstract class CdcBaseClass {

    @Autowired
    private InventoryController inventoryController;

    @MockBean
    private InventoryService inventoryService;

    @BeforeEach
    public void setup() throws InternalServerException {
        RestAssuredMockMvc.standaloneSetup(inventoryController);

        when(inventoryService.isInStock("iphone_12"))
                .thenReturn(new InventoryStockStatus("iphone_12", true));
        when(inventoryService.isInStock("iphone_13"))
                .thenReturn(new InventoryStockStatus("iphone_13", true));
        when(inventoryService.isInStock("iphone_14"))
                .thenReturn(new InventoryStockStatus("iphone_14", false));


        when(inventoryService.stocksStatus(List.of("iphone_12", "iphone_13")))
                .thenReturn(List.of(
                        new InventoryStockStatus("iphone_12", true),
                        new InventoryStockStatus("iphone_13", true)
                ));
        when(inventoryService.stocksStatus(List.of("iphone_13", "iphone_14")))
                .thenReturn(List.of(
                        new InventoryStockStatus("iphone_13", true),
                        new InventoryStockStatus("iphone_14", false)
                ));
        when(inventoryService.stocksStatus(List.of("iphone_12", "iphone_13", "iphone_14")))
                .thenReturn(List.of(
                        new InventoryStockStatus("iphone_12", true),
                        new InventoryStockStatus("iphone_13", true),
                        new InventoryStockStatus("iphone_14", false)
                ));
    }

}

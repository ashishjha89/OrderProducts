package com.orderproduct.inventoryservice;

import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.controller.ControllerExceptionHandler;
import com.orderproduct.inventoryservice.controller.InventoryController;
import com.orderproduct.inventoryservice.controller.ReservationController;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.request.ReservationStateUpdateRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.ReservationStateUpdateResponse;
import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;
import com.orderproduct.inventoryservice.entity.ReservationState;
import com.orderproduct.inventoryservice.service.InventoryAvailabilityService;
import com.orderproduct.inventoryservice.service.InventoryManagementService;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("contracts")
public abstract class CdcBaseClass {

        @Autowired
        private InventoryController inventoryController;

        @Autowired
        private ReservationController reservationController;

        @MockBean
        private InventoryAvailabilityService inventoryAvailabilityService;

        @MockBean
        private InventoryManagementService inventoryManagementService;

        @MockBean
        private ReservationManagementService reservationManagementService;

        private final int iphone12AvailableQuantityIs5 = 5;
        private final int iphone13AvailableQuantityIs10 = 10;
        private final int iphone14AvailableQuantityIs0 = 0;

        @BeforeEach
        public void setup() throws InternalServerException, NotEnoughItemException {
                RestAssuredMockMvc.standaloneSetup(inventoryController, reservationController,
                                new ControllerExceptionHandler());

                // Mock for GET /api/inventory
                // iPhone12 = 5, iPhone13 = 10, iPhone14 = 0
                when(inventoryAvailabilityService.getAvailableInventory(List.of("iphone_12", "iphone_13")))
                                .thenReturn(List.of(
                                                new AvailableInventoryResponse("iphone_12",
                                                                iphone12AvailableQuantityIs5),
                                                new AvailableInventoryResponse("iphone_13",
                                                                iphone13AvailableQuantityIs10)));
                when(inventoryAvailabilityService.getAvailableInventory(List.of("iphone_13", "iphone_14")))
                                .thenReturn(List.of(
                                                new AvailableInventoryResponse("iphone_13",
                                                                iphone13AvailableQuantityIs10),
                                                new AvailableInventoryResponse("iphone_14",
                                                                iphone14AvailableQuantityIs0)));

                // Mock for successful POST /api/reservations
                final var successfulRequest = new OrderReservationRequest("ORDER-123",
                                List.of(
                                                new ItemReservationRequest("iphone_12", 3),
                                                new ItemReservationRequest("iphone_13", 5)));
                when(reservationManagementService.reserveProductsIfAvailable(successfulRequest))
                                .thenReturn(List.of(
                                                new AvailableInventoryResponse("iphone_12", 2), // 5 - 3 = 2
                                                new AvailableInventoryResponse("iphone_13", 5) // 10 - 5 = 5
                                ));

                // Mock for failed POST /api/reservations (insufficient item)
                final var failedRequest = new OrderReservationRequest("ORDER-123",
                                List.of(new ItemReservationRequest("iphone_12", 100)));
                when(reservationManagementService.reserveProductsIfAvailable(failedRequest))
                                .thenThrow(new NotEnoughItemException(List.of(
                                                new UnavailableProduct("iphone_12", 100, 5))));

                // Mock for successful PUT /api/reservations/{orderNumber}/state
                final var successfulUpdateRequest = new ReservationStateUpdateRequest("ORDER-123",
                                List.of("iphone_12", "iphone_13"), ReservationState.FULFILLED);
                final var successfulUpdateResponse = new ReservationStateUpdateResponse(
                                "ORDER-123",
                                ReservationState.FULFILLED,
                                List.of(
                                                new ReservationStateUpdateResponse.ReservationItemResponse("iphone_12",
                                                                3, ReservationState.FULFILLED),
                                                new ReservationStateUpdateResponse.ReservationItemResponse("iphone_13",
                                                                5, ReservationState.FULFILLED)));
                when(reservationManagementService.updateReservationState(successfulUpdateRequest))
                                .thenReturn(successfulUpdateResponse);

                // Mock for PUT /api/reservations/{orderNumber}/state with no reservations found
                final var noReservationsRequest = new ReservationStateUpdateRequest("ORDER-123",
                                List.of("iphone_12", "iphone_13"), ReservationState.CANCELLED);
                final var noReservationsResponse = new ReservationStateUpdateResponse(
                                "ORDER-123",
                                ReservationState.CANCELLED,
                                List.of()); // Empty list - no reservations found
                when(reservationManagementService.updateReservationState(noReservationsRequest))
                                .thenReturn(noReservationsResponse);
        }

}

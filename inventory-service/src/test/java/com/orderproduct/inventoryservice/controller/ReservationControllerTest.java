package com.orderproduct.inventoryservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.inventoryservice.common.exception.InternalServerException;
import com.orderproduct.inventoryservice.common.exception.NotEnoughItemException;
import com.orderproduct.inventoryservice.dto.request.ItemReservationRequest;
import com.orderproduct.inventoryservice.dto.request.OrderReservationRequest;
import com.orderproduct.inventoryservice.dto.response.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.dto.response.UnavailableProduct;
import com.orderproduct.inventoryservice.service.ReservationManagementService;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private ReservationManagementService reservationManagementService;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("POST /api/reservations should reserve products successfully")
        public void reserveProducts_Success() throws Exception {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5),
                                new ItemReservationRequest("skuCode2", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var expectedResponses = List.of(
                                new AvailableInventoryResponse("skuCode1", 7),
                                new AvailableInventoryResponse("skuCode2", 5));

                when(reservationManagementService.reserveProductsIfAvailable(any(OrderReservationRequest.class)))
                                .thenReturn(expectedResponses);

                // When & Then
                mockMvc.perform(post("/api/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].skuCode").value("skuCode1"))
                                .andExpect(jsonPath("$[0].quantity").value(7))
                                .andExpect(jsonPath("$[1].skuCode").value("skuCode2"))
                                .andExpect(jsonPath("$[1].quantity").value(5));
        }

        @Test
        @DisplayName("POST /api/reservations should return 409 when insufficient stock")
        public void reserveProducts_InsufficientStock_Returns409() throws Exception {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 10));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                final var unavailableProducts = List.of(
                                new UnavailableProduct("skuCode1", 10, 5));

                when(reservationManagementService.reserveProductsIfAvailable(any(OrderReservationRequest.class)))
                                .thenThrow(new NotEnoughItemException(unavailableProducts));

                // When & Then
                mockMvc.perform(post("/api/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.errorCode").value("NOT_ENOUGH_ITEM_ERROR_CODE"))
                                .andExpect(jsonPath("$.errorMessage").value("Not enough stock for some products"));
        }

        @Test
        @DisplayName("POST /api/reservations should return 500 when internal server error")
        public void reserveProducts_InternalServerError_Returns500() throws Exception {
                // Given
                final var orderNumber = "ORDER-001";
                final var itemRequests = List.of(
                                new ItemReservationRequest("skuCode1", 5));
                final var request = new OrderReservationRequest(orderNumber, itemRequests);

                when(reservationManagementService.reserveProductsIfAvailable(any(OrderReservationRequest.class)))
                                .thenThrow(new InternalServerException());

                // When & Then
                mockMvc.perform(post("/api/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isInternalServerError())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.errorCode").value("SOMETHING_WENT_WRONG"));
        }

        @Test
        @DisplayName("POST /api/reservations should return 400 when invalid request")
        public void reserveProducts_InvalidRequest_Returns400() throws Exception {
                // Given
                final var invalidRequest = "{\"orderNumber\":\"\",\"itemReservationRequests\":[]}";

                // When & Then
                mockMvc.perform(post("/api/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest))
                                .andExpect(status().isBadRequest());
        }
}
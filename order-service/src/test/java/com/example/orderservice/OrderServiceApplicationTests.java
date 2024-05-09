package com.example.orderservice;

import com.example.orderservice.common.ErrorBody;
import com.example.orderservice.common.ErrorComponent;
import com.example.orderservice.dto.InventoryStockStatus;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.repository.InventoryStatusRepository;
import com.example.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OrderServiceApplicationTests {

    @Container
    static final MySQLContainer mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:5.7.34"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryStatusRepository inventoryStatusRepository;

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @AfterEach
    void cleanup() {
        orderRepository.deleteAll();
    }

    // In this test, we mock call to inventory-service (by mocking InventoryStatusRepository)
    // In unit-test of InventoryStatusRepository, the logic for http-request & response is tested
    @Test
    void placeOrderTest() throws Exception {
        // Initialise
        when(inventoryStatusRepository.retrieveStocksStatus(List.of("random_sku"))).thenReturn(List.of(new InventoryStockStatus("random_sku", true)));
        final var orderRequest = new OrderRequest(
                List.of(new OrderLineItemsDto("random_sku", BigDecimal.valueOf(1200), 10))
        );
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call
        mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(status().isCreated());

        // Assert item is inserted
        assertEquals(1, orderRepository.findAll().size());
    }

   /*
   // In this test, we make call to inventory-service -> however, then this test depends on response from inventory-service
    @Test
    void placeOrderTest() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest(
                List.of(new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(1200), 10))
        );
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call
        mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(status().isCreated());

        // Assert item is inserted
        assertEquals(1, orderRepository.findAll().size());
    }*/

    @Test
    void placeOrder_WhenEmptyOrderLineItemsIsPassed() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest(List.of());
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call and expect BadRequest
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var errorBody = objectMapper.readValue(jsonStr, ErrorBody.class);

        // Assert
        assertEquals(ErrorComponent.BAD_REQUEST, errorBody.errorCode());
        assertEquals(ErrorComponent.badRequestMsg, errorBody.errorMessage());
    }

    @Test
    void placeOrder_WhenOrderLineItemsIsMissing() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest();
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call and expect BadRequest
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var errorBody = objectMapper.readValue(jsonStr, ErrorBody.class);

        // Assert
        assertEquals(ErrorComponent.BAD_REQUEST, errorBody.errorCode());
        assertEquals(ErrorComponent.badRequestMsg, errorBody.errorMessage());
    }

}

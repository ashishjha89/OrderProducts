package com.example.orderservice;

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
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureStubRunner(
        stubsMode = LOCAL,
        ids = "com.example:inventory-service:0.0.1-SNAPSHOT:stubs:8082")
public class OrderConsumerContractTest {

    @Container
    static final MySQLContainer mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:5.7.34"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryStatusRepository inventoryStatusRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // In this test, we make call to inventory-service through consumer contract
    @Test
    void placeOrderWithConsumerContractTest() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest(
                List.of(
                        new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(900), 5),
                        new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(1200), 10)
                )
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
}

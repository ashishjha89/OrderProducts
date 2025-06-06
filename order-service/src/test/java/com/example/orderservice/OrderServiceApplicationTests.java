package com.example.orderservice;

import com.example.orderservice.common.ErrorBody;
import com.example.orderservice.common.ErrorComponent;
import com.example.orderservice.dto.OrderLineItemsDto;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
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
import static org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureStubRunner(
        stubsMode = LOCAL,
        ids = "com.orderproduct:inventory-service:0.0.1-SNAPSHOT:stubs:8082")
@EmbeddedKafka(topics = {"notification.topic"})
@SuppressWarnings("unused")
class OrderServiceApplicationTests {

    @Container
    static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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
        circuitBreakerRegistry.circuitBreaker("inventory").transitionToClosedState();
    }

    @Test
    @DisplayName("POST:/api/order should save OrderRequest in request-body, if its lineItems are available in inventory")
    void postOrder_ShouldAddItem_IfAllLineItemsAreAvailable() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest(
                List.of(
                        new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(900), 5),
                        new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(1200), 10)
                )
        );
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for async completion and verify
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andDo(print());

        // Assert item is inserted
        assertEquals(1, orderRepository.findAll().size());
    }

    @Test
    @DisplayName("POST:/api/order should respond with INVENTORY_NOT_IN_STOCK when request-body in OrderRequest have some lineItems not available in inventory")
    void postOrder_ShouldNotAddOrder_IfSomeLineItemIsNotAvailable() throws Exception {
        // Initialise
        final var orderRequest = new OrderRequest(
                List.of(
                        new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(1200), 10),
                        new OrderLineItemsDto("iphone_14", BigDecimal.valueOf(1600), 7)
                )
        );
        final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

        // Make Api call
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestStr))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isBadRequest()).andDo(print());

        // Process response
        final var jsonStr = result.getResponse().getContentAsString();
        final var errorBody = objectMapper.readValue(jsonStr, ErrorBody.class);

        // Assert
        assertEquals(ErrorComponent.INVENTORY_NOT_IN_STOCK, errorBody.errorCode());
        assertEquals(ErrorComponent.inventoryNotInStockMsg, errorBody.errorMessage());
        // Assert item is not inserted
        assertEquals(0, orderRepository.findAll().size());
    }

    @Test
    @DisplayName("POST:/api/order should return BAD_REQUEST when request-body in OrderRequest has empty LineItems")
    void postOrder_ShouldThrowBadRequest_WhenEmptyOrderLineItemsIsPassed() throws Exception {
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
    @DisplayName("POST:/api/order should return BAD_REQUEST when OrderRequest in request-body has missing LineItems")
    void postOrder_ShouldThrowBadRequest_WhenOrderLineItemsIsMissing() throws Exception {
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

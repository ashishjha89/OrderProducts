package com.orderproduct.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderproduct.orderservice.common.ErrorBody;
import com.orderproduct.orderservice.common.ErrorComponent;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.repository.OrderRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureStubRunner(stubsMode = LOCAL, ids = "com.orderproduct:inventory-service:0.0.1-SNAPSHOT:stubs:8082")
@EmbeddedKafka(topics = { "notification.topic" })
class OrderServiceApplicationTests {

        @Container
        static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private WebTestClient webTestClient;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CircuitBreakerRegistry circuitBreakerRegistry;

        private final int iphone12SQuantityInStubIsFive = 5;
        private final int iphone13QuantityInStubIsTen = 10;
        private final int iphone14QuantityInStubIsZero = 0;

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
                                                new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(100),
                                                                iphone12SQuantityInStubIsFive),
                                                new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(200),
                                                                iphone13QuantityInStubIsTen)));
                final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequestStr)
                                .exchange()
                                .expectStatus().isCreated();

                // Assert item is inserted
                assertEquals(1, orderRepository.findAll().size());
        }

        @Test
        @DisplayName("POST:/api/order should respond with INVENTORY_NOT_IN_STOCK when request-body in OrderRequest have some lineItems not available in inventory")
        void postOrder_ShouldNotAddOrder_IfSomeLineItemIsNotAvailable() throws Exception {
                // Initialise
                final var orderRequest = new OrderRequest(
                                List.of(
                                                new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(200),
                                                                15), // Requesting 15 when only 10 are available
                                                new OrderLineItemsDto("iphone_14", BigDecimal.valueOf(400),
                                                                iphone14QuantityInStubIsZero)));
                final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequestStr)
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody(ErrorBody.class)
                                .isEqualTo(new ErrorBody(ErrorComponent.INVENTORY_NOT_IN_STOCK_ERROR_CODE,
                                                ErrorComponent.inventoryNotInStockMsg));

                // Assert item is not inserted
                assertEquals(0, orderRepository.findAll().size());
        }

        @Test
        @DisplayName("POST:/api/order should return BAD_REQUEST when request-body in OrderRequest has empty LineItems")
        void postOrder_ShouldThrowBadRequest_WhenEmptyOrderLineItemsIsPassed() throws Exception {
                // Initialise
                final var orderRequest = new OrderRequest(List.of());
                final var orderRequestStr = objectMapper.writeValueAsString(orderRequest);

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequestStr)
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody(ErrorBody.class)
                                .isEqualTo(new ErrorBody(ErrorComponent.BAD_REQUEST_ERROR_CODE,
                                                ErrorComponent.badRequestMsg));
        }

        @Test
        @DisplayName("POST:/api/order should return BAD_REQUEST when OrderRequest in request-body has missing LineItems")
        void postOrder_ShouldThrowBadRequest_WhenOrderLineItemsIsMissing() throws Exception {
                // Initialise
                final var orderRequestStr = "{}";

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequestStr)
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody(ErrorBody.class)
                                .isEqualTo(new ErrorBody(ErrorComponent.BAD_REQUEST_ERROR_CODE,
                                                ErrorComponent.badRequestMsg));
        }
}

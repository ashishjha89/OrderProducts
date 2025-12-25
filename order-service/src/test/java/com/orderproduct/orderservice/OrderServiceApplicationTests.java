package com.orderproduct.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties.StubsMode.LOCAL;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.orderproduct.orderservice.common.ErrorBody;
import com.orderproduct.orderservice.common.ErrorComponent;
import com.orderproduct.orderservice.dto.OrderLineItemsDto;
import com.orderproduct.orderservice.dto.OrderRequest;
import com.orderproduct.orderservice.repository.OrderRepository;
import com.orderproduct.orderservice.service.OrderDataGenerator;

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
        private CircuitBreakerRegistry circuitBreakerRegistry;

        @MockitoBean
        private OrderDataGenerator orderDataGenerator;

        @DynamicPropertySource
        static void configureTestProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
                registry.add("spring.datasource.username", mySQLContainer::getUsername);
                registry.add("spring.datasource.password", mySQLContainer::getPassword);
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        }

        @AfterEach
        void cleanup() {
                orderRepository.deleteAll();
                circuitBreakerRegistry.circuitBreaker("inventory").transitionToClosedState();
        }

        @BeforeEach
        void setUp() {
                when(orderDataGenerator.getUniqueOrderNumber()).thenReturn("ORDER-123");
                when(orderDataGenerator.getUniqueOutboxEventId()).thenReturn("outbox-event-123");
                when(orderDataGenerator.getCurrentTimestamp()).thenReturn(1234567890L);
        }

        @Test
        @DisplayName("POST:/api/order should save OrderRequest in request-body, if its lineItems are available in inventory")
        void postOrder_ShouldAddItem_IfAllLineItemsAreAvailable() throws Exception {
                // Initialise
                // For iphone12, Requesting 3 when 5 are available
                // For iphone13, Requesting 5 when 10 are available
                final var orderRequest = new OrderRequest(
                                List.of(
                                                new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(100),
                                                                3),
                                                new OrderLineItemsDto("iphone_13", BigDecimal.valueOf(200),
                                                                5)));

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequest)
                                .exchange()
                                .expectStatus().isCreated()
                                .expectBody()
                                .jsonPath("$.orderId").exists()
                                .jsonPath("$.orderNumber").exists();

                // Assert item is inserted
                assertEquals(1, orderRepository.findAll().size());
        }

        @Test
        @DisplayName("POST:/api/order should respond with INVENTORY_NOT_IN_STOCK when request-body in OrderRequest have some lineItems not available in inventory")
        void postOrder_ShouldNotAddOrder_IfSomeLineItemIsNotAvailable() throws Exception {
                // Initialise
                // For iphone12, Requesting 100 when 5 are available
                final var orderRequest = new OrderRequest(
                                List.of(
                                                new OrderLineItemsDto("iphone_12", BigDecimal.valueOf(200),
                                                                100)));

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequest)
                                .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                                .expectBody()
                                .jsonPath("$.errorCode").isEqualTo("INVENTORY_NOT_IN_STOCK")
                                .jsonPath("$.unavailableProducts[0].skuCode").isEqualTo("iphone_12")
                                .jsonPath("$.unavailableProducts[0].requestedQuantity").isEqualTo(100)
                                .jsonPath("$.unavailableProducts[0].availableQuantity").isEqualTo(5);
        }

        @Test
        @DisplayName("POST:/api/order should return BAD_REQUEST when request-body in OrderRequest has empty LineItems")
        void postOrder_ShouldThrowBadRequest_WhenEmptyOrderLineItemsIsPassed() throws Exception {
                // Initialise
                final var orderRequest = new OrderRequest(List.of());

                // Make Api call and verify
                webTestClient.post()
                                .uri("/api/order")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(orderRequest)
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

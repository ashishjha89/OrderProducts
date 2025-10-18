# Implementation Plan: gRPC Client for Inventory Reservation Service

## Overview
Implement a gRPC client in `order-service` to communicate with `inventory-service` for product reservation as an alternative to the existing HTTP-based `InventoryReservationService.java`. 

**Feature Flag**: `inventory.reservation.use-grpc` (default: `false` for HTTP, `true` for gRPC)

**Design**: Interface-based abstraction - both implementations share `InventoryReservation` interface with identical error handling and Resilience4j patterns.

---

## 1. Project Setup

### 1.1 Update `pom.xml`
Add the following dependencies to `order-service/pom.xml`:

```xml
<!-- gRPC Client Dependencies -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-client-spring-boot-starter</artifactId>
    <version>3.1.0.RELEASE</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.58.0</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>1.58.0</version>
</dependency>
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>3.24.4</version>
</dependency>
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>

<!-- gRPC Test Dependencies -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>1.58.0</version>
    <scope>test</scope>
</dependency>
```

### 1.2 Add Maven Plugins
Add the following plugins to `<build><plugins>` section:

```xml
<!-- Protobuf Compiler Plugin -->
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.24.4:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.58.0:exe:${os.detected.classifier}</pluginArtifact>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
                <goal>compile-custom</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- OS Detection Plugin -->
<plugin>
    <groupId>kr.motd.maven</groupId>
    <artifactId>os-maven-plugin</artifactId>
    <version>1.7.1</version>
    <executions>
        <execution>
            <phase>initialize</phase>
            <goals>
                <goal>detect</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 1.3 Copy Proto File
Copy `inventory-service/src/main/proto/reservation_service.proto` to `order-service/src/main/proto/reservation_service.proto`, then run `mvn clean compile` to generate Java classes in `target/generated-sources/protobuf/`.

---

## 2. Configuration

### 2.1 Update `application.properties`
Add the following properties to `order-service/src/main/resources/application.properties`:

```properties
# gRPC Client Configuration
# Feature flag to enable/disable gRPC (true = use gRPC, false = use HTTP)
inventory.reservation.use-grpc=false

# gRPC client configuration
grpc.client.inventory-reservation.address=static://localhost:9090
grpc.client.inventory-reservation.negotiationType=PLAINTEXT
grpc.client.inventory-reservation.enableKeepAlive=true
grpc.client.inventory-reservation.keepAliveTime=30s
grpc.client.inventory-reservation.keepAliveTimeout=5s

# gRPC timeout configuration (aligned with Resilience4j timeout)
grpc.client.inventory-reservation.deadline=3s
```

**Note**: Port 9090 is inventory-service's gRPC port. In production, use service discovery: `discovery:///inventory-service`. Deadline (3s) aligns with HTTP timeout.

---

## 3. Implementation

### 3.1 Create gRPC Client Service
**File**: `order-service/src/main/java/com/orderproduct/orderservice/service/InventoryReservationGrpcClientService.java`
```java
package com.orderproduct.orderservice.service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import com.orderproduct.inventoryservice.grpc.AvailableInventoryResponse;
import com.orderproduct.inventoryservice.grpc.ItemReservationRequest;
import com.orderproduct.inventoryservice.grpc.ReservationServiceGrpc;
import com.orderproduct.inventoryservice.grpc.ReserveProductsRequest;
import com.orderproduct.inventoryservice.grpc.ReserveProductsResponse;
import com.orderproduct.orderservice.common.ApiException;
import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Slf4j
@Service
public class InventoryReservationGrpcClientService implements InventoryReservation {

    @GrpcClient("inventory-reservation")
    private ReservationServiceGrpc.ReservationServiceBlockingStub reservationServiceStub;

    @CircuitBreaker(name = "inventory", fallbackMethod = "onReserveOrderFailure")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    @NonNull
    @Override
    public CompletableFuture<List<InventoryAvailabilityStatus>> reserveOrder(
            @NonNull OrderReservationRequest orderReservationRequest)
            throws InternalServerException, InvalidInventoryException, InvalidInputException,
            InventoryNotInStockException {
        
        validateOrderReservationRequest(orderReservationRequest);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("gRPC:ReserveOrder - Reserving products for order: {}", 
                    orderReservationRequest.orderNumber());
                
                ReserveProductsRequest grpcRequest = convertToGrpcRequest(orderReservationRequest);
                ReserveProductsResponse grpcResponse = reservationServiceStub.reserveProducts(grpcRequest);
                
                List<InventoryAvailabilityStatus> result = convertFromGrpcResponse(grpcResponse);
                
                log.info("gRPC:ReserveOrder - Successfully reserved {} items for order: {}",
                    result.size(), orderReservationRequest.orderNumber());
                
                return result;
            } catch (StatusRuntimeException e) {
                log.error("gRPC:ReserveOrder - Error for order: {}: {}", 
                    orderReservationRequest.orderNumber(), e.getStatus());
                throw handleGrpcException(e, orderReservationRequest.orderNumber());
            } catch (Exception e) {
                log.error("gRPC:ReserveOrder - Unexpected error for order: {}: {}", 
                    orderReservationRequest.orderNumber(), e.getMessage());
                throw new InternalServerException();
            }
        });
    }

    private void validateOrderReservationRequest(OrderReservationRequest orderReservationRequest) {
        if (orderReservationRequest.itemReservationRequests().isEmpty()) {
            log.warn("Attempted to reserve products with empty item reservation requests for order: {}",
                    orderReservationRequest.orderNumber());
            throw new InvalidInputException("Item reservation requests cannot be empty");
        }
    }

    private ReserveProductsRequest convertToGrpcRequest(OrderReservationRequest request) {
        ReserveProductsRequest.Builder builder = ReserveProductsRequest.newBuilder()
                .setOrderNumber(request.orderNumber());

        for (com.orderproduct.orderservice.dto.ItemReservationRequest item : request.itemReservationRequests()) {
            builder.addItemReservationRequests(
                    ItemReservationRequest.newBuilder()
                            .setSkuCode(item.skuCode())
                            .setQuantity(item.quantity())
                            .build());
        }

        return builder.build();
    }

    private List<InventoryAvailabilityStatus> convertFromGrpcResponse(ReserveProductsResponse response) {
        if (response.getAvailableInventoryCount() == 0) {
            log.warn("Received empty response from gRPC inventory service");
            throw new InvalidInventoryException();
        }
        
        return response.getAvailableInventoryList().stream()
                .map(item -> new InventoryAvailabilityStatus(
                        item.getSkuCode(),
                        item.getAvailableQuantity()))
                .collect(Collectors.toList());
    }

    private RuntimeException handleGrpcException(StatusRuntimeException e, String orderNumber) {
        Status.Code code = e.getStatus().getCode();
        
        if (code == Status.Code.RESOURCE_EXHAUSTED) {
            return handleResourceExhausted(e, orderNumber);
        } else if (code == Status.Code.INTERNAL) {
            return handleInternalError(e, orderNumber);
        } else {
            log.error("Received unexpected gRPC error code {} for order: {}", code, orderNumber);
            return new InvalidInventoryException();
        }
    }

    private RuntimeException handleResourceExhausted(StatusRuntimeException e, String orderNumber) {
        ErrorInfo errorInfo = extractErrorInfo(e);
        
        if (errorInfo != null && "NOT_ENOUGH_ITEM_ERROR_CODE".equals(errorInfo.getReason())) {
            log.error("Inventory not in stock for order: {}", orderNumber);
            String unavailableProducts = errorInfo.getMetadataMap()
                    .getOrDefault("unavailable_products", "");
            
            List<String> unavailableSkus = unavailableProducts.isEmpty() 
                    ? List.of() 
                    : Arrays.asList(unavailableProducts.split(","));
            
            return new InventoryNotInStockException(
                    e.getStatus().getDescription() != null 
                            ? e.getStatus().getDescription() 
                            : "Not enough stock for some products",
                    unavailableSkus);
        }
        
        log.error("Received RESOURCE_EXHAUSTED without expected error code for order: {}", orderNumber);
        return new InvalidInventoryException();
    }

    private RuntimeException handleInternalError(StatusRuntimeException e, String orderNumber) {
        ErrorInfo errorInfo = extractErrorInfo(e);
        
        if (errorInfo != null && "SOMETHING_WENT_WRONG_ERROR_CODE".equals(errorInfo.getReason())) {
            log.error("Internal server error from gRPC inventory service for order: {}", orderNumber);
            return new InternalServerException();
        }
        
        log.error("Received INTERNAL error without expected error code for order: {}", orderNumber);
        return new InternalServerException();
    }

    private ErrorInfo extractErrorInfo(StatusRuntimeException exception) {
        try {
            io.grpc.Status grpcStatus = exception.getStatus();
            com.google.rpc.Status status = StatusProto.fromStatusAndTrailers(
                    grpcStatus,
                    exception.getTrailers());

            if (status != null) {
                for (Any detail : status.getDetailsList()) {
                    if (detail.is(ErrorInfo.class)) {
                        try {
                            return detail.unpack(ErrorInfo.class);
                        } catch (Exception e) {
                            log.warn("Failed to unpack ErrorInfo: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract error info from gRPC exception: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unused")
    private CompletableFuture<List<InventoryAvailabilityStatus>> onReserveOrderFailure(
            @NonNull OrderReservationRequest orderReservationRequest,
            Throwable throwable) {
        log.error("Circuit breaker fallback triggered for order: {}. Error: {}", 
            orderReservationRequest.orderNumber(), throwable.getMessage());
        
        CompletableFuture<List<InventoryAvailabilityStatus>> failedFuture = new CompletableFuture<>();
        if (throwable instanceof ApiException) {
            failedFuture.completeExceptionally(throwable);
        } else {
            failedFuture.completeExceptionally(new InternalServerException());
        }
        return failedFuture;
    }
}
```


---

### 3.2 Create Abstraction Interface
**File**: `order-service/src/main/java/com/orderproduct/orderservice/service/InventoryReservation.java`

```java
package com.orderproduct.orderservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.orderproduct.orderservice.common.InternalServerException;
import com.orderproduct.orderservice.common.InvalidInputException;
import com.orderproduct.orderservice.common.InvalidInventoryException;
import com.orderproduct.orderservice.common.InventoryNotInStockException;
import com.orderproduct.orderservice.dto.InventoryAvailabilityStatus;
import com.orderproduct.orderservice.dto.OrderReservationRequest;

import lombok.NonNull;

public interface InventoryReservation {
    @NonNull
    CompletableFuture<List<InventoryAvailabilityStatus>> reserveOrder(
            @NonNull OrderReservationRequest orderReservationRequest)
            throws InternalServerException, InvalidInventoryException,
            InvalidInputException, InventoryNotInStockException;
}
```


---

### 3.3 Create Configuration Class
**File**: `order-service/src/main/java/com/orderproduct/orderservice/config/InventoryReservationConfig.java`

```java
package com.orderproduct.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.orderproduct.orderservice.service.InventoryReservation;
import com.orderproduct.orderservice.service.InventoryReservationGrpcClientService;
import com.orderproduct.orderservice.service.InventoryReservationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class InventoryReservationConfig {

    @Bean
    @ConditionalOnProperty(
            name = "inventory.reservation.use-grpc",
            havingValue = "false",
            matchIfMissing = true)
    public InventoryReservation httpInventoryReservation(
            WebClient.Builder webClientBuilder,
            @Value("${inventory.api.base-url}") String inventoryApiBaseUrl) {
        log.info("Configuring HTTP-based inventory reservation service");
        return new InventoryReservationService(webClientBuilder, inventoryApiBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(
            name = "inventory.reservation.use-grpc",
            havingValue = "true")
    public InventoryReservation grpcInventoryReservation(
            InventoryReservationGrpcClientService grpcClientService) {
        log.info("Configuring gRPC-based inventory reservation service");
        return grpcClientService;
    }
}
```


---

### 3.4 Update OrderTransactionService
**File**: `order-service/src/main/java/com/orderproduct/orderservice/service/OrderTransactionService.java`

Change `InventoryReservationService inventoryReservationService` field to `InventoryReservation inventoryReservation` and update all method calls accordingly.

---

### 3.5 Update InventoryReservationService
**File**: `order-service/src/main/java/com/orderproduct/orderservice/service/InventoryReservationService.java`

Add `implements InventoryReservation` to class declaration and `@Override` to `reserveOrder` method.

---

## 4. Data Model Mapping

Request/response conversion methods are included in the `InventoryReservationGrpcClientService` template (Section 3.1).

---

## 5. Error Handling

**gRPC Status → Order Service Exception Mapping**:
- `RESOURCE_EXHAUSTED` + ErrorInfo.reason="NOT_ENOUGH_ITEM_ERROR_CODE" → `InventoryNotInStockException`
- `INTERNAL` + ErrorInfo.reason="SOMETHING_WENT_WRONG_ERROR_CODE" → `InternalServerException`
- Other errors → `InvalidInventoryException`

Error handling implementation is included in the `InventoryReservationGrpcClientService` template (Section 3.1).

---

## 6. Resilience4j Configuration

Reuses existing "inventory" instance configuration. Only `InternalServerException` triggers circuit breaker and retry (mirrors HTTP client behavior).

---

## 7. Testing Strategy

### 7.1 Unit Tests
**File**: `order-service/src/test/java/com/orderproduct/orderservice/service/InventoryReservationGrpcClientServiceTest.java`

Test cases: successful reservation, insufficient stock, internal error, unexpected error, empty request, circuit breaker fallback. Use Mockito to mock gRPC stub.

### 7.2 Integration Tests
**File**: `order-service/src/test/java/com/orderproduct/orderservice/service/InventoryReservationGrpcClientServiceIntegrationTest.java`

Use `@SpringBootTest` with in-process gRPC server and test properties for end-to-end testing.

### 7.3 Configuration Tests
**File**: `order-service/src/test/java/com/orderproduct/orderservice/config/InventoryReservationConfigTest.java`

Verify correct bean creation based on `inventory.reservation.use-grpc` property value.

---

## 8. Implementation Checklist

### Phase 1: Setup and Dependencies
- [ ] Update `pom.xml` with gRPC dependencies and plugins (Section 1)
- [ ] Copy `reservation_service.proto` to `order-service/src/main/proto/`
- [ ] Run `mvn clean compile` and verify generated classes

### Phase 2: Configuration
- [ ] Add gRPC configuration to `application.properties` (Section 2)

### Phase 3: Core Implementation
- [ ] Create `InventoryReservation` interface (Section 3.2)
- [ ] Create `InventoryReservationGrpcClientService` (Section 3.1)
- [ ] Create `InventoryReservationConfig` (Section 3.3)
- [ ] Update `InventoryReservationService` to implement interface (Section 3.5)
- [ ] Update `OrderTransactionService` to use interface (Section 3.4)

### Phase 4: Testing
- [ ] Write unit tests (Section 7.1)
- [ ] Write integration tests (Section 7.2)
- [ ] Test feature flag switching (Section 7.3)
- [ ] Verify backward compatibility with HTTP mode

### Phase 5: Validation
- [ ] Test error scenarios
- [ ] Verify distributed tracing
- [ ] Run full test suite with both modes

---

## 9. Production Considerations

- **Service Discovery**: Use `discovery:///inventory-service` address
- **Security**: Enable TLS, add authentication via gRPC metadata
- **Load Balancing**: Configure client-side load balancing policy

---

## 10. Troubleshooting Common Issues

### Issue 1: Generated gRPC classes not found
**Symptom**: Compilation errors - cannot find `ReservationServiceGrpc`, `ReserveProductsRequest`, etc.

**Solutions**:
1. Ensure `reservation_service.proto` is in `order-service/src/main/proto/`
2. Run `mvn clean compile` to regenerate classes
3. Verify generated classes are in `target/generated-sources/protobuf/grpc-java/` and `target/generated-sources/protobuf/java/`
4. In IDE, mark `target/generated-sources/protobuf/grpc-java/` and `target/generated-sources/protobuf/java/` as source folders
5. Check that `os-maven-plugin` and `protobuf-maven-plugin` are properly configured in `pom.xml`

### Issue 2: Cannot connect to gRPC server
**Symptom**: `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception`

**Solutions**:
1. Verify inventory-service is running: `grpcurl -plaintext localhost:9090 list`
2. Check `grpc.client.inventory-reservation.address` in `application.properties`
3. Verify inventory-service gRPC server is on port 9090 (check inventory-service `application.properties`)
4. Ensure firewall allows connection to port 9090

### Issue 3: Multiple beans of type InventoryReservation
**Symptom**: `NoUniqueBeanDefinitionException: expected single matching bean but found 2`

**Solutions**:
1. Ensure `InventoryReservationService` is NOT annotated with `@Service` or `@Component`
2. Verify `@ConditionalOnProperty` annotations are correct in `InventoryReservationConfig`
3. Check that only one property value is set for `inventory.reservation.use-grpc`
4. Clear compiled classes: `mvn clean` and rebuild

### Issue 4: ErrorInfo not being extracted from gRPC exception
**Symptom**: Error handling falls back to generic `InvalidInventoryException`

**Solutions**:
1. Ensure you're using `StatusProto.fromStatusAndTrailers()` to extract status
2. Add debug logging in `extractErrorInfo()` method
3. Verify inventory-service is sending `ErrorInfo` in the error response
4. Check that `com.google.rpc.Status` and `com.google.rpc.ErrorInfo` classes are available (from protobuf dependency)

### Issue 5: Timeout errors
**Symptom**: `io.grpc.StatusRuntimeException: DEADLINE_EXCEEDED`

**Solutions**:
1. Increase timeout in `application.properties`: `grpc.client.inventory-reservation.deadline=5s`
2. Ensure Resilience4j timeout is aligned: `resilience4j.timelimiter.instances.inventory.timeout-duration=5s`
3. Check if inventory-service database is slow
4. Verify network latency between services

### Issue 6: Circuit breaker not working
**Symptom**: Retries happening when they shouldn't, or circuit not opening

**Solutions**:
1. Verify only `InternalServerException` is mapped to gRPC `INTERNAL` status
2. Check that `InventoryNotInStockException` (from `RESOURCE_EXHAUSTED`) is NOT in retry/circuit breaker exceptions
3. Review Resilience4j configuration in `application.properties`
4. Add logging in fallback method to verify it's being called
5. Check actuator endpoint: `http://localhost:<port>/actuator/circuitbreakers`

### Issue 7: Tests failing with "No gRPC client bean"
**Symptom**: Test fails with `@GrpcClient` injection error

**Solutions**:
1. Add `@TestPropertySource` with gRPC test configuration
2. Use in-process gRPC server for tests
3. Ensure test dependencies include `grpc-client-spring-boot-starter`
4. Mock the gRPC stub in unit tests instead of using `@GrpcClient`

### Issue 8: ClassNotFoundException for javax.annotation.Generated
**Symptom**: `java.lang.ClassNotFoundException: javax.annotation.Generated`

**Solutions**:
1. Add dependency to `pom.xml`:
   ```xml
   <dependency>
       <groupId>javax.annotation</groupId>
       <artifactId>javax.annotation-api</artifactId>
       <version>1.3.2</version>
   </dependency>
   ```
2. Rebuild with `mvn clean compile`

### Issue 9: Feature flag not working as expected
**Symptom**: Wrong implementation being used despite correct property setting

**Solutions**:
1. Verify property is set correctly: `inventory.reservation.use-grpc=true` (no spaces, correct case)
2. Check property is in correct `application.properties` file (not commented out)
3. Add debug logging in `InventoryReservationConfig` beans
4. Use actuator to check beans: `http://localhost:<port>/actuator/beans`
5. Ensure Spring Boot is picking up the properties file

### Issue 10: Distributed tracing not working for gRPC calls
**Symptom**: Trace IDs not propagated, gaps in Zipkin traces

**Solutions**:
1. Ensure `grpc-spring-boot-starter` version is compatible with Spring Boot tracing
2. Verify both services have tracing enabled
3. Check Zipkin endpoint configuration
4. May need to add custom gRPC interceptor for trace propagation (usually automatic)

---

## References

**Key Files**:
- Proto: `inventory-service/src/main/proto/reservation_service.proto`
- gRPC Server: `inventory-service/src/main/java/com/orderproduct/inventoryservice/grpc/ReservationGrpcService.java`
- HTTP Client: `order-service/src/main/java/com/orderproduct/orderservice/service/InventoryReservationService.java`
- Server Tests: `inventory-service/src/test/java/com/orderproduct/inventoryservice/grpc/`
- DTOs: `order-service/src/main/java/com/orderproduct/orderservice/dto/`
- Exceptions: `order-service/src/main/java/com/orderproduct/orderservice/common/`
- Service Usage: `order-service/src/main/java/com/orderproduct/orderservice/service/OrderTransactionService.java`

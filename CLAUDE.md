# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This project is implemented to **practise** developing well-architected, scalable and reliable backend applications.

## Important Rule

NEVER run anything on AWS unless you are told to do so very explicitly. ALWAYS confirm clearly with the user before proceeding working on AWS.

## Build & Test Commands

Each service is built independently with Maven. Run commands from the service directory.

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=InventoryIntegrationTests

# Run a single test method
./mvnw test -Dtest=InventoryIntegrationTests#testCreateInventory

# Run the application locally
./mvnw spring-boot:run
```

Integration tests (files named `*IntegrationTests.java` or `*IntegrationTest.java`) use Testcontainers and require Docker to be running. Unit tests (`*Test.java`) use H2 or mocks and run without Docker.

## Local Development Setup

### Case 1: Apps run locally, infra in Docker (preferred for debugging)

```bash
# Start infrastructure only from the infrastructure/ directory
cd infrastructure
docker-compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector

# Then run each service in a separate terminal (from its directory)
cd discovery-server && ./mvnw spring-boot:run
cd api-gateway     && ./mvnw spring-boot:run
cd product-service && ./mvnw spring-boot:run
cd inventory-service && ./mvnw spring-boot:run
cd order-service   && ./mvnw spring-boot:run
```

### Case 2: Everything in Docker

```bash
cd infrastructure
docker-compose up -d          # start all
docker-compose ps             # check status
docker-compose logs -f <svc>  # tail logs
docker-compose down -v        # stop and wipe volumes
```

Services registered in Docker Compose use `pull_policy: always` and pull from `ashishjha/orderproducts-*:latest` on Docker Hub.

## Architecture

### Services & Ports

| Service | External Port | Notes |
|---|---|---|
| API Gateway | 8080 | Single ingress for all client traffic |
| Discovery Server | 8761 | Eureka registry |
| Inventory Service | dynamic (Eureka) | gRPC server on 9090 |
| Order Service | dynamic (Eureka) | |
| Product Service | dynamic (Eureka) | Standalone; not in core order flow |

### Communication Patterns

Order placement uses **two inventory communication paths** that are toggled by a feature flag in `order-service/src/main/resources/application.properties`:

```properties
inventory.reservation.use-grpc=true   # gRPC (default)
inventory.reservation.use-grpc=false  # HTTP via WebClient + Eureka
```

`InventoryReservationConfig` (`order-service`) wires the correct `InventoryReservationService` implementation at startup based on this flag. Both `InventoryReservationGrpcClientService` and `InventoryReservationHttpService` implement the same interface.

### Outbox Pattern (Order → Inventory event flow)

Order Service never publishes directly to Kafka. Instead:
1. `OrderTransactionService` writes an `OutboxEvent` row to MySQL (`order_db.outbox_event`) in the same transaction as the order save.
2. Debezium (Kafka Connect) reads MySQL binlog and publishes CDC events to the `outbox.event.Order` Kafka topic.
3. Inventory Service's `OrderEventHandler` consumes that topic and updates reservation state (`FULFILLED` or `CANCELLED`).

The Debezium connector config lives in `infrastructure/debezium_connector_config/mysql-outbox-connector.json`.

### Inventory Reservation State Machine

`Reservation` entities (in `inventory_db.inventory_reservation`) transition through:
`PENDING` → `FULFILLED` or `CANCELLED`

When order placement fails after reservation, `OrderTransactionService.saveOrderCancelledEvent` writes a CANCELLED outbox event so inventory is released.

Available quantity = `on_hand_quantity` (Inventory table) − sum of PENDING `reserved_quantity` (Reservation table).

### gRPC Contract

The `.proto` definition lives in **both** services (must be kept in sync manually):
- `inventory-service/src/main/proto/reservation_service.proto` (server)
- `order-service/src/main/proto/reservation_service.proto` (client)

The protobuf Maven plugin compiles these at build time. Generated sources land in `target/generated-sources/protobuf/`.

On Apple Silicon (M1/M2), the `macos-m1` Maven profile in `order-service/pom.xml` activates automatically to add the Netty DNS resolver native library.

### Database Separation

MySQL has two separate databases with separate users (provisioned by `infrastructure/mysql_init_scripts/`):
- `inventory_db` / `inventory_user` — Inventory Service
- `order_db` / `order_user` — Order Service

Schemas are not managed by Hibernate (`ddl-auto=none`); they are created by the SQL init scripts.

### Testing Approach

- **Unit tests** (`*Test.java`): Mockito mocks, H2 in-memory DB for inventory-service. No Docker needed.
- **Integration tests** (`*IntegrationTests.java` / `*IntegrationTest.java`): `@Testcontainers` spins up real MySQL containers.
- **CDC contract tests**: `inventory-service` uses Spring Cloud Contract (`spring-cloud-contract-maven-plugin`). `CdcBaseClass` is the base class for generated contract verifier tests.
- `order-service` uses WireMock (`spring-cloud-contract-wiremock`) and OkHttp `MockWebServer` for HTTP-layer testing.

### Observability

All services ship traces to Zipkin (`localhost:9411`). Tracing is wired via Micrometer + Brave. Log pattern includes `traceId` and `spanId`. `OrderService` creates a named Micrometer observation (`inventory-service-reservation`) around each inventory call.

Circuit breaker (Resilience4J) in Order Service wraps inventory calls: TIME_BASED sliding window, 50% failure threshold, 30 s open state, 3 s timeout, 3 retries with 5 s wait.

# CLAUDE.md

## Important Rule
NEVER run anything on AWS unless explicitly told to. ALWAYS confirm with the user before any AWS work.

## Build & Test Commands
Each service is built independently with Maven. Run from the service directory.

```bash
./mvnw clean package -DskipTests          # build
./mvnw test                               # all tests
./mvnw test -Dtest=ClassName              # single class
./mvnw test -Dtest=ClassName#method       # single method
./mvnw spring-boot:run                    # run locally
```

Test naming: `*IntegrationTests.java` / `*IntegrationTest.java` use Testcontainers (require Docker). `*Test.java` use mocks/H2 and run without Docker.

## Local Development Setup

**Preferred — apps local, infra in Docker:**
```bash
cd infrastructure
docker compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector
# Then in separate terminals:
cd discovery-server  && ./mvnw spring-boot:run
cd api-gateway       && ./mvnw spring-boot:run
cd product-service   && ./mvnw spring-boot:run
cd inventory-service && ./mvnw spring-boot:run
cd order-service     && ./mvnw spring-boot:run
```

**Everything in Docker:**
```bash
cd infrastructure && docker compose up -d
```
Docker Compose pulls `ashishjha/orderproducts-*:latest` from Docker Hub (`pull_policy: always`).

## Architecture

### Services & Ports
| Service           | Port             | Notes                              |
|-------------------|------------------|------------------------------------|
| API Gateway       | 8080             | Single ingress                     |
| Discovery Server  | 8761             | Eureka                             |
| Inventory Service | dynamic (Eureka) | gRPC server on 9090                |
| Order Service     | dynamic (Eureka) |                                    |
| Product Service   | dynamic (Eureka) | Standalone; not in core order flow |

### Outbox Pattern (Order → Inventory)
Order Service writes an outbox row to MySQL in the same transaction as the order — it never publishes to Kafka directly. Debezium reads the binlog and publishes to `outbox.event.Order`. Inventory Service consumes that topic to transition reservations to `FULFILLED` or `CANCELLED`.

Debezium connector config: `infrastructure/debezium_connector_config/mysql-outbox-connector.json`.

### Database Schemas
`ddl-auto=none` — schemas are owned by SQL init scripts in `infrastructure/mysql_init_scripts/`, not Hibernate. Do not rely on Hibernate to create or alter tables.

Two isolated MySQL databases: `inventory_db` (inventory_user) and `order_db` (order_user).

### gRPC Proto Sync
The `.proto` file exists in **both** services and must be kept in sync manually:
- `inventory-service/src/main/proto/reservation_service.proto`
- `order-service/src/main/proto/reservation_service.proto`

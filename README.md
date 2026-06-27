# OrderProducts System Architecture

## System Overview
Microservices order & inventory management system with Spring Boot services, service discovery, API gateway, and event-driven architecture.

## Services

### Core Services
- **API Gateway** (8080): Single entry point, routes to microservices via REST
- **Discovery Server** (8761): Eureka service registry for dynamic discovery
- **Inventory Service**: Internal service managing stock, exposes REST and gRPC
- **Order Service**: Handles order processing, validates stock via inventory; GraphQL federation subgraph
- **Product Service**: Product catalog management; GraphQL federation subgraph

### GraphQL Federation
- **Apollo Router** (4000): Supergraph gateway — composes `product-service` and `order-service` into a single GraphQL API. Clients query one endpoint; the router fans out to subgraphs and merges the result.

### Infrastructure
- **MySQL**: Inventory & Order data
- **MongoDB**: Product catalog
- **Kafka**: Event streaming between services
- **Zipkin**: Distributed tracing
- **Debezium**: CDC for database events

## Communication Patterns

### Synchronous
- REST APIs via API Gateway using Spring Cloud LoadBalancer
- gRPC for internal inventory operations (port 9090)
- Service-to-service via Eureka discovery

### Asynchronous
- Kafka events for order notifications
- Debezium CDC for database change events

## Data Flow

1. **Product Creation**: Client → API Gateway → Product Service → MongoDB
2. **Order Placement**: 
   - Client → API Gateway → Order Service
   - Order Service → Inventory Service (stock check via REST/gRPC)
   - Order Service → MySQL (persist order)
   - Order Service → Kafka (notification event)
3. **Inventory Check**: Order Service → Inventory Service → MySQL

## Technology Stack
- **Framework**: Spring Boot 3.x, Spring Cloud
- **Languages**: Java 21, Kotlin
- **Databases**: MySQL 8, MongoDB 7
- **Messaging**: Apache Kafka
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway (REST), Apollo Router (GraphQL federation)
- **GraphQL**: Spring for GraphQL, Apollo Router, rover CLI
- **Tracing**: Zipkin, Micrometer
- **Testing**: TestContainers, Mockito, Spring Cloud Contract
- **Build**: Maven

## Resilience Patterns
- Circuit Breaker (Resilience4J) in Order Service
- Service discovery with automatic failover
- Retry mechanisms for transient failures

## Security Considerations
- Internal services (Inventory) not exposed externally
- API Gateway as single entry point
- Separate database users with minimal privileges

## Setup & Deployment

### CASE 1: Local Debug & Development (REST only)

Run infrastructure in Docker, applications locally for debugging. Uses the REST API Gateway on port 8080.

```bash
# Start only infrastructure (MySQL, MongoDB, Kafka, Zipkin, etc.)
cd infrastructure
docker compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector

# Run each application locally in separate terminals or IDE
cd ../discovery-server && mvn spring-boot:run
cd ../api-gateway && mvn spring-boot:run
cd ../product-service && mvn spring-boot:run
cd ../inventory-service && mvn spring-boot:run
cd ../order-service && mvn spring-boot:run
```

REST endpoints are available via `http://localhost:8080`.

### CASE 2: Local Debug & Development (with GraphQL Federation)

Same as Case 1 but also starts the Apollo Router so you can query the federated GraphQL supergraph. The router runs in Docker and requires product-service and order-service on fixed ports so it can reach them on the host.

```bash
# Start infrastructure
cd infrastructure
docker compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector

# Run discovery server and other services normally
cd ../discovery-server && mvn spring-boot:run
cd ../api-gateway && mvn spring-boot:run
cd ../inventory-service && mvn spring-boot:run

# Run product-service and order-service on the ports the router expects
cd ../product-service && mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8082"
cd ../order-service && ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8084"

# Start only the router (--no-deps skips trying to start Dockerised service containers)
cd ../infrastructure && docker compose up apollo-router --no-deps -d
```

The federated GraphQL endpoint is at `http://localhost:4000/`. Apollo Sandbox UI is also available there.

If you change either service's `schema.graphqls`, regenerate the supergraph before restarting the router:
```bash
cd infrastructure
APOLLO_ELV2_LICENSE=accept rover supergraph compose --config supergraph.yaml 2>/dev/null > supergraph.graphql
docker compose restart apollo-router
```

### CASE 3: Running Everything as Docker Containers (Locally)

Complete dockerised environment. The Apollo Router starts automatically and uses the container names to reach the services.

```bash
# Start all services (infrastructure + applications + Apollo Router)
cd infrastructure
docker compose up -d

# Check service status
docker compose ps

# View logs for specific service
docker compose logs -f apollo-router
docker compose logs -f order-service

# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v
```

REST is available via `http://localhost:8080`. GraphQL federation is available via `http://localhost:4000/`.

### CASE 4: Access application via AWS

Setup EC2 using terraform. See [terraform-setup-readme](/infrastructure/aws_setup/terraform-ec2/README.md).

Connect to AWS using SSH

```bash
ssh -i orderproducts-ec2-key.pem ec2-user@[ec2-public-ip]
```

## Next steps

- Improve AWS Setup - see [aws_roadmap](/infrastructure/aws_setup/1_deploy_to_aws_roadmap.md).
- Scalability & observability in AWS (e.g. Auto scaling, Rate limiting, ALB & WAF, ACM certificate and AWS Cloud Watch monitoring).
- CI/CD setup for project (e.g. generate image, deploy to cloud)
- Dev environment setup.
- Migrate to Spring Boot 4.x
- Become cloud-native.
- Decouple order-service and inventory-service by coordinating only via events.
- Improve event-handling (e.g. versioning for schema evolution, DLQs, monitoring for event processing pipeline).
- Setup to update libraries automatically (useful for security and modernisation).
- Enhance inventory-service.  See [inventory-service-readme-next-steps](/inventory-service/README.md).
- Enhance product-service. See [product-service-readme-next-steps](/product-service/README.md).
- Add frontend (e.g. to see list of products, take order if stock is available, CRUD for inventory).
- Authentication & authorisation.

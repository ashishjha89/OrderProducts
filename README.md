# OrderProducts System Architecture

## System Overview
Microservices order & inventory management system with Spring Boot services, service discovery, API gateway, and event-driven architecture.

## Services

### Core Services
- **API Gateway** (8080): Single entry point, routes to microservices
- **Discovery Server** (8761): Eureka service registry for dynamic discovery
- **Inventory Service**: Internal service managing stock, exposes REST and gRPC
- **Order Service**: Handles order processing, validates stock via inventory
- **Product Service** (currently unused for core flow): Product catalog management

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
- **Languages**: Java 21
- **Databases**: MySQL 8, MongoDB 7
- **Messaging**: Apache Kafka
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
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

### CASE 1: Local Debug & Development

Run infrastructure components as docker containers, while run applications locally for debugging.

```bash
# Start only infrastructure (MySQL, MongoDB, Kafka, Zipkin, etc.)
cd infrastructure
docker-compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector

# Run each application locally in separate terminals or IDE
cd ../discovery-server && mvn spring-boot:run
cd ../api-gateway && mvn spring-boot:run
cd ../product-service && mvn spring-boot:run
cd ../inventory-service && mvn spring-boot:run
cd ../order-service && mvn spring-boot:run
```

### CASE 2: Running Infrastructure and Applications as Docker Containers (Locally)

Complete dockerized environment.

```bash
# Start all services (infrastructure + applications)
cd infrastructure
docker-compose up -d

# Check service status
docker-compose ps

# View logs for specific service
docker-compose logs -f api-gateway

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### CASE 3: Access application via AWS

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
- Enhance product-service. See [inventory-service-readme-next-steps](/inventory-service/README.md).
- Enhance inventory-service.  See [product-service-readme-next-steps](/product-service/README.md).
- Add frontend (e.g. to see list of products, take order if stock is available, CRUD for inventory).
- Authentication & authorisation.

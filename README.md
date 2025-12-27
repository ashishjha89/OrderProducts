# OrderProducts System Architecture

## System Overview
Microservices e-commerce system with Spring Boot services, service discovery, API gateway, and event-driven architecture.

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

### CASE 2: Running Infrastructure and Applications as Docker Containers
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

### Service Access Points

**Main Entry:**
- API Gateway: http://localhost:8080
  - Products: `http://localhost:8080/api/products`
  - Orders: `http://localhost:8080/api/order`

**Monitoring:**
- Eureka Dashboard: http://localhost:8761
- Zipkin Tracing: http://localhost:9411

**Infrastructure:**
- MySQL: `localhost:3306`
- MongoDB: `localhost:27017`
- Kafka: `localhost:9092`

### AWS Deployment Notes

When deploying to AWS, you'd only need to:
1. Put API Gateway behind AWS ALB (Application Load Balancer)
2. Remove Eureka/Zipkin ports (or keep in private subnet)
3. Use AWS managed databases (RDS for MySQL, DocumentDB for MongoDB, MSK for Kafka)
4. Deploy services to ECS (Elastic Container Service) or EKS (Elastic Kubernetes Service)
5. Configure VPC with private subnets for internal services
6. Use AWS Secrets Manager for credentials

### Next Action Items

- [ ] Review `docker-compose.yml` to see if the ports need to be exposed (e.g. can we definitely remove for: "zookeeper, broker, connect, mysql, mongodb" and potentially also remove for "zipkin and discovery-server").
- [ ] Review if there is a need to keep separate copy for EC2, e.g. `docker-compose.ec2.yml`.
- [ ] Review `application.properties` from all services to make them EC2 ready.

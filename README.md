# OrderProducts System Architecture

## System Overview
Microservices e-commerce system with Spring Boot services, service discovery, API gateway, and event-driven architecture.

## Services

### Core Services
- **API Gateway** (8080): Single entry point, routes to microservices
- **Discovery Server** (8761): Eureka service registry for dynamic discovery
- **Inventory Service**: Internal service managing stock, exposes REST and gRPC
- **Order Service**: Handles order processing, validates stock via inventory
- **Product Service**: Product catalog management

### Infrastructure
- **MySQL**: Inventory & Order data (port 3306)
- **MongoDB**: Product catalog (port 27017)
- **Kafka**: Event streaming between services (port 9092)
- **Zipkin**: Distributed tracing (port 9411)
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

## Quick links and tips

### Eureka dashboard
http://localhost:8080/eureka/web

### Zipkin dashboard
http://localhost:9411/zipkin/

### Clean install all services
```
./clean_install_services.sh
```

### Run all services
```
./run_services.sh
```

### Stop all services
```
./stop_services.sh
```
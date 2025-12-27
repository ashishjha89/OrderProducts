# Order Service

## Overview

This is a Spring MVC project designed to manage orders.

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```

## Push the container image to DockerHub

```bash
VERSION=0.1.0
docker build -t ashishjha/orderproducts-order-service:$VERSION -t ashishjha/orderproducts-order-service:latest .

docker push ashishjha/orderproducts-order-service:$VERSION
docker push ashishjha/orderproducts-order-service:latest
```

## Prerequisites

Before running the service:
- Ensure the `discovery-server` (Eureka server) is running to enable service registration and discovery.
- Ensure the `api-gateway` is running so that this service is available via port 8080.
- Ensure the `inventory-service` is running to check stock availability.
- Ensure that `infrastructure` is setup (see parent directory of project). This service relies on: MySQL, Kafka and Zipkin.

## API Documentation

The API documentation is available at:

```
http://localhost:8080/api/order/swagger-ui/index.html
```

## Supported Endpoints

The service supports the following REST endpoints:

- **POST** `/api/order`: Posts order details after verifying stock availability (from `inventory-service`).

## Testing

### Test Frameworks Used
- **TestContainers**: For integration tests with MySQL
- **MockMvc**: For testing REST endpoints
- **Mockito**: For unit testing with mocks

### Types of Tests
- **Unit Tests**: Testing individual components using Mockito
- **Integration Tests**: Using TestContainers for MySQL integration
- **Controller Tests**: Using MockMvc for API endpoint testing

## Technologies used
- Integration tests
   - TestContainers
   - Consumer Driven Contracts (spring-cloud-contract)
- MySql
- Kafka
- CircuitBreakers (Resilience4J)
- Distributed tracking (Zipkin & micrometer)
- Swagger
- Integration with Eureka Discovery Server and Api Gateway

## Note about Debezium
After running `docker compose up -d`, you can check if the connector registered successfully by running:
```bash
curl localhost:8083/connectors
```

You should see "mysql-outbox-connector" listed. You can also check its status:
```bash
curl localhost:8083/connectors/mysql-outbox-connector/status
```
It should show RUNNING.

## Next Steps

- Update `application.properties` to make it production-ready.
- Implement authorizations according to different operations.

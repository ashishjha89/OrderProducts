# Order Service

## Overview

This is a Spring MVC project designed to manage orders with thorough unit and integration tests.

## Prerequisites

Before running the service, ensure the following dependencies are set up:

1. **MongoDB**: Used to store order data. You can start MongoDB using:
   ```bash
   brew services start mongodb-community
   ```

2. **Zipkin Server**: Used to visualize traces for distributed tracing. Start the Zipkin server using:
   ```bash
   docker run -d -p 9411:9411 openzipkin/zipkin
   ```

3. Ensure the `discovery-server` (Eureka server) is running to enable service registration and discovery.

4. Ensure the `api-gateway` is running so that this service is available via port 8080.

5. Ensure the `inventory-service` is running to check stock availability.

## API Documentation

The API documentation is available at:

```
http://localhost:8080/api/order/swagger-ui/index.html
```

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
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

Run tests using:

```bash
mvn test
```

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

- Implement logic to allow posting order if SKU quantities are in the inventory.
- Implement logic to manage SKU quantities in the inventory.
- Use outbox-pattern to ensure that order creation and related updates are atomic.
- Update `application.properties` to make it production-ready.
- Implement authorizations according to different operations.

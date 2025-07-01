# Inventory Service

## Overview

This is a Spring MVC project designed to manage product inventory.
This service is intended for internal use only and is not exposed through the API Gateway.

## Prerequisites

Before running the service, ensure the following dependencies are set up:

1. **MySQL**: Used as the primary database. You can start MySQL using:
   ```bash
   brew services start mysql
   ```

2. **Zipkin Server**: Used to visualize traces for distributed tracing. Start the Zipkin server using:
   ```bash
   docker run -d -p 9411:9411 openzipkin/zipkin
   ```

3. Ensure the `discovery-server` (Eureka server) is running to enable service registration and
   discovery.

## API Documentation

The API documentation is available at:
```
http://localhost:8080/api/inventory/swagger-ui/index.html
```

## Supported Endpoints

The service supports the following REST endpoints:

- **GET** `/api/inventory/{sku-code}`: Check if a specific SKU is in stock
- **GET** `/api/inventory?skuCode=<code1>&skuCode=<code2>`: Check stock status for multiple SKUs
- **POST** `/api/inventory`: Create new inventory entry
- **DELETE** `/api/inventory/{sku-code}`: Delete an inventory entry

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```

## Testing

The project includes comprehensive testing using various frameworks and approaches:

### Test Frameworks Used
- **TestContainers**: For integration tests with MySQL
- **MockMvc**: For testing REST endpoints
- **Mockito**: For unit testing with mocks

### Types of Tests
- **Unit Tests**: Testing individual components using Mockito
- **Integration Tests**: Using TestContainers for MySQL integration
- **Controller Tests**: Using MockMvc for API endpoint testing

Run all tests using:
```bash
mvn test
```

## Technologies used
- Integration tests
   - TestContainers
   - Consumer Driven Contracts (spring-cloud-contract)
- MySql
- Distributed tracking (Zipkin & micrometer)
- Swagger
- Integration with Eureka Discovery Server and Api Gateway

## Next Steps
- Add endpoints to update SKU quantities.
- Add pagination to get all SKUs.
- Update `application.properties` to make it production-ready.
- Implement authorizations according to different operations.

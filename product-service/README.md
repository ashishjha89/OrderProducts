# Product Service

## Overview

This is a Spring MVC project designed to manage products (store & retrieve), with thorough unit and integration tests.

## Prerequisites

Before running the service, ensure the following dependencies are set up:

1. **MongoDB**: Used to store "Products." You can start MongoDB using:
   ```bash
   brew services start mongodb-community
   ```

2. **Zipkin Server**: Used to visualize traces for distributed tracing. Start the Zipkin server using:
   ```bash
   docker run -d -p 9411:9411 openzipkin/zipkin
   ```

3. Ensure the `discovery-server` (Eureka server) is running to enable service registration and
   discovery.

4. Ensure the `api-gateway` is running so that this service is available via port 8080.

## API Documentation

The API documentation is available at:

```
http://localhost:8080/api/product/swagger-ui/index.html
```

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```

## Testing

The project includes:

- **Unit Tests**: To validate individual components.
- **Integration Tests**: To ensure the application works as a whole.

Run tests using:

```bash
mvn test
```

## Technologies used
- Integration tests with TestContainers
- MongoDB
- Distributed tracking (Zipkin & micrometer)
- Swagger
- Integration with Eureka Discovery Server and Api Gateway

## Next Steps
- Add endpoints to update product.
- Add endpoint to get all products with pagination.
- Update `application.properties` to make it production-ready.
- Implement authorizations for create, update, and delete operations.
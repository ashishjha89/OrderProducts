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

3. **Discovery Server**: Ensure the `discovery-server` (Eureka server) is running to enable service registration and
   discovery.

## API Documentation

The API documentation is available at:

```
http://localhost:<dynamic-port>/api/product/swagger-ui/index.html
```

> **Note**: The port is dynamically assigned. Check the logs during application startup to find the correct port.

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
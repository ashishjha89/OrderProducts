# Product Service

## Overview

This is a Spring MVC project designed to manage products (store & retrieve).

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```

## Push the container image to DockerHub

```bash
VERSION=0.1.0
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ashishjha/orderproducts-product-service:$VERSION \
  -t ashishjha/orderproducts-product-service:latest \
  --push .
```

## Prerequisites

Before running the service:
- Ensure that `discovery-server` (Eureka server) is running to enable service registration and discovery.
- Ensure that `api-gateway` is running.
- Ensure that `infrastructure` is setup (see parent directory of project). This service relies on: MongoDB and Zipkin.

## API Documentation

The API documentation is available at:

```
http://localhost:8080/api/product/swagger-ui/index.html
```

## Supported Endpoints

The service supports the following REST endpoints:

- **POST** `/api/products`: Create a product.
- **GET** `/api/products`: Get all products.

## Testing

### Test Frameworks Used
- **TestContainers**: For integration tests with MongoDB.
- **MockMvc**: For testing REST endpoints.
- **Mockito**: For unit testing with mocks.

### Types of Tests
- **Unit Tests**: Testing individual components using Mockito.
- **Integration Tests**: Using TestContainers for MySQL integration.
- **Controller Tests**: Using MockMvc for API endpoint testing.

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
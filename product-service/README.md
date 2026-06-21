# Product Service

## Overview

A Kotlin Spring WebFlux service for managing products (store & retrieve). Uses coroutines throughout — `CoroutineCrudRepository` for MongoDB and `suspend` functions in the service and controller layers — so no thread is ever blocked waiting for I/O.

## Running the Application

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
- Ensure that `infrastructure` is set up (see parent directory). This service relies on: MongoDB and Zipkin.

## API Documentation

```
http://localhost:8080/api/product/swagger-ui/index.html
```

## Supported Endpoints

- **POST** `/api/products`: Create a product.
- **GET** `/api/products`: Get all products.

## Testing

### Test Frameworks Used
- **TestContainers**: Integration tests with a real MongoDB container.
- **WebTestClient**: Controller-layer tests (replaces MockMvc for WebFlux).
- **Mockito-Kotlin**: Unit tests with Kotlin-friendly mock DSL.

### Types of Tests
- **Unit tests** (`*Test.kt`): Pure Mockito-Kotlin mocks, no Docker needed.
- **Controller tests** (`*ControllerTest.kt`): `@WebFluxTest` slice with `WebTestClient`.
- **Integration tests** (`*ApplicationTests.kt`): Testcontainers spins up a real MongoDB instance.

## Technologies Used
- **Kotlin** with coroutines (`kotlinx-coroutines-core`, `kotlinx-coroutines-reactor`)
- **Spring WebFlux** (non-blocking HTTP layer)
- **Spring Data MongoDB Reactive** with `CoroutineCrudRepository`
- **Testcontainers** for integration tests
- **Zipkin & Micrometer** for distributed tracing
- **Swagger / SpringDoc OpenAPI** (`springdoc-openapi-starter-webflux-ui`)
- **Eureka** service discovery via API Gateway

## Next Steps
- Add endpoint to update a product.
- Add endpoint to get all products with pagination.
- Implement authentication and authorisation for create, update, and delete operations.

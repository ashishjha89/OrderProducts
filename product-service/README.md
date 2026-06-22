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

### REST
- **POST** `/api/products`: Create a product.
- **GET** `/api/products`: Get all products.

### GraphQL
Endpoint: **POST** `/graphql`

**Get all products**
```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ products { id name description price } }"}'
```
```json
{
  "data": {
    "products": [
      { "id": "abc123", "name": "iPhone", "description": "Apple phone", "price": 999 }
    ]
  }
}
```

**Create a product**
```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { createProduct(input: { name: \"iPhone\", description: \"Apple phone\", price: 999 }) { productId } }"}'
```
```json
{
  "data": {
    "createProduct": { "productId": "abc123" }
  }
}
```

**Error format**

Errors are returned in the `errors` array alongside a `null` data field. The `extensions.code` follows the Apollo convention.

```json
{
  "data": { "createProduct": null },
  "errors": [
    {
      "message": "This is an incorrect request-body",
      "extensions": { "code": "BAD_USER_INPUT", "classification": "ValidationError" }
    }
  ]
}
```

| `extensions.code`       | Cause                          |
|-------------------------|--------------------------------|
| `BAD_USER_INPUT`        | Blank or invalid field value   |
| `INTERNAL_SERVER_ERROR` | Unexpected server-side failure |

GraphiQL browser IDE available at `/graphiql` - e.g. `http://localhost:<port>/graphiql`.
For examples:
```graphql
# Get all products
query GetAllProducts {
  products {
    id
    name
    description
    price
  }
}

# Create a product
mutation CreateProduct($input: CreateProductInput!) {
    createProduct(input: $input) {
        productId
    }
}
{
    "input": {
        "name": "MacBook Pro",
        "description": "Apple laptop",
        "price": 2500
    }
}
```

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

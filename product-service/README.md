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
  -d '{"query":"mutation { createProduct(input: { name: \"iPhone\", description: \"Apple phone\", price: 999, skuCode: \"iphone\" }) { productId } }"}'
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
        "price": 2500,
        "skuCode": "macbook-pro"
    }
}
```

## GraphQL Federation

This service is a **federation-compliant subgraph**. In addition to the domain queries above, it implements two internal queries that the Apollo Router calls automatically — clients never call these directly.

### What "subgraph" means here

In a federated architecture, a **subgraph** is an individual GraphQL service that owns a slice of the total schema. An Apollo Router (or similar gateway) sits in front of all subgraphs, composes their schemas into a single unified **supergraph**, and routes client queries to the right services transparently.

This service declares `Product` as a **federation entity** with two `@key` directives:

```graphql
type Product @key(fields: "id") @key(fields: "skuCode") {
    id: String!
    name: String!
    description: String!
    price: BigDecimal!
    skuCode: String
}
```

**Why two keys?** The `@key` directive is `repeatable` — a type can have multiple keys. The router picks whichever key it has available:
- `id` is the MongoDB document identifier. Used when product-service resolves its own entities.
- `skuCode` is the business identifier shared with other services. `order-service` stores `skuCode` on each order line item, so when the router resolves `product` from an order line item, it uses this key to hydrate from product-service.

### Federation endpoints

**`_service { sdl }`** — called by the router at startup to discover this subgraph's schema.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _service { sdl } }"}'
```

**`_entities` by `id`** — router calls this when it has a product's internal id.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _entities(representations: [{__typename: \"Product\", id: \"abc123\"}]) { ... on Product { id name skuCode } } }"}'
```

**`_entities` by `skuCode`** — router calls this when order-service returns a `product: { skuCode: "iphone" }` stub.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _entities(representations: [{__typename: \"Product\", skuCode: \"iphone\"}]) { ... on Product { id name skuCode } } }"}'
```
```json
{
  "data": {
    "_entities": [
      { "id": "abc123", "name": "iPhone", "skuCode": "iphone" }
    ]
  }
}
```

If a `Product` with the given key does not exist, `null` is returned for that position in the list.

### Schema file layout

There are two GraphQL schema files in `src/main/resources/graphql/`. Spring for GraphQL merges all `.graphqls` files in that directory at startup, so together they form the full runtime schema.

**`schema.graphqls` — user-authored schema (single source of truth)**

Contains the domain types (`Product`, `CreatedProduct`), the `products` query, the `createProduct` mutation, input types, and federation directive declarations (`@key`). This file is also what `_service { sdl }` returns to the Apollo Router — `ProductGraphQLController.kt` reads it directly from the classpath. Because this file excludes the federation runtime built-ins, the router sees exactly what it expects during supergraph composition with no duplicate definition errors.

**`federation.graphqls` — federation runtime plumbing (Spring only)**

These are never returned to the router — they exist solely so Spring can route those incoming requests to the controller methods.

Uses `extend type Query` (not `type Query`) because `schema.graphqls` already defines `type Query { products }` — this file extends it with the federation-internal fields.

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

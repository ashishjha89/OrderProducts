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

## GraphQL Federation

This service is a **federation-compliant subgraph**. In addition to the domain queries above, it implements two internal queries that the Apollo Router calls automatically — clients never call these directly.

### What "subgraph" means here

In a federated architecture, a **subgraph** is an individual GraphQL service that owns a slice of the total schema. An Apollo Router (or similar gateway) sits in front of all subgraphs, composes their schemas into a single unified **supergraph**, and routes client queries to the right services transparently.

This service declares `Product` as a **federation entity** by annotating it with `@key(fields: "id")` in the schema:

```graphql
type Product @key(fields: "id") {
    id: String!
    name: String!
    description: String!
    price: BigDecimal!
}
```

`@key` means: "A `Product` can be uniquely identified by its `id`. If another subgraph (e.g. `order-service`) holds a `Product` stub containing only an `id`, the router knows to call this service to hydrate the remaining fields."

### Federation endpoints

**`_service { sdl }`** — called by the router at startup to discover this subgraph's schema.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _service { sdl } }"}'
```
```json
{
  "data": {
    "_service": {
      "sdl": "scalar BigDecimal\n\ndirective @key(...) ...\n\ntype Product @key(fields: \"id\") { ... }"
    }
  }
}
```

**`_entities`** — called by the router at query execution time to hydrate `Product` stubs referenced by other subgraphs. Accepts a list of **representations** (objects containing `__typename` and key fields) and returns the fully resolved entities.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _entities(representations: [{__typename: \"Product\", id: \"abc123\"}]) { ... on Product { id name description price } } }"}'
```
```json
{
  "data": {
    "_entities": [
      { "id": "abc123", "name": "iPhone", "description": "Apple phone", "price": 999 }
    ]
  }
}
```

If a `Product` with the given `id` does not exist, `null` is returned for that position in the list (the federation spec allows nullable elements in the `_entities` response).

### Two schema representations

There are two distinct GraphQL schema representations in this service, each serving a different audience:

**`src/main/resources/graphql/schema.graphqls` — the operational schema**

Read by the Spring for GraphQL engine at startup to build the runtime. It must include the federation built-in types (`_Any`, `_Service`, `_Entity`) and the federation queries (`_service`, `_entities`) so the service can handle those requests at runtime.

**`SUBGRAPH_SDL` constant in `ProductGraphQLController.kt` — the advertised schema**

Returned to the router via `_service { sdl }`. This is what the router uses for supergraph composition. It contains only the user-authored schema: the domain types (`Product`, `CreatedProduct`), the domain queries (`products`, `createProduct`), and the `@key` directive usage. It deliberately excludes the federation built-ins (`_entities`, `_service`, `_Entity`, `_Service`, `_Any`) because the router already knows about those and would produce duplicate definition errors if they appeared in the subgraph SDL during composition.

This duplication is an artifact of the **manual federation implementation** used here (no third-party federation library). In a production setup, a library such as `federation-graphql-java-support` or the Netflix DGS Framework eliminates the duplication: you author one clean schema, and the library programmatically injects the federation built-ins into the runtime schema and derives the `_service { sdl }` response from your clean schema automatically.

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

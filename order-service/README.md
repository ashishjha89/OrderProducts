# Order Service

## Overview

This is a Spring MVC project designed to manage orders.

## Setup & Deployment

### Prerequisites

This service depends on the following infrastructure (start via `cd infrastructure && docker compose up -d mysql zookeeper broker zipkin`):

- **MySQL** — order storage (`order_db`)
- **Kafka** — order event publishing
- **Zipkin** — distributed tracing
- **Discovery Server (Eureka)** — service registration; also needed by `api-gateway`
- **Inventory Service** — stock availability checks (via gRPC by default)

### Running standalone (REST + GraphQL, no federation)

Any port works. Eureka assigns one dynamically at startup.

```bash
./mvnw spring-boot:run
```

GraphQL is available at `/graphql` on whichever port Spring selects. Check the startup log for the port.

### Running with Apollo Router (federated GraphQL)

The Apollo Router runs in Docker and needs to reach this service on the host. Its `override_subgraph_url` in `infrastructure/router.yaml` is hardcoded to port **8084** for order-service. Use that port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8084"
```

Then start the router (from the `infrastructure` directory):

```bash
docker compose up apollo-router --no-deps -d
```

The supergraph endpoint is at `http://localhost:4000/`.

### Push the container image to DockerHub

```bash
VERSION=0.1.0
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ashishjha/orderproducts-order-service:$VERSION \
  -t ashishjha/orderproducts-order-service:latest \
  --push .
```

## API Documentation

The API documentation is available at:

```
http://localhost:8080/api/order/swagger-ui/index.html
```

## Supported Endpoints

### REST

- **POST** `/api/order`: Place an order after verifying stock availability (from `inventory-service`).

### GraphQL

Endpoint: **POST** `/graphql`

**Place an order**

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { placeOrder(input: { orderLineItems: [{ skuCode: \"samsung-s10\", price: 100, quantity: 1 }] }) { orderId orderNumber } }"}'
```

```json
{
  "data": {
    "placeOrder": {
      "orderId": "1",
      "orderNumber": "a27e6807-7795-4cab-88b9-11d02b352373"
    }
  }
}
```

**Error format**

Errors are returned in the `errors` array alongside a `null` data field. The `extensions.code` follows the Apollo
convention.

```json
{
  "data": {
    "placeOrder": null
  },
  "errors": [
    {
      "message": "This product is not in stock.",
      "extensions": {
        "code": "INVENTORY_NOT_IN_STOCK",
        "classification": "ValidationError"
      }
    }
  ]
}
```

| `extensions.code`        | Cause                             |
|--------------------------|-----------------------------------|
| `BAD_USER_INPUT`         | Empty or missing order line items |
| `INVENTORY_NOT_IN_STOCK` | One or more products out of stock |
| `INTERNAL_SERVER_ERROR`  | Unexpected server-side failure    |

GraphiQL browser IDE available at `/graphiql` — e.g. `http://localhost:<port>/graphiql`.

```graphql
# Place an order
mutation PlaceOrder($input: PlaceOrderInput!) {
    placeOrder(input: $input) {
        orderId
        orderNumber
    }
}
# Variables:
{
    "input": {
        "orderLineItems": [
            { "skuCode": "samsung-s10", "price": 100, "quantity": 1 }
        ]
    }
}
```

## GraphQL Federation

This service is a **federation-compliant subgraph**. In addition to the `placeOrder` mutation above, it implements two internal queries that the Apollo Router calls automatically — clients never call these directly.

### What "subgraph" means here

In a federated architecture, an Apollo Router sits in front of all subgraphs and routes client queries to the right services. This service declares `PlacedOrder` as a **federation entity** and references `Product` from `product-service`:

```graphql
type PlacedOrder @key(fields: "orderNumber") {
    orderId: String!
    orderNumber: String!
    lineItems: [OrderLineItem!]!
}

type OrderLineItem {
    skuCode: String!
    price: BigDecimal!
    quantity: Int!
    product: Product
}

type Product @key(fields: "skuCode") @external {
    skuCode: String!
}
```

**`@key(fields: "orderNumber")` on `PlacedOrder`**: A `PlacedOrder` can be uniquely identified by `orderNumber`. If another subgraph holds a stub with only `orderNumber`, the router calls this service to hydrate the remaining fields.

**Why `orderNumber` not `orderId`?** `orderId` is the stringified MySQL auto-increment ID (e.g. `"7"`) — a DB artefact with no meaning outside this service. `orderNumber` is a business UUID generated at order-creation time, stable and meaningful across service boundaries.

**`@key(fields: "skuCode") @external` on `Product`**: This is a **cross-service reference**. Each order line item knows the `skuCode` of the product it ordered. By declaring `Product @external`, order-service tells the router: "I don't own `Product` — product-service does. When a client queries `lineItems { product { name price } }`, I'll return stubs `{ skuCode: "samsung-s10" }` and the router will call product-service's `_entities` to hydrate the full product."

**`@external`** means: this type is owned by another subgraph. Order-service declares only the fields it needs for the reference (`skuCode`), not the full `Product` schema.

**Why this design matters:** The router handles the cross-service join transparently. A client can query:
```graphql
mutation {
    placeOrder(input: ...) {
        orderNumber
        lineItems {
            skuCode
            product { name price }   # resolved by product-service, not order-service
        }
    }
}
```
Without federation, this would require order-service to call product-service directly and compose the response itself, introducing coupling. With federation, the router handles it.

### Federation endpoints

**`_service { sdl }`** — called by the router at startup to discover this subgraph's schema.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _service { sdl } }"}'
```

**`placeOrder` with lineItems and product stub**

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { placeOrder(input: { orderLineItems: [{ skuCode: \"samsung-s10\", price: 100, quantity: 1 }] }) { orderId orderNumber lineItems { skuCode product { skuCode } } } }"}'
```
```json
{
  "data": {
    "placeOrder": {
      "orderId": "7",
      "orderNumber": "cb3c03a6-...",
      "lineItems": [{ "skuCode": "samsung-s10", "product": { "skuCode": "samsung-s10" } }]
    }
  }
}
```

The `product` object here is a **stub** — it contains only `skuCode` (the federation key). The Apollo Router uses this to call `product-service._entities([{__typename: "Product", skuCode: "samsung-s10"}])` and merge the full product data into the response.

**`_entities`** — called by the router to hydrate `PlacedOrder` stubs referenced by other subgraphs.

```bash
curl -X POST http://localhost:<port>/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ _entities(representations: [{__typename: \"PlacedOrder\", orderNumber: \"ORD-001\"}]) { ... on PlacedOrder { orderId orderNumber lineItems { skuCode product { skuCode } } } } }"}'
```

If a `PlacedOrder` with the given `orderNumber` does not exist, `null` is returned for that position in the list.

### Schema file layout

There are two GraphQL schema files in `src/main/resources/graphql/`. Spring for GraphQL merges all `.graphqls` files in that directory at startup, so together they form the full runtime schema.

**`schema.graphqls` — user-authored schema (single source of truth)**

Contains the domain types (`PlacedOrder`, `OrderLineItem`), the `placeOrder` mutation, input types, and federation directive/type declarations (`@key`, `@external`, `Product @external`). This file is also what `_service { sdl }` returns to the Apollo Router — `OrderGraphQLController.java` reads it directly from the classpath. Because this file excludes the federation runtime built-ins, the router sees exactly what it expects during supergraph composition with no duplicate definition errors.

Note that order-service has **no user-facing Query fields**, so no `type Query` appears here. The router handles this correctly.

**`federation.graphqls` — federation runtime plumbing (Spring only)**

These are never returned to the router — they exist solely so Spring can route those incoming requests to the controller methods.

Uses `type Query` (not `extend type Query`) because order-service has no user-facing queries in `schema.graphqls` to extend.

## Testing

### Test Frameworks Used

- **TestContainers**: For integration tests with MySQL
- **WebTestClient**: For testing REST endpoints
- **GraphQlTester**: For testing GraphQL endpoints (`@GraphQlTest` slice)
- **Mockito**: For unit testing with mocks

### Types of Tests

- **Unit Tests** (`*Test.java`): Pure Mockito mocks, no Docker needed.
- **Controller Tests** (`*ControllerTest.java`): `WebTestClient` for REST; `GraphQlTester` for GraphQL.
- **Integration Tests** (`*IntegrationTest.java`, `*IntegrationTests.java`): Testcontainers spins up a real MySQL
  instance.

## Technologies used

- Integration tests
    - TestContainers
    - Consumer Driven Contracts (spring-cloud-contract)
- MySql
- Kafka
- CircuitBreakers (Resilience4J)
- Distributed tracking (Zipkin & micrometer)
- Swagger / SpringDoc OpenAPI
- GraphQL (Spring for GraphQL, graphql-java-extended-scalars)
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

- Decouple order-service and inventory-service by coordinating only via events.
- Improve event-handling (e.g. versioning for schema evolution, DLQs, monitoring for event processing pipeline).
- Authentication & authorisation.

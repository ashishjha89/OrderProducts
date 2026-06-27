# Order Service

## Overview

This is a Spring MVC project designed to manage orders.

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
  -t ashishjha/orderproducts-order-service:$VERSION \
  -t ashishjha/orderproducts-order-service:latest \
  --push .
```

## Prerequisites

Before running the service:

- Ensure the `discovery-server` (Eureka server) is running to enable service registration and discovery.
- Ensure the `api-gateway` is running so that this service is available via port 8080.
- Ensure the `inventory-service` is running to check stock availability.
- Ensure that `infrastructure` is setup (see parent directory of project). This service relies on: MySQL, Kafka and
  Zipkin.

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

### Two schema representations

**`src/main/resources/graphql/schema.graphqls` — the operational schema**

Read by the Spring for GraphQL engine at startup. It includes the federation built-in types (`_Any`, `_Service`, `_Entity`) and the federation queries (`_service`, `_entities`) so the service can handle those requests at runtime.

**`SUBGRAPH_SDL` constant in `OrderGraphQLController.java` — the advertised schema**

Returned to the router via `_service { sdl }`. Contains only the user-authored schema — `PlacedOrder @key(fields: "orderNumber")`, the `placeOrder` mutation, and the input types. Deliberately excludes the federation built-ins, because the router already knows about those and would produce duplicate definition errors during supergraph composition.

Note that order-service has **no user-facing Query fields**, so no `type Query` appears in the advertised SDL. The router handles this correctly — it only requires subgraphs to declare the queries they own, and `_entities`/`_service` are added by the router itself.

The `@external` directive and `Product @key(fields: "skuCode") @external` appear in the advertised SDL so the router knows order-service references a product entity owned by product-service. The router uses this during supergraph composition to build the query plan that routes `lineItems { product { ... } }` to product-service.

This duplication is an artifact of the **manual federation implementation** used here (no third-party federation library). A library such as `federation-graphql-java-support` or Netflix DGS would eliminate it by deriving the advertised SDL automatically from the authored schema.

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

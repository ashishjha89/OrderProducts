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

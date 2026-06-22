# inventory-service

## Important
This service is **not exposed through the API Gateway**. It is internal-only; calls from outside go directly to its dynamic Eureka port, not through 8080.

## Reservation State Machine
Reservations transition: `PENDING` → `FULFILLED` or `CANCELLED`

Available quantity = `on_hand_quantity` (inventory table) − sum of PENDING `reserved_quantity` (reservation table).

When an order fails after a reservation is created, a CANCELLED outbox event is written so inventory is released.

## CDC Contract Tests
Use Spring Cloud Contract (`spring-cloud-contract-maven-plugin`). `CdcBaseClass` is the base class for generated contract verifier tests.

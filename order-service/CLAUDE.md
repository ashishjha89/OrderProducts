# order-service

## Inventory Communication
Toggle between gRPC (default) and HTTP at startup via `application.properties`:
```properties
inventory.reservation.use-grpc=true   # gRPC (default)
inventory.reservation.use-grpc=false  # HTTP via WebClient + Eureka
```

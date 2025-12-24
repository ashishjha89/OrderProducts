# Infrastructure for OrderProducts

This directory contains the complete Docker Compose setup for both infrastructure components and microservices.

The `docker-compose.yml` defines:
- **Infrastructure**: MySQL, MongoDB, Kafka, Zookeeper, Zipkin, Debezium.
- **Microservices**: Discovery Server, API Gateway, Product Service, Inventory Service, Order Service.

## Databases

Note that `mysql_init_scripts` contains initial scripts to initialise databases.

### MySQL (Port 3306)
- **Purpose**: Relational data for inventory and orders.
- **Databases**:
  - `inventory_db`:
    - Purpose: Holds inventory and their stock levels.
    - Tables: `inventory` and `inventory_reservation`.
  - `order_db`:
    - Purpose: Holds order information.
    - Tables: `orders`, `order_line_items` and `outbox_event`.
- **Users**:
  - `inventory_user` / `kV33CaPPgSu1YuXJ`
  - `order_user` / `nS3johd59oQIcZhN`
  - `debezium_user` / `fBWsBYOGzcggYQfM` (CDC)

### MongoDB (Port 27017)
- **Purpose**: Document store for product catalog
- **Database**: `product-service`
- **Collections**: `products`

## Message Broker

### Kafka (Port 9092)
- **Purpose**: Event streaming and async communication
- **Topics**:
  - `notificationTopic`: Order events
  - `order.events.outbox`: CDC events

### Zookeeper (Port 2181)
- **Purpose**: Kafka coordination

## CDC (Change Data Capture)

See `debezium_connector_config`, `connect_config` for debezium related setup.

### Debezium Connect (Port 8083)
- **Purpose**: Database change streaming
- **Connectors**: MySQL outbox connector
- **Configuration**: JSON-based connector configs
- **REST API**: `http://localhost:8083/connectors`

## Microservices

### discovery-server (Port 8761)
- **Purpose**: Eureka service registry for service discovery
- **UI**: `http://localhost:8761`
- **Health**: Internal only, monitored via Docker healthcheck

### api-gateway (Port 8080)
- **Purpose**: Single entry point for all client requests
- **Routes**:
  - `/api/products/**` → Product Service
  - `/api/order/**` → Order Service
  - `/eureka/**` → Discovery Server UI
  - `/api/inventory/swagger-ui/**` → Inventory Swagger docs
- **Access**: `http://localhost:8080`

### product-service (Internal)
- **Purpose**: Product catalog management
- **Database**: MongoDB
- **Port**: 8080 (internal only, not exposed to host)
- **Access**: Via API Gateway or Eureka service discovery
- **Endpoints**: `/api/products`

### inventory-service (Internal)
- **Purpose**: Stock level management and reservations
- **Database**: MySQL (`inventory_db`)
- **Ports**: 
  - 8080 (HTTP REST - internal only)
  - 9090 (gRPC - internal only)
- **Access**: Via order-service only (not exposed to host or API Gateway)
- **Security**: Completely isolated, accessible only from within Docker network
- **Endpoints**:
  - REST: `/api/inventory`, `/api/reservations`
  - gRPC: Inventory reservation service

### order-service (Internal)
- **Purpose**: Order processing and fulfillment
- **Database**: MySQL (`order_db`)
- **Port**: 8080 (internal only, not exposed to host)
- **Access**: Via API Gateway or Eureka service discovery
- **Dependencies**: 
  - Calls Inventory Service via gRPC for stock validation
  - Publishes events to Kafka
- **Endpoints**: `/api/order`

## Observability

### Zipkin (Port 9411)
- **Purpose**: Distributed tracing
- **UI**: `http://localhost:9411`
- **Integration**: All services send traces via Micrometer

## Quick Start

### Start All Services
```bash
cd infrastructure
docker-compose up -d
```

### Check Status
```bash
docker-compose ps
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f api-gateway
docker-compose logs -f inventory-service
```

### Stop All Services
```bash
docker-compose down
```

### Clean Restart (Remove Volumes)
```bash
docker-compose down -v
docker-compose up -d
```

## Network Architecture
All services run on the `microservices-network` bridge network, enabling:
- Service-to-service communication via container names
- DNS resolution within the network
- Network isolation from host (unless ports explicitly mapped)

## Troubleshooting

### Services Not Starting
```bash
# Check logs
docker-compose logs <service-name>

# Restart specific service
docker-compose restart <service-name>

# Clean restart
docker-compose down -v && docker-compose up -d
```

### Service Shows Unhealthy
```bash
# Check health endpoint
docker exec <container-name> curl http://localhost:8080/actuator/health

# View detailed logs
docker-compose logs -f <service-name>
```

### Kafka Issues
```bash
# Ensure Zookeeper is healthy first
docker-compose ps zookeeper

# Check Kafka broker
docker-compose logs broker

# Restart Kafka stack
docker-compose restart zookeeper broker connect
```

## Testing Scripts
- `setup_and_test.sh`: Initial infrastructure setup and validation
- `test_database_separation.sh`: Verify database isolation
- `test_debezium_setup.sh`: Test CDC functionality
- `restart_and_test_debezium.sh`: Restart and verify Debezium

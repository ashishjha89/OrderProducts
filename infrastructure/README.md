# Infrastructure for OrderProducts

Note that `docker-compose.yml` defines all infrastructure components needed by the Spring Boot applications in the project.

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
- **Connectors**: MySQL outbox connector. See 
- **Configuration**: JSON-based connector configs
- **REST API**: `http://localhost:8083/connectors`

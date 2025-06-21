# Debezium Outbox Pattern Setup

This document explains the Debezium outbox pattern implementation for the OrderProducts microservices.

## Overview

The outbox pattern ensures that when an order is placed, both the order data and the corresponding event are saved atomically in a single transaction. Debezium then captures these events from the database and publishes them to Kafka topics.

## Architecture

1. **Order Service**: Creates orders and saves events to the `outbox_event` table
2. **MySQL Database**: Stores orders and outbox events
3. **Debezium**: Captures changes from the `outbox_event` table and routes them to Kafka topics
4. **Kafka**: Distributes events to consumers
5. **Notification Service**: Consumes events from Kafka topics

## Key Changes Made

### 1. Database Schema (`infrastructure/mysql_init_scripts/02_order-service_schema.sql`)

The `outbox_event` table uses `BIGINT` for timestamp fields to ensure compatibility with Debezium:

```sql
CREATE TABLE outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    processed_at BIGINT NULL,
    payload JSON NOT NULL,
    INDEX idx_processed_at (processed_at)
);
```

### 2. Java Entity (`order-service/src/main/java/com/orderproduct/orderservice/entity/OutboxEvent.java`)

Updated to use `Long` for timestamp fields:

```java
@Column(name = "created_at", nullable = false)
private Long createdAt;

@Column(name = "processed_at")
private Long processedAt;
```

### 3. Service Layer (`order-service/src/main/java/com/orderproduct/orderservice/service/OrderDataGenerator.java`)

Updated to return timestamp in milliseconds:

```java
public Long getCurrentTimestamp() {
    return Instant.now().toEpochMilli();
}
```

### 4. Debezium Configuration (`infrastructure/debezium_connector_config/mysql-outbox-connector.json`)

Clean configuration for the outbox pattern:

```json
{
    "name": "order-outbox-connector",
    "config": {
        "connector.class": "io.debezium.connector.mysql.MySqlConnector",
        "tasks.max": "1",
        "database.hostname": "mysql",
        "database.port": "3306",
        "database.user": "order_product_user",
        "database.password": "283656ff3b8e513f",
        "database.server.id": "12345",
        "database.server.name": "mysql_order_service",
        "topic.prefix": "mysql_order_service",
        "database.include.list": "order_product_db",
        "table.include.list": "order_product_db.outbox_event",
        "schema.history.internal.kafka.bootstrap.servers": "broker:29092",
        "schema.history.internal.kafka.topic": "schema-changes.mysql_order_service",
        "include.schema.changes": "false",
        "decimal.handling.mode": "precise",
        "time.precision.mode": "connect",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.topic.regex": "mysql_order_service.order_product_db.outbox_event",
        "transforms.outbox.topic.replacement": "${routedByValue}.${eventType}",
        "transforms.outbox.route.by.field": "aggregate_type",
        "transforms.outbox.payload.field": "payload",
        "transforms.outbox.key.field": "aggregate_id",
        "transforms.outbox.table.field.event.timestamp": "created_at",
        "snapshot.mode": "initial",
        "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType,aggregate_type:header:aggregateType,aggregate_id:header:aggregateId,event_id:header:eventId"
    }
}
```

### 5. Notification Service (`notification-service/src/main/java/com/orderproduct/notificationservice/NotificationServiceApplication.java`)

Updated to listen to the correct topic:

```java
@KafkaListener(topics = "Order.OrderPlacedEvent")
public void handleNotification(OrderPlacedEvent orderPlacedEvent) {
    log.info("Got message <{}>", orderPlacedEvent);
    // Do some action, e.g. send out an email notification
}
```

## How It Works

1. **Order Placement**: When an order is placed, the `OrderTransactionService` saves both the order and an outbox event in a single transaction.

2. **Event Creation**: The outbox event contains:
   - `event_type`: "OrderPlacedEvent"
   - `aggregate_type`: "Order"
   - `aggregate_id`: The order number
   - `payload`: JSON representation of the event
   - `created_at`: Timestamp in milliseconds

3. **Debezium Capture**: Debezium monitors the `outbox_event` table and captures INSERT operations.

4. **Event Routing**: The EventRouter transform routes events to topics based on the `aggregate_type` and `event_type` fields. For example, an event with `aggregate_type="Order"` and `event_type="OrderPlacedEvent"` will be routed to the `Order.OrderPlacedEvent` topic.

5. **Event Consumption**: The notification service listens to the `Order.OrderPlacedEvent` topic and processes the events.

## Testing the Setup

### Quick Test

Run the test script to verify the setup:

```bash
./test_debezium_setup.sh
```

### Manual Testing

1. **Start the infrastructure**:
   ```bash
   cd infrastructure
   docker-compose up -d
   ```

2. **Check connector status**:
   ```bash
   curl http://localhost:8083/connectors/order-outbox-connector/status | jq
   ```

3. **Insert a test event**:
   ```bash
   mysql -h localhost -P 3306 -u order_product_user -p283656ff3b8e513f order_product_db -e "
   INSERT INTO outbox_event(event_id, event_type, aggregate_type, aggregate_id, payload, created_at) 
   VALUES (UUID(), 'OrderPlacedEvent', 'Order', 'test-order-123', '{\"orderNumber\":\"test-order-123\"}', UNIX_TIMESTAMP(NOW(3)) * 1000);
   "
   ```

4. **Check for messages in the topic**:
   ```bash
   docker exec broker kafka-console-consumer --bootstrap-server localhost:29092 --topic Order.OrderPlacedEvent --from-beginning --max-messages 1
   ```

### Full Restart and Test

To completely restart and test the setup:

```bash
./restart_and_test_debezium.sh
```

## Troubleshooting

### Common Issues

1. **Connector Fails**: Check the connector status and logs:
   ```bash
   curl http://localhost:8083/connectors/order-outbox-connector/status | jq
   docker logs connect
   ```

2. **Timestamp Issues**: Ensure the `created_at` field is stored as `BIGINT` (milliseconds since epoch).

3. **Topic Not Created**: Check if the connector is running and the table exists:
   ```bash
   docker exec broker kafka-topics --bootstrap-server localhost:29092 --list
   ```

4. **No Messages**: Verify the event was inserted correctly:
   ```bash
   mysql -h localhost -P 3306 -u order_product_user -p283656ff3b8e513f order_product_db -e "SELECT * FROM outbox_event ORDER BY created_at DESC LIMIT 5;"
   ```

### Logs

Check logs for different services:

```bash
# Debezium Connect logs
docker logs connect

# Kafka broker logs
docker logs broker

# MySQL logs
docker logs mysql
```

## Benefits

1. **Reliability**: Events are guaranteed to be published if the order is saved (atomic transaction)
2. **Scalability**: Events are processed asynchronously
3. **Decoupling**: Services communicate through events, not direct calls
4. **Auditability**: All events are stored in the database for audit purposes

## Next Steps

1. Implement event processing for other event types
2. Add event versioning for schema evolution
3. Implement dead letter queues for failed events
4. Add monitoring and alerting for the event processing pipeline 
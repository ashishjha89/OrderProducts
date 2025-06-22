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

### 1. Database Schema
`infrastructure/mysql_init_scripts/02_order-service_schema.sql`

### 2. Debezium Configuration
`infrastructure/debezium_connector_config/mysql-outbox-connector.json`

## How It Works

1. **Order Placement**: When an order is placed, the `OrderTransactionService` saves both the order and an outbox event in a single transaction.

2. **Event Creation**: The outbox event contains:
   - `eventtype`: "OrderPlacedEvent"
   - `aggregatetype`: "Order"
   - `aggregateid`: The order number
   - `payload`: JSON representation of the event
   - `createdat`: Timestamp in milliseconds

3. **Debezium Capture**: Debezium monitors the `outbox_event` table and captures INSERT operations.

4. **Event Routing**: The EventRouter transform routes events to topics based on the `aggregatetype` and `eventtype` fields. For example, an event with `aggregatetype="Order"` and `eventtype="OrderPlacedEvent"` will be routed to the `Order.OrderPlacedEvent` topic.

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
   docker exec broker kafka-console-consumer --bootstrap-server localhost:29092 --topic outbox.event.Order --from-beginning --max-messages 1
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

2. **Topic Not Created**: Check if the connector is running and the table exists:
   ```bash
   docker exec broker kafka-topics --bootstrap-server localhost:29092 --list
   ```

3. **No Messages**: Verify the event was inserted correctly:
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

1. Implement event processing in consumers.
2. Add event versioning for schema evolution.
3. Implement dead letter queues for failed events.
4. Add monitoring and alerting for the event processing pipeline.
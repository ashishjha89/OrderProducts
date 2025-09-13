#!/bin/bash

echo "=== Testing Debezium Outbox Pattern Setup ==="

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 10

# Check if connector is running
echo "1. Checking connector status..."
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq .

# Check if topics are created
echo -e "\n2. Checking Kafka topics..."
docker exec broker kafka-topics --bootstrap-server localhost:29092 --list

# Test inserting an event manually using Docker container
echo -e "\n3. Testing manual event insertion..."
docker exec mysql mysql -u order_user -pnS3johd59oQIcZhN order_db -e "
INSERT INTO outbox_event(eventid, eventtype, aggregatetype, aggregateid, payload, createdat) 
VALUES (UUID(), 'OrderStatusChangedEvent', 'Order', 'test-order-123', '{\"orderNumber\":\"test-order-123\", \"status\":\"FULFILLED\"}', UNIX_TIMESTAMP(NOW(3)) * 1000);
"

# Wait a moment for processing
sleep 5

# Check connector status again
echo -e "\n4. Checking connector status after insertion..."
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq .

# Check if the topic was created
echo -e "\n5. Checking if outbox.event.Order topic was created..."
docker exec broker kafka-topics --bootstrap-server localhost:29092 --list | grep "outbox.event.Order" || echo "Topic not created yet"

# Check if the topic has messages (only if topic exists)
if docker exec broker kafka-topics --bootstrap-server localhost:29092 --list | grep -q "outbox.event.Order"; then
    echo -e "\n6. Checking messages in the outbox.event.Order topic..."
    docker exec broker kafka-console-consumer --bootstrap-server localhost:29092 --topic outbox.event.Order --from-beginning --max-messages 1 --timeout-ms 10000
else
    echo -e "\n6. Topic outbox.event.Order not found. Checking connector logs for errors..."
    docker logs connect --tail 20
fi

# Check the outbox_event table to verify the insert worked
echo -e "\n7. Verifying event was inserted into outbox_event table..."
docker exec mysql mysql -u order_user -pnS3johd59oQIcZhN order_db -e "SELECT id, eventtype, aggregatetype, aggregateid, createdat FROM outbox_event ORDER BY createdat DESC LIMIT 3;"

echo -e "\n=== Test completed ===" 
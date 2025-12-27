#!/bin/bash

echo "=== Restarting Infrastructure and Testing Debezium Setup ==="

# Stop existing containers
echo "1. Stopping existing containers..."
docker-compose down -v

# Start infrastructure (not microservices)
echo "2. Starting infrastructure..."
docker-compose up -d mysql mongodb zookeeper broker zipkin connect register_debezium_connector

# Wait for infrastructure services to be ready
echo "3. Waiting for services to be ready..."
sleep 30

# Check if all infrastructure services are healthy
echo "4. Checking service health..."
docker-compose ps

# Wait for connector to be registered
echo "5. Waiting for connector registration..."
sleep 20

# Run the test script
echo "6. Running Debezium test..."
./test_debezium_setup.sh

echo "=== Setup and test completed ===" 
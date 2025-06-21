#!/bin/bash

echo "=== Restarting Infrastructure and Testing Debezium Setup ==="

# Stop existing containers
echo "1. Stopping existing containers..."
cd infrastructure
docker-compose down -v

# Start infrastructure
echo "2. Starting infrastructure..."
docker-compose up -d

# Wait for services to be ready
echo "3. Waiting for services to be ready..."
sleep 30

# Check if all services are healthy
echo "4. Checking service health..."
docker-compose ps

# Wait for connector to be registered
echo "5. Waiting for connector registration..."
sleep 20

# Run the test script
echo "6. Running Debezium test..."
cd ..
./test_debezium_setup.sh

echo "=== Setup and test completed ===" 
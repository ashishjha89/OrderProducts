#!/bin/bash

echo "=== Setting up Infrastructure with Separated Databases ==="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to wait for a service
wait_for_service() {
    local service=$1
    local check_command=$2
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}Waiting for $service to be ready...${NC}"
    while [ $attempt -lt $max_attempts ]; do
        if eval $check_command > /dev/null 2>&1; then
            echo -e "${GREEN}✓ $service is ready${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    echo -e "\n${RED}✗ Timeout waiting for $service${NC}"
    return 1
}

# Step 1: Stop existing containers
echo -e "\n${BLUE}Step 1: Stopping existing containers...${NC}"
docker-compose down -v

# Step 2: Start infrastructure
echo -e "\n${BLUE}Step 2: Starting infrastructure...${NC}"
docker-compose up -d

# Step 3: Wait for MySQL to be ready
wait_for_service "MySQL" "docker exec mysql mysql -u root -prootpassword -e 'SELECT 1;'"

# Step 4: Wait for Kafka to be ready
wait_for_service "Kafka" "docker exec broker kafka-topics --bootstrap-server localhost:29092 --list"

# Step 5: Wait for Kafka Connect to be ready
wait_for_service "Kafka Connect" "curl -f http://localhost:8083/connectors"

# Step 6: Give time for database initialization scripts to run
echo -e "\n${YELLOW}Waiting for database initialization...${NC}"
sleep 10

# Step 7: Verify database separation
echo -e "\n${BLUE}Step 7: Verifying database separation...${NC}"
./test_database_separation.sh

# Step 8: Wait for Debezium connector registration
echo -e "\n${BLUE}Step 8: Waiting for Debezium connector registration...${NC}"
sleep 15

# Step 9: Check connector status
echo -e "\n${BLUE}Step 9: Checking Debezium connector status...${NC}"
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq .

# Step 10: Test Debezium setup
echo -e "\n${BLUE}Step 10: Testing Debezium setup...${NC}"
./test_debezium_setup.sh

# Step 11: Show running containers
echo -e "\n${BLUE}Step 11: Container Status...${NC}"
docker-compose ps

echo -e "\n${GREEN}=== Setup Complete ===${NC}"
echo -e "\n${YELLOW}Database Configuration Summary:${NC}"
echo "┌─────────────────────┬──────────────────┬─────────────────────┐"
echo "│ Service             │ Database         │ User                │"
echo "├─────────────────────┼──────────────────┼─────────────────────┤"
echo "│ Order Service       │ order_db         │ order_user          │"
echo "│ Inventory Service   │ inventory_db     │ inventory_user      │"
echo "│ Debezium CDC        │ All (read-only)  │ debezium_user       │"
echo "└─────────────────────┴──────────────────┴─────────────────────┘"

echo -e "\n${YELLOW}Connection Details:${NC}"
echo "- MySQL: localhost:3306"
echo "- Kafka: localhost:9092"
echo "- Kafka Connect: http://localhost:8083"
echo "- Zipkin: http://localhost:9411"

echo -e "\n${YELLOW}Next Steps:${NC}"
echo "1. Start your microservices"
echo "2. Access Eureka Dashboard at http://localhost:8761"
echo "3. Monitor traces at http://localhost:9411"

echo -e "\n${GREEN}Database passwords have been configured as:${NC}"
echo "- order_user: nS3johd59oQIcZhN"
echo "- inventory_user: kV33CaPPgSu1YuXJ"
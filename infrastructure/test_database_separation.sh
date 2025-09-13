#!/bin/bash

echo "=== Testing Database Separation Setup ==="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "\n${YELLOW}1. Testing Order Database Connection${NC}"
echo "Connecting to order_db as order_user..."
docker exec mysql mysql -u order_user -pnS3johd59oQIcZhN order_db -e "SHOW TABLES;" 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Successfully connected to order_db${NC}"
else
    echo -e "${RED}✗ Failed to connect to order_db${NC}"
fi

echo -e "\n${YELLOW}2. Testing Inventory Database Connection${NC}"
echo "Connecting to inventory_db as inventory_user..."
docker exec mysql mysql -u inventory_user -pkV33CaPPgSu1YuXJ inventory_db -e "SHOW TABLES;" 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Successfully connected to inventory_db${NC}"
else
    echo -e "${RED}✗ Failed to connect to inventory_db${NC}"
fi

echo -e "\n${YELLOW}3. Verifying Order Service Tables${NC}"
docker exec mysql mysql -u order_user -pnS3johd59oQIcZhN order_db -e "
SELECT 'orders' as table_name, COUNT(*) as row_count FROM orders
UNION ALL
SELECT 'order_line_items', COUNT(*) FROM order_line_items
UNION ALL
SELECT 'outbox_event', COUNT(*) FROM outbox_event;
" 2>/dev/null

echo -e "\n${YELLOW}4. Verifying Inventory Service Tables${NC}"
docker exec mysql mysql -u inventory_user -pkV33CaPPgSu1YuXJ inventory_db -e "
SELECT 'inventory' as table_name, COUNT(*) as row_count FROM inventory
UNION ALL
SELECT 'inventory_reservation', COUNT(*) FROM inventory_reservation;
" 2>/dev/null

echo -e "\n${YELLOW}5. Testing Cross-Database Access Restrictions${NC}"
echo "Testing if order_user can access inventory_db (should fail)..."
docker exec mysql mysql -u order_user -pnS3johd59oQIcZhN inventory_db -e "SHOW TABLES;" 2>/dev/null
if [ $? -ne 0 ]; then
    echo -e "${GREEN}✓ order_user correctly denied access to inventory_db${NC}"
else
    echo -e "${RED}✗ WARNING: order_user has access to inventory_db!${NC}"
fi

echo "Testing if inventory_user can access order_db (should fail)..."
docker exec mysql mysql -u inventory_user -pkV33CaPPgSu1YuXJ order_db -e "SHOW TABLES;" 2>/dev/null
if [ $? -ne 0 ]; then
    echo -e "${GREEN}✓ inventory_user correctly denied access to order_db${NC}"
else
    echo -e "${RED}✗ WARNING: inventory_user has access to order_db!${NC}"
fi

echo -e "\n${YELLOW}6. Verifying Debezium User Privileges${NC}"
docker exec mysql mysql -u root -prootpassword -e "SHOW GRANTS FOR 'debezium_user'@'%';" 2>/dev/null | grep -E "(REPLICATION|RELOAD|LOCK TABLES)" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ debezium_user has required CDC privileges${NC}"
    docker exec mysql mysql -u root -prootpassword -e "SHOW GRANTS FOR 'debezium_user'@'%';" 2>/dev/null
else
    echo -e "${RED}✗ debezium_user missing CDC privileges${NC}"
fi

echo -e "\n${YELLOW}7. Sample Data Verification${NC}"
echo "Checking inventory data..."
docker exec mysql mysql -u inventory_user -pkV33CaPPgSu1YuXJ inventory_db -e "
SELECT sku_code, on_hand_quantity FROM inventory LIMIT 5;
" 2>/dev/null

echo -e "\n=== Database Separation Test Completed ==="
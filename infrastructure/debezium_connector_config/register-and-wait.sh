#!/bin/sh
set -e

# Use environment variables provided by docker-compose, with fallbacks
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-debezium_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-fBWsBYOGzcggYQfM}"
MYSQL_DB="${MYSQL_DB:-order_db}"

echo "Waiting for MySQL to be available..."
echo "Connecting to ${MYSQL_DB} as ${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}"

# Wait for MySQL to be ready
until mysql -h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USER} -p${MYSQL_PASSWORD} -e "SELECT 1;" ${MYSQL_DB}; do
  echo "Waiting for MySQL to be ready..."
  sleep 2
done

# Wait until the table exists
until mysql -h${MYSQL_HOST} -P${MYSQL_PORT} -u${MYSQL_USER} -p${MYSQL_PASSWORD} -D${MYSQL_DB} -e "DESCRIBE outbox_event;" > /dev/null 2>&1; do
  echo "Waiting for outbox_event table to exist..."
  sleep 2
done

echo "MySQL and outbox_event table are ready!"

# Now register the connector
echo "Registering Debezium MySQL Outbox Connector..."
curl -X POST -H "Content-Type: application/json" --data @/config/mysql-outbox-connector.json http://connect:8083/connectors || exit 1
echo "Debezium MySQL Outbox Connector registration command sent."
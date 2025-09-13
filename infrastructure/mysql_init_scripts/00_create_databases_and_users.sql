-- This script creates the databases and users for both services
-- It runs first due to the 00_ prefix

-- Create databases first
CREATE DATABASE IF NOT EXISTS order_db;
CREATE DATABASE IF NOT EXISTS inventory_db;

-- Create a special debezium user for CDC operations
CREATE USER IF NOT EXISTS 'debezium_user'@'%' IDENTIFIED WITH mysql_native_password BY 'fBWsBYOGzcggYQfM';
-- Debezium needs these global privileges for CDC
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, LOCK TABLES ON *.* TO 'debezium_user'@'%';

-- Create order_user with privileges for order_db only
CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED WITH mysql_native_password BY 'nS3johd59oQIcZhN';
-- Grant all privileges on order_db except those that would allow cross-database access
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE, CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EVENT, TRIGGER, REFERENCES ON order_db.* TO 'order_user'@'%';

-- Create inventory_user with privileges for inventory_db only  
CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED WITH mysql_native_password BY 'kV33CaPPgSu1YuXJ';
-- Grant all privileges on inventory_db except those that would allow cross-database access
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE, CREATE VIEW, SHOW VIEW, CREATE ROUTINE, ALTER ROUTINE, EVENT, TRIGGER, REFERENCES ON inventory_db.* TO 'inventory_user'@'%';

FLUSH PRIVILEGES;
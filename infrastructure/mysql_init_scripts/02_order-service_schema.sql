-- This script will be automatically executed by the MySQL container on first startup
-- when mounted to /docker-entrypoint-initdb.d/.

USE order_product_db;

-- Drop tables in reverse dependency order to avoid foreign key constraints issues
DROP TABLE IF EXISTS order_line_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS outbox_event;

-- Create the orders table (for Order entity)
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE
);

-- Create the order_line_items table (for OrderLineItems entity)
-- It includes a foreign key (order_id) referencing the 'orders' table
CREATE TABLE order_line_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(255) NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    quantity INT NOT NULL,
    order_id BIGINT NOT NULL, -- This column links to the 'orders' table
    CONSTRAINT fk_order_line_items_order -- Name for the foreign key constraint
        FOREIGN KEY (order_id)
        REFERENCES orders (id)
        ON DELETE CASCADE -- If an order is deleted, its line items are automatically deleted
);

CREATE TABLE outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at TIMESTAMP(3) NULL,
    payload JSON NOT NULL,
    INDEX idx_processed_at (processed_at)
);
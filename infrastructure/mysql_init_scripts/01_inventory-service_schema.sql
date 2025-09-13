-- This script will be automatically executed by the MySQL container on first startup
-- when mounted to /docker-entrypoint-initdb.d/.
-- Creates tables for inventory service

USE inventory_db;

DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS inventory_reservation;

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(255) NOT NULL UNIQUE,
    on_hand_quantity INT NOT NULL CHECK (on_hand_quantity >= 0)
);

CREATE TABLE inventory_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL,
    sku_code VARCHAR(255) NOT NULL,
    reserved_quantity INT NOT NULL CHECK (reserved_quantity >= 0),
    reserved_at DATETIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    
    -- Unique constraint to prevent duplicate reservations for same order+sku
    UNIQUE KEY uk_order_sku (order_number, sku_code),
    
    -- Indexes for better query performance
    INDEX idx_sku_code_status (sku_code, status),
    INDEX idx_order_number (order_number),
    INDEX idx_order_sku (order_number, sku_code)
);
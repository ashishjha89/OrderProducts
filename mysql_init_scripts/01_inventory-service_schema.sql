-- This script will be automatically executed by the MySQL container on first startup
-- when mounted to /docker-entrypoint-initdb.d/.

USE order_product_db;

DROP TABLE IF EXISTS inventory;

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(255) NOT NULL UNIQUE,
    quantity INT NOT NULL
);
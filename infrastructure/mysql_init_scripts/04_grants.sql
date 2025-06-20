ALTER USER 'order_product_user'@'%' IDENTIFIED WITH mysql_native_password BY '283656ff3b8e513f';
GRANT ALL PRIVILEGES ON order_product_db.* TO 'order_product_user'@'%';
GRANT REPLICATION SLAVE, REPLICATION CLIENT, RELOAD, SHOW VIEW, LOCK TABLES ON *.* TO 'order_product_user'@'%';
FLUSH PRIVILEGES;
# Complete Dockerization Guide for OrderProducts Microservices

## Overview

This guide provides step-by-step instructions to run the OrderProducts microservices system in two environments:
1. **Local Development**: All services running as Docker containers on your local machine
2. **AWS Production**: Deployment to AWS using ECS/EKS with managed services

Follow the sections in order to successfully dockerize and deploy your services.

## System Architecture

### Microservices Components

1. **Discovery Server (Eureka)**: Service registry for all microservices
2. **API Gateway**: Single entry point for all client requests
3. **Inventory Service**: Manages product inventory (internal service)
4. **Order Service**: Handles order processing
5. **Product Service**: Manages product catalog
6. **Notification Service**: Handles notifications (if present)

### Infrastructure Components

- **MySQL Database**: Persistence for inventory and order services
- **MongoDB**: Persistence for product service
- **Apache Kafka**: Event streaming between services
- **Zipkin**: Distributed tracing
- **Zookeeper**: Kafka coordination

### Service Communication Patterns

- **Synchronous**: REST APIs via API Gateway, gRPC for internal communication
- **Asynchronous**: Event-driven via Kafka
- **Service Discovery**: Dynamic service location via Eureka

## Prerequisites

### For Local Docker Setup
- Docker Desktop installed (version 4.0+)
- Docker Compose (version 2.0+)
- Minimum 8GB RAM available
- Ports available: 8080, 8761, 8082-8084, 9090, 9092, 9411, 3306, 27017, 2181
- grpcurl installed (optional, for testing gRPC)
- **NO local MySQL installation required!**

### For AWS Deployment
- AWS Account with appropriate permissions
- AWS CLI installed and configured
- Terraform or AWS CDK (for infrastructure as code)
- Domain name (optional, for production URLs)

## Critical: Solving the MySQL Startup Dependency Issue

### The Problem
Currently, your Spring Boot applications (inventory-service, order-service) check for MySQL connection during startup. If MySQL isn't available at `localhost:3306`, the application fails to start, even if you intend to use Docker MySQL.

### The Solution
We have three approaches to solve this:

#### Solution 1: Run Apps Locally with Docker MySQL (Simplest)
```bash
# 1. Start Docker MySQL first
cd infrastructure
docker-compose up -d mysql

# 2. Wait for MySQL to be ready (important!)
sleep 10
docker exec -it mysql mysql -u root -prootpassword -e "SHOW DATABASES;"

# 3. Run your applications - they'll connect to Docker MySQL on localhost:3306
cd ../inventory-service
mvn spring-boot:run

cd ../order-service
mvn spring-boot:run
```

**Why this works**: Docker MySQL is exposed on `localhost:3306`, so your applications can connect to it as if it were a local MySQL installation.

#### Solution 2: Use Spring Profiles for Docker Environment
Create `application-local.properties` in each service:

```properties
# inventory-service/src/main/resources/application-local.properties
spring.datasource.url=jdbc:mysql://localhost:3306/inventory_db
spring.datasource.username=inventory_user
spring.datasource.password=kV33CaPPgSu1YuXJ
spring.jpa.hibernate.ddl-auto=validate
# Add other localhost-specific configs
```

Run with profile:
```bash
mvn spring-boot:run -Dspring.profiles.active=local
```

#### Solution 3: Lazy Database Initialization (Best for Development)
Add to your application.properties:

```properties
# Delay datasource initialization
spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.datasource.hikari.initialization-fail-timeout=-1
```

This allows the application to start even if MySQL isn't immediately available.

## Part 1: Local Docker Setup

### Important: Solving the MySQL Localhost Dependency

**Current Challenge**: The applications currently require MySQL on localhost for startup, even though they connect to Docker MySQL at runtime.

**Solution**: We'll run applications with environment variables that point directly to the Docker MySQL container, eliminating the need for localhost MySQL.

### Option A: Run Applications Locally (Without Dockerizing Apps)

This option keeps your applications running on localhost but connects them to infrastructure in Docker containers.

#### Step 1: Start Infrastructure Containers Only

```bash
cd infrastructure
# Start only infrastructure services (MySQL, MongoDB, Kafka, Zipkin)
docker-compose up -d mysql mongodb zookeeper broker zipkin
```

#### Step 2: Wait for MySQL to Initialize

```bash
# Check if MySQL is ready
docker exec -it mysql mysql -u root -prootpassword -e "SHOW DATABASES;"

# Verify databases and users exist
docker exec -it mysql mysql -u root -prootpassword -e "
  SELECT User, Host FROM mysql.user WHERE User IN ('inventory_user', 'order_user');
  SHOW DATABASES LIKE '%_db';
"
```

#### Step 3: Run Applications with Docker MySQL Connection

**For inventory-service:**
```bash
cd inventory-service
# Run with environment variable pointing to Docker MySQL
MYSQL_HOST=localhost mvn spring-boot:run
# OR with explicit Java command
java -DMYSQL_HOST=localhost -jar target/inventory-service-*.jar
```

**For order-service:**
```bash
cd order-service
# Run with environment variable pointing to Docker MySQL
MYSQL_HOST=localhost mvn spring-boot:run
# OR with explicit Java command
java -DMYSQL_HOST=localhost -jar target/order-service-*.jar
```

**Note**: This works because Docker MySQL is exposed on `localhost:3306`, and the environment variable `MYSQL_HOST=localhost` overrides the default in application.properties.

### Option B: Run Everything in Docker (Recommended)

This option dockerizes all applications and infrastructure, completely eliminating localhost dependencies.

### Step-by-Step Implementation

#### Step 1: Verify MySQL Initialization Scripts

First, ensure your `infrastructure/mysql_init_scripts/` contains proper database and user setup:

Create `infrastructure/mysql_init_scripts/01-init-databases.sql`:

```sql
-- Create databases
CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE DATABASE IF NOT EXISTS order_db;

-- Create users with proper permissions
CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED BY 'kV33CaPPgSu1YuXJ';
CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'nS3johd59oQIcZhN';

-- Grant privileges
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_user'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'%';

-- Important: Also grant localhost access for local app development
CREATE USER IF NOT EXISTS 'inventory_user'@'localhost' IDENTIFIED BY 'kV33CaPPgSu1YuXJ';
CREATE USER IF NOT EXISTS 'order_user'@'localhost' IDENTIFIED BY 'nS3johd59oQIcZhN';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_user'@'localhost';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'localhost';

FLUSH PRIVILEGES;

-- Create tables if needed (optional, if not using Hibernate auto-ddl)
USE inventory_db;
-- Add your inventory table creation scripts here if ddl-auto=none

USE order_db;
-- Add your order table creation scripts here if ddl-auto=none
```

#### Step 2: Create All Required Dockerfiles

##### 2.1 Discovery Server (Eureka)

Create `discovery-server/Dockerfile`:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/discovery-server-*.jar app.jar
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `discovery-server/src/main/resources/application-docker.properties`:

```properties
spring.application.name=discovery-server
server.port=8761

eureka.instance.hostname=discovery-server
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.server.enable-self-preservation=false

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0
```

##### 2.2 API Gateway

Create `api-gateway/Dockerfile`:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/api-gateway-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `api-gateway/src/main/resources/application-docker.properties`:

```properties
spring.application.name=api-gateway
server.port=8080

eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.instance.prefer-ip-address=true

## Routing Configuration
spring.cloud.gateway.routes[0].id=product-service
spring.cloud.gateway.routes[0].uri=lb://product-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/products/**

spring.cloud.gateway.routes[1].id=order-service
spring.cloud.gateway.routes[1].uri=lb://order-service
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/order/**

spring.cloud.gateway.routes[2].id=discovery-server
spring.cloud.gateway.routes[2].uri=http://discovery-server:8761
spring.cloud.gateway.routes[2].predicates[0]=Path=/eureka/web
spring.cloud.gateway.routes[2].filters[0]=SetPath=/

spring.cloud.gateway.routes[3].id=discovery-server-static
spring.cloud.gateway.routes[3].uri=http://discovery-server:8761
spring.cloud.gateway.routes[3].predicates[0]=Path=/eureka/**

spring.cloud.gateway.routes[4].id=inventory-service-swagger
spring.cloud.gateway.routes[4].uri=lb://inventory-service
spring.cloud.gateway.routes[4].predicates[0]=Path=/api/inventory/v3/api-docs/**,/api/inventory/swagger-ui/**

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0
```

##### 2.3 Inventory Service

Create `inventory-service/Dockerfile`:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/inventory-service-*.jar app.jar
EXPOSE 8082 9090
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `inventory-service/src/main/resources/application-docker.properties`:

```properties
# Application Configuration
spring.application.name=inventory-service
server.port=8082

# Database Configuration
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://mysql:3306/inventory_db
spring.datasource.username=inventory_user
spring.datasource.password=kV33CaPPgSu1YuXJ
spring.jpa.hibernate.ddl-auto=none

# Eureka Configuration
eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka
eureka.instance.prefer-ip-address=true
eureka.instance.hostname=inventory-service

# Kafka Configuration
spring.kafka.bootstrap-servers=broker:29092
spring.kafka.consumer.group-id=inventoryId
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

# Zipkin Configuration
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

# gRPC Configuration
grpc.server.port=9090

# Actuator Configuration
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always

# Swagger Configuration
springdoc.api-docs.path=/api/inventory/v3/api-docs
springdoc.swagger-ui.path=/api/inventory/swagger-ui.html
```

##### 2.4 Order Service

Create `order-service/Dockerfile`:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/order-service-*.jar app.jar
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `order-service/src/main/resources/application-docker.properties`:

```properties
spring.application.name=order-service
server.port=8083

# Database Configuration
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://mysql:3306/order_db
spring.datasource.username=order_user
spring.datasource.password=nS3johd59oQIcZhN
spring.jpa.hibernate.ddl-auto=none

# Service Discovery
eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.instance.prefer-ip-address=true

# Internal Service Communication
inventory.api.base-url=http://inventory-service

# Kafka Configuration
spring.kafka.bootstrap-servers=broker:29092
spring.kafka.template.default-topic=notificationTopic
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Zipkin Configuration
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

# Resilience4j Configuration (same as original)
resilience4j.circuitbreaker.instances.inventory.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.inventory.slidingWindowType=TIME_BASED
resilience4j.circuitbreaker.instances.inventory.slidingWindowSize=10
resilience4j.circuitbreaker.instances.inventory.failureRateThreshold=50
resilience4j.circuitbreaker.instances.inventory.waitDurationInOpenState=30s
```

##### 2.5 Product Service

Create `product-service/Dockerfile`:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/product-service-*.jar app.jar
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `product-service/src/main/resources/application-docker.properties`:

```properties
spring.application.name=product-service
server.port=8084

# MongoDB Configuration
spring.data.mongodb.uri=mongodb://mongodb:27017/product-service

# Service Discovery
eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.instance.prefer-ip-address=true

# Zipkin Configuration
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

# Swagger Configuration
springdoc.api-docs.path=/api/product/v3/api-docs
springdoc.swagger-ui.path=/api/product/swagger-ui.html
```

#### Step 3: Create Docker Compose Configuration

Create `infrastructure/docker-compose-full.yml` for the complete system:

```yaml
version: '3.8'

services:
  # Infrastructure Services (from existing docker-compose.yml)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.3
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    networks:
      - orderproduct-network

  broker:
    image: confluentinc/cp-kafka:7.5.3
    container_name: broker
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://broker:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    networks:
      - orderproduct-network

  mysql:
    image: mysql:8.0
    container_name: mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
    command: ["mysqld", "--binlog-format=ROW", "--log-bin=mysql-bin", "--server-id=1"]
    volumes:
      - ./mysql_init_scripts:/docker-entrypoint-initdb.d
    networks:
      - orderproduct-network

  mongodb:
    image: mongo:7.0
    container_name: mongodb
    ports:
      - "27017:27017"
    networks:
      - orderproduct-network

  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - orderproduct-network

  # Discovery Server
  discovery-server:
    build: ../discovery-server
    container_name: discovery-server
    ports:
      - "8761:8761"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx256m -Xms128m
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - orderproduct-network

  # API Gateway
  api-gateway:
    build: ../api-gateway
    container_name: api-gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      discovery-server:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - orderproduct-network

  # Inventory Service
  inventory-service:
    build: ../inventory-service
    container_name: inventory-service
    ports:
      - "9090:9090"  # gRPC port
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - mysql
      - broker
      - discovery-server
      - zipkin
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - orderproduct-network

  # Order Service
  order-service:
    build: ../order-service
    container_name: order-service
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - mysql
      - broker
      - discovery-server
      - inventory-service
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8083/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - orderproduct-network

  # Product Service
  product-service:
    build: ../product-service
    container_name: product-service
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - mongodb
      - discovery-server
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8084/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
    networks:
      - orderproduct-network

networks:
  orderproduct-network:
    driver: bridge

volumes:
  mysql_data:
  mongodb_data:
  kafka_data:
  zookeeper_data:
```

**Important Note on Database Connectivity**: 
- The Docker MySQL container exposes port 3306 to localhost
- Applications running locally can connect to `localhost:3306`
- No local MySQL installation is needed!
- Just ensure the Docker MySQL container is running before starting applications

#### Step 4: Build and Run Services

##### Building Images

```bash
# Build all services from project root
docker-compose -f infrastructure/docker-compose-full.yml build

# Or build individually
cd discovery-server && docker build -t discovery-server:latest .
cd ../api-gateway && docker build -t api-gateway:latest .
cd ../inventory-service && docker build -t inventory-service:latest .
cd ../order-service && docker build -t order-service:latest .
cd ../product-service && docker build -t product-service:latest .
```

##### Starting Services

```bash
# Start everything
cd infrastructure
docker-compose -f docker-compose-full.yml up -d

# Start in specific order (recommended for first run)
docker-compose -f docker-compose-full.yml up -d mysql mongodb
docker-compose -f docker-compose-full.yml up -d zookeeper
docker-compose -f docker-compose-full.yml up -d broker
docker-compose -f docker-compose-full.yml up -d zipkin
docker-compose -f docker-compose-full.yml up -d discovery-server
docker-compose -f docker-compose-full.yml up -d inventory-service product-service
docker-compose -f docker-compose-full.yml up -d order-service
docker-compose -f docker-compose-full.yml up -d api-gateway

# View logs for all services
docker-compose -f docker-compose-full.yml logs -f

# View specific service logs
docker-compose -f docker-compose-full.yml logs -f order-service
```

#### Step 5: Verify Everything is Working

1. **Eureka Dashboard**: http://localhost:8761
   - All services should be registered
   - Check instances status

2. **API Gateway Health**: http://localhost:8080/actuator/health

3. **Service Swagger UIs** (via API Gateway):
   - Product Service: http://localhost:8080/api/product/swagger-ui.html
   - Order Service: http://localhost:8080/api/order/swagger-ui.html
   - Inventory Service (internal): http://localhost:8082/api/inventory/swagger-ui.html

4. **Zipkin Dashboard**: http://localhost:9411
   - Verify distributed tracing

#### Step 6: Test Your Services

##### API Testing

```bash
# Product Service - Get all products
curl http://localhost:8080/api/products

# Product Service - Create product
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 14","description":"Latest iPhone","price":999.99}'

# Order Service - Place order
curl -X POST http://localhost:8080/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsList": [
      {"skuCode": "iphone-13", "price": 899, "quantity": 1}
    ]
  }'

# Order Service - Get orders
curl http://localhost:8080/api/order
```

##### Internal Service Testing

```bash
# Inventory Service (not exposed through gateway)
curl "http://localhost:8082/api/inventory?skuCode=samsung-s10,iphone-13"
```

##### gRPC Testing

```bash
# List available gRPC services
grpcurl -plaintext localhost:9090 list

# Test ReserveProducts method
grpcurl -plaintext -d '{
  "order_number": "TEST-ORDER-001",
  "item_reservation_requests": [
    {
      "sku_code": "samsung-s10",
      "quantity": 1
    }
  ]
}' localhost:9090 com.orderproduct.inventoryservice.grpc.ReservationService/ReserveProducts
```

##### Verify Tracing

1. Access Zipkin UI: http://localhost:9411
2. Search for traces from `inventory-service`
3. Verify trace spans are being collected

## Part 2: AWS Deployment

### Pre-Deployment Checklist

- [ ] All services tested locally with Docker
- [ ] AWS account created and CLI configured
- [ ] ECR repositories created for each service
- [ ] VPC and security groups configured
- [ ] Decide between ECS or EKS

### Deployment Options

#### Option 1: ECS with Fargate (Recommended)

```yaml
# ecs-task-definition.json example for a service
{
  "family": "order-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "order-service",
      "image": "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/order-service:latest",
      "portMappings": [
        {
          "containerPort": 8083,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "aws"},
        {"name": "EUREKA_SERVER", "value": "http://discovery-server.internal:8761/eureka"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/order-service",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Option 2: EKS (Kubernetes)

```yaml
# kubernetes-deployment.yaml example
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/order-service:latest
        ports:
        - containerPort: 8083
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "aws"
        - name: EUREKA_SERVER
          value: "http://discovery-server:8761/eureka"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
```

### Step 1: Set Up AWS Infrastructure

#### Create AWS Resources

1. **RDS MySQL** - Replace containerized MySQL
   ```properties
   # application-aws.properties
   spring.datasource.url=jdbc:mysql://${RDS_ENDPOINT}:3306/${DB_NAME}
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   ```

2. **Amazon DocumentDB** - Replace MongoDB
   ```properties
   spring.data.mongodb.uri=mongodb://${DOCDB_USERNAME}:${DOCDB_PASSWORD}@${DOCDB_ENDPOINT}:27017/${DB_NAME}?ssl=true&replicaSet=rs0
   ```

3. **Amazon MSK** - Replace Kafka
   ```properties
   spring.kafka.bootstrap-servers=${MSK_BOOTSTRAP_SERVERS}
   spring.kafka.properties.security.protocol=SSL
   ```

4. **AWS X-Ray** - Replace Zipkin (optional)
   ```xml
   <!-- Add to pom.xml -->
   <dependency>
     <groupId>com.amazonaws</groupId>
     <artifactId>aws-xray-recorder-sdk-spring</artifactId>
   </dependency>
   ```

### Step 2: Build and Push Images to ECR

```bash
#!/bin/bash
# deploy-to-aws.sh

# Build and push to ECR
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

for service in discovery-server api-gateway inventory-service order-service product-service; do
  docker build -t $service:latest ./$service
  docker tag $service:latest ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/$service:latest
  docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/$service:latest
done

# Update ECS services
for service in discovery-server api-gateway inventory-service order-service product-service; do
  aws ecs update-service --cluster orderproducts-cluster --service $service --force-new-deployment
done
```

### Step 3: Deploy Services

#### Using Terraform

```hcl
# main.tf snippet
resource "aws_ecs_cluster" "main" {
  name = "orderproducts-cluster"
}

resource "aws_ecs_service" "order_service" {
  name            = "order-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.order_service.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    security_groups = [aws_security_group.ecs_tasks.id]
    subnets         = aws_subnet.private.*.id
  }

  service_registries {
    registry_arn = aws_service_discovery_service.order_service.arn
  }
}
```

### Step 4: Configure Production Settings

#### Application Properties for AWS

Create `application-aws.properties` for each service:

```properties
# Common settings for all services
spring.profiles.active=aws
eureka.client.serviceUrl.defaultZone=http://${EUREKA_HOST}:8761/eureka/
management.metrics.export.cloudwatch.enabled=true
```

### Step 5: Monitoring and Optimization

#### Cost Optimization

1. **Use Spot Instances** for non-critical services
2. **Auto-scaling** based on CPU/memory metrics
3. **Reserved Instances** for predictable workloads
4. **Service Mesh (AWS App Mesh)** for advanced traffic management
5. **CloudWatch** for monitoring and alerting

## Quick Reference

### Environment Variables

#### Local Docker Environment

| Service | Key Variables | Values |
|---------|--------------|--------|
| All Services | `SPRING_PROFILES_ACTIVE` | `docker` |
| Discovery Server | `server.port` | `8761` |
| API Gateway | `server.port` | `8080` |
| Inventory Service | `server.port`, `grpc.server.port` | `8082`, `9090` |
| Order Service | `server.port` | `8083` |
| Product Service | `server.port` | `8084` |

#### AWS Production Environment

| Service | Key Variables | Values |
|---------|--------------|--------|
| All Services | `SPRING_PROFILES_ACTIVE` | `aws` |
| Database Services | `RDS_ENDPOINT`, `DOCDB_ENDPOINT` | Your AWS endpoints |
| Kafka | `MSK_BOOTSTRAP_SERVERS` | Your MSK endpoints |
| Monitoring | `XRAY_ENABLED` | `true` |

## Troubleshooting

### Application Won't Start - MySQL Connection Error

**Problem**: "Cannot connect to MySQL" or "Access denied for user" when starting application locally.

**Solution**:
1. Ensure Docker MySQL is running FIRST:
   ```bash
   docker-compose up -d mysql
   docker ps | grep mysql  # Should show mysql container running
   ```

2. Wait for MySQL to fully initialize (10-15 seconds after container starts)

3. Verify connection from localhost:
   ```bash
   # Test connection using Docker MySQL
   mysql -h localhost -P 3306 -u inventory_user -pkV33CaPPgSu1YuXJ inventory_db
   # If this works, your application should also connect
   ```

4. If still failing, check if port 3306 is already in use:
   ```bash
   lsof -i :3306  # On Mac/Linux
   netstat -an | grep 3306  # On Windows
   ```

5. Make sure NO local MySQL is running:
   ```bash
   # Stop local MySQL if running
   brew services stop mysql  # On Mac
   sudo systemctl stop mysql  # On Linux
   ```

### Service Won't Start in Docker

1. Check logs: `docker-compose logs inventory-service`
2. Verify MySQL is running: `docker-compose ps mysql`
3. Check database connectivity from within container:
   ```bash
   docker exec -it inventory-service sh
   # Inside container, test connection
   nc -zv mysql 3306
   ```

### Cannot Connect to Eureka

1. Verify Discovery Server is running: `docker-compose ps discovery-server`
2. Check Eureka logs: `docker-compose logs discovery-server`
3. Access Eureka dashboard: http://localhost:8761

### Kafka Connection Issues

1. Verify Kafka is healthy: `docker-compose ps broker`
2. Check Kafka logs: `docker-compose logs broker`
3. Test Kafka connectivity:
   ```bash
   docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --list
   ```

### gRPC Not Accessible

1. Check port mapping: `docker-compose ps inventory-service`
2. Verify gRPC server is started in logs
3. Test with grpcurl: `grpcurl -plaintext localhost:9090 list`

## Development Workflow

### Docker Compose Profiles

Create `infrastructure/docker-compose-full.yml` with profiles:

```yaml
# Add to each service definition
services:
  discovery-server:
    profiles: ["core", "all"]
    # ... rest of config
  
  api-gateway:
    profiles: ["core", "all"]
    # ... rest of config
  
  inventory-service:
    profiles: ["services", "all"]
    # ... rest of config
  
  order-service:
    profiles: ["services", "all"]
    # ... rest of config
  
  product-service:
    profiles: ["services", "all"]
    # ... rest of config
```

Run specific profiles:

```bash
# Run only infrastructure
docker-compose -f docker-compose-full.yml --profile core up

# Run all services
docker-compose -f docker-compose-full.yml --profile all up
```

### Development Mode with Hot Reload

For local development with hot reload:

```yaml
# docker-compose-dev.yml
services:
  inventory-service:
    build:
      context: ../inventory-service
      dockerfile: Dockerfile.dev
    volumes:
      - ../inventory-service/src:/app/src
      - ../inventory-service/target:/app/target
    command: mvn spring-boot:run
```

### Useful Docker Commands

```bash
# Build all images
docker-compose -f docker-compose-full.yml build

# Start specific services
docker-compose -f docker-compose-full.yml up discovery-server api-gateway

# Scale services
docker-compose -f docker-compose-full.yml up --scale order-service=3

# View resource usage
docker stats

# Clean up
docker-compose -f docker-compose-full.yml down -v --remove-orphans
docker system prune -a --volumes
```

## Network Architecture

### Service Communication Flow

```
Client → API Gateway (8080) → Service Discovery → Microservices
                ↓
         Load Balancing
                ↓
    ┌──────────────────────────┐
    │   Product Service (8084)  │ ← MongoDB
    │   Order Service (8083)    │ ← MySQL
    │   Inventory Service(8082) │ ← MySQL
    └──────────────────────────┘
                ↓
         Kafka Events → Notification Service
                ↓
         Zipkin Tracing
```

### Internal Communication

- **Service Discovery**: All services register with Eureka
- **API Gateway**: Routes external requests to services
- **Inter-service**: Direct HTTP calls using service names
- **Async Events**: Kafka for event-driven communication
- **gRPC**: Inventory service exposes gRPC on port 9090

## Security Best Practices

### Local Development

1. **Secrets Management**:
   ```yaml
   # Use .env file (add to .gitignore)
   MYSQL_PASSWORD=${MYSQL_PASSWORD}
   MONGO_PASSWORD=${MONGO_PASSWORD}
   ```

2. **Network Segmentation**:
   ```yaml
   networks:
     frontend:
       driver: bridge
     backend:
       driver: bridge
     data:
       driver: bridge
   ```

3. **Resource Limits**:
   ```yaml
   services:
     order-service:
       deploy:
         resources:
           limits:
             cpus: '0.5'
             memory: 512M
           reservations:
             cpus: '0.25'
             memory: 256M
   ```

### Production (AWS)

1. **AWS Secrets Manager**:
   ```java
   @Value("${aws.secretsmanager.secret-name}")
   private String secretName;
   ```

2. **IAM Roles** for service-to-service authentication
3. **VPC** with private subnets for services
4. **WAF** for API Gateway protection
5. **TLS/SSL** for all communications

## Action Checklist for AI Agent

### To Run Services Locally in Docker:

1. **Create all Dockerfiles** (Step 1)
   ```bash
   # In each service directory, create Dockerfile as specified above
   ```

2. **Create Docker-specific properties** (Step 1)
   ```bash
   # In each service's src/main/resources/, create application-docker.properties
   ```

3. **Create docker-compose-full.yml** (Step 2)
   ```bash
   # In infrastructure directory, create the complete compose file
   ```

4. **Build all images** (Step 4)
   ```bash
   cd infrastructure
   docker-compose -f docker-compose-full.yml build
   ```

5. **Start services** (Step 4)
   ```bash
   docker-compose -f docker-compose-full.yml up -d
   ```

6. **Verify** (Step 5)
   - Check Eureka: http://localhost:8761
   - Test API: curl http://localhost:8080/api/products

### To Deploy to AWS:

1. **Prepare AWS Resources**
   ```bash
   # Create ECR repositories
   aws ecr create-repository --repository-name discovery-server
   aws ecr create-repository --repository-name api-gateway
   # ... for each service
   ```

2. **Push Images to ECR**
   ```bash
   # Login to ECR
   aws ecr get-login-password | docker login --username AWS --password-stdin [ECR_URL]
   
   # Tag and push each image
   docker tag discovery-server:latest [ECR_URL]/discovery-server:latest
   docker push [ECR_URL]/discovery-server:latest
   ```

3. **Deploy Infrastructure**
   ```bash
   # Using Terraform
   terraform init
   terraform plan
   terraform apply
   ```

4. **Deploy Services**
   ```bash
   # For ECS
   aws ecs update-service --cluster orderproducts --service [service-name] --force-new-deployment
   ```

## Key Insight: No Local MySQL Required!

**The dockerize-services.md strategy successfully eliminates the need for local MySQL installation.**

How it works:
1. **Docker MySQL exposes port 3306 to localhost** (via port mapping in docker-compose)
2. **Your applications connect to `localhost:3306`** (as configured in application.properties)
3. **The connection goes to Docker MySQL**, not local MySQL
4. **Result**: Applications start successfully without any local MySQL!

### Verification Checklist

- ✅ Docker MySQL container is running and healthy
- ✅ Port 3306 is mapped from container to localhost
- ✅ Databases (inventory_db, order_db) exist in Docker MySQL
- ✅ Users (inventory_user, order_user) have proper permissions
- ✅ Applications can connect to localhost:3306
- ✅ No local MySQL installation needed

## Summary

This guide enables you to:

1. **Run Locally**: Complete microservices stack in Docker on your machine
2. **Deploy to AWS**: Production-ready deployment with managed services
3. **Scale**: From single-machine development to distributed cloud architecture

### Key Files to Create:

| File | Location | Purpose |
|------|----------|---------||
| Dockerfile | Each service directory | Build service images |
| application-docker.properties | Each service's resources | Docker configurations |
| docker-compose-full.yml | infrastructure/ | Orchestrate all services |
| application-aws.properties | Each service's resources | AWS configurations |
| deploy-to-aws.sh | Project root | Deployment automation |

## Quick Start Guide

### IMPORTANT: Running Applications WITHOUT Local MySQL

The following approach ensures your applications start successfully using only Docker MySQL, with no local MySQL installation needed.

### Option 1: Run Apps Locally + Infrastructure in Docker (Recommended for Development)

This is the simplest approach that solves the MySQL dependency issue:

```bash
# 1. Navigate to project root
cd /Users/ashishjha/Projects/OrderProducts

# 2. Start ONLY infrastructure in Docker
cd infrastructure
docker-compose up -d mysql mongodb zookeeper broker zipkin

# 3. Wait for MySQL to be fully ready (CRITICAL!)
echo "Waiting for MySQL to initialize..."
sleep 15

# 4. Verify MySQL is ready and has databases/users
docker exec -it mysql mysql -u root -prootpassword -e "
  SHOW DATABASES;
  SELECT User, Host FROM mysql.user WHERE User IN ('inventory_user', 'order_user');
"

# 5. Start Discovery Server locally (if needed)
cd ../discovery-server
mvn spring-boot:run &

# 6. Start Inventory Service (connects to Docker MySQL on localhost:3306)
cd ../inventory-service
mvn spring-boot:run &

# 7. Start Order Service (connects to Docker MySQL on localhost:3306)
cd ../order-service
mvn spring-boot:run &

# 8. Start Product Service (connects to Docker MongoDB on localhost:27017)
cd ../product-service
mvn spring-boot:run &

# 9. Start API Gateway
cd ../api-gateway
mvn spring-boot:run &

# 10. Verify services are running
curl http://localhost:8761  # Eureka (if using discovery server)
curl http://localhost:8080/actuator/health  # API Gateway

# Your applications are now running locally but using Docker infrastructure!
# No local MySQL installation needed!
```

### Option 2: Run Everything in Docker (Complete Containerization)

```bash
# 1. Navigate to project root
cd /Users/ashishjha/Projects/OrderProducts

# 2. Create all Dockerfiles and docker properties as specified in Steps 1-2

# 3. Navigate to infrastructure
cd infrastructure

# 4. Build all images
docker-compose -f docker-compose-full.yml build

# 5. Start everything
docker-compose -f docker-compose-full.yml up -d

# 6. Monitor startup
docker-compose -f docker-compose-full.yml logs -f

# All services running in containers - completely isolated from localhost!
```

### For AI Agent - AWS Deployment

```bash
# 1. Set AWS credentials
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=123456789012

# 2. Create ECR repositories
for service in discovery-server api-gateway inventory-service order-service product-service; do
  aws ecr create-repository --repository-name $service --region $AWS_REGION
done

# 3. Build and push images
for service in discovery-server api-gateway inventory-service order-service product-service; do
  docker build -t $service:latest ../$service
  docker tag $service:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$service:latest
  docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$service:latest
done

# 4. Deploy using Terraform or AWS CLI
# See Step 3 in Part 2 for detailed commands
```

## Validation Checklist

After running the setup, verify:

- [ ] All containers are running: `docker ps`
- [ ] Eureka shows all services: http://localhost:8761
- [ ] API Gateway responds: http://localhost:8080/actuator/health
- [ ] Can create a product via API
- [ ] Can place an order via API
- [ ] Traces appear in Zipkin: http://localhost:9411
- [ ] No errors in logs: `docker-compose logs`
# Dockerization Guide for OrderProducts Microservices

## Overview

This guide covers:
1. **Hybrid Approach (Recommended for Solo Development)** - Infrastructure in Docker, develop one service locally
2. **Full Docker Containerization** - For testing production parity before AWS deployment
3. **AWS Deployment** - Production deployment guide

## Why Dockerize?

### Current Setup Limitations
- **Environment conflicts**: Must remember to stop local MySQL
- **Manual startup**: Run each service separately in different terminals
- **No production parity**: Local setup differs from AWS deployment
- **Hard to replicate**: Difficult to reset to clean state

### Benefits of Dockerization

#### 1. Consistency Across Environments
- **Dev = Production**: Same Docker images run locally and in AWS
- Catch deployment issues before they reach production
- No "works on my machine" problems

#### 2. One-Command Operations
```bash
# Current: 5+ commands in different terminals
# Dockerized: Single command
docker-compose up -d
```

#### 3. Easy Environment Reset
```bash
# Clean slate for testing
docker-compose down -v
docker-compose up -d
```

#### 4. No Local Dependencies
- No need to install MySQL, MongoDB, Kafka locally
- No Java/Maven version conflicts with other projects
- No port conflicts with local services

#### 5. Resource Control
- Limit memory/CPU per service
- Prevent runaway processes
- Predictable performance

#### 6. Seamless AWS Deployment
- Build Docker images locally → test → deploy to ECS/EKS
- Same images, same behavior
- Simplified CI/CD pipeline

#### 7. Isolation & Security
- Services communicate via private Docker network
- No accidental connections to local databases
- Easy to tear down and rebuild

## Part 1: Running Apps Locally with Docker Infrastructure

### Current Setup (Already Working)

The existing `infrastructure/docker-compose.yml` provides all infrastructure services. Applications run locally and connect to Docker infrastructure on `localhost`.

### Quick Start

```bash
# 1. Ensure local MySQL is stopped
brew services stop mysql

# 2. Start infrastructure
cd infrastructure
docker-compose up -d

# 3. Wait for MySQL initialization
sleep 15

# 4. Verify setup
docker exec mysql mysql -u root -prootpassword -e "SHOW DATABASES;"

# 5. Run applications locally
cd ../inventory-service && mvn spring-boot:run &
cd ../order-service && mvn spring-boot:run &
cd ../product-service && mvn spring-boot:run &
cd ../api-gateway && mvn spring-boot:run &
```

## Part 2: Full Docker Containerization

**When to use:** Testing production parity before AWS deployment, or sharing environment with team.

**Purpose:** Ensure everything works together as Docker containers (same as AWS deployment).

### Step 1: Create Dockerfiles

#### Discovery Server

Create `discovery-server/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

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
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0
```

#### API Gateway

Create `api-gateway/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

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

spring.cloud.gateway.routes[0].id=product-service
spring.cloud.gateway.routes[0].uri=lb://product-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/products/**

spring.cloud.gateway.routes[1].id=order-service
spring.cloud.gateway.routes[1].uri=lb://order-service
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/order/**

spring.cloud.gateway.routes[2].id=discovery-server
spring.cloud.gateway.routes[2].uri=http://discovery-server:8761
spring.cloud.gateway.routes[2].predicates[0]=Path=/eureka/**

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0
```

#### Inventory Service

Create `inventory-service/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

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
spring.application.name=inventory-service
server.port=8082

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://mysql:3306/inventory_db
spring.datasource.username=inventory_user
spring.datasource.password=kV33CaPPgSu1YuXJ
spring.jpa.hibernate.ddl-auto=none

eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka
eureka.instance.prefer-ip-address=true

spring.kafka.bootstrap-servers=broker:29092
spring.kafka.consumer.group-id=inventoryId

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

grpc.server.port=9090

management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
```

#### Order Service

Create `order-service/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

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

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://mysql:3306/order_db
spring.datasource.username=order_user
spring.datasource.password=nS3johd59oQIcZhN
spring.jpa.hibernate.ddl-auto=none

eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.instance.prefer-ip-address=true

inventory.api.base-url=http://inventory-service

spring.kafka.bootstrap-servers=broker:29092
spring.kafka.template.default-topic=notificationTopic

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

resilience4j.circuitbreaker.instances.inventory.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.inventory.slidingWindowType=TIME_BASED
resilience4j.circuitbreaker.instances.inventory.slidingWindowSize=10
resilience4j.circuitbreaker.instances.inventory.failureRateThreshold=50
resilience4j.circuitbreaker.instances.inventory.waitDurationInOpenState=30s
```

#### Product Service

Create `product-service/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

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

spring.data.mongodb.uri=mongodb://mongodb:27017/product-service

eureka.client.serviceUrl.defaultZone=http://discovery-server:8761/eureka/
eureka.instance.prefer-ip-address=true

management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0
```

### Step 2: Create Full Docker Compose

Create `infrastructure/docker-compose-full.yml`:

```yaml
version: '3.8'

services:
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
      - mysql_data:/var/lib/mysql
    networks:
      - orderproduct-network

  mongodb:
    image: mongo:7.0
    container_name: mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    networks:
      - orderproduct-network

  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - orderproduct-network

  discovery-server:
    build: ../discovery-server
    container_name: discovery-server
    ports:
      - "8761:8761"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx256m -Xms128m
    networks:
      - orderproduct-network

  api-gateway:
    build: ../api-gateway
    container_name: api-gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - discovery-server
    networks:
      - orderproduct-network

  inventory-service:
    build: ../inventory-service
    container_name: inventory-service
    ports:
      - "9090:9090"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - mysql
      - broker
      - discovery-server
    networks:
      - orderproduct-network

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
    networks:
      - orderproduct-network

  product-service:
    build: ../product-service
    container_name: product-service
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - mongodb
      - discovery-server
    networks:
      - orderproduct-network

networks:
  orderproduct-network:
    driver: bridge

volumes:
  mysql_data:
  mongodb_data:
```

### Step 3: Build and Run

```bash
# Build all images
cd infrastructure
docker-compose -f docker-compose-full.yml build

# Start everything
docker-compose -f docker-compose-full.yml up -d

# View logs
docker-compose -f docker-compose-full.yml logs -f
```

### Step 4: Verify

- Eureka: http://localhost:8761
- API Gateway: http://localhost:8080/actuator/health
- Zipkin: http://localhost:9411

### When to Use Full Dockerization

Use full Docker setup to:

1. **Test before AWS deployment**: Verify all services work as containers
2. **Validate Docker images**: Ensure no runtime issues with containerized apps
3. **Test resource limits**: See how services behave with memory/CPU constraints
4. **Integration testing**: Run automated tests against containerized services
5. **Demo environment**: Share complete working system with stakeholders

After validating, switch back to hybrid mode for daily development.

## Part 3: AWS Deployment

### Prerequisites

- AWS Account with permissions
- AWS CLI configured
- ECR repositories created

### Step 1: Create ECR Repositories

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=<your-account-id>

for service in discovery-server api-gateway inventory-service order-service product-service; do
  aws ecr create-repository --repository-name $service --region $AWS_REGION
done
```

### Step 2: Build and Push Images

```bash
# Login to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build, tag, and push
for service in discovery-server api-gateway inventory-service order-service product-service; do
  docker build -t $service:latest ./$service
  docker tag $service:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$service:latest
  docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$service:latest
done
```

### Step 3: AWS Infrastructure

Replace containerized infrastructure with AWS managed services:

- **RDS MySQL** instead of Docker MySQL
- **Amazon DocumentDB** instead of MongoDB  
- **Amazon MSK** instead of Kafka
- **AWS X-Ray** instead of Zipkin

Create `application-aws.properties` for each service:

```properties
spring.profiles.active=aws
spring.datasource.url=jdbc:mysql://${RDS_ENDPOINT}:3306/${DB_NAME}
spring.kafka.bootstrap-servers=${MSK_BOOTSTRAP_SERVERS}
eureka.client.serviceUrl.defaultZone=http://${EUREKA_HOST}:8761/eureka/
```

### Step 4: Deploy with ECS/EKS

Use Terraform or AWS CLI to deploy services to ECS Fargate or EKS.

## Troubleshooting

### MySQL Connection Error

**Problem**: Access denied when starting application locally

**Solution**:
```bash
# Stop local MySQL
brew services stop mysql

# Verify Docker MySQL is accessible
mysql -h 127.0.0.1 -P 3306 -u inventory_user -pkV33CaPPgSu1YuXJ -e "SHOW DATABASES;"
```

### Port Already in Use

Check what's using the port:
```bash
lsof -i :3306
```

### Container Not Starting

Check logs:
```bash
docker-compose logs <service-name>
```

## Recommended Workflow for Solo Development with AWS Deployment Goal

### Daily Development (Hybrid Approach)
```bash
# Start infrastructure + stable services in Docker
cd infrastructure
docker-compose -f docker-compose-full.yml up -d mysql mongodb broker zipkin discovery-server

# Develop one service locally with full IDE debugging
cd ../inventory-service
# Run in IDE with breakpoints and debugging
```

**Benefits:**
- Fast development iteration
- Full debugging capabilities
- No Docker rebuild overhead
- Infrastructure isolated and consistent

### Weekly/Before AWS Deployment (Full Docker)
```bash
# Test everything as containers (production parity)
cd infrastructure
docker-compose -f docker-compose-full.yml build
docker-compose -f docker-compose-full.yml up -d

# Run integration tests
# Verify all services work together
# Check logs for any container-specific issues
```

**Benefits:**
- Catch deployment issues early
- Validate Docker images before AWS
- Test resource limits and scaling
- Ensure production parity

### Deployment to AWS
```bash
# Push tested images to AWS ECR
aws ecr get-login-password | docker login ...
docker-compose -f docker-compose-full.yml push

# Deploy to ECS/EKS (same images you tested locally)
```

**Benefits:**
- Confidence in deployment (tested locally first)
- Same images: local → AWS
- No surprises in production

## Summary

### Hybrid Approach (Daily Development)
✅ Infrastructure in Docker (MySQL, MongoDB, Kafka, Zipkin)  
✅ Stable services in Docker (discovery-server, api-gateway)  
✅ Service under development runs locally via IDE  
✅ Full debugging with breakpoints  
✅ Hot-reload and fast iteration  
✅ One-time setup: `brew services stop mysql`

### Full Containerization (Pre-Deployment Testing)
✅ All services as Docker containers  
✅ Test production parity before AWS  
✅ Validate resource limits  
✅ Integration testing  
✅ Build once, deploy anywhere

### AWS Deployment (Production)
✅ Same Docker images from local testing  
✅ Push to ECR  
✅ Deploy to ECS/EKS  
✅ Replace infrastructure with RDS, DocumentDB, MSK  
✅ Use `application-aws.properties`

## Quick Reference

| Scenario | Command | Use Case |
|----------|---------|----------|
| Daily dev | `docker-compose -f docker-compose-full.yml up -d mysql mongodb broker zipkin discovery-server` + run 1 service in IDE | Develop with debugging |
| Pre-AWS test | `docker-compose -f docker-compose-full.yml up -d` | Test production parity |
| Clean slate | `docker-compose -f docker-compose-full.yml down -v && docker-compose -f docker-compose-full.yml up -d` | Reset environment |
| Stop all | `docker-compose -f docker-compose-full.yml down` | End of day |
| View logs | `docker-compose -f docker-compose-full.yml logs -f <service>` | Debug container issues |
| Check status | `docker-compose -f docker-compose-full.yml ps` | See running containers |

## Development Tips

### Hot Reload with Spring DevTools

Add Spring DevTools to your `pom.xml` for automatic restart on code changes:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### Selective Service Startup

Run only the services you need:

```bash
# Minimal setup for inventory-service development
docker-compose -f docker-compose-full.yml up -d mysql broker zipkin

# Add discovery if testing service registration
docker-compose -f docker-compose-full.yml up -d discovery-server
```

### Environment Variables

Override configuration without changing files:

```bash
# Run with different database
MYSQL_HOST=localhost mvn spring-boot:run

# Run with different profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

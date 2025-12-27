# Discovery-Server

## Eureka Service Registry
It runs on:
```
http://localhost:8761/
```

Note: Start Eureka Service Registry before running your services.

## Responsibilities
- This service acts as Eureka Server. It registers other services (implemented as Eureka Client).
- Through Eureka Server dashboard, we can see service health related metrics.

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```

## Push the container image to DockerHub

```bash
VERSION=0.1.0
docker build -t ashishjha/orderproducts-discovery-server:$VERSION -t ashishjha/orderproducts-discovery-server:latest .

docker push ashishjha/orderproducts-discovery-server:$VERSION
docker push ashishjha/orderproducts-discovery-server:latest
```

# API Gateway (Spring Cloud Gateway)

This project is a **Spring Cloud Gateway** application that routes requests to your microservices, including Product, Order, Inventory, and Eureka Discovery Server. It acts as a single entry point to your system, centralizing routing, security, and monitoring.

## Features

- Centralized gateway for all microservices
- Built-in routes for microservices
- Access Eureka Discovery Server UI via the gateway
- Zipkin tracing enabled for distributed tracing

## Prerequisites

- Eureka Discovery Server (running on `localhost:8761`)
- Zipkin (optional, for tracing; running on `localhost:9411`)

## Running the Gateway
The API Gateway will start on port 8080 by default.

Once running, you can access your services through the gateway at http://localhost:8080

| Service                    | API Gateway Path                 | Notes                                   |
|----------------------------|----------------------------------|-----------------------------------------|
| Product Service            | `/api/products/**`               | For all product-related APIs            |
| Order Service              | `/api/order/**`                  | For all order-related APIs              |
| Inventory Swagger UI       | `/api/inventory/swagger-ui.html` | Inventory service Swagger documentation |
| Eureka Discovery Server UI | `/eureka/web`                    | Eureka web interface (see below)        |

For viewing traces, always use http://localhost:9411/ in your browser.

## Running the Application

To run the application, use:

```bash
mvn spring-boot:run
```


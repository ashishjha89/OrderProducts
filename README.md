### Eureka dashboard
http://localhost:8080/eureka/web

### Zipkin dashboard
http://localhost:9411/zipkin/

### Clean install all services
```
./clean_install_services.sh
```

### Run all services
```
./run_services.sh
```

### Stop all services
```
./stop_services.sh
```

### Technologies/concepts used
- Integration tests
  - TestContainers
  - Consumer Driven Contracts (spring-cloud-contract)
- Databases
  - MySql
  - MongoDB
- Kafka
- CircuitBreakers (Resilience4J)
- Distributed tracking (Zipkin & micrometer)
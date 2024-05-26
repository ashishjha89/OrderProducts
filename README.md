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

### Setup infrastructure and run services
It will start Docker-Desktop, MySql, MongoDB and then start all services
```
./start_infrastructure_and_services.sh
```

### Shutdown infrastructure and services
It will stop Docker-Desktop, MySql, MongoDB and then stop all services
```
./shutdown_infrastructure_and_services.sh
```

### Technologies/concepts used
- Integration tests
  - TestContainers
  - Consumer Driven Contracts (spring-cloud-contract)
- Databases
  - MySql
  - MongoDB
- CircuitBreakers (Resilience4J)
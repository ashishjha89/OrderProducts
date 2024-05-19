### Clean install all services
```
./clean_install_services.sh
```

### Run all services
```
./run_all_services.sh
```

### Run all services and required infrastructure
It will start MySql & MongoDB as well
```
./infrastructure_init_and_run_services.sh
```

### Stop all services
```
./stop_all_services.sh
```

### Eureka dashboard
http://localhost:8080/eureka/web

### Pre-requisites
Start MySql:
```
mysql.server start
```
Start mongodb:
```
brew services start mongodb-community
```

### Cleanup
Stop MySql:
```
mysql.server stop
```
Stop mongodb:
```
brew services stop mongodb-community
```
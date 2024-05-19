### Clean install all services
```
./clean_install_services.sh
```

### Eureka dashboard
http://localhost:8080/eureka/web


### Setup
Start MySql:
```
mysql.server start
```
Start mongodb:
```
brew services start mongodb-community@7.0
```

### Cleanup
Stop MySql:
```
mysql.server stop
```
Stop mongodb:
```
brew services stop mongodb-community@7.0
```
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Install Maven
RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src

RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp

RUN apk add --no-cache curl
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

ARG JAR_FILE=/workspace/app/target/*.jar
COPY --from=build ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]

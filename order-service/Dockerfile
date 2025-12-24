# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw clean install -DskipTests -Dmaven.test.skip=true

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp

RUN apk add --no-cache curl
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

ARG JAR_FILE=/workspace/app/target/*.jar
COPY --from=build ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]

# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
VOLUME /tmp

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash spring
USER spring

COPY --from=build /workspace/app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-XX:InitialRAMPercentage=25","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

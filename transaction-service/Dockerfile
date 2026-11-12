# Stage 1: Build the JAR with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy pom files first for dependency resolution
COPY pom.xml .
COPY transaction-service/pom.xml transaction-service/
COPY withdrawal-service/pom.xml withdrawal-service/

# Copy transaction-service source code
COPY transaction-service/src transaction-service/src

# Build transaction-service JAR
RUN mvn clean package -DskipTests -pl transaction-service -am

# Stage 2: Lightweight runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/transaction-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

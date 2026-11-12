# Stage 1: Build the JAR with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy pom files first for dependency resolution
COPY pom.xml .
COPY withdrawal-service/pom.xml withdrawal-service/
COPY transaction-service/pom.xml transaction-service/

# Copy withdrawal-service source code
COPY withdrawal-service/src withdrawal-service/src

# Build withdrawal-service JAR
RUN mvn clean package -DskipTests -pl withdrawal-service -am

# Stage 2: Lightweight runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/withdrawal-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

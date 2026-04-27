FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy pom.xml first (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy fat-jar (maven-shade-plugin output)
COPY --from=builder /build/target/vertx-app-1.0.0-SNAPSHOT.jar app.jar

# Copy external config directory (Spring Boot style, outside the JAR)
COPY config ./config

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:8888/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]

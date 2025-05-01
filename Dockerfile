FROM openjdk:21-jdk-slim as base

# Set working dir
WORKDIR /app

# Copy Java application
COPY target/search-gateway-api-1.0-SNAPSHOT.jar /app/search-gateway-api.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "search-gateway-api.jar"]

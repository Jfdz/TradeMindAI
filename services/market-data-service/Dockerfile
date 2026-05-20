# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Route Maven Central via repo1 to dodge intermittent 403s from the
# repo.maven.apache.org CDN edge on GitHub Actions runner IPs.
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21.0.5_11-jre-noble
RUN groupadd -r appgroup && useradd -r -g appgroup appuser && \
    apt-get update && apt-get install -y curl --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || curl -sf http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

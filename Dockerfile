# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first so dependency layer is cached separately
# from source code. This means `docker build` reuses the dep layer as long as
# pom.xml has not changed, even when source files change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copy source and build, skipping tests (tests run in CI before image build)
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for Docker health checks
RUN apk add --no-cache curl

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /workspace/target/intelligence-rag-api-*.jar /app/app.jar

# Expose application port
EXPOSE 8080

# Health check — Kubernetes also uses the /actuator/health endpoint via probes,
# but this Docker-level check is useful for `docker-compose` and local testing.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# JVM tuning for containers:
#  -XX:+UseContainerSupport        — respect Docker CPU/memory limits
#  -XX:MaxRAMPercentage=75.0       — use 75% of container memory for heap
#  -XX:+ExitOnOutOfMemoryError     — fail fast on OOM instead of degrading silently
#  -Djava.security.egd=...         — faster startup on Linux (avoids /dev/random block)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

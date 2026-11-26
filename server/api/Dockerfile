# syntax=docker/dockerfile:1.7
# Build context: repository root (see docker-compose.yml).

# ---------- Stage 1: build the Ktor service with the Gradle wrapper ----------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
ENV LABRUNNER_SERVER_ONLY=true \
    GRADLE_USER_HOME=/gradle-home \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

# 1) Build scripts change rarely -> dependency resolution is cached in its own layer.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared/contracts/build.gradle.kts shared/contracts/
COPY server/api/build.gradle.kts server/api/gradle.lockfile server/api/
COPY server/orchestrator/build.gradle.kts server/orchestrator/gradle.lockfile server/orchestrator/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    ./gradlew --quiet :server:api:dependencies --configuration runtimeClasspath > /dev/null

# 2) Sources change often -> only this layer is rebuilt on code changes.
COPY shared/contracts/src shared/contracts/src
COPY server/api/src server/api/src
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    ./gradlew --console=plain :server:api:installDist

# ---------- Stage 2: minimal runtime image (JRE only, non-root) ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S labrunner && adduser -S -D -H -u 10001 -G labrunner labrunner
WORKDIR /app
COPY --from=builder --chown=labrunner:labrunner /workspace/server/api/build/install/api/ ./

# Numeric UID: Kubernetes runAsNonRoot can verify it without /etc/passwd.
USER 10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# Exec form: the start script execs java, so the JVM is PID 1 and receives SIGTERM.
ENTRYPOINT ["/app/bin/api"]

# syntax=docker/dockerfile:1.7
# Build context: repository root (see docker-compose.yml).

# ---------- Stage 1: test and build the orchestrator ----------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
ENV LABRUNNER_SERVER_ONLY=true \
    GRADLE_USER_HOME=/gradle-home \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared/contracts/build.gradle.kts shared/contracts/
COPY server/api/build.gradle.kts server/api/gradle.lockfile server/api/
COPY server/orchestrator/build.gradle.kts server/orchestrator/gradle.lockfile server/orchestrator/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    ./gradlew --quiet :server:orchestrator:dependencies --configuration runtimeClasspath > /dev/null

COPY shared/contracts/src shared/contracts/src
COPY server/orchestrator/src server/orchestrator/src
# Unit tests run inside the build: a failing test means no image.
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    ./gradlew --console=plain :server:orchestrator:test :server:orchestrator:installDist

# ---------- Stage 2: minimal runtime image (JRE only, non-root) ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S labrunner && adduser -S -D -H -u 10001 -G labrunner labrunner
WORKDIR /app
COPY --from=builder --chown=labrunner:labrunner /workspace/server/orchestrator/build/install/orchestrator/ ./

# Numeric UID: Kubernetes runAsNonRoot can verify it without /etc/passwd.
USER 10001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
ENTRYPOINT ["/app/bin/orchestrator"]

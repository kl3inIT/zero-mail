# syntax=docker/dockerfile:1.7
# Build context: repo root
# docker build -f backend/worker/Dockerfile -t zeromail-worker .

# ============================================================================
# STAGE 1: Dependency resolver (cached layer)
# ============================================================================
FROM eclipse-temurin:25-jdk-noble AS deps

ENV GRADLE_USER_HOME=/cache/.gradle \
    LANG=C.UTF-8 \
    TZ=UTC

WORKDIR /build

COPY gradle/                           gradle/
COPY gradlew                           gradlew
COPY settings.gradle.kts              settings.gradle.kts
COPY build.gradle.kts                 build.gradle.kts
COPY gradle.properties                gradle.properties
COPY buildSrc/                        buildSrc/
COPY backend/core/build.gradle.kts    backend/core/build.gradle.kts
COPY backend/api/build.gradle.kts     backend/api/build.gradle.kts
COPY backend/worker/build.gradle.kts  backend/worker/build.gradle.kts

RUN --mount=type=cache,id=gradle-cache,target=/cache/.gradle,sharing=locked \
    chmod +x gradlew && \
    ./gradlew :backend:worker:dependencies --no-daemon --parallel --console=plain

# ============================================================================
# STAGE 2: Application builder
# ============================================================================
FROM deps AS builder

COPY backend/core/src/main/    backend/core/src/main/
COPY backend/worker/src/main/  backend/worker/src/main/

RUN --mount=type=cache,id=gradle-cache,target=/cache/.gradle,sharing=locked \
    ./gradlew :backend:worker:bootJar --no-daemon --parallel --console=plain -x test && \
    mkdir -p /app && \
    mv backend/worker/build/libs/*.jar /app/app.jar

# Extract Spring Boot layers
RUN java -Djarmode=layertools -jar /app/app.jar extract --destination /app/extracted

# ============================================================================
# STAGE 3: Runtime (eclipse-temurin:25-jre-noble — same glibc as builder)
# ============================================================================
FROM eclipse-temurin:25-jre-noble

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get upgrade -y && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      tini \
      curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1654 appgroup && \
    useradd  -u 1654 -g appgroup -s /sbin/nologin appuser

WORKDIR /app

COPY --from=builder --chown=1654:1654 /app/extracted/dependencies/          ./
COPY --from=builder --chown=1654:1654 /app/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=1654:1654 /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=1654:1654 /app/extracted/application/           ./

USER 1654:1654

ENV JAVA_TOOL_OPTIONS="--enable-preview \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled \
  -XX:+DisableExplicitGC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8"

EXPOSE 8081

LABEL org.opencontainers.image.title="ZeroMail Worker" \
      org.opencontainers.image.description="ZeroMail background worker — Spring Boot 4 / JDK 25" \
      org.opencontainers.image.source="https://github.com/kl3inIT/zero-mail" \
      maintainer="dathip04@gmail.com"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]

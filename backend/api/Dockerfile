# syntax=docker/dockerfile:1.7
# Build context: repo root
# docker build -f backend/api/Dockerfile -t zeromail-api .

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
    ./gradlew :backend:api:dependencies --no-daemon --parallel --console=plain

# ============================================================================
# STAGE 2: Application builder
# ============================================================================
FROM deps AS builder

COPY backend/core/src/main/   backend/core/src/main/
COPY backend/api/src/main/    backend/api/src/main/

RUN --mount=type=cache,id=gradle-cache,target=/cache/.gradle,sharing=locked \
    ./gradlew :backend:api:bootJar --no-daemon --parallel --console=plain -x test && \
    mkdir -p /app && \
    mv backend/api/build/libs/*.jar /app/app.jar

# Extract Spring Boot layers with the SB 3.4+ `tools` jarmode (layertools is
# deprecated). --launcher is REQUIRED so the spring-boot-loader layer keeps the
# JarLauncher classes; without it the CMD below ClassNotFounds. With --launcher
# the overlaid layout (BOOT-INF/ + META-INF/ + org/) is identical to the old
# layertools output, so the stage-3 COPY paths + JarLauncher CMD stay unchanged.
RUN java -Djarmode=tools -jar /app/app.jar extract --layers --launcher --destination /app/extracted

# ============================================================================
# STAGE 3: Runtime (BellSoft Liberica JRE 25 on Alpaquita glibc).
# Alpaquita glibc keeps the same libc family as the Temurin builder, so the
# extracted glibc-linked native libs (netty-tcnative, etc.) stay compatible —
# no musl footguns — while dropping the base from Ubuntu Noble (~570MB) to
# Alpaquita (~210MB). BellSoft ships monthly CVE rebuilds for LTS JDKs.
# Package manager is apk (BusyBox userland), not apt.
# ============================================================================
FROM bellsoft/liberica-runtime-container:jre-25-glibc

# apk has no persistent global cache config to fight; --no-cache keeps the layer
# clean. tini for PID-1 signal handling, curl for the healthcheck probe.
RUN apk add --no-cache tini curl

RUN addgroup -g 1654 -S appgroup && \
    adduser  -u 1654 -S appuser -G appgroup

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

EXPOSE 8080

LABEL org.opencontainers.image.title="ZeroMail API" \
      org.opencontainers.image.description="ZeroMail API server — Spring Boot 4 / JDK 25" \
      org.opencontainers.image.source="https://github.com/kl3inIT/zero-mail" \
      maintainer="dathip04@gmail.com"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/usr/sbin/tini", "--"]
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]

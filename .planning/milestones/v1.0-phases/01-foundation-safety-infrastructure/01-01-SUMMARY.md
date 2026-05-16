---
phase: 01-foundation-safety-infrastructure
plan: 01
status: complete
completed: 2026-04-25
---

# Plan 01-01 — Gradle Multi-Module Scaffold

## What shipped

- Gradle 9.4.1 wrapper (bin distribution).
- Kotlin DSL multi-project: `backend:core` (java-library), `backend:api` (Boot app), `backend:worker` (Boot app).
- `gradle/libs.versions.toml` with all locked versions (Spring Boot 4.0.6, Spring AI 2.0.0-M4, Spring Cloud GCP 8.0.2, Spring Modulith 2.0.7-SNAPSHOT, Liquibase 5.0.2, ArchUnit 1.3.0, Testcontainers 1.21.3, springdoc 2.8.6, logstash-logback 8.0, jsoup 1.18.3, Gmail API v1-rev20250331-2.0.0, google-auth-library 1.35.0).
- Four `buildSrc` convention plugins: `zeromail.java-conventions` (JDK 25 toolchain, UTF-8, `-parameters`, JUnit Platform), `zeromail.spring-boot-conventions` (Boot + GCP BOMs), `zeromail.archunit-conventions` (archunit-junit5 testImpl), `zeromail.modulith-conventions` (Modulith BOM + starter-core / starter-test).
- Runnable Boot shells: `com.zeromail.api.Application`, `com.zeromail.worker.WorkerApplication` with `@EnableScheduling` + `HealthcheckScheduler` (per CONTEXT.md D-A1).
- Runtime config only in `backend/api/src/main/resources/application.yml` and `backend/worker/src/main/resources/application.yml`. `backend/core` is yaml-free (NIT-2 enforced).
- Virtual threads enabled (`spring.threads.virtual.enabled=true`).
- `.gitignore`, `.env.example`.

## Verification

- `./gradlew --version` → Gradle 9.4.1.
- `./gradlew projects` → lists `backend:core`, `backend:api`, `backend:worker`.
- `./gradlew :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava` → BUILD SUCCESSFUL.
- `./gradlew :backend:api:bootJar :backend:worker:bootJar` → BUILD SUCCESSFUL.
- `grep "springBoot = \"4.0.6\"" gradle/libs.versions.toml` → 1 match.
- `grep "springModulith = \"2.0.7-SNAPSHOT\"" gradle/libs.versions.toml` → 1 match.
- `grep "JavaLanguageVersion.of(25)" buildSrc/src/main/kotlin/zeromail.java-conventions.gradle.kts` → 1 match.
- `test ! -f backend/core/src/main/resources/application.yml` → PASS (core yaml-free).

## Notes for downstream plans

- Modulith starter dependencies pull from Spring snapshot repo; first offline build will fail without network.
- `@Modulithic` annotation deferred to Plan 02 (added after tenant/persistence packages exist).
- `spring-cloud-gcp-starter-secretmanager` present in api deps; key loading wired in Plan 06.
- springdoc starter present in api deps; OpenApiConfig wired in Plan 07.

## Requirements addressed

FND-01, FND-02, FND-03, FND-04, FND-05, FND-06 — all scaffolding groundwork.

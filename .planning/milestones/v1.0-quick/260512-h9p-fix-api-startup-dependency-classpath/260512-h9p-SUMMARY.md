---
quick_id: 260512-h9p
status: complete
completed: 2026-05-12
---

# Quick Task 260512-h9p Summary

## Task

Diagnose and fix the backend API startup failure reported from the IntelliJ run configuration.

## Findings

- Gradle `backend:api` runtimeClasspath resolves `org.springframework.ai:spring-ai-openai:2.0.0-M6`, not M5.
- Gradle `backend:api` runtimeClasspath includes `com.google.guava:guava:33.5.0-jre`, which provides `com.google.common.cache.CacheLoader`.
- JetBrains project dependencies also show Spring AI M6 and Guava on the project classpath.
- The pasted `NoClassDefFoundError` / `NoSuchMethodError` stack trace was therefore from a stale IDE/Gradle classpath, not the current Gradle dependency graph.

## Changes

- Restored Java preview runtime support in `ZeroMailApi` and `ZeroMailWorker` run configurations while keeping the existing UTC timezone setting.
- Updated the root Gradle wrapper task declaration to `9.5.0` so it matches the checked-in wrapper distribution.

## Verification

- `./gradlew.bat --console=plain :backend:api:compileJava :backend:core:compileJava :backend:api:test --tests "*PubSubOidcAuthFilterTest*"` - passed.
- JetBrains `ZeroMailApi` run configuration started successfully on port 8080 and passed the previous Pub/Sub OIDC / Spring AI bean creation point.

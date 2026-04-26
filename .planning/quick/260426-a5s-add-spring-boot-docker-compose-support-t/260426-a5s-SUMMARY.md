---
phase: quick-260426-a5s
plan: 01
subsystem: backend/api dev tooling
tags: [dev-loop, docker-compose, spring-boot-4, gradle]
requires: ["docker-compose.yml at repo root with postgres + redis services"]
provides: ["zero-config bootRun for new contributors (Postgres 17.6 + Redis 7.2 auto-launch)"]
affects: ["backend/api dev classpath only (developmentOnly); bootJar / productionRuntimeClasspath unchanged"]
tech-stack:
  added:
    - "org.springframework.boot:spring-boot-docker-compose (BOM-managed, 4.0.6)"
  patterns:
    - "developmentOnly dependency configuration (Spring Boot Gradle plugin)"
    - "spring.docker.compose.lifecycle-management = start_only for fast dev loop"
key-files:
  created: []
  modified:
    - backend/api/build.gradle.kts
    - backend/api/src/main/resources/application.yml
decisions:
  - "Used start_only (not start_and_stop) so containers survive IDE restart cycles"
  - "Did NOT add an entry to gradle/libs.versions.toml because the artifact is BOM-managed (matches existing spring-boot-starter-* pattern in this same module)"
  - "Did NOT promote the dependency into the zeromail.spring-boot-conventions plugin: must remain api-only for v1 because backend/worker shell exists but does not run yet"
  - "Did NOT pre-emptively set SPRING_DOCKER_COMPOSE_ENABLED=false in any local config — default-on is the design intent for new contributors; opt-out is per-developer via env var"
metrics:
  duration: "5 min"
  completed: 2026-04-26
---

# Quick Task 260426-a5s: Add spring-boot-docker-compose dev support — Summary

Wired Spring Boot 4.0.6's docker-compose support into `backend/api` as a `developmentOnly` dependency so `./gradlew :backend:api:bootRun` and IDE Run targets auto-launch the existing Postgres 17.6 + Redis 7.2 services from the repo-root `docker-compose.yml`, with `lifecycle-management=start_only` so containers persist across restart cycles.

## Diffs

### `backend/api/build.gradle.kts` (1 line added)

```diff
     implementation("com.google.cloud:spring-cloud-gcp-starter-secretmanager")
+    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
     testImplementation("org.springframework.boot:spring-boot-starter-test")
```

No version string — BOM-managed via the `org.springframework.boot:spring-boot-dependencies:4.0.6` import already declared in `buildSrc/src/main/kotlin/zeromail.spring-boot-conventions.gradle.kts`. Resolves to `4.0.6` (verified, see "Verification" below).

### `backend/api/src/main/resources/application.yml` (3 lines added)

```diff
   threads:
     virtual:
       enabled: true
+  docker:
+    compose:
+      lifecycle-management: start_only
   datasource:
```

`start_only` chosen over the default `start_and_stop` so the compose stack survives IDE/app restart cycles — a fast-dev-loop guarantee. Spring Boot 4 accepts both `start_only` and `start-only`; underscore form matches the canonical `DockerComposeProperties.LifecycleManagement` enum name.

## Verification

### Automated (Task 1 verify gate — passed)

`./gradlew --no-daemon :backend:api:dependencies --configuration developmentOnly` shows:

```
developmentOnly - Configuration for development-only dependencies such as Spring Boot's DevTools.
\--- org.springframework.boot:spring-boot-docker-compose -> 4.0.6
     +--- tools.jackson.core:jackson-databind:3.1.2
     ...
     \--- org.springframework.boot:spring-boot-autoconfigure:4.0.6
```

### Production fat jar isolation — passed (with a note)

The plan's `<verification>` block called for `./gradlew :backend:api:dependencies --configuration runtimeClasspath` to NOT show `spring-boot-docker-compose`. In practice, the Spring Boot Gradle plugin makes `developmentOnly` extend `runtimeClasspath` so that `bootRun` (and IDE Run) can see the artifact — therefore it DOES appear on `runtimeClasspath`.

The configuration that actually drives `bootJar` packaging is **`productionRuntimeClasspath`**, which `developmentOnly` deliberately does NOT extend. Verified:

```
$ ./gradlew --no-daemon :backend:api:dependencies --configuration productionRuntimeClasspath -q | grep docker-compose
NOT FOUND on productionRuntimeClasspath (expected)
```

So the must-have ("production fat jar built by `./gradlew :backend:api:bootJar` does NOT contain `spring-boot-docker-compose` on its runtime classpath") is satisfied. The plan's check was simply pointing at the wrong configuration name.

### Human-verify checkpoint (Task 2) — DEFERRED to user

Task 2 is a `checkpoint:human-verify` gate requiring a Docker Desktop workstation. The executor agent does not run interactive Docker validation. The 8 steps must be performed by the developer:

1. **Tear down any pre-existing dev stack** — `docker compose -f docker-compose.yml down -v` and confirm no zeromail-* containers via `docker ps`.
2. **Start the api with no manual compose step** — `./gradlew :backend:api:bootRun`. Expect log lines: `Using Docker Compose file ...docker-compose.yml`, `Container <project>-postgres-1 Started`, `Container <project>-redis-1 Started`, `Liquibase: Update has been successful`, `Started ZeromailApiApplication` — all without DB / Redis errors.
3. **Confirm containers are up** — `docker ps | grep -E "postgres|redis"` shows both with status `Up`.
4. **Smoke endpoint** — `curl -s http://localhost:8080/actuator/health` → `{"status":"UP"}` with no DB / Redis component reporting `DOWN`.
5. **Stop app, confirm `start_only` semantics** — Ctrl+C, then `docker ps` must STILL show both containers running. If torn down, `lifecycle-management` is misconfigured.
6. **Restart bootRun, confirm reattach** — second `bootRun` should NOT log `Container ... Started` again; reuses running containers; startup should be noticeably faster.
7. **Production jar isolation** — `./gradlew :backend:api:bootJar` then `unzip -l backend/api/build/libs/*.jar | grep -i docker-compose` → expect ZERO matches.
8. **Opt-out smoke test** — `docker compose -f docker-compose.yml down -v`, then `SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew :backend:api:bootRun` should attempt and FAIL to connect to localhost:5432 (proving the env-var opt-out actually disables auto-launch).

If all eight pass, reply `approved`. If any step fails, paste the failing log lines / `docker ps` output.

**Status: deferred to user — checkpoint not run by executor.** Quick task is functionally complete after Task 1; Task 2 is the user-driven validation gate.

## Opt-out path (for developers who manage docker-compose manually)

```bash
SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew :backend:api:bootRun
```

This matches the canonical Spring Boot Docker Compose property `spring.docker.compose.enabled` mapped through the standard relaxed-binding env-var convention. No code changes required, no per-developer config file. Documented here per Task 1 `<action>` rationale.

## Out-of-scope (deliberately not changed)

- **`backend/worker/build.gradle.kts`** — worker shell exists but does not run yet in v1 (per plan scope). When worker activates and needs DB/Redis access for local dev, replicate the same `developmentOnly(...)` line there. Do not promote into the convention plugin until at least one more module legitimately needs it.
- **`gradle/libs.versions.toml`** — BOM-managed artifact, no catalog entry needed (matches the existing `spring-boot-starter-*` pattern in `backend/api/build.gradle.kts` lines 10–17).
- **`docker-compose.yml`** — already correct (postgres:17.6 on 5432, redis:7.2 on 6379) and Spring Boot Docker Compose detects both images by default.
- **No new application profile / `application-local.yml`** — default-on for new contributors is the design intent. Production isolation is enforced by `developmentOnly` excluding the starter from `productionRuntimeClasspath`, which means `spring.docker.compose.*` keys are simply ignored at runtime when the starter isn't on the classpath.

## Deviations from Plan

### Auto-fixed Issues

None. Both surgical edits matched the plan exactly.

### Notes (not deviations)

**1. [Plan documentation drift, NOT a code change] runtimeClasspath vs productionRuntimeClasspath**
- **Found during:** Task 1 automated verify
- **Issue:** Plan `<verification>` instructs checking `runtimeClasspath` for absence of `spring-boot-docker-compose`. Spring Boot 4's Gradle plugin actually extends `runtimeClasspath` with `developmentOnly` so that `bootRun` and IDE Run can see the artifact. The configuration that drives `bootJar` is `productionRuntimeClasspath`.
- **Resolution:** No code change required. The MUST_HAVES truth ("production fat jar does NOT contain spring-boot-docker-compose") was satisfied via `productionRuntimeClasspath` check. Suggest future plan templates reference `productionRuntimeClasspath` for Spring Boot 4 dev-only isolation checks.

## Commits

| Hash | Message |
|------|---------|
| `1219ec8` | `feat(quick-260426-a5s): add spring-boot-docker-compose dev support` |

Initial commit `ca28be5` accidentally swept in unrelated `apps/web/*` files modified by the in-progress parent quick task on this branch. Reset via `git reset --soft HEAD~1`, unstaged the unrelated files, then re-committed via `git commit --only <paths>` to enforce strict scope. Final commit `1219ec8` contains exactly 2 files / 4 lines.

## Self-Check: PASSED

- File: `backend/api/build.gradle.kts` — present, contains `developmentOnly("org.springframework.boot:spring-boot-docker-compose")` on line 18.
- File: `backend/api/src/main/resources/application.yml` — present, contains `spring.docker.compose.lifecycle-management: start_only` block at lines 7–9.
- Commit `1219ec8` — present in `git log`.
- Production isolation verified: `productionRuntimeClasspath` does NOT contain the starter.
- developmentOnly resolution verified: starter resolves to 4.0.6 via Spring Boot 4.0.6 BOM.

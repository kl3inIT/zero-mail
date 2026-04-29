---
phase: 02A-mail-ingestion
plan: "03"
subsystem: api
tags: [gmail, pubsub, oidc, spring-security, postgresql, tenant]
requires:
  - phase: 02A-01
    provides: Gmail ingestion schema, repositories, JSONB payload storage, and Pub/Sub delivery insert contract
  - phase: 02A-02
    provides: Gmail worker processing semantics that consume pending Pub/Sub delivery rows
provides:
  - Pub/Sub push receiver protected by Google OIDC bearer-token verification
  - PubSubIngestionService with unscoped Gmail email lookup and tenant-bound idempotent delivery insert
  - Test-profile user authentication chain that excludes Pub/Sub machine endpoints
  - Triage pause API endpoint backed by tenant state
  - Extended /me response with triage pause and Gmail ingestion health
affects: [02A-mail-ingestion, backend-api, backend-core, gmail-ingestion, tenant-settings]
tech-stack:
  added: [Google TokenVerifier runtime use, Spring Security dual SecurityFilterChain, JdbcTemplate unscoped lookup]
  patterns:
    - Machine-authenticated Pub/Sub endpoints use their own first-order security chain
    - Controllers stay thin while core services own persistence and tenant binding
    - Test-profile user endpoints must use TestSessionSupport headers instead of bypassing auth
key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java
    - backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java
    - backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/FlexibleLongDeserializer.java
    - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/tenant/package-info.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java
    - backend/api/src/main/java/com/zeromail/api/controllers/MeController.java
    - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
    - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java
    - backend/api/src/main/resources/application.yml
    - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
    - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
    - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
key-decisions:
  - "Pub/Sub OIDC verification is isolated in an @Order(1) SecurityFilterChain that remains active under test profile."
  - "PubSubIngestionService performs Gmail email lookup with unscoped JdbcTemplate, then binds TenantContext before inserting delivery rows."
  - "GmailPubSubController returns normal 200 responses without ResponseEntity to avoid existing controller-boundary ArchUnit false positives on classes matching .*Entity."
  - "/me composes tenant pause state and Gmail ingestion health from services; googleEmail is response-only and not logged."
patterns-established:
  - "Disable servlet auto-registration for filters that are only intended to run inside a Spring Security chain."
  - "TestSessionSupport protects non-Pub/Sub test endpoints and explicitly excludes /internal/pubsub/** so machine-auth tests exercise PubSubSecurityConfig."
requirements-completed: [MAIL-01, MAIL-03, MAIL-04, MAIL-06]
duration: 20min
completed: 2026-04-29
---

# Phase 02A Plan 03: Pub/Sub Receiver and Triage Pause API Summary

**Google OIDC-protected Gmail Pub/Sub push receiver with tenant-bound idempotent ingestion, triage pause controls, and /me ingestion health status**

## Performance

- **Duration:** 20 min
- **Started:** 2026-04-29T06:03:00Z
- **Completed:** 2026-04-29T06:20:45Z
- **Tasks:** 2 completed
- **Files modified:** 26

## Accomplishments

- Added a dedicated Pub/Sub security chain, `PubSubOidcAuthFilter`, and disabled servlet auto-registration so `/internal/pubsub/**` is machine-authenticated before the normal user OAuth chain.
- Added `PubSubIngestionService` as the sole persistence owner for Pub/Sub pushes: unscoped Gmail email lookup, tenant-bound `TransactionTemplate`, and `insertPendingIfAbsent` dedup by row count.
- Added thin API controllers for Gmail Pub/Sub push and tenant triage pause, with test-profile auth coverage for user endpoints.
- Extended `/me` to return `triagePaused` and `gmailConnectionStatus.ingestionHealth`, including green coverage for authenticated and unauthenticated test-profile requests.

## Task Commits

1. **Task 1: PubSubIngestionService + OIDC filter + dual SecurityFilterChain + thin controllers** - `5e6351b` (feat)
2. **Task 2: Extend MeResponse with triagePaused + gmailConnectionStatus** - `0de7d2d` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java` - Controller-to-service ingestion result enum.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java` - Ack-fast delivery insertion with privacy-safe logging and tenant binding.
- `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java` - Google OIDC bearer-token verification for Pub/Sub pushes.
- `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java` - First-order `/internal/pubsub/**` security chain and disabled filter servlet registration.
- `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java` - Thin Pub/Sub push receiver.
- `backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java` - `PUT /tenant/triage-pause`.
- `backend/api/src/main/java/com/zeromail/api/dto/gmail/*.java` - Pub/Sub and Gmail notification DTOs with flexible `historyId` parsing.
- `backend/api/src/main/java/com/zeromail/api/dto/tenant/*.java` - Triage pause request/response DTOs and tenant DTO named interface.
- `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java` - Extended account response shape.
- `backend/api/src/main/java/com/zeromail/api/controllers/MeController.java` - Adds tenant pause and Gmail status reads for `/me`.
- `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java` - Adds `ingestionHealth` to the service projection.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` - Populates extended Gmail connection projection.
- `backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java` - Adds triage pause mutation and read helpers.
- `backend/api/src/test/java/com/zeromail/api/**/*.java` - Enables and extends Pub/Sub, triage pause, OIDC, and `/me` tests.

## Decisions Made

- Kept Pub/Sub endpoints out of the user-session chain by giving them their own `@Order(1)` `SecurityFilterChain`; the normal user chain is explicitly `@Order(2)`.
- Chose service-owned `JdbcTemplate` lookup plus tenant-bound transaction for Pub/Sub ingestion, preserving the tenant invariant while still accepting pre-tenant Gmail push payloads.
- Returned `void` from `GmailPubSubController` rather than `ResponseEntity<Void>` because the current controller-boundary ArchUnit rule treats any type ending in `Entity` as a forbidden persistence entity.
- Added `/me` Gmail connection status through `GmailConnectionService.currentStatus`, extending the projection rather than exposing persistence entities to the API layer.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Avoided controller-boundary ArchUnit false positive on ResponseEntity**
- **Found during:** Task 1
- **Issue:** The plan sketch used `ResponseEntity<Void>`, but the existing controller-boundary rule matches classes named `.*Entity`, which includes `org.springframework.http.ResponseEntity`.
- **Fix:** `GmailPubSubController` returns `void` for normal ack paths, preserving HTTP 200 behavior without importing `ResponseEntity`.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java`
- **Verification:** Pub/Sub controller integration tests passed and static grep confirmed no direct repository injection.
- **Committed in:** `5e6351b`

**2. [Rule 3 - Blocking] Used a direct RequestMatcher for test-profile non-Pub/Sub endpoints**
- **Found during:** Task 1
- **Issue:** The expected `AntPathRequestMatcher` import path was not available under the Spring Security 7 dependency set in this project.
- **Fix:** Used a direct `RequestMatcher` lambda that excludes `/internal/pubsub/**`, keeping Pub/Sub tests owned by `PubSubSecurityConfig`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java`
- **Verification:** Static checks confirmed Pub/Sub tests do not import `TestSessionSupport`; all targeted tests passed.
- **Committed in:** `5e6351b`

**3. [Rule 2 - Missing Critical] Added test-base Pub/Sub security defaults**
- **Found during:** Task 1 test startup
- **Issue:** The new always-active Pub/Sub security config needs audience and service-account properties even under the test profile.
- **Fix:** Added test defaults for `pubsub.push-audience-url` and `pubsub.sa-principal-email` in `ApiPostgresTestBase`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java`
- **Verification:** All API integration tests in this plan booted under `@ActiveProfiles("test")`.
- **Committed in:** `5e6351b`

**4. [Rule 2 - Missing Critical] Exposed tenant DTO package as a Modulith named interface**
- **Found during:** Task 1 API DTO wiring
- **Issue:** The new nested `api.dto.tenant` package needed the same named-interface exposure pattern used by existing API DTO subpackages.
- **Fix:** Added `backend/api/src/main/java/com/zeromail/api/dto/tenant/package-info.java` with `@NamedInterface("tenant")`.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/dto/tenant/package-info.java`
- **Verification:** API compile and targeted tests passed.
- **Committed in:** `5e6351b`

---

**Total deviations:** 4 auto-fixed (1 bug, 2 missing critical, 1 blocking)
**Impact on plan:** All fixes were required for compatibility with existing architecture constraints, test-profile startup, or endpoint authentication correctness. No unrelated product scope was added.

## Issues Encountered

- `FlexibleLongDeserializer` compiles with a Jackson deprecation warning for `JsonParser.getText()`. The plan verification still passes; this is not a blocking correctness issue for the accepted behavior.

## Verification

- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava :backend:api:test --tests "com.zeromail.api.controllers.MeControllerTest"` - PASS.
- `.\gradlew.bat :backend:api:compileJava :backend:core:compileJava :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" --tests "*TriagePauseControllerTest*" --tests "*PubSubIdempotencyTest*" --tests "com.zeromail.api.controllers.MeControllerTest" --rerun-tasks` - PASS.
- Test XML counts: `PubSubOidcAuthFilterTest` 7, `GmailPubSubControllerIntegrationTest` 6, `TriagePauseControllerTest` 3, `PubSubIdempotencyTest` 2, `MeControllerTest` 4; all had 0 failures, 0 errors, 0 skipped.
- Static check: `GmailPubSubController` has no `GmailConnectionRepository` or `PubSubDeliveryRepository` references - PASS.
- Static check: `SecurityConfig` has `@Order(2)` on the user-session `SecurityFilterChain` method - PASS.
- Static check: `application.yml` contains `PUBSUB_PUSH_AUDIENCE_URL:?` and `PUBSUB_SA_PRINCIPAL_EMAIL:?` - PASS.
- Static check: `MeControllerTest` and `TriagePauseControllerTest` import `TestSessionSupport` and send `HEADER_SUBJECT` / `HEADER_EMAIL` on successful requests - PASS.
- Static check: `PubSubIdempotencyTest` and `GmailPubSubControllerIntegrationTest` do not import `TestSessionSupport` - PASS.

## Known Stubs

None. Stub-pattern scan found no blocking TODO/FIXME/placeholder code; matches were log placeholder braces, null guards, and explanatory comments/Javadocs.

## User Setup Required

Runtime configuration must provide `PUBSUB_PUSH_AUDIENCE_URL` and `PUBSUB_SA_PRINCIPAL_EMAIL`. `PUBSUB_OIDC_CERTIFICATES_URL` defaults to Google's certificate endpoint.

## Auth Gates

None.

## Next Phase Readiness

Ready for 02A-04. The API now receives authenticated Gmail Pub/Sub pushes, writes idempotent pending delivery rows, exposes tenant triage pause controls, and returns pause/ingestion health status to the frontend.

## Self-Check: PASSED

- Summary and key created files exist on disk.
- Task commits found in git history: `5e6351b`, `0de7d2d`.
- No tracked file deletions were introduced by plan commits.
- Stub-pattern scan found no blocking stubs; matches were log placeholders, null guards, and explanatory comments/Javadocs.

---
*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*

---
phase: 02A-mail-ingestion
plan: "02"
subsystem: worker
tags: [gmail, oauth, pubsub, scheduler, spring, postgresql]
requires:
  - phase: 02A-01
    provides: Gmail ingestion schema, repositories, Google Gmail API dependencies, and Wave 0 worker tests
provides:
  - Gmail API client factory with headless OAuth refresh and invalid-grant detection
  - GmailConnectionService watch/history state transitions plus DB-only disconnect semantics
  - Public transactional GmailDeliveryProcessingService for privacy-safe history fan-out
  - GmailWatchScheduler and GmailHistoryProcessor worker schedulers
  - Worker runtime JPA/Postgres wiring and hermetic worker scheduler tests
affects: [02A-mail-ingestion, backend-core, backend-worker, gmail-ingestion]
tech-stack:
  added: [google-api-services-gmail runtime use, google-auth-library-oauth2-http runtime use, testcontainers-postgresql in worker tests]
  patterns:
    - Public service-owned transaction boundary for scheduled per-row work
    - Invalid-grant paths call DB-only markDisconnected instead of user disconnect cleanup
    - Worker tests use a shared Postgres container plus hermetic Gmail/OAuth HTTP server
key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java
    - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
    - backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java
    - backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java
    - backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java
    - backend/worker/src/main/resources/application.yml
    - backend/worker/build.gradle.kts
    - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
    - backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java
    - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
key-decisions:
  - "GmailConnectionService.markDisconnected uses TransactionTemplate for a DB-only durable state update before best-effort users.stop cleanup."
  - "GmailHistoryProcessor remains a thin scheduled loop; GmailDeliveryProcessingService owns the public @Transactional per-delivery boundary."
  - "WorkerApplication mirrors API entity/repository scanning because the worker directly consumes backend/core repositories."
  - "Worker REFRESH_TOKEN_KEY_BASE64 is fail-fast with no sm:// fallback, honoring the no-GCP-hosting baseline."
patterns-established:
  - "Headless worker OAuth refresh uses direct POST to the Google token endpoint; tests redirect the endpoint to a hermetic local server."
  - "Generated Gmail client tests must account for gzip-compressed JSON request bodies."
requirements-completed: [MAIL-01, MAIL-02, MAIL-05]
duration: 12min
completed: 2026-04-29
---

# Phase 02A Plan 02: Gmail Worker Schedulers Summary

**Gmail watch renewal and history fan-out workers with DB-only invalid-grant disconnects and privacy-floor observed-message writes**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-29T05:45:29Z
- **Completed:** 2026-04-29T05:57:09Z
- **Tasks:** 2 completed
- **Files modified:** 15

## Accomplishments

- Added `GmailApiClientFactory` for generated Gmail clients and direct OAuth refresh, including `invalid_grant` mapping.
- Extended `GmailConnectionService` with history/watch health transitions, reconnect cleanup, and DB-only `markDisconnected`.
- Added `GmailDeliveryProcessingService` as the public transactional delivery processor, with metadata-only message fetches and idempotent observed-message inserts.
- Added `GmailWatchScheduler` and `GmailHistoryProcessor`, binding `TenantContext` per row and delegating per-delivery transactions correctly.
- Turned the worker Wave 0 scheduler tests green against Postgres and a hermetic Gmail/OAuth mock.

## Task Commits

1. **Task 1: GmailApiClientFactory + connection state + delivery processing** - `7ae437d` (feat)
2. **Task 2: GmailWatchScheduler + GmailHistoryProcessor + worker config** - `81015ba` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` - Builds Gmail clients and refreshes access tokens without request-scoped OAuth state.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` - Processes claimed Pub/Sub deliveries in a public service transaction.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` - Adds watch/history state methods and durable DB-only disconnect.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java` - Adds watch-renewal and monotonic history-pointer update queries.
- `backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java` - Clears watch/history state only after explicit reconnect/re-consent succeeds.
- `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` - Registers and renews `users.watch` every minute.
- `backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java` - Claims delivery rows every second and delegates to the core processing service.
- `backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java` - Enables core entity and repository scanning for worker execution.
- `backend/worker/src/main/resources/application.yml` - Adds fail-fast Pub/Sub topic config and removes the worker secret-manager fallback.
- `backend/worker/src/test/java/com/zeromail/worker/*.java` - Makes worker scheduler tests green with Postgres and local Gmail/OAuth mocks.

## Decisions Made

- DB disconnect and Gmail watch cleanup are separate operations: invalid-grant handlers call `markDisconnected`, while user disconnect still attempts `users.stop()` best-effort after the DB update commits.
- Watch success only initializes `lastSyncedHistoryId` when the cursor is null and only clears `WATCH_UNHEALTHY`; `HISTORY_LOST` survives until explicit reconnect.
- The Gmail client factory exposes test-only root/token endpoint properties that default to Google endpoints in production.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated Wave 0 history processor test entrypoint**
- **Found during:** Task 2 verification
- **Issue:** `GmailHistoryProcessorTest` still called the RED scaffold method `processPendingBatch()`, while the accepted implementation surface is scheduled `tick()`.
- **Fix:** Updated the tests to call `processor.tick()`.
- **Files modified:** `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java`
- **Verification:** Worker tests compiled after the rename.
- **Committed in:** `81015ba`

**2. [Rule 2 - Missing Critical] Added worker JPA/Postgres runtime wiring**
- **Found during:** Task 2 worker test boot
- **Issue:** `WorkerApplication` scanned Spring components but did not enable core entity/repository scanning, and the worker module did not carry its own PostgreSQL runtime driver.
- **Fix:** Added `@EntityScan` and `@EnableJpaRepositories` for `com.zeromail.core`, plus worker `runtimeOnly("org.postgresql:postgresql")`.
- **Files modified:** `backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java`, `backend/worker/build.gradle.kts`
- **Verification:** Worker Spring context loaded and targeted scheduler tests passed.
- **Committed in:** `81015ba`

**3. [Rule 3 - Blocking] Added hermetic worker test infrastructure**
- **Found during:** Task 2 verification
- **Issue:** Worker tests had no datasource properties and would have called real Google OAuth/Gmail endpoints through the production factory.
- **Fix:** Added worker `PostgresContainerTest`, redirected Gmail/OAuth endpoints to `MockGmailHistoryServer`, seeded encrypted refresh tokens, handled generated-client gzip request bodies, and converted raw JDBC `Instant` values to `Timestamp`.
- **Files modified:** `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java`, `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java`, `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java`, `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java`
- **Verification:** `.\gradlew.bat :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` passed.
- **Committed in:** `81015ba`

**4. [Rule 2 - Missing Critical] Enforced worker secret fail-fast config**
- **Found during:** AGENTS.md enforcement for edited `backend/worker/src/main/resources/application.yml`
- **Issue:** The worker refresh-token key still had an `sm://` fallback, contradicting the project baseline that does not use GCP Secret Manager by default.
- **Fix:** Replaced it with the same `REFRESH_TOKEN_KEY_BASE64:?` fail-fast pattern used by the API module.
- **Files modified:** `backend/worker/src/main/resources/application.yml`
- **Verification:** Combined backend compile and targeted worker tests passed with dynamic test properties.
- **Committed in:** `81015ba`

---

**Total deviations:** 4 auto-fixed (2 blocking, 2 missing critical)
**Impact on plan:** All fixes were required for correctness, production worker startup, or hermetic verification. No unrelated product scope was added.

## Issues Encountered

- The generated Gmail Java client gzip-compresses JSON request bodies in tests. The mock server now decompresses request bodies before assertions.

## Verification

- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava` - PASS.
- `.\gradlew.bat :backend:worker:compileJava` - PASS.
- `.\gradlew.bat :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` - PASS.
- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` - PASS.
- Privacy log grep for token/email/content terms inside log statements - PASS, no matches.

## Known Stubs

None. Stub scan found no TODO/FIXME/placeholder code; matches were null checks, query text blocks, log placeholders, and test helper state resets.

## User Setup Required

Worker runtime now requires `GOOGLE_PUBSUB_TOPIC_NAME` and `REFRESH_TOKEN_KEY_BASE64` to be provided by the deployment secret/config source.

## Auth Gates

None.

## Next Phase Readiness

Ready for 02A-03. The data layer and worker layer now cover Gmail watch registration/renewal, Pub/Sub delivery claims, history fan-out, history-loss handling, and invalid-grant disconnect semantics.

## Self-Check: PASSED

- Summary and key created files exist on disk.
- Task commits found in git history: `7ae437d`, `81015ba`.
- Stub-pattern scan found no blocking stubs; matches were query text blocks, null checks, log placeholders, and test helper reset state.

---
*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*

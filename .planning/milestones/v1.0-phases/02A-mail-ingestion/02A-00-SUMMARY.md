---
phase: 02A-mail-ingestion
plan: "00"
subsystem: testing
tags: [gmail, pubsub, oidc, vitest, junit, red-scaffold]
requires:
  - phase: 01.2.1
    provides: PostgresContainerTest pattern, IdentifiedEnum convention, domain-owned backend test layout
  - phase: 01.3
    provides: Feature-folder frontend test conventions and Vitest architecture guards
provides:
  - Wave 0 RED scaffold coverage for Gmail Pub/Sub OIDC, controller, idempotency, persistence, worker, pause, and reconnect contracts
  - Hermetic OIDC JWKS fixture and Gmail history/watch fixture for later implementation waves
  - Feature-directory Vitest discovery for future feature-owned tests
affects: [02A-mail-ingestion, frontend-tests, backend-tests, gmail-ingestion]
tech-stack:
  added: []
  patterns:
    - Hermetic test HTTP servers using JDK HttpServer for OIDC JWKS and Gmail history/watch responses
    - Feature-owned Vitest files are included by the web Vitest config
key-files:
  created:
    - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java
    - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
    - backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java
    - backend/api/src/test/java/com/zeromail/api/support/MockGoogleOidcServer.java
    - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
    - apps/web/__tests__/architecture/phase-02a-files.test.ts
    - apps/web/features/triage/components/PauseBanner.test.tsx
    - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
    - apps/web/features/gmail/components/ReconnectPrompt.test.tsx
  modified:
    - apps/web/vitest.config.ts
key-decisions:
  - "Worker RED tests use a package-local SpringBootTest scaffold because backend/core test sources are not on the worker test classpath."
  - "Vitest now includes features/**/*.{test,spec}.{ts,tsx} so feature-owned Wave 0 tests are discoverable."
patterns-established:
  - "Backend Wave 0 active tests intentionally fail on future production symbols while disabled Java scaffolds avoid missing DTO/controller references."
  - "Frontend Wave 0 feature tests live beside future feature files and are collected by Vitest."
requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06]
duration: 14min
completed: 2026-04-29
---

# Phase 02A Plan 00: Mail Ingestion Wave 0 Summary

**Nyquist Wave 0 RED test spine for Gmail ingestion, Pub/Sub OIDC, idempotent delivery, worker processing, pause controls, and reconnect health gates**

## Performance

- **Duration:** 14 min
- **Started:** 2026-04-29T05:08:54Z
- **Completed:** 2026-04-29T05:23:09Z
- **Tasks:** 2 completed
- **Files modified:** 17

## Accomplishments

- Created all 16 requested Wave 0 scaffold files: 10 backend tests, 2 backend hermetic fixtures, and 4 frontend test files.
- Backend compile verification is RED only on intended future production symbols: `PubSubOidcAuthFilter`, `GmailPubSubController`, ingestion entities/repositories/enum, and worker schedulers.
- Frontend Vitest verification shows the intended RED/SKIP shape: missing triage production files, missing i18n keys, PauseBanner/hook import failures, and skipped ReconnectPrompt ingestion-health gates.

## Task Commits

1. **Task 1: Backend Wave 0 RED scaffolds** - `0c9a1bd` (test)
2. **Task 2: Frontend Wave 0 RED scaffolds** - `39cb3d0` (test)

## Files Created/Modified

- `backend/api/src/test/java/com/zeromail/api/support/MockGoogleOidcServer.java` - Hermetic JWKS fixture that signs synthetic Google OIDC JWTs.
- `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` - Hermetic Gmail history/watch/message metadata fixture.
- `backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java` - OIDC acceptance contract, including wrong aud/email/issuer/expiry/signature and non-Pub/Sub path guard.
- `backend/api/src/test/java/com/zeromail/api/controllers/*.java` - Pub/Sub controller, `/me`, pause, and idempotency RED contracts.
- `backend/core/src/test/java/com/zeromail/core/gmail/**` - Persistence and enum RED contracts for delivery, observed mail, and ingestion health.
- `backend/worker/src/test/java/com/zeromail/worker/*.java` - Worker RED contracts for history processing and watch renewal.
- `apps/web/__tests__/architecture/phase-02a-files.test.ts` - File-presence and i18n parity guard for Phase 02A frontend production files.
- `apps/web/features/triage/components/PauseBanner.test.tsx` - Pause banner RED import and behavior contract.
- `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` - Pause mutation RED import and `accountKeys.me()` invalidation contract.
- `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` - Skipped ingestion-health gate scaffold.
- `apps/web/vitest.config.ts` - Adds feature-directory tests to Vitest discovery.

## Decisions Made

- Worker tests no longer import `backend/core` test sources directly because Gradle does not expose test source sets across modules. A package-local `PostgresContainerTest` scaffold in the worker test source keeps the RED failures focused on future worker classes.
- Feature-owned tests are now part of the web Vitest include set. Without this, `features/**.test.tsx` files were invisible to the verification command.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced worker import of core test-only base**
- **Found during:** Task 1
- **Issue:** `backend/worker:compileTestJava` failed with `package com.zeromail.core.support does not exist` because `backend/core` test sources are not visible to worker test compilation.
- **Fix:** Removed the cross-module test-source import and added a package-local `@SpringBootTest(classes = WorkerApplication.class)` scaffold named `PostgresContainerTest` inside the worker RED test source.
- **Files modified:** `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java`, `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java`
- **Verification:** `./gradlew.bat :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava --continue` now reports worker failure only for missing `GmailHistoryProcessor` and `GmailWatchScheduler`.
- **Committed in:** `0c9a1bd`

**2. [Rule 2 - Missing Critical] Added feature test discovery to Vitest**
- **Found during:** Task 2
- **Issue:** The repo's Vitest config only included `__tests__/**` and `test/**`, so feature-owned RED tests were not collected.
- **Fix:** Added `features/**/*.{test,spec}.{ts,tsx}` to `apps/web/vitest.config.ts`.
- **Files modified:** `apps/web/vitest.config.ts`
- **Verification:** Targeted Vitest command collected all 4 frontend scaffold files and reported `3 failed | 1 skipped (4)`.
- **Committed in:** `39cb3d0`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both changes keep verification aligned with the plan's RED scaffold intent. No production behavior was added.

## Issues Encountered

- The plan's frontend verification command used a non-existent `test:run` script/package alias for this repo. Verification used the actual package and command: `pnpm -F zeromail-web exec vitest run ... --reporter=verbose`.
- A first Prettier invocation used repo-root paths through `pnpm -F zeromail-web exec`, but that command runs with `apps/web` as cwd. Re-running with package-relative paths succeeded.

## Known Stubs

Intentional Wave 0 scaffolds:

- `backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java` - `@Disabled` until Plan 03 extends `/me`.
- `backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java` - `@Disabled` until Plan 03 adds the pause endpoint.
- `backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java` - `@Disabled` until Plan 03 adds the Pub/Sub endpoint and persistence path.
- `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` - `it.skip` ingestion-health gate scaffold until Plan 04 updates the settings mount condition.

These stubs are the requested RED acceptance contract for later waves and do not block this plan's goal.

## Verification

- `./gradlew.bat :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava --continue` - RED as expected. Failures are missing future production symbols only after the worker test-base fix.
- `pnpm -F zeromail-web exec vitest run __tests__/architecture/phase-02a-files.test.ts features/triage/components/PauseBanner.test.tsx features/triage/hooks/useToggleTriagePause.test.tsx features/gmail/components/ReconnectPrompt.test.tsx --reporter=verbose` - RED/SKIP as expected: `3 failed | 1 skipped (4)`, `5 failed | 3 skipped (8)`.
- File check - PASS: all 16 Wave 0 scaffold files exist.

## User Setup Required

None - no external service configuration required for Wave 0 scaffolds.

## Next Phase Readiness

Ready for Phase 02A Plan 01. The RED contracts now define the production classes, endpoints, schema, and frontend files that later waves must satisfy.

## Self-Check: PASSED

- All 16 scaffold files and the summary file exist on disk.
- Task commits found in git history: `0c9a1bd`, `39cb3d0`.
- Stub-pattern scan found no untracked placeholder/TODO/FIXME markers outside the intentional RED scaffolds documented above.

---
*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*

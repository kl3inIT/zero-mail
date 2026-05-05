---
phase: 02A-mail-ingestion
plan: "05"
subsystem: testing
tags: [gmail, pubsub, oidc, spring-security, spring-modulith, archunit, vitest, pnpm]

requires:
  - phase: 02A-00..02A-04
    provides: Wave 0 tests, ingestion schema, worker/API implementation, frontend pause/reconnect UI
provides:
  - Phase 02A Nyquist validation with backend and frontend automated gates green
  - Pub/Sub OIDC ceremony blocker retired from STATE.md
  - ROADMAP.md Phase 2A completion state
affects: [Phase 2A, Phase 2B, Phase 2C, Phase 3, Phase 4, mail-ingestion]

tech-stack:
  added: []
  patterns:
    - Test-only public security routes must remain explicit when test auth chains are active
    - Refreshed OAuth access tokens are wrapped in Sensitive<T> before crossing service boundaries
    - Frontend verification package selector is pnpm -F web

key-files:
  created:
    - .planning/phases/02A-mail-ingestion/02A-05-SUMMARY.md
  modified:
    - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
    - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
    - apps/web/package.json
    - package.json

key-decisions:
  - "Retired Phase 01.5 D-D5 Pub/Sub OIDC ceremony blocker after PubSubOidcAuthFilter 7-case verification passed."
  - "Normalized the private frontend package selector to pnpm -F web so plan-level verification commands execute real checks."
  - "Wrapped refreshed Gmail access tokens in Sensitive<String> to preserve the FND-03/FND-04 deny-list contract."

patterns-established:
  - "TestSessionSupport permits only /v3/api-docs/** and /test/** without test auth; user endpoints still require the header-backed session."
  - "Core Postgres integration tests provide dummy Google OAuth registration properties because GmailApiClientFactory now lives in backend/core."

requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06]

duration: 20min
completed: 2026-04-29
---

# Phase 02A Plan 05: Full Verification Sweep Summary

**Phase 2A mail ingestion is Nyquist-verified with backend, worker, API, frontend, Modulith, ArchUnit, and i18n gates green.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-04-29T06:49:31Z
- **Completed:** 2026-04-29T07:09:40Z
- **Tasks:** 2 completed
- **Files modified:** 11

## Accomplishments

- Ran the full backend suite successfully: `./gradlew clean check` ended with `BUILD SUCCESSFUL`.
- Ran the frontend suite successfully: `pnpm -F web run test:run` reported 27 files / 150 tests passed; typecheck, lint, and i18n checks exited 0.
- Updated `02A-VALIDATION.md` with `nyquist_compliant: true`, `wave_0_complete: true`, GREEN per-task verification rows, all Wave 0 checkboxes checked, and manual staging replay instructions.
- Removed the `Pub/Sub OIDC verification ceremony` blocker from `STATE.md` and marked Phase 2A complete in `ROADMAP.md`.

## Task Commits

1. **Task 1: Full verification sweep** - `ec627b1` (`fix`)
2. **Task 2: Close STATE.md blocker + roadmap count** - `c0146d5` (`docs`)

## Files Created/Modified

- `.planning/phases/02A-mail-ingestion/02A-VALIDATION.md` - final validation flags, task verification map, Wave 0 checklist, manual replay notes.
- `.planning/STATE.md` - Phase 2A current position, closure decision, Pub/Sub OIDC blocker removal.
- `.planning/ROADMAP.md` - Phase 2A checked complete, 6/6 plan list and progress row.
- `backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java` - permits `/v3/api-docs/**` and `/test/**` while preserving auth on user endpoints.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` - wraps refreshed access tokens in `Sensitive<String>`.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` - unwraps `Sensitive<String>` only at Gmail client construction.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` - unwraps `Sensitive<String>` only at Gmail client construction.
- `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java` - supplies dummy Google OAuth client properties for core test context boot.
- `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` - unwraps `Sensitive<String>` only at Gmail client construction.
- `apps/web/package.json` - package renamed to `web`; added `test:run` and `typecheck` scripts.
- `package.json` - root scripts and lint-staged filters updated to `--filter web`.

## Decisions Made

- Pub/Sub push-token validation is closed locally by the 7-case `PubSubOidcAuthFilterTest`, plus controller integration tests covering the actual push receiver path.
- The frontend workspace package is now named `web` so the phase plans' `pnpm -F web ...` verification commands execute real checks instead of selecting no package.
- Refreshed Gmail access tokens must remain `Sensitive<String>` until the final Gmail client construction call site.

## Verification

- `./gradlew clean check` - PASS, `BUILD SUCCESSFUL in 1m 59s`.
- `./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" --tests "*MeControllerTest*" --tests "*TriagePauseControllerTest*" --tests "*PubSubIdempotencyTest*"` - PASS.
- `./gradlew :backend:core:test --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*" --tests "*GmailIngestionHealthTest*"` - PASS.
- `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` - PASS.
- `./gradlew :backend:api:test --tests "*ApplicationModulesTest*" :backend:core:test --tests "*DomainBoundaryArchTests*"` - PASS.
- `pnpm -F web run test:run` - PASS, 27 files / 150 tests.
- `pnpm -F web run typecheck` - PASS.
- `pnpm -F web run lint` - PASS.
- `pnpm -F web run i18n:check` - PASS, 318 leaf keys.
- `grep`/`rg` disabled checks - PASS: no `@Disabled` in the three API Wave 0 scaffolds and no `it.skip` in `ReconnectPrompt.test.tsx`.
- `grep -c -- "- \\*\\*Pub/Sub OIDC verification ceremony\\*\\*" .planning/STATE.md` equivalent - PASS, count 0.
- `grep "nyquist_compliant" .planning/phases/02A-mail-ingestion/02A-VALIDATION.md` - PASS, `true`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored public test fixture and OpenAPI routes under TestSessionSupport**
- **Found during:** Task 1
- **Issue:** The test-only user security chain required authentication for every non-Pub/Sub request, causing older safety and OpenAPI tests to receive 401 before reaching their controller/error paths.
- **Fix:** Added explicit `permitAll()` matchers for `/v3/api-docs/**` and `/test/**`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java`
- **Verification:** Targeted API failure classes passed; final `./gradlew clean check` passed.
- **Committed in:** `ec627b1`

**2. [Rule 2 - Missing Critical] Wrapped refreshed Gmail access tokens**
- **Found during:** Task 1
- **Issue:** `SafetyContractArchTests` failed because `TokenRefreshResult.accessToken` exposed a deny-listed field name as raw `String`.
- **Fix:** Changed the record field to `Sensitive<String>` and unwrapped only at Gmail client construction call sites.
- **Files modified:** `GmailApiClientFactory.java`, `GmailConnectionService.java`, `GmailDeliveryProcessingService.java`, `GmailWatchScheduler.java`
- **Verification:** `SafetyContractArchTests` passed; final `./gradlew clean check` passed.
- **Committed in:** `ec627b1`

**3. [Rule 3 - Blocking] Added core test OAuth properties**
- **Found during:** Task 1
- **Issue:** Core Postgres integration tests could not boot after `GmailApiClientFactory` entered the core scan because Google OAuth client id/secret placeholders were missing.
- **Fix:** Added dummy Google OAuth client properties to `PostgresContainerTest`.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java`
- **Verification:** Targeted core persistence context test passed; final `./gradlew clean check` passed.
- **Committed in:** `ec627b1`

**4. [Rule 3 - Blocking] Made the planned frontend verification selector real**
- **Found during:** Task 1
- **Issue:** `pnpm -F web run test:run` selected no package, and `test:run` / `typecheck` scripts were missing.
- **Fix:** Renamed the private workspace package to `web`, added `test:run` and `typecheck`, and updated root package filters.
- **Files modified:** `apps/web/package.json`, `package.json`
- **Verification:** `pnpm -F web run test:run`, `typecheck`, `lint`, and `i18n:check` all passed.
- **Committed in:** `ec627b1`

---

**Total deviations:** 4 auto-fixed (1 missing critical security contract, 3 blocking issues)
**Impact on plan:** All fixes were required to make the planned verification commands meaningful and green. No new product behavior or architecture was introduced.

## Issues Encountered

- One optional targeted Vitest rerun used an invalid `--reporter=basic` argument under Vitest 4. It was rerun without that reporter and passed; no code changes were needed.

## Known Stubs

None. Stub scan found only existing `STATE.md` pending-todo text and normal Java null checks/log placeholders.

## Threat Flags

None. This plan introduced no new endpoints, schema, auth paths, file access patterns, or trust boundaries. The security-relevant change tightened token handling by wrapping refreshed access tokens in `Sensitive<String>`.

## Authentication Gates

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 2A is complete for local automated verification. Four manual staging checks remain documented in `02A-VALIDATION.md`: real Pub/Sub delivery, `users.watch` renewal rehearsal, history-404 reconnect UX, and pause-toggle visual confirmation.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/02A-mail-ingestion/02A-05-SUMMARY.md`
- Task commits found: `ec627b1`, `c0146d5`
- Validation flags confirmed: `nyquist_compliant: true`, `wave_0_complete: true`
- STATE blocker removal confirmed: Pub/Sub OIDC ceremony blocker count = 0

---
*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*

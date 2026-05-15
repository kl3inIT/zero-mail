---
phase: 06-polish-casa-verified-launch
plan: 03
subsystem: testing
tags: [playwright, e2e, spring-profile, gmail-stub, mime, launch]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: e2e-stub launch profile, delivery processing fixes, and launch readiness hardening from Plans 06-01 and 06-02
provides:
  - dedicated golden-path Playwright config that leaves the base Gates config untouched
  - real-backend launch spec that authenticates through seed-session and asserts saved draft text
  - MIME-decoded e2e-stub draft peek regression coverage
affects: [06-04-release-ci, 06-05-launch-artifacts, launch-go-no-go, casa-verified-launch]

tech-stack:
  added: []
  patterns:
    - dedicated launch-only Playwright config layered over the base config
    - header-augmented SSR/backend fetch context for real backend authentication
    - MIME-decoded e2e-stub draft peek for quoted-printable text/plain bodies
    - tenant-scoped transaction replay for launch-only mail observation events

key-files:
  created:
    - apps/web/e2e/launch-golden-path.spec.ts
    - backend/api/src/test/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactoryTest.java
  modified:
    - apps/web/app/(protected)/(app)/layout.tsx
    - apps/web/package.json
    - apps/web/playwright.golden.config.ts
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactory.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
    - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java

key-decisions:
  - "Keep the golden-path runner in a dedicated Playwright config instead of mutating the base Gates config."
  - "Authenticate the spec with real backend seed-session data and X-Test headers instead of seedAuthenticatedSession cookie writes."
  - "Decode the stub draft body to plain text before exposing it through the e2e-stub drafts peek endpoint so the assertion matches the ChatModel output."
  - "Propagate the incoming X-Test headers through the protected app layout so server-side fetches see the same launch identity as the browser context."

patterns-established:
  - "Launch-only Playwright flows should be isolated to a dedicated config and script."
  - "Stub draft assertions should read decoded text content, not raw MIME."
  - "Launch smoke SSR requests may need explicit auth header forwarding in app layout fetch helpers."

requirements-completed: [SPEC-06-R1]

duration: 2h 47m
completed: 2026-05-15
---

# Phase 06 Plan 03: Golden-Path Launch Summary

**Golden-path launch now runs against the real backend via seed-session headers, a dedicated Playwright config, and a MIME-decoded stub draft assertion.**

## Performance

- **Duration:** 2h 47m
- **Started:** 2026-05-15T03:36:12+07:00
- **Completed:** 2026-05-15T06:22:55+07:00
- **Tasks:** 2 completed
- **Files modified:** 11

## Accomplishments

- Added a dedicated `playwright.golden.config.ts` and `test:e2e:golden` script so the launch smoke boots Spring Boot only for the golden path.
- Built a 9-step golden-path Playwright spec that uses `seed-session`, forwards `X-Test-Subject` / `X-Test-Email` to both request and browser contexts, and drives the real backend journey.
- Fixed the e2e-stub draft peek so it exposes decoded text/plain body text instead of raw quoted-printable MIME.
- Verified the launch path end to end: audit apply, undo, draft save, and analytics load all pass on the real backend under `e2e-stub`.

## Task Commits

1. **Task 1: Create dedicated golden-path Playwright config** - `404d68c` (feat)
2. **Task 2: Finish golden-path launch and stub hardening** - `6ae6b21` (feat)

## Files Created/Modified

- `apps/web/playwright.golden.config.ts` - dedicated launch-only Playwright config.
- `apps/web/package.json` - adds `test:e2e:golden`.
- `apps/web/e2e/launch-golden-path.spec.ts` - full golden-path launch spec.
- `apps/web/app/(protected)/(app)/layout.tsx` - forwards launch auth headers into backend fetches.
- `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactory.java` - decodes stub draft MIME into text.
- `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java` - seeds watch state and exposes delivery processing for the launch path.
- `backend/api/src/test/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactoryTest.java` - regression test for decoded draft bodies.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` - inserts observed rows in its own transaction before publishing launch events.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java` - runs tenant-scoped orchestration inside a new transaction and skips zero-cost deterministic reservations.
- `backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java` - updated transaction-manager test wiring.
- `backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java` - updated transaction-manager test wiring.

## Decisions Made

- Kept the base `apps/web/playwright.config.ts` untouched so the daily Gates e2e job stays isolated from the launch-only backend boot.
- Used the real `seed-session` response plus explicit headers instead of the browser-cookie-only helper.
- Normalized the stub draft peek to decoded text so the assertion matches the canned `E2eStubChatModel` text.
- Forwarded launch headers through the protected app layout so SSR fetches see the same identity as the browser context.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Stub draft peek returned raw quoted-printable MIME**
- **Found during:** Task 2 (golden-path spec verification)
- **Issue:** The draft peek exposed `=E2=80=94` instead of the canonical text body, so the saved-draft assertion could not match the stub ChatModel output.
- **Fix:** Parsed the raw Gmail MIME and extracted the plain-text body before exposing it from the e2e-stub drafts endpoint.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactory.java`, `backend/api/src/test/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactoryTest.java`
- **Verification:** `./gradlew.bat --no-daemon :backend:api:test --tests "*E2eStubGmailApiClientFactoryTest"` passed and the golden Playwright spec passed.
- **Committed in:** `6ae6b21`

**2. [Rule 2 - Missing Critical] Server-side fetches were not carrying the launch auth headers**
- **Found during:** Task 2 (golden-path auth flow)
- **Issue:** The browser context had the headers, but the protected app layout also needed them for server-side backend fetches.
- **Fix:** Added a header bridge in the protected app layout so SSR fetches forward `X-Test-Subject` / `X-Test-Email`.
- **Files modified:** `apps/web/app/(protected)/(app)/layout.tsx`
- **Verification:** The golden Playwright spec passed against the real backend.
- **Committed in:** `6ae6b21`

**3. [Rule 2 - Missing Critical] Launch path needed deterministic backend replay to reach audit/undo/draft states**
- **Found during:** Task 2 (full golden-path execution)
- **Issue:** The real backend path needed the delivery-processing and triage transaction fixes plus the e2e-stub reset/watch seeding to keep the launch flow deterministic.
- **Fix:** Hardened the launch-only backend path around delivery processing, tenant-scoped orchestration, and e2e-stub seed/reset behavior.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java`, `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java`, `backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java`, `backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java`
- **Verification:** Focused backend tests passed and the golden Playwright spec passed.
- **Committed in:** `6ae6b21`

---

**Total deviations:** 3 auto-fixed (all missing-critical/correctness).
**Impact on plan:** Necessary to make the launch smoke actually reflect the real backend path the plan promised.

## Issues Encountered

- The first golden-path run exposed raw quoted-printable draft output instead of decoded body text.
- JetBrains `get_file_problems` timed out repeatedly, so Gradle, Playwright, and `git diff --check` were the verification source.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 06-04. The launch smoke is isolated in a dedicated config, the spec is green, and the draft assertion now checks the decoded saved body instead of MIME encoding artifacts.

## Self-Check: PASSED

- Summary exists at `.planning/phases/06-polish-casa-verified-launch/06-03-SUMMARY.md`.
- Key created and modified files exist on disk.
- Task commits resolve in git: `404d68c`, `6ae6b21`.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*

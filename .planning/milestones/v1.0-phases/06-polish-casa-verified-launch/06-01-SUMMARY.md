---
phase: 06-polish-casa-verified-launch
plan: 01
subsystem: backend launch test infrastructure
tags: [spring-profile, pubsub, gmail-stub, archunit, launch-readiness]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: locked launch-readiness context, D-03 loadtest profile, D-06/D-07 e2e-stub profile
provides:
  - Pub/Sub OIDC verifier bean seam for production and launch-only profile fakes
  - e2e-stub Gmail, Pub/Sub, auth, session, and chat-model infrastructure for the golden-path spec
  - loadtest Pub/Sub verifier profile and bootable loadtest application profile
  - ArchUnit and YAML guards preventing launch-only profiles from leaking into production
affects: [06-02-loadtest, 06-03-playwright-golden-path, backend-api-security, backend-core-llm]

tech-stack:
  added: []
  patterns:
    - profile plus ConditionalOnProperty plus ArchUnit guard for launch-only main-scope beans
    - GmailApiClientFactory subclass replacement for deterministic offline Gmail API behavior
    - pure-Java PubSubTokenVerifier adapter around Google Auth TokenVerifier for fakes

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubAuthConfig.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubChatModel.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubGmailApiClientFactory.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubPubsubVerifierConfig.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java
    - backend/api/src/main/java/com/zeromail/api/e2estub/SeedMessageRequest.java
    - backend/api/src/main/java/com/zeromail/api/loadtest/LoadtestPubsubVerifierConfig.java
    - backend/api/src/main/java/com/zeromail/api/security/PubSubTokenVerifier.java
    - backend/api/src/main/resources/application-e2e-stub.yml
    - backend/api/src/main/resources/application-loadtest.yml
    - backend/api/src/test/java/com/zeromail/api/arch/LaunchProfileArchUnitTest.java
  modified:
    - backend/api/build.gradle.kts
    - backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java
    - backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java

key-decisions:
  - "Use option A: replace GmailApiClientFactory with an e2e-stub @Primary subclass instead of refactoring eight production consumers to a new GmailClient interface."
  - "Keep Google Auth TokenVerifier as the production bean, then adapt it through PubSubTokenVerifier so profile fakes do not need fragile Google Auth internals."
  - "Make e2e-stub provide both Spring AI ChatModel and Zero Mail LlmModelClient so the production draft path saves the canned reply in stub Gmail."

patterns-established:
  - "Launch-only profiles must be guarded by @Profile, @ConditionalOnProperty, ArchUnit package isolation, and YAML profile-leak scans."
  - "Profile ymls used for boot verification carry literal dummy secrets and no :? fail-fast references."

requirements-completed: [SPEC-06-R1, SPEC-06-R2]

duration: 1h 51m
completed: 2026-05-15
---

# Phase 06 Plan 01: Test-Only Spring Profile Scaffolding Summary

**Profile-gated Gmail, Pub/Sub, auth, and LLM fakes for deterministic launch E2E and loadtest execution.**

## Performance

- **Duration:** 1h 51m
- **Started:** 2026-05-15T00:59:47+07:00
- **Completed:** 2026-05-15T02:50:26+07:00
- **Tasks:** 4 completed
- **Files modified:** 17

## Accomplishments

- Extracted production Pub/Sub OIDC verification into a swappable bean while preserving the `com.google.auth.oauth2.TokenVerifier` production path.
- Added six `e2e-stub` Java files plus self-contained profile configuration for deterministic Gmail, Pub/Sub, header auth, seed endpoints, and canned draft generation.
- Added the `loadtest` Pub/Sub verifier profile and loadtest yml that boots without host-side launch secrets.
- Added `LaunchProfileArchUnitTest` package isolation and YAML-scan guards so `e2e-stub` and `loadtest` cannot leak into production configuration.
- Verified the full e2e-stub smoke: seed session, seed message, synthetic Pub/Sub push, draft generation, and saved stub Gmail draft containing the canned reply.

## Task Commits

1. **Task 1: Extract Pub/Sub OIDC token verifier bean** - `e1f1b2d` (feat)
2. **Task 2: Add e2e-stub launch profile** - `02c665c` (feat)
3. **Task 3: Add loadtest Pub/Sub verifier profile** - `a79c2ea` (feat)
4. **Task 4: Guard launch-only Spring profiles** - `5daef36` (test)
5. **Corrective: make loadtest profile bootable without host secrets** - `2cc9e63` (fix)
6. **Corrective: make e2e stub draft smoke save canned reply** - `e7c1322` (fix)
7. **Corrective: allow launch profile readiness probes** - `21a7374` (fix)

Plan metadata is recorded in the final docs commit for this plan.

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java` - owns the production `TokenVerifier` bean and wires the Pub/Sub filter with a swappable verifier.
- `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java` - receives verifier behavior by constructor, preserves the three `event=pubsub_oidc_*` log lines, and no longer builds the verifier internally.
- `backend/api/src/main/java/com/zeromail/api/security/PubSubTokenVerifier.java` - local adapter seam used by profile fakes and production verifier wrapping.
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` - permits `/actuator/health/**` so launch profile readiness probes can pass.
- `backend/api/src/main/java/com/zeromail/api/e2estub/*` - deterministic Gmail API factory, Pub/Sub verifier fake, header auth chain, ChatModel/LlmModelClient fake, reset/seed/draft endpoints, and `SeedMessageRequest`.
- `backend/api/src/main/java/com/zeromail/api/loadtest/LoadtestPubsubVerifierConfig.java` - `loadtest` profile verifier fake guarded by profile and property.
- `backend/api/src/main/resources/application-e2e-stub.yml` - self-contained e2e-stub profile configuration with exact 32-byte AES placeholder `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=`.
- `backend/api/src/main/resources/application-loadtest.yml` - self-contained loadtest profile configuration with `spring.ai.model.chat: none`.
- `backend/api/src/test/java/com/zeromail/api/arch/LaunchProfileArchUnitTest.java` - package isolation and YAML profile-leak tests.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java` - excluded from `e2e-stub` so the stub `LlmModelClient` owns draft generation.

## Decisions Made

- **Gmail interception option:** picked option A, a subclass of `GmailApiClientFactory`, because all inspected production consumers inject the concrete factory. Refactoring all eight consumers to a new interface would be broader and riskier than a profile-scoped `@Primary` subclass.
- **Overridden Gmail chain:** the stub covers `users().messages().get`, `users().messages().modify`, `users().messages().list`, `users().drafts().create`, `users().drafts().get`, `users().drafts().delete`, `users().labels().list`, `users().history().list`, and `users().watch`.
- **Pub/Sub verifier shape:** production still exposes a `TokenVerifier` bean from `PubSubSecurityConfig`; `PubSubOidcAuthFilter` wraps it with `PubSubTokenVerifier` so fakes can return verified email addresses without relying on Google Auth subclass internals.
- **Auth chain order:** e2e-stub adds `SecurityFilterChain @Order(2)` ahead of the normal `SecurityConfig` chain at `@Order(3)`. It permits `/api/test/e2e-stub/**` and `/actuator/health/**`, excludes `/internal/pubsub/**`, and authenticates API calls via `X-Test-Subject` plus `X-Test-Email`.

## Verification Results

- `.\gradlew.bat --no-daemon :backend:core:check :backend:api:check` - PASS, `BUILD SUCCESSFUL in 1m 32s`.
- `.\gradlew.bat --no-daemon :backend:api:test --tests "com.zeromail.api.arch.LaunchProfileArchUnitTest"` - PASS, `BUILD SUCCESSFUL in 20s`.
- `.\gradlew.bat --no-daemon :backend:api:bootRun --args="--spring.profiles.active=e2e-stub --zeromail.e2e-stub.enabled=true"` - PASS. `/actuator/health/readiness` returned 200; reset returned 204; seed-session returned a tenant id; synthetic Pub/Sub returned 200; `POST /api/threads/{threadId}/draft` returned `GENERATED`; draft store contained `Hello tester`.
- `.\gradlew.bat --no-daemon :backend:api:bootRun --args="--spring.profiles.active=loadtest --zeromail.loadtest.enabled=true"` - PASS. `/actuator/health/readiness` returned 200 after the readiness matcher fix.
- Static guard script - PASS:
  - production profile references outside profile ymls: `0`
  - test-scope imports in `e2estub` or `loadtest`: `0`
  - `TokenVerifier.newBuilder` in `PubSubOidcAuthFilter`: `0`
  - `event=pubsub_oidc_` log lines retained: `3`
  - e2e-stub Java file count: `6`
  - loadtest Java file count: `1`
  - `SeedMessageRequest` canonical fields matched: `6`
  - Gmail subclass/interceptor matches: `19`
  - e2e-stub endpoint path matches: `4`
  - wrong TokenVerifier package matches: `0`
  - e2e-stub yml fail-fast references: `0`
  - loadtest yml has `zeromail.loadtest.enabled: true` and `chat: none`: `1` each
  - ArchUnit annotation and rule-name matches: `1` and `3`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added PubSubTokenVerifier adapter seam**
- **Found during:** Task 1
- **Issue:** The Google Auth `TokenVerifier` is correct for production but awkward for profile fakes because launch fakes only need to return the verified email address.
- **Fix:** Added `PubSubTokenVerifier` and kept the public `TokenVerifier` constructor path as a wrapper.
- **Files modified:** `PubSubOidcAuthFilter.java`, `PubSubSecurityConfig.java`, `PubSubTokenVerifier.java`, `PubSubOidcAuthFilterTest.java`
- **Verification:** `TokenVerifier.newBuilder` count in the filter is 0; Pub/Sub OIDC log events remain 3; backend checks pass.
- **Committed in:** `e1f1b2d`

**2. [Rule 3 - Blocking] Made loadtest profile boot without host launch secrets**
- **Found during:** Task 3 verification
- **Issue:** The initial `application-loadtest.yml` was too minimal for clean `bootRun` verification because inherited `:?` production secret placeholders still failed before the fake verifier could be exercised.
- **Fix:** Added literal dummy values for required crypto, Pub/Sub, OAuth, billing, and LLM settings while keeping `spring.ai.model.chat: none`.
- **Files modified:** `backend/api/src/main/resources/application-loadtest.yml`
- **Verification:** loadtest profile booted with readiness 200 and grep confirmed no profile leakage into production ymls.
- **Committed in:** `2cc9e63`

**3. [Rule 2 - Missing Critical] Seeded billing credits for e2e draft smoke**
- **Found during:** Task 2 functional smoke
- **Issue:** The real draft generation path reserves credits; seeded e2e users had no credits, so the SPEC draft smoke failed with insufficient credits.
- **Fix:** `seed-session` now credits the seeded tenant through the existing billing top-up flow if available credits are below the launch-smoke floor.
- **Files modified:** `E2eStubResetController.java`
- **Verification:** e2e-stub smoke returned draft status `GENERATED`.
- **Committed in:** `e7c1322`

**4. [Rule 3 - Blocking] Routed canned draft generation through LlmModelClient**
- **Found during:** Task 2 functional smoke
- **Issue:** A `ChatModel @Primary` alone did not affect the production draft path because draft generation injects Zero Mail's `LlmModelClient` seam.
- **Fix:** `E2eStubChatModel` now implements both `ChatModel` and `LlmModelClient`, and `SpringAiLlmModelClient` is excluded under `e2e-stub`.
- **Files modified:** `E2eStubChatModel.java`, `SpringAiLlmModelClient.java`
- **Verification:** the saved stub Gmail draft body contains `Hello tester`.
- **Committed in:** `e7c1322`

**5. [Rule 3 - Blocking] Allowed launch readiness health probes**
- **Found during:** Overall verification
- **Issue:** The normal security chain permitted `/actuator/health` but not `/actuator/health/readiness`, so the loadtest profile booted but readiness returned 401.
- **Fix:** Added `/actuator/health/**` to the permitted request matchers in `SecurityConfig`.
- **Files modified:** `SecurityConfig.java`
- **Verification:** loadtest `bootRun` readiness returned 200.
- **Committed in:** `21a7374`

---

**Total deviations:** 5 auto-fixed (3 blocking, 2 missing-critical/correctness).
**Impact on plan:** All fixes were required to satisfy the stated launch-profile boot and SPEC draft-smoke contracts. No product behavior was added outside the launch-only profiles and readiness health endpoint.

## Issues Encountered

- A first e2e-stub boot verification command passed Spring arguments incorrectly to Gradle; rerunning with `--args="--spring.profiles.active=e2e-stub --zeromail.e2e-stub.enabled=true"` passed.
- The first loadtest readiness run timed out because `/actuator/health/readiness` returned 401. This was fixed in `21a7374` and the profile then returned readiness 200.

## Known Stubs

None. The profile yml dummy secret values and in-memory Gmail/LLM fakes are intentional launch-test infrastructure, guarded by profile, property, ArchUnit, and YAML-scan checks.

## User Setup Required

None - no external service configuration required for this plan.

## Next Phase Readiness

Ready for Plan 06-02 and Plan 06-03. The loadtest plan can activate `loadtest` with a readiness probe, and the Playwright golden-path plan can seed a real tenant session, seed a Gmail message, post the Pub/Sub envelope, and verify that a canned AI draft was saved in stub Gmail.

## Self-Check: PASSED

- Summary exists at `.planning/phases/06-polish-casa-verified-launch/06-01-SUMMARY.md`.
- Key created files exist on disk.
- Task commits resolve in git: `e1f1b2d`, `02c665c`, `a79c2ea`, `5daef36`, `2cc9e63`, `e7c1322`, `21a7374`.
- No unexpected untracked files were present; the only untracked file before metadata commit was this summary.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*

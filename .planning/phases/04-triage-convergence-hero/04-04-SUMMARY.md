---
phase: 04-triage-convergence-hero
plan: 04
subsystem: backend-triage-services
tags: [java, spring-boot, gmail-api, redis, triage, safety]

# Dependency graph
requires:
  - phase: 04-02
    provides: "Triage domain, audit persistence, sender opt-in and protected-sender observation tables"
  - phase: 04-03
    provides: "Semantic intent gateway contracts consumed by later orchestrator work"
provides:
  - "TriageSafetyPolicy allow-list gate for LABEL, ARCHIVE, and SAVE_DRAFT proposals"
  - "GmailApiClientFactory tenant facade that decrypts refresh tokens inside core.gmail"
  - "TriageGmailWriter as the single send-free triage Gmail write adapter with inverse undo calls"
  - "SenderSafetyNetService with Gmail sent-history heuristic, hashed Redis cache, opt-in override, and protected-sender observations"
affects: [04-05-triage-orchestrator, 04-06-triage-api-undo-sender-net, 04-08-verification-closure]

# Tech tracking
tech-stack:
  added: [spring-boot-starter-data-redis]
  patterns:
    - "core.gmail facade owns refresh-token decrypt/refresh/client-build flow for triage consumers"
    - "core.triage Gmail writes are funneled through one send-free writer class"
    - "Sender safety net stores only boolean cache values under hashed per-tenant Redis keys"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/triage/service/TriageSafetyPolicy.java
    - backend/core/src/main/java/com/zeromail/core/triage/service/TriageGmailWriter.java
    - backend/core/src/main/java/com/zeromail/core/triage/service/SenderSafetyNetService.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/ProtectedSenderListItem.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageSafetyPolicyContractTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationRepository.java
    - backend/core/build.gradle.kts
    - backend/worker/build.gradle.kts
    - backend/worker/src/main/resources/application.yml

key-decisions:
  - "Keep RefreshTokenCipher inside core.gmail by adding GmailApiClientFactory.buildClientForTenant(UUID)."
  - "Use Boot's auto-configured StringRedisTemplate through ObjectProvider instead of adding a custom TriageRedisConfig."
  - "Fail safe on sender safety net Gmail lookup failure by returning protected=true."

patterns-established:
  - "Triage write adapters log only tenant/message/thread/draft ids and operation names."
  - "Sender opt-in cache invalidation registers Redis deletion with TransactionSynchronization.afterCommit."
  - "Protected sender observations are upserted from both Gmail protected verdicts and protected cache hits."

requirements-completed: [TRG-02, TRG-03, TRG-04, TRG-08]

# Metrics
duration: 20min
completed: 2026-05-11
---

# Phase 04 Plan 04: Triage Services Summary

**Send-free Gmail triage services with an allow-list gate, core.gmail client facade, and hashed Redis sender safety net**

## Performance

- **Duration:** 20 min
- **Started:** 2026-05-11T12:02:21Z
- **Completed:** 2026-05-11T12:21:44Z
- **Tasks:** 3 completed
- **Files modified:** 11

## Accomplishments

- Added `TriageSafetyPolicy` as the runtime fail-closed gate for rule proposals, mirroring the LLM gateway allow-list and logging only ids.
- Added `GmailApiClientFactory.buildClientForTenant(UUID)` so triage code never imports or decrypts Gmail refresh-token ciphertext directly.
- Added `TriageGmailWriter` with exactly the forward Gmail writes and inverse undo writes needed for label, archive, and draft operations, with no send API use.
- Added `SenderSafetyNetService` with canonical sender handling, hashed Redis 24h boolean cache, Gmail SENT metadata-only lookup, opt-in override, protected observation upsert, and fail-safe outage behavior.

## Task Commits

Each task was committed atomically:

1. **Task 1: TriageSafetyPolicy allow-list gate + core.gmail Gmail-client facade** - `6ba53ea` (feat)
2. **Task 2: TriageGmailWriter single Gmail-write call site** - `0cb7545` (feat)
3. **Task 3: SenderSafetyNetService + Redis cache wiring** - `d4a8772` (feat)

**Plan metadata:** this final docs commit.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/triage/service/TriageSafetyPolicy.java` - Fail-closed allow-list gate over triage action proposals.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` - Tenant Gmail client facade that decrypts and zeroes refresh-token bytes inside core.gmail.
- `backend/core/src/main/java/com/zeromail/core/triage/service/TriageGmailWriter.java` - Sole triage Gmail write adapter for apply/remove label, archive/restore inbox, create/delete draft.
- `backend/core/src/main/java/com/zeromail/core/triage/service/SenderSafetyNetService.java` - Sender sent-history heuristic with Redis cache, opt-in override, observations, and list projection.
- `backend/core/src/main/java/com/zeromail/core/triage/application/ProtectedSenderListItem.java` - Shared sender safety net list item record.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInRepository.java` - Native idempotent insert helper for sender opt-ins.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationRepository.java` - Native upsert helper for protected sender observations.
- `backend/core/build.gradle.kts` - Spring Data Redis dependency for core triage service compilation.
- `backend/worker/build.gradle.kts` - Redis starter available on worker runtime classpath.
- `backend/worker/src/main/resources/application.yml` - Worker Redis host/port defaults.
- `backend/core/src/test/java/com/zeromail/core/triage/TriageSafetyPolicyContractTest.java` - Contract coverage for allow-list and fail-closed rejection.

## Decisions Made

- `GmailApiClientFactory` is the only new triage-facing Gmail authentication facade; `RefreshTokenCipher` remains hidden inside `core.gmail`.
- `TriageGmailWriter` owns all triage Gmail write API calls and exposes no send-shaped methods.
- Sender safety net cache uses Boot's auto-configured `StringRedisTemplate` optionally via `ObjectProvider`; no dedicated Redis config class was needed.
- Gmail lookup failures in the sender safety net return protected=true so an outage suppresses writes instead of allowing over-eager automation.

## Deviations from Plan

None - plan executed within the planned implementation choices.

## Issues Encountered

- IntelliJ SQL inspection could not resolve native upsert table-qualified columns in `TenantProtectedSenderObservationRepository`; the query was adjusted to alias the insert target while preserving PostgreSQL `ON CONFLICT` behavior.
- API Redis wiring already existed before this plan, so only core dependency and worker runtime config were added.

## Verification

- `.\gradlew.bat :backend:core:compileJava :backend:worker:compileJava :backend:api:compileJava --console=plain` - BUILD SUCCESSFUL.
- `.\gradlew.bat :backend:core:test --tests "*TriageSafetyPolicyContractTest" --tests "*TriageGmailWriteBoundaryTest" --tests "*NoGmailSendAllowedTest" --tests "*SenderSafetyNetServiceContractTest" --console=plain` - BUILD SUCCESSFUL.
- `rg "messages\(\)\.send|drafts\(\)\.send" backend` - zero matches.
- `rg "RefreshTokenCipher" backend/core/src/main/java/com/zeromail/core/triage backend/core/src/test/java/com/zeromail/core/triage` - zero matches.
- `git diff --check` - no whitespace errors.
- JetBrains file problem checks found no Java errors; remaining warnings are expected forward-use warnings for services consumed by later Phase 04 plans and weak SQL-resolution warnings on native queries.

## Known Stubs

None. Stub scan found only a pre-existing worker YAML comment containing the word "placeholder"; this plan did not introduce functional stubs.

## Threat Flags

None. The new external Gmail and Redis surfaces are the surfaces explicitly covered by the plan threat model.

## User Setup Required

None - no external service configuration required beyond the existing Redis service expected by the project.

## Next Phase Readiness

Ready for `04-05-PLAN.md`. The orchestrator can now call the safety policy, sender safety net, and the single send-free Gmail writer without touching Gmail token crypto directly.

## Self-Check: PASSED

- Verified summary and key created files exist.
- Verified task commits `6ba53ea`, `0cb7545`, and `d4a8772` exist in git history.
- Verified no committed task deleted tracked files unexpectedly.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*

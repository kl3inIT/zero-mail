---
phase: 04-triage-convergence-hero
plan: 05
subsystem: backend-triage
tags: [spring-modulith, spring-transactions, gmail-api, triage, postgres]

requires:
  - phase: 04-02
    provides: triage audit persistence, direct-terminal inserts, pending/apply/fail transitions
  - phase: 04-04
    provides: send-free Gmail writer, safety policy, sender safety net, LLM gateway credit lifecycle
provides:
  - metadata-only triage input facade for RuleEvaluationInput
  - MailMessageObserved triage orchestrator with a single ApplicationModuleListener
  - TriageAuditSaga reserve/write/finalize transaction boundary
  - no-active-transaction Gmail write regression test
affects: [04-07, 04-08, backend-core, backend-worker]

tech-stack:
  added: []
  patterns:
    - orchestrator drives a directly injected saga bean for cross-proxy transaction phases
    - Gmail writes run under Propagation.NOT_SUPPORTED with an integration regression test

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/triage/application/TriageRuleEvaluationInputFactory.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/TriageAuditSaga.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/TriageOrchestratorService.java
    - backend/core/src/test/java/com/zeromail/core/triage/NoActiveTransactionDuringGmailWriteTest.java
    - backend/worker/src/main/java/com/zeromail/worker/triage/package-info.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java

key-decisions:
  - "The service owns the single @ApplicationModuleListener; no worker adapter was needed because the worker scans com.zeromail.core."
  - "The orchestrator does not double-reserve platform LLM credits; LlmGateway.evaluateSemanticIntents already owns platform/BYOK credit lifecycle."
  - "Label actions currently persist labelId=labelName because no Gmail label resolver exists in this phase; future label resolution should replace that mapping before user-authored custom labels rely on Gmail ids."
  - "Worker triage retry/cleanup/reaper classes were added only as marker contract types; scheduled behavior remains owned by plan 04-07."

patterns-established:
  - "Use TriageAuditSaga reservePhase -> gmailWritePhase -> finalizePhase for every real Gmail write."
  - "Use recordTerminal for SHADOW_LOGGED and REJECTED_* states so rejected/shadow proposals never create PENDING rows."
  - "Build semanticEvalContent from sanitized subject excerpt and content-free metadata only."

requirements-completed: [TRG-01, TRG-02, TRG-03, TRG-04, TRG-05, TRG-08]

duration: 25min
completed: 2026-05-11T12:48:29Z
---

# Phase 04 Plan 05: Triage Convergence Hero Summary

**MailMessageObserved now drives metadata-only rule evaluation, semantic-intent resolution, safety gating, sender-net checks, and a transaction-split Gmail write saga.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-05-11T12:24:00Z
- **Completed:** 2026-05-11T12:48:29Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added a strict triage input path that fetches Gmail metadata only and maps it into `RuleEvaluationInput` plus sender/thread context.
- Added `TriageOrchestratorService` as the only `@ApplicationModuleListener` for `MailMessageObserved`.
- Added `TriageAuditSaga` with `REQUIRES_NEW` reserve/finalize phases and `NOT_SUPPORTED` Gmail write phase.
- Added `NoActiveTransactionDuringGmailWriteTest`, proving `TriageGmailWriter` is invoked with no active Spring transaction.

## Task Commits

1. **Task 1: TriageRuleEvaluationInputFactory metadata-only facade** - `be61baf`
2. **Task 2: TriageAuditSaga reserve/write/finalize flow** - `e92c059`
3. **Task 3: TriageOrchestratorService application module listener flow** - `e92c059`

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java` - Added strict `fetchTriageInput` Gmail metadata fetch with 404 skip sentinel.
- `backend/core/src/main/java/com/zeromail/core/triage/application/TriageRuleEvaluationInputFactory.java` - Converts Gmail metadata into a triage wrapper carrying `RuleEvaluationInput`, sender email, and thread id.
- `backend/core/src/main/java/com/zeromail/core/triage/application/TriageAuditSaga.java` - Owns reserve, Gmail write, finalize, and direct-terminal transaction phases.
- `backend/core/src/main/java/com/zeromail/core/triage/application/TriageOrchestratorService.java` - Runs tenant rebind, pause check, rules, semantic LLM resolution, merge, safety, sender net, shadow/rejected/apply routing.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java` - Adds pending-row lookup by canonical action key for stale pending reclaim.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java` - Exposes the validated pending-row lookup using the same canonical hash path as inserts.
- `backend/core/src/test/java/com/zeromail/core/triage/NoActiveTransactionDuringGmailWriteTest.java` - Regression test for `NOT_SUPPORTED` Gmail write behavior.
- `backend/worker/src/main/java/com/zeromail/worker/triage/*` - Adds worker triage package marker and future job contract types.

## Decisions Made

- The orchestrator annotation stays on `TriageOrchestratorService`; no worker adapter was added because worker component scanning already includes `com.zeromail.core`.
- Platform LLM credit reservation remains inside `LlmGateway.evaluateSemanticIntents`; the orchestrator only reserves deterministic zero-cost messages.
- Label actions map `labelId` to the rule label name for now because no Gmail label resolver exists in the current phase.
- Task 2 and Task 3 share commit `e92c059` because the saga and orchestrator had to be wired together atomically to keep the transaction-boundary tests meaningful.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Added pending-audit lookup for stale reclaim**
- **Found during:** Task 2
- **Issue:** `insertPending` returns empty on conflict, so an existing stale PENDING row could not be reclaimed by audit id.
- **Fix:** Added `findPendingAuditIdByKey` on `TriageAuditRepository` and a validated wrapper on `TriageAuditWriter`; `reservePhase` now inserts or finds the pending row before `reclaimStalePending`.
- **Files modified:** `TriageAuditRepository.java`, `TriageAuditWriter.java`, `TriageAuditSaga.java`
- **Verification:** targeted core/worker tests and acceptance greps passed.
- **Committed in:** `e92c059`

**2. [Rule 3 - Blocking] Added worker triage contract types**
- **Found during:** Task 2
- **Issue:** Existing worker Wave-0 type-presence tests require `TriageEventRetryJob`, `TriageEventCleanupJob`, and `TriagePendingReaperJob` classes even though scheduled behavior is planned for 04-07.
- **Fix:** Added minimal worker triage marker components and package-info so the 04-05 worker contract suite passes without implementing 04-07 scheduling behavior early.
- **Files modified:** `backend/worker/src/main/java/com/zeromail/worker/triage/*`
- **Verification:** `:backend:worker:test --tests "*TriageOrchestratorIntegrationContractTest" --tests "*TriageIdempotencyContractTest" --tests "*TriageShadowModeContractTest" --tests "*TriageCreditAccountingContractTest"` passed.
- **Committed in:** `e92c059`

**Total deviations:** 2 auto-fixed (1 Rule 2, 1 Rule 3)
**Impact on plan:** Both were necessary to preserve the plan's idempotency and verification guarantees. Scheduled worker behavior remains deferred to 04-07.

## Issues Encountered

- `.planning/CONVENTIONS.md` referenced by the plan does not exist; root `CONVENTIONS.md` and `AGENTS.md` conventions were used instead.
- The worker Wave-0 behavior probes remain disabled in the existing tests; the active type-presence tests pass. The new orchestrator and transaction test cover the executable 04-05 trust boundary.

## Known Stubs

- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventRetryJob.java` - marker component only; retry scheduling is owned by plan 04-07.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventCleanupJob.java` - marker component only; cleanup scheduling is owned by plan 04-07.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java` - marker component only; pending reaper scheduling is owned by plan 04-07.

## Verification

- `./gradlew.bat :backend:core:compileJava`
- `./gradlew.bat :backend:core:compileJava :backend:worker:compileJava`
- `./gradlew.bat :backend:core:test --tests "*NoActiveTransactionDuringGmailWriteTest"`
- `./gradlew.bat :backend:core:test --tests "*ApplicationModulesTest" --tests "*NoGmailSendAllowedTest" --tests "*NoActiveTransactionDuringGmailWriteTest" --tests "*MultiTenantLeakIntegrationTest"`
- `./gradlew.bat :backend:worker:test --tests "*TriageOrchestratorIntegrationContractTest" --tests "*TriageIdempotencyContractTest" --tests "*TriageShadowModeContractTest" --tests "*TriageCreditAccountingContractTest"`
- `./gradlew.bat :backend:api:test --tests "*ApplicationModulesTest"`
- Acceptance grep passed: one `@ApplicationModuleListener`, no `@Lazy`, `NOT_SUPPORTED` present, no direct orchestrator calls to `TriageGmailWriter` / `insertAuditPendingIfAbsent` / `markApplied`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-07 can implement the real retry, cleanup, and pending reaper schedules using the worker triage package and the saga's 2-minute lease semantics. Plan 04-08 should add the broader privacy sweep around semanticEvalContent and audit reason fields.

## Self-Check: PASSED

- Found summary file and all key created source/test files.
- Found task commits `be61baf` and `e92c059` in git history.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*

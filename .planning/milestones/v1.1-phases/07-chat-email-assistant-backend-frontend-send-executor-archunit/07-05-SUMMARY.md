---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 05
subsystem: backend
tags: [chat, gmail, confirmation, archunit, postgres, redis, safety-net]

requires:
  - phase: 07-04
    provides: Chat controller shell, ChatOrchestrator, pending-action reconciler, history projection
provides:
  - Confirmation lease and pending-action state machine for assistant write confirmations
  - Assistant write handlers for the remaining reversible and confirm-required tools
  - Sole Gmail send executor with pre-send SEND_IN_FLIGHT audit and ARCH-01 0->1 flip
  - Confirm/cancel HTTP wiring backed by ConfirmActionService
  - Tests for pending-action creation, double-click race, audit atomicity, VIP reject, and controller wiring
affects: [phase-07, phase-07-06, chat-frontend, gmail-send-safety]

tech-stack:
  added: []
  patterns:
    - TransactionTemplate around send audit state transitions; Gmail send remains outside DB transactions
    - Source-aware pending-action creation from ChatOrchestrator for non-read tool calls
    - ArchUnit marker annotation carve-out paired with exact call-site count assertion

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AllowedSendCallSite.java
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java
    - backend/core/src/test/java/com/zeromail/core/chat/confirm/ConfirmationRaceIT.java
    - backend/core/src/test/java/com/zeromail/core/chat/confirm/AuditAtomicityIT.java
    - backend/core/src/test/java/com/zeromail/core/chat/confirm/AssistantSendExecutorVipIT.java
    - backend/api/src/test/java/com/zeromail/api/controllers/chat/ConfirmControllerIT.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationStateMachine.java
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantWriteExecutor.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java
    - backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java
    - backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java
    - .github/workflows/gates.yml
    - backend/api/src/test/java/com/zeromail/api/controllers/chat/ConfirmControllerShellIT.java

key-decisions:
  - "AssistantSendExecutor is the only production Gmail send call site and is annotated with @AllowedSendCallSite."
  - "ConfirmActionService owns lease acquisition and reservation; AssistantSendExecutor and AssistantWriteExecutor always release the lease."
  - "ChatOrchestrator now inserts assistant_pending_action rows for non-read tool calls so confirm/cancel endpoints have durable state."
  - "No Liquibase changelog was added; plan reuse stays on assistant_pending_action and assistant_action_audit from changelogs 043-044."

patterns-established:
  - "ARCH-01 flip protocol: marker annotation, ArchUnit count == 1, NoGmailSendAllowedTest carve-out, CI executor count == 1, and shell-test deletion landed in one commit."
  - "HIGH-5 send protocol: SEND_IN_FLIGHT audit row is committed before Gmail send, then the same row is flipped to COMMITTED after Gmail success."
  - "VIP confirmation is rechecked server-side in AssistantSendExecutor before any audit row or Gmail send is attempted."

requirements-completed:
  - ARCH-01
  - ARCH-03
  - ARCH-04
  - CHAT-04
  - CHAT-05
  - SET-SAFE-05

duration: 45min
completed: 2026-05-18
---

# Phase 07 Plan 05: Confirmation and Send Executor Summary

**Confirmed-send backend execution with one Gmail send call site, durable pending actions, and atomic ARCH-01 gate flip**

## Performance

- **Duration:** 45 min
- **Started:** 2026-05-18T15:01:08+07:00
- **Completed:** 2026-05-18T15:33:32+07:00
- **Tasks:** 4
- **Files modified:** 28 across the plan

## Accomplishments

- Added `ConfirmationLeaseService`, `ConfirmationStateMachine`, and `GmailMessageBuilder` for lease, CAS, audit, and MIME construction.
- Wired the remaining write-reversible, confirm-required, and confirmed-send tool paths through `AssistantWriteExecutor`, `AssistantSendExecutor`, and `ConfirmActionService`.
- Created pending-action rows from `ChatOrchestrator` when non-read tool calls are persisted, closing the confirm endpoint durability gap.
- Landed the ARCH-01 atomic flip: exactly one production `gmail.users().messages().send(` call, `@AllowedSendCallSite`, ArchUnit count `1`, CI `executor=1`, and deleted the Wave 3 501 shell test.
- Added focused regression coverage for pending action creation, double-click race, 100-row audit atomicity, VIP rejection before send, and confirm/cancel controller wiring.

## Task Commits

1. **Task 5.1: Confirmation lease, state machine, Gmail message builder** - `270dcb9d`
2. **Task 5.2: Assistant write tool handlers** - `60d29610`
3. **Tasks 5.3/5.4: AssistantSendExecutor + ARCH-01 atomic flip** - `0e4b65ea`

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest" --tests "com.zeromail.core.arch.NoGmailSendAllowedTest" --tests "com.zeromail.core.chat.confirm.ConfirmationRaceIT" --tests "com.zeromail.core.chat.confirm.AuditAtomicityIT" --tests "com.zeromail.core.chat.confirm.AssistantSendExecutorVipIT" --tests "com.zeromail.core.chat.confirm.ConfirmationLeaseServiceIT" --tests "com.zeromail.core.chat.usecases.ChatOrchestratorIT" :backend:api:test --tests "com.zeromail.api.controllers.chat.*"` - PASS
- `./gradlew :backend:core:test --tests "com.zeromail.core.chat.usecases.tools.*"` - PASS
- `rg -n --glob '*.java' --glob '!**/test/**' --glob '!**/generated/**' 'messages\(\)\.send\(|drafts\(\)\.send\(' backend` - one production hit: `AssistantSendExecutor.java`
- `C:\Program Files\Git\bin\bash.exe scripts/ci/count-gmail-send-call-sites.sh` - `non_executor=0`, `executor=1`

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java` - sole Gmail send executor, pre-send audit, Gmail send outside DB transaction, server-side VIP reject.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java` - confirm/cancel orchestration over pending actions, leases, state reservation, and executor dispatch.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java` - persists `assistant_pending_action` rows for non-read tool calls.
- `backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java` - wires confirm and cancel endpoints.
- `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java` - count flipped to exactly 1.
- `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` - excludes `@AllowedSendCallSite` and requires non-empty scope.
- `.github/workflows/gates.yml` - CI grep gate now expects `executor=1`.
- `backend/api/src/test/java/com/zeromail/api/controllers/chat/ConfirmControllerShellIT.java` - deleted.

## Decisions Made

- The ARCH-01 flip commit is `0e4b65ea`, with the required `[ATOMIC-GROUP: arch01-flip]` marker.
- The send executor records `SEND_IN_FLIGHT` before Gmail and only flips to `COMMITTED` after Gmail succeeds; failures before/inside Gmail are marked `FAILED`.
- The executor rechecks VIP/safety-net recipients before audit insertion, so a missing acknowledgment produces no audit row and no Gmail call.
- Pending action creation lives beside tool-call persistence in `ChatOrchestrator`; no new table or changelog was introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Pending-action creation was missing from Wave 3 orchestration**
- **Found during:** Task 5.4 controller/executor wiring
- **Issue:** Confirm/cancel could not work because non-read tool calls were persisted to `chat_message` but not to `assistant_pending_action`.
- **Fix:** `ChatOrchestrator.persistToolCall(...)` now creates `PENDING` rows for non-read tool calls and stores draft bodies only for send/reply/forward/saveDraft carve-out tools.
- **Verification:** `ChatOrchestratorIT.confirmable_tool_call_persists_pending_action_for_confirm_endpoint` and full targeted suite passed.
- **Committed in:** `0e4b65ea`

**2. [Rule 2 - Missing Critical] Lease release needed to cover pre-audit rejection paths**
- **Found during:** AssistantSendExecutor VIP testing
- **Issue:** VIP rejection can occur before audit insertion; the executor still needed to release the confirm lease.
- **Fix:** Wrapped the send executor flow in a broad `finally` release, and kept write executor release in `finally`.
- **Verification:** `AssistantSendExecutorVipIT` passed.
- **Committed in:** `0e4b65ea`

**Total deviations:** 2 auto-fixed missing-critical issues.
**Impact on plan:** Both fixes were necessary for the planned confirm path to work safely. No new schema or product scope was added.

## Issues Encountered

- JetBrains MCP was attached to a different IDE project, so file-problem inspection was unavailable for this repo. Fallback verification used `compileJava`, `compileTestJava`, Spotless, targeted Gradle tests, and grep gates.
- WSL `/bin/bash` was unavailable for the CI grep script; the script passed through Git Bash at `C:\Program Files\Git\bin\bash.exe`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 07-06 can build the frontend chat surface against working confirm/cancel endpoints and replayable pending-action state. `.planning/REQUIREMENTS.md` was intentionally not marked complete yet; Phase 7 requirement closure remains gated on full Phase 7 verification.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*

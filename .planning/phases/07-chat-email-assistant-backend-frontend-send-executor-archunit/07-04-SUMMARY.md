---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 04
subsystem: api
tags: [chat, sse, spring-ai, spring-mvc, gmail, reconciliation, modulith, postgres]

requires:
  - phase: 07-03
    provides: core.chat module contracts, Spring AI adapter, stream sink, read tools, and tool catalog
provides:
  - Public chat SSE API surface with Vercel UI message stream header and lifecycle cleanup
  - Chat history list/detail/delete API backed by persisted chat_message.parts projection
  - Wave 3 confirm endpoint shell that returns 501 until Wave 4 executor wiring
  - ChatOrchestrator read-tool loop with assistant text persistence via TransactionTemplate
  - API-side assistant pending-action reconciliation cron for expired leases and stale SEND_IN_FLIGHT audits
affects: [07-05-confirmation-executor, 07-06-chat-frontend, ARCH-04, CHAT-01, CHAT-07]

tech-stack:
  added: []
  patterns:
    - Spring MVC SseEmitter adapted through core-local ChatStreamSink
    - TransactionTemplate-owned stream persistence after async model callbacks
    - API-local @Scheduled reconciliation for single-VPS assistant send recovery

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatHistoryController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java
    - backend/api/src/main/java/com/zeromail/api/chat/AssistantPendingActionReconciler.java
    - backend/api/src/test/java/com/zeromail/api/controllers/chat/ChatControllerStreamIT.java
    - backend/api/src/test/java/com/zeromail/api/controllers/chat/ChatHistoryControllerIT.java
    - backend/api/src/test/java/com/zeromail/api/controllers/chat/ConfirmControllerShellIT.java
    - backend/api/src/test/java/com/zeromail/api/chat/ReconciliationCronIT.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/ZeroMailApiApplication.java
    - backend/api/src/main/resources/application.yml
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatHistoryService.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java

key-decisions:
  - "API scheduling is enabled on ZeroMailApiApplication so the assistant reconciler runs in the v1.1 single-VPS API process, not the worker."
  - "Assistant send recovery queries Gmail by pre-generated Message-ID but never logs the literal gmail_message_id."
  - "ConfirmControllerShellIT is intentionally temporary and must be deleted in 07-05 with the executor flip."

patterns-established:
  - "ChatOrchestrator.stream() stays non-transactional and uses TransactionTemplate for prep, tool envelopes, and assistant text persistence."
  - "Read-tool loops execute on TenantAwareReactorScheduler, wait for model turn completion, append sanitized tool output, and call the model again."
  - "AssistantPendingActionReconciler exposes package-private sweep methods for direct integration testing instead of waiting for cron timing."

requirements-completed: [CHAT-01, CHAT-02, CHAT-07, ARCH-04]

duration: 2h 6m
completed: 2026-05-18
---

# Phase 07 Plan 04: Chat API Surface and Reconciliation Summary

**Spring MVC chat SSE + history APIs with core.chat orchestration and API-side assistant action reconciliation**

## Performance

- **Duration:** 2h 6m
- **Started:** 2026-05-18T05:38:26Z
- **Completed:** 2026-05-18T07:44:42Z
- **Tasks:** 3
- **Files modified:** 33 app/test/config files

## Accomplishments

- Landed `ChatOrchestrator`, history projection, Vercel stream adaptation, and Spring AI replay handling so read-tool calls can run synchronously and final assistant text persists after stream completion.
- Exposed `/api/chat`, `/api/chat/history`, `/api/chat/{id}`, and the Wave 3 `/api/chat/{id}/confirm` 501 shell with focused API integration tests.
- Added `AssistantPendingActionReconciler` in `backend/api` with expired-lease and stale `SEND_IN_FLIGHT` sweeps, Micrometer counters, tenant rebinds, and Gmail Message-ID lookup recovery.

## Task Commits

1. **Task 4.1: Chat orchestrator + history projection** - `89342346` (feat)
2. **Task 4.2: Chat API controllers + DTOs** - `4b962e40` (feat)
3. **Task 4.3: Assistant action reconciliation cron** - `1c1bb77b` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java` - Owns the user-turn prep transaction, model read-tool loop, sanitized tool output persistence, confirmation pause, and assistant text persistence.
- `backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java` - Adapts `SseEmitter` to the Vercel stream protocol and disposes upstream stream + heartbeat on lifecycle events.
- `backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatHistoryController.java` - Lists, loads, and soft-deletes tenant-owned chats.
- `backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java` - Returns 501 with Wave 4 marker until the send executor lands.
- `backend/api/src/main/java/com/zeromail/api/chat/AssistantPendingActionReconciler.java` - Runs the ARCH-04 reconciliation cron in the API process.
- `backend/api/src/test/java/com/zeromail/api/chat/ReconciliationCronIT.java` - Proves both reconciliation sweeps, counters, tenant rebinds, and log privacy.

## Decisions Made

- API-side `@EnableScheduling` is the narrowest change that makes the reconciler live in the process actually deployed for v1.1.
- Reconciliation logs include tenant and chat IDs but omit literal Gmail Message-ID values because Message-ID can carry tenant-identifying data.
- `ConfirmControllerShellIT` remains a Wave 3 guard only; 07-05 must delete it in the same atomic commit that wires `ConfirmController`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Enabled API scheduling**
- **Found during:** Task 4.3
- **Issue:** The plan requires an API-local `@Scheduled` reconciler, but scheduling was enabled only in worker applications.
- **Fix:** Added `@EnableScheduling` to `ZeroMailApiApplication`.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/ZeroMailApiApplication.java`
- **Verification:** `ReconciliationCronIT` Spring context loads with the reconciler bean and full 07-04 verification passes.
- **Committed in:** `1c1bb77b`

---

**Total deviations:** 1 auto-fixed (missing critical)
**Impact on plan:** Required for the planned cron to run in the API process. No scope creep.

## Issues Encountered

- `ReconciliationCronIT` initially made send-in-flight pending actions expired, so Sweep A handled them before Sweep B. The fixture was corrected so Sweep B cases stay non-expired and isolate Gmail Message-ID recovery.
- `bash scripts/ci/count-gmail-send-call-sites.sh` could not run because `/bin/bash` is unavailable in this Windows environment. The same regex gate was run with `rg` in PowerShell and returned `non_executor=0`, `executor=0`.

## Verification

- `./gradlew :backend:api:test --tests "com.zeromail.api.chat.ReconciliationCronIT"` - PASS
- `./gradlew :backend:core:test ... :backend:api:test ...` full 07-04 selected suite - PASS
- `./gradlew :backend:api:spotlessCheck --quiet` - PASS
- PowerShell `rg` Gmail-send gate - PASS (`non_executor=0`, `executor=0`)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 07-05. The confirm endpoint shell, temporary 501 test, pending-action tables, audit table, and reconciler are in place for the Wave 4 executor/state-machine flip.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*

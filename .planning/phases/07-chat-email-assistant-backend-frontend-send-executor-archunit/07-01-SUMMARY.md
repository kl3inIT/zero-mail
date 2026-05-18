---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 01
subsystem: testing
tags: [archunit, gmail, privacy, ci, chat, fixtures]
requires:
  - phase: v1.0
    provides: Existing Gmail OAuth, triage safety net, LLM gateway, and CI workflow baseline
provides:
  - Wave 0 Gmail send call-site count gate armed at zero
  - Chat persistence body-ban, Reactor scheduler, and Spring AI adapter boundary tests
  - v1 chat_message.parts fixture set with draft-body carve-out proof shape
  - CI script and workflow gate for Gmail send call-site counts
affects: [phase-07, chat, gmail-send, privacy, ci]
tech-stack:
  added: []
  patterns:
    - ArchUnit tests are armed before core.chat production code lands
    - CI Gmail send counts are script-driven to keep zero-match grep stable
key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/ChatNoReactorSchedulerTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/ChatLlmAdapterBoundaryTest.java
    - backend/core/src/test/resources/chat-message-fixtures/v1/text-only.json
    - backend/core/src/test/resources/chat-message-fixtures/v1/single-tool-call.json
    - backend/core/src/test/resources/chat-message-fixtures/v1/multi-tool-call-confirmed-send.json
    - backend/core/src/test/resources/chat-message-fixtures/v1/send-email-with-draft-body.json
    - scripts/ci/count-gmail-send-call-sites.sh
  modified:
    - .github/workflows/gates.yml
    - backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
    - .planning/phases/07-chat-email-assistant-backend-frontend-send-executor-archunit/07-CONTEXT.md
key-decisions:
  - "Proceed with existing gmail.modify OAuth scope because it covers Gmail users.messages.send."
  - "Treat sender_safety_entry.mode as a stale plan assumption and use shipped tenant_sender_opt_in plus tenant_protected_sender_observation tables."
  - "Keep ChatToolCallRegistry and ZeroMailChatMemory workaround path because Spring AI #5167 remains open."
patterns-established:
  - "ARCH-01 flip must update OnlyOneGmailSendCallSiteTest and gates.yml in the same Wave 4 commit."
  - "chat_message.parts fixtures distinguish email-read metadata-only outputs from user-authored send draft inputs."
requirements-completed:
  - ARCH-01
  - ARCH-02
  - ARCH-05
  - ARCH-07
  - CHAT-07
duration: 21 min
completed: 2026-05-18
---

# Phase 07 Plan 01: Wave 0 Test Scaffolding Summary

**Pre-production chat safety gates for Gmail send, privacy persistence, Reactor tenant context, Spring AI boundaries, and CI enforcement**

## Performance

- **Duration:** 21 min
- **Started:** 2026-05-18T02:17:50Z
- **Completed:** 2026-05-18T02:38:53Z
- **Tasks:** 9/9
- **Files modified:** 12

## Accomplishments

- Completed all five blocking Wave 0 preflight checks and recorded the operator decision in `07-CONTEXT.md`.
- Added the paired Gmail send call-site count test with Wave 0 count `0` and future Wave 4 annotation/package carve-out guard.
- Added chat architecture gates for source-aware body persistence, Reactor scheduler use, and Spring AI confinement.
- Added four v1 `chat_message.parts` fixtures, including the allowed user-authored `sendEmail` draft-body shape.
- Added `scripts/ci/count-gmail-send-call-sites.sh` and wired the backend CI gate to expect `non_executor=0` and `executor=0` in Wave 0.

## Task Commits

Each task was committed atomically:

1. **Preflight decision:** `8f7d133d` (docs)
2. **Task 1.6: Gmail send call-site count gate:** `d1e2ccff` (test)
3. **Task 1.7: Chat architecture boundary gates:** `a01f2575` (test)
4. **Task 1.8: v1 fixture set:** `2320694b` (test)
5. **Task 1.9: CI grep gate:** `214b419c` (test)

## Files Created/Modified

- `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java` - counts Gmail `messages().send` / `drafts().send` call sites and locks the future assistant executor carve-out.
- `backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java` - guards `chat_message.parts` persistence against body-shaped fields and requires source-aware sanitizer policy once `core.chat` exists.
- `backend/core/src/test/java/com/zeromail/core/arch/ChatNoReactorSchedulerTest.java` - bans raw Reactor scheduler fan-out inside chat code.
- `backend/core/src/test/java/com/zeromail/core/arch/ChatLlmAdapterBoundaryTest.java` - confines chat Spring AI imports to `core.chat.llm.springai`.
- `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java` - extends the existing global Spring AI/vendor SDK allow-list for the future chat adapter package.
- `backend/core/src/test/resources/chat-message-fixtures/v1/*.json` - v1 persisted message envelope fixtures.
- `scripts/ci/count-gmail-send-call-sites.sh` - stable Gmail send call-site counter script.
- `.github/workflows/gates.yml` - backend CI count gate and atomic ArchUnit gate-change guard.
- `.planning/phases/07-chat-email-assistant-backend-frontend-send-executor-archunit/07-CONTEXT.md` - Wave 0 preflight decision log.

## Decisions Made

- **OAuth scope:** Proceeded without adding `gmail.send` because current `gmail.modify` is send-capable for Gmail messages.
- **Safety-net schema:** Treated `sender_safety_entry.mode` as stale planning terminology; Phase 7 should use existing `tenant_sender_opt_in` and `tenant_protected_sender_observation` tables.
- **Spring AI workaround:** Kept workaround path because #5167 remains open.
- **ApplicationModulesTest:** Left unchanged because it auto-discovers modules via `package-info.java`; `core.chat` does not exist until Wave 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Canonicalized plan filenames before execution**
- **Found during:** Phase execute preflight
- **Issue:** `gsd-sdk query phase-plan-index 07` ignored `07-PLAN-01.md` through `07-PLAN-06.md` as noncanonical.
- **Fix:** Renamed plans to `07-01-PLAN.md` through `07-06-PLAN.md` and updated same-phase references.
- **Files modified:** Phase 7 plan files, `07-REVIEWS.md`, `.planning/ROADMAP.md`
- **Verification:** `gsd-sdk query phase-plan-index 07` returned all six plans and waves.
- **Committed in:** `1f70f423`

**2. [Rule 3 - Blocking] Recorded stale preflight assumptions**
- **Found during:** Tasks 1.1 through 1.5
- **Issue:** The plan expected explicit `gmail.send` and `sender_safety_entry.mode`; current implementation uses `gmail.modify` and differently named safety-net tables.
- **Fix:** Stopped for checkpoint, received operator option `1`, and recorded the explicit proceed decision in context.
- **Files modified:** `07-CONTEXT.md`
- **Verification:** Preflight evidence checked and committed before production test scaffolding.
- **Committed in:** `8f7d133d`

---

**Total deviations:** 2 auto-fixed / operator-approved blocking issues.
**Impact on plan:** Execution proceeded with the same safety intent; no production behavior was weakened.

## Issues Encountered

- `act` is not installed locally, so the GitHub Actions workflow could not be run through a local runner. The script was verified with Git for Windows bash, and `gates.yml` passed Prettier formatting.

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest"` - passed.
- `./gradlew :backend:core:test --tests "com.zeromail.core.arch.ChatPersistenceContentBanTest" --tests "com.zeromail.core.arch.ChatNoReactorSchedulerTest" --tests "com.zeromail.core.arch.ChatLlmAdapterBoundaryTest" --tests "com.zeromail.core.arch.LlmGatewayBoundaryTest"` - passed.
- `./gradlew :backend:core:test --tests "com.zeromail.core.arch.OnlyOneGmailSendCallSiteTest" --tests "com.zeromail.core.arch.ChatPersistenceContentBanTest" --tests "com.zeromail.core.arch.ChatNoReactorSchedulerTest" --tests "com.zeromail.core.arch.ChatLlmAdapterBoundaryTest" --tests "com.zeromail.core.arch.LlmGatewayBoundaryTest" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` - passed.
- JSON fixture parse and `schemaVersion: 1` check - passed for all four fixtures.
- `scripts/ci/count-gmail-send-call-sites.sh` via Git for Windows bash - returned `non_executor=0` and `executor=0`.
- JetBrains project build for modified Java test files - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 1 can now add Liquibase 041-046 and chat persistence/sanitizer code against armed test and CI gates.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*

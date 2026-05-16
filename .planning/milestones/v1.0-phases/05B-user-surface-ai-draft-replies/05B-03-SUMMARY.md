---
phase: 05B-user-surface-ai-draft-replies
plan: 03
subsystem: backend
tags: [spring-ai, gmail-api, redis, spring-modulith, draft-generation]

requires:
  - phase: 05B-01
    provides: threaded Gmail draft MIME creation and ReplyHeaders validation
  - phase: 05B-02
    provides: thread reply-status classifier, repository, and ThreadDraftSaved event
provides:
  - core.draft Modulith module with tone-context and draft-generation use cases
  - LlmGateway.chatForDraft seam using CallSite.DRAFT and SAVE_DRAFT_ONLY tools
  - RedisDistributedLock helper for per-thread regenerate locking
  - On-demand GenerateThreadDraftService with save-new-then-delete-old semantics
  - Automatic triage save_draft path now sources its body from DraftBodyGenerator
  - Triage inbound path now classifies reply status after successful draft writes
affects: [draft, triage, thread, llm, gmail, needs-reply]

tech-stack:
  added: []
  patterns:
    - LLM draft generation is owned by core.draft and reaches models only through LlmGateway
    - Regenerate is protected by Redis SETNX and saves the replacement draft before deleting the prior one
    - Post-external-call persistence is isolated in short TransactionOperations blocks

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/draft/domain/ToneContext.java
    - backend/core/src/main/java/com/zeromail/core/draft/domain/GeneratedDraft.java
    - backend/core/src/main/java/com/zeromail/core/draft/domain/DraftStatus.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/ToneContextBuilder.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftBodyGenerator.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftReplySourceLoader.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java
    - backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java
    - backend/core/src/main/java/com/zeromail/core/shared/pagination/package-info.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java
    - backend/core/src/main/java/com/zeromail/core/llm/domain/AllowListedTools.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
    - backend/core/src/main/java/com/zeromail/core/triage/package-info.java
    - backend/core/src/main/java/com/zeromail/core/thread/package-info.java

key-decisions:
  - "Used the existing LlmGatewayImpl/Spring AI client split rather than introducing a new SpringAiLlmGateway class name; all Spring AI imports remain confined to core.llm.gateway.springai."
  - "On-demand draft generation publishes ThreadDraftSaved only after the new Gmail draft is saved and the metadata transaction completes."
  - "core.triage and core.thread gained shared.pagination edges in this plan so Plan 04 projection subpackages do not need parent package-info edits."

patterns-established:
  - "Draft path returns only metadata to callers; draft bodies, prompts, completions, and tone snippets are never returned or logged."
  - "Redis lock handles are released explicitly in finally blocks, matching the concurrency contract and test assertions."
  - "Triage automatic draft decisions pass through DraftBodyGenerator and never call LlmGateway.chatForDraft directly."

requirements-completed: [DRFT-02, DRFT-03, DRFT-04]

duration: 17min
completed: 2026-05-13
---

# Phase 05B Plan 03: Draft Generation Path Summary

**Tone-matched Gmail draft generation through a single LlmGateway path, shared by automatic triage and on-demand regenerate flows**

## Performance

- **Duration:** 17 min
- **Started:** 2026-05-12T22:14:00Z
- **Completed:** 2026-05-12T22:31:00Z
- **Tasks:** 2
- **Files modified:** 34

## Accomplishments

- Added `core.draft` domain/use-case code for tone context, draft body generation, on-demand draft generation, and privacy-safe result records.
- Added `LlmGateway.chatForDraft(...)`, `SAVE_DRAFT_ONLY`, explicit draft `maxTokens`, and a draft system prompt while preserving the no-`core.draft` dependency inside `core.llm`.
- Added `RedisDistributedLock` and used it to guard per-tenant/thread on-demand generation with explicit finally release.
- Implemented `GenerateThreadDraftService`: acquire lock, load reply source, generate body, save new draft, delete old draft only after successful save, persist audit/classification metadata, publish `ThreadDraftSaved`, return Gmail link without body.
- Wired `TriageOrchestratorService` so automatic `save_draft` bodies come from `DraftBodyGenerator`, then classify reply status after successful draft writes.
- Added/converted tests for tone generation, lock release, privacy logging, architecture boundaries, and automatic triage draft generation.

## Task Commits

1. **Task 1: ToneContextBuilder, DraftBodyGenerator, LlmGateway draft seam, Redis lock** - `2d0539a`
2. **Task 2: On-demand draft generation service and triage wiring** - `7b8b990`

**Plan metadata:** this summary commit

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftBodyGenerator.java` - Shared body-generation path used by automatic and on-demand drafts.
- `backend/core/src/main/java/com/zeromail/core/draft/usecases/ToneContextBuilder.java` - Fetches and sanitizes sent-mail tone samples with descriptor-only degradation.
- `backend/core/src/main/java/com/zeromail/core/draft/usecases/GenerateThreadDraftService.java` - On-demand generate/regenerate flow with Redis lock and save-new-then-delete-old behavior.
- `backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftReplySourceLoader.java` - Loads thread reply target headers and raw inbound content from Gmail.
- `backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java` - SETNX+TTL lock helper with token-checked release.
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java` - Adds neutral `chatForDraft(...)` seam.
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java` - Routes draft calls through Spring AI model clients using `CallSite.DRAFT`.
- `backend/core/src/main/java/com/zeromail/core/llm/domain/AllowListedTools.java` - Adds `SAVE_DRAFT_ONLY`.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java` - Uses `DraftBodyGenerator` and classifies reply status after draft writes.
- `backend/core/src/test/java/com/zeromail/core/draft/GenerateThreadDraftServiceTest.java` - Covers save/delete ordering, in-flight lock, release, safety failure, persistence, and events.
- `backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacyLogScrubTest.java` - Proves draft logs omit body/prompt/completion sentinels.

## Decisions Made

- Reused `CallSite.DRAFT`; no `DRAFT_REPLY` enum or call site was introduced.
- Kept all Spring AI-specific implementation in `core.llm.gateway.springai` and routed draft generation through the existing `LlmGatewayImpl`.
- Kept `TriageAuditSaga.java` untouched. The orchestrator now supplies a generated body to the existing SaveDraft intent path.
- Used `TransactionOperations` inside `GenerateThreadDraftService` only for current-draft lookup and post-Gmail persistence/classification, so no DB transaction spans Gmail or LLM calls.

## Deviations from Plan

None - plan executed within the planned service-layer scope.

## Issues Encountered

- The initial Task 2 test run showed `LockHandle.release()` was not observed because the service used try-with-resources and Mockito saw `close()`. The service now uses explicit `try/finally { lockHandle.release(); }`, matching the plan contract.
- Full `./gradlew.bat :backend:core:test :backend:api:test` still fails in `:backend:api:test` on later 05B RED contracts (`AuditLog*`, `TriageAuditControllerContractTest`, `ThreadDraftControllerContractTest`, `DraftLockContentionTest`). The `:backend:core:test` half passes, and those API/query/controller contracts are owned by Plans 04 and 05.

## User Setup Required

None - no external service configuration required.

## Verification

- `./gradlew.bat :backend:core:spotlessApply`
- `./gradlew.bat :backend:core:test --tests "*GenerateThreadDraft*" --tests "*DraftPathArchUnit*" --tests "*DraftPrivacyLogScrub*" --tests "*AutomaticTriageDraftUsesTone*" --tests "*ActionValidator*" --tests "*TriageOrchestrator*" --tests "*ApplicationModules*"`
- `./gradlew.bat :backend:core:test --tests "*GenerateThreadDraft*" --tests "*ToneContextBuilder*" --tests "*DraftBodyGenerator*" --tests "*DraftPathArchUnit*" --tests "*DraftPrivacyLogScrub*" --tests "*AutomaticTriageDraftUsesTone*" --tests "*ActionValidator*" --tests "*TriageOrchestrator*" --tests "*ApplicationModules*"`
- `rg -n "drafts\(\)\.send|drafts\(\)\.update|messages\(\)\.send|org\.springframework\.ai|jakarta\.mail" backend/core/src/main/java/com/zeromail/core/draft` - no matches
- `rg -n "import com\.zeromail\.core\.draft" backend/core/src/main/java/com/zeromail/core/llm` - no matches
- `rg -n "DRAFT_REPLY" backend/core/src/main` - no matches
- `git diff --name-only` did not include `TriageAuditSaga.java`
- JetBrains file-problem scans and file rebuild: no errors on touched production Java files

## Next Phase Readiness

Plan 04 can build the audit-log and needs-reply read side on top of `thread_reply_status`, the on-demand draft service, and the parent module `shared.pagination` edges that landed here. Plan 05 can expose this service through thread controllers and map `DraftGenerationInFlightException` to the planned HTTP 409 contract.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*

## Self-Check: PASSED

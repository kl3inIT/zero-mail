---
phase: 09-user-settings-ui-on-curated-catalog
plan: 02
subsystem: settings-backend
tags: [settings, personalization, knowledge, draft, sanitizer, api]
requires: [09-01]
provides:
  - Voice and behavior settings backend endpoints
  - Tenant-scoped knowledge snippet CRUD
  - Shared sanitizer and knowledge write-site architecture tests
  - Draft runtime reads for auto-draft, signature, and sensitive-data toggles
affects: [assistant-settings, knowledge-snippets, chat-tools, triage-drafts, llm-sanitization]
key-decisions:
  - "ADD_TO_KNOWLEDGE_BASE is implemented by com.zeromail.core.chat.usecases.tools.WriteReversibleToolHandlers, so the append-callers ArchUnit rule pins that class plus KnowledgeSnippetController."
  - "SensitiveDataProtectionDecider lives in core.llm.usecases, not llm.redaction, so chat settings can implement the toggle without depending on a non-exported implementation package."
  - "Triage reads draft settings through TriageDraftSettings to avoid a triage -> chat Modulith cycle."
requirements-completed:
  - SET-VOICE-01
  - SET-VOICE-02
  - SET-VOICE-03
  - SET-VOICE-04
  - SET-VOICE-05
  - SET-VOICE-06
  - SET-BEHV-01
  - SET-BEHV-02
  - SET-BEHV-04
duration: 74min
completed: 2026-05-27
---

# Phase 09-02: Settings Backend Surface Summary

**Voice, behavior, and knowledge settings APIs with runtime draft/sanitizer wiring**

## Performance

- **Duration:** 74 min
- **Started:** 2026-05-26T17:42:00Z
- **Completed:** 2026-05-26T18:55:00Z
- **Tasks:** 3
- **Files modified:** 46

## Accomplishments

- Added `GET/PUT /api/settings/voice` and `GET/PUT /api/settings/behavior` with DTO validation, full snapshots, and mapped domain error codes.
- Extended `AssistantKnowledgeService` and added `GET/POST/PUT/DELETE /api/knowledge-snippets` with tenant-scoped reads/writes, duplicate-title conflict handling, and opaque 404 for cross-tenant access.
- Filled the three architecture invariants: shared personalization sanitizer call sites, knowledge repository write site, and knowledge append callers.
- Wired runtime behavior settings into existing draft/sanitization paths: auto-draft skip for auto triage, signature append in `DraftBodyGenerator`, per-tenant sensitive-data redaction toggle, and LOW/MEDIUM/HIGH threshold resolution.

## Task Commits

1. **Task 1: Voice + behavior settings endpoints** - `45a168f1` (`feat(09-02): add voice and behavior settings endpoints`)
2. **Task 2: Knowledge snippet CRUD** - `78db0456` (`feat(09-02): add knowledge snippet CRUD`)
3. **Task 3: Draft runtime toggles** - `4a3bebdb` (`feat(09-02): wire draft settings runtime toggles`)

## Key Files

- `SettingsVoiceController` / `SettingsBehaviorController` - thin REST controllers over tenant-scoped settings services.
- `SettingsVoiceService` / `SettingsBehaviorService` - transactional writes over `assistant_settings`, including writing-style bounds and fail-loud enum parsing.
- `KnowledgeSnippetController` / `AssistantKnowledgeService` - tenant-scoped CRUD while keeping the chat tool and REST path on the same append method.
- `AssistantDraftSettingsService` - reads draft-related assistant settings and implements `SensitiveDataProtectionDecider` plus `TriageDraftSettings`.
- `DraftBodyGenerator` - appends configured email signatures verbatim to generated drafts.
- `TriageOrchestratorService` - skips auto draft writes when `autoDraftReplies=false` and avoids fallback draft creation when auto-drafts are disabled.
- `SensitiveDataRedactor` - redacts email/phone by default and bypasses redaction only when the per-tenant toggle is explicitly false.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan referenced nonexistent worker/runtime classes**
- **Found during:** Task 3 implementation
- **Issue:** The plan referenced `backend/worker/src/main/java/com/zeromail/worker/draft/DraftReplyWorker.java` and an existing `core.llm.redaction.SensitiveDataRedactor`; the actual runtime paths are `TriageOrchestratorService`, `DraftBodyGenerator`, and the sanitizer pipeline.
- **Fix:** Wired settings into the current production paths instead of creating parallel worker-only behavior.
- **Committed in:** `4a3bebdb`

**2. [Rule 3 - Blocking] Initial draft-settings dependency created a Modulith cycle**
- **Found during:** Broad 09-02 verification
- **Issue:** Direct triage dependency on `AssistantDraftSettingsService` introduced a `triage -> chat` cycle; chat also needed an explicit shared exception dependency and the sensitive-data decider could not live in a non-exported implementation package.
- **Fix:** Introduced `TriageDraftSettings`, moved `SensitiveDataProtectionDecider` to `llm.usecases`, and updated module declarations.
- **Verification:** `ThreadModuleScenarioTest` passed after the fix.
- **Committed in:** `4a3bebdb`

### Deferred Scope

- `DraftConfidenceThresholdResolver` maps LOW/MEDIUM/HIGH to 0.50/0.70/0.85 and is tested, but runtime short-circuiting by confidence is not wired because the current auto-draft execution path does not expose a confidence score.
- `autoDraftReplies=false` intentionally affects background/auto triage draft writes. User-triggered draft generation remains available because it is an explicit user action.

**Total deviations:** 2 auto-fixed, 2 deferred scope notes.
**Impact on plan:** Backend settings surface is complete for the listed requirements; confidence-score runtime enforcement needs a future source of draft confidence before it can become active behavior.

## Issues Encountered

- JetBrains MCP `get_file_problems`, `build_project`, `get_file_text_by_path`, and `create_new_file` timed out after IntelliJ restart. Verification fell back to Gradle and PowerShell for docs close-out.
- Focused post-Spotless Gradle output included a scheduled catalog-sync shutdown error while Spring contexts were closing, but the test task completed with `BUILD SUCCESSFUL`.
- No Docker/local DB files were added after the user corrected the project scope.

## Verification

- `./gradlew.bat :backend:core:test --tests ThreadModuleScenarioTest --tests SensitiveDataRedactionToggleTest --tests TriageOutboundRuntimeGateTest` - passed.
- `./gradlew.bat :backend:worker:test --tests DraftAutoToggleIntegrationTest --tests DraftConfidenceThresholdTest --tests DraftSignatureIntegrationTest` - passed.
- `./gradlew.bat :backend:core:test :backend:api:test :backend:worker:test --tests "*Settings*" --tests "*Knowledge*" --tests "*Sanitizer*" --tests "*Draft*" --tests "*Sensitive*" --tests "*AssistantKnowledgeAppendCallSite*"` - passed.
- `./gradlew.bat :backend:core:spotlessApply :backend:api:spotlessApply :backend:worker:spotlessApply` - passed.
- `./gradlew.bat :backend:core:test :backend:worker:test --tests ThreadModuleScenarioTest --tests SensitiveDataRedactionToggleTest --tests TriageOutboundRuntimeGateTest --tests DraftAutoToggleIntegrationTest --tests DraftConfidenceThresholdTest --tests DraftSignatureIntegrationTest` - passed after Spotless.
- `git diff --cached --check` - clean before Task 3 commit.

## User Setup Required

None.

## Next Phase Readiness

09-03 can build the safety-net backend on the Phase 9 schema and settings patterns. 09-06 can consume the live settings/knowledge endpoints after OpenAPI regeneration, with voice/behavior DTOs and knowledge snippet APIs already in place.

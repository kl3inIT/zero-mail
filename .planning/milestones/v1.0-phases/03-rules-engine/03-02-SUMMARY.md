---
phase: 03-rules-engine
plan: "02"
subsystem: llm
tags: [rules-engine, llm-gateway, spring-ai-boundary, tool-calling, prompt-safety]

requires:
  - phase: 03-rules-engine
    provides: Rules domain model and rules.v1 action/matcher vocabulary
  - phase: 02C-llm-gateway
    provides: LlmGateway, tool-call enforcement, BYOK routing, credit lifecycle, and Spring AI adapter boundary
provides:
  - Gateway-owned rule compile tool profile
  - Dedicated LlmGateway.compileRule(CallSite.PREVIEW, payload) contract
  - RuleCompileGatewayResult for rules-owned validation without widening ToolCallResult
  - Checked-in rule compile system prompt resource
affects: [03-rules-engine, 04-triage-convergence, core-llm-gateway]

tech-stack:
  added: []
  patterns:
    - Gateway-owned LlmToolProfile separates safe action tools from rule compile tools
    - Rule compilation uses RuleCompileGatewayResult instead of adding rule_compile to Action
    - Compile prompts are loaded from checked-in resources through SystemPrompts

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/model/LlmToolProfile.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/RuleCompileGatewayResult.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/RuleCompileToolValidator.java
    - backend/core/src/main/resources/prompts/rule-compile-system-prompt.txt
    - backend/core/src/test/java/com/zeromail/core/llm/model/ToolCallResultCompatibilityTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/model/RuleCompileSystemPromptTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileGatewayContractTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileToolProfileTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/model/SystemPrompts.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/AllowListedTools.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java

key-decisions:
  - "Rule compilation uses a dedicated compileRule gateway method and RuleCompileGatewayResult; ToolCallResult and Action remain safe-action-only."
  - "The rule_compile schema and validator live in core.llm so core.rules never passes arbitrary Spring AI tools or imports Spring AI."
  - "STATE.md and ROADMAP.md were left untouched per phase-orchestrator single-writer constraint."

patterns-established:
  - "Use LlmToolProfile when adding gateway-owned tool sets; callers still receive methods, not arbitrary schema handles."
  - "Tool validators throw content-free SafetyViolationException and never carry raw tool arguments."

requirements-completed: [RULE-02]

duration: 25min
completed: 2026-05-10
---

# Phase 03 Plan 02: Rule Compile Gateway Summary

**Gateway-owned rule_compile tool path with a dedicated result type, checked-in compile prompt, and preserved safe-action/worker behavior.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-05-09T19:37:02Z
- **Completed:** 2026-05-09T20:02:25Z
- **Tasks:** 3 completed
- **Files modified:** 12

## Accomplishments

- Added `LlmGateway.compileRule(CallSite.PREVIEW, compilerPayload)` with a PREVIEW-only guard and `RuleCompileGatewayResult`.
- Added `LlmToolProfile` so `SAFE_ACTIONS` and `RULE_COMPILE` resolve inside the gateway instead of through caller-provided schemas.
- Added `RuleCompileToolValidator` and profile tests proving only `rule_compile` is accepted and unsafe/unknown names throw content-free `SafetyViolationException`.
- Added `rule-compile-system-prompt.txt` and regression coverage for required guardrail phrases.
- Preserved existing `ToolCallResult.action()` behavior and worker drift tests with no rule compile awareness.

## Task Commits

Each task was committed atomically:

1. **Task 1: Dedicated rule compile gateway contract** - `3c318ee` (feat)
2. **Task 2: Rule compile tool schema and validation** - `1eea7f2` (feat)
3. **Task 3: Compile prompt resource and Spring AI adapter preservation** - `b720eef` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/llm/model/LlmToolProfile.java` - Adds gateway-owned tool profiles for safe actions and rule compilation.
- `backend/core/src/main/java/com/zeromail/core/llm/model/RuleCompileGatewayResult.java` - Adds the dedicated rule compile gateway result without an `action()` convenience method.
- `backend/core/src/main/java/com/zeromail/core/llm/model/SystemPrompts.java` - Loads the rule compile prompt resource.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` - Adds `compileRule(CallSite, String)`.
- `backend/core/src/main/java/com/zeromail/core/llm/service/AllowListedTools.java` - Adds profile-specific tool schema resolution.
- `backend/core/src/main/java/com/zeromail/core/llm/service/RuleCompileToolValidator.java` - Adds content-free validation for `rule_compile`.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` - Routes safe action parsing and rule compile parsing through separate private paths.
- `backend/core/src/main/resources/prompts/rule-compile-system-prompt.txt` - Adds the checked-in rules.v1 compile prompt baseline.
- `backend/core/src/test/java/com/zeromail/core/llm/model/ToolCallResultCompatibilityTest.java` - Proves existing safe-action result behavior remains unchanged.
- `backend/core/src/test/java/com/zeromail/core/llm/model/RuleCompileSystemPromptTest.java` - Verifies prompt guardrails.
- `backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileGatewayContractTest.java` - Verifies compileRule contract and tool profile routing.
- `backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileToolProfileTest.java` - Verifies schema shape, validator behavior, and privacy-safe rejection logs.

## Decisions Made

- `Action` remains limited to `label`, `archive`, and `save_draft`; `rule_compile` is a tool name, not an action.
- Rule compile raw arguments cross the gateway as `toolArguments()` for rules-owned validation in Plan 03-03.
- Spring AI remains confined to `core.llm.gateway.springai`; no Spring AI imports were added outside the adapter.
- Shared `.planning/STATE.md` and `.planning/ROADMAP.md` updates were skipped because this run is under the phase orchestrator, which owns shared tracking writes.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.service.RuleCompileGatewayContractTest" --tests "com.zeromail.core.llm.model.ToolCallResultCompatibilityTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.service.RuleCompileToolProfileTest" --tests "com.zeromail.core.llm.service.*"` - PASS
- `.\gradlew.bat :backend:worker:test --tests "com.zeromail.worker.llm.*"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.service.RuleCompileGatewayContractTest" --tests "com.zeromail.core.llm.model.RuleCompileSystemPromptTest" --tests "LlmGatewayBoundaryTest" --tests "RulesBoundaryArchTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.*" --tests "LlmGatewayBoundaryTest" --tests "RulesBoundaryArchTest"` - PASS
- `rg -n "org\.springframework\.ai" backend/core/src/main/java/com/zeromail/core/rules` - PASS, no matches.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Completed nested rule_compile action-intent schema after profile test failure**
- **Found during:** Task 2 (Rule compile tool schema and validation)
- **Issue:** The first `rule_compile` schema shape exposed `actionIntents` as an array without an `items` object, so the profile test could not verify the safe action enum constraint.
- **Fix:** Replaced the minimal schema with a nested JSON schema containing `actionIntents.items.properties.type.enum = [label, archive, save_draft]`, `rules.v1`, source language enum, clarification fields, and additionalProperties=false.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/service/AllowListedTools.java`, `backend/core/src/test/java/com/zeromail/core/llm/service/RuleCompileToolProfileTest.java`
- **Verification:** Re-ran the Task 2 core LLM service tests and worker LLM tests; both passed.
- **Committed in:** `1eea7f2`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix tightened the planned schema contract and did not expand scope.

## Issues Encountered

- JetBrains had a stale editor buffer for `AllowListedTools.java`/`LlmGatewayImpl.java` during Task 2 edits; the final changes were applied through JetBrains `replace_text_in_file` and verified with Gradle.
- `.planning/STATE.md` was dirty before this executor started and remains intentionally unstaged per orchestration constraint.

## Known Stubs

None. Stub scan found only intentional null checks and structured-log `{}` placeholders.

## Threat Flags

None. The new LLM rule compile tool path, prompt, and validator were explicitly planned by 03-02; no unplanned endpoints, persistence schema, auth path, file access path, or network surface was introduced.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-03. The rules compiler can now call `LlmGateway.compileRule(CallSite.PREVIEW, payload)` and validate `RuleCompileGatewayResult.toolArguments()` without importing Spring AI or widening safe-action result behavior.

## Self-Check: PASSED

- Verified summary file exists.
- Verified all created/modified files listed in the summary exist.
- Verified task commits `3c318ee`, `1eea7f2`, and `b720eef` exist in git history.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*

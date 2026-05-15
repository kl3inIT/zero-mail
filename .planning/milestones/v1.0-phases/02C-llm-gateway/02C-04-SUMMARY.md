---
phase: 02C-llm-gateway
plan: 04
subsystem: llm-gateway
tags: [spring-ai, tool-calls, safety, allow-list, privacy, testing]

requires:
  - phase: 02C-03
    provides: LlmGatewayImpl, LlmModelClient seam, Spring AI adapter, and allow-listed tool descriptors
provides:
  - Content-free SafetyViolationException contract
  - ActionValidator with fail-loud Action.fromFunctionName plus independent EnumSet allow-list check
  - LlmGatewayImpl post-parse validation and fail-closed empty-tool-call handling
  - Metadata-only safety violation logging
  - Spring AI adapter tests pinning toolChoice required and internal tool execution disabled
affects: [02C-05a, 02C-05b, 02C-06, phase-03-rules-engine, phase-04-triage]

tech-stack:
  added: []
  patterns:
    - Gateway service remains Spring-AI-free; adapter owns OpenAiChatOptions builder usage
    - SafetyViolationException carries no message or cause constructor
    - Tool-call function names are validated only through ActionValidator

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/llm/model/SafetyViolationExceptionTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayActionValidatorTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClientTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayPlatformPathTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayMultiTenantLeakTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/llm/observability/LlmGatewayObservabilityTest.java
    - backend/api/src/test/java/com/zeromail/api/config/GlobalExceptionHandlerLogContentTest.java

key-decisions:
  - "Place Spring AI tool-choice enforcement in SpringAiLlmModelClient, not LlmGatewayImpl, preserving the LLM-01 adapter boundary."
  - "SafetyViolationException has only a no-arg constructor so rejected action names, args, model output, and cause messages cannot be carried accidentally."
  - "Use ActionValidator as the single parser path for function names; LlmGatewayImpl no longer calls Action.fromFunctionName directly."

patterns-established:
  - "Gateway safety logs use event=llm_safety_violation tenantId={} callSite={} reason={} with exception class name only."
  - "Adapter-level tests capture OpenAiChatOptions from ChatClient.prompt().options(...) to pin Spring AI M4 builder behavior."

requirements-completed: [LLM-07]

duration: 16min
completed: 2026-05-07
---

# Phase 02C Plan 04: Tool-Call Allow-List Enforcement Summary

**Defense-in-depth LLM tool-call validation with required tool choice at the Spring AI adapter and fail-closed ActionValidator parsing in the gateway.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-05-07T14:11:43Z
- **Completed:** 2026-05-07T14:28:11Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments

- Replaced the temporary `ActionValidator` marker with a Spring component that accepts only `label`, `archive`, and `save_draft`.
- Tightened `SafetyViolationException` to a no-message, no-cause runtime exception and added constructor reflection tests.
- Wired `LlmGatewayImpl.parseToolCall(...)` through `ActionValidator`, including fail-closed handling for empty model tool-call output.
- Added metadata-only safety violation logging and tests proving `send` / argument content do not appear in logs.
- Added adapter-level Spring AI tests proving `OpenAiChatOptions.builder().toolChoice("required").internalToolExecutionEnabled(false)` is applied behind the pure Java `LlmModelClient` seam.

## Task Commits

TDD task commits:

1. **Task 1 RED:** `fd38b6d` (`test(02C-04): add failing action validator tests`)
2. **Task 1 GREEN:** `202dd37` (`feat(02C-04): implement action safety validator`)
3. **Task 2 RED:** `beb3ab6` (`test(02C-04): add failing gateway action validator tests`)
4. **Task 2 GREEN:** `c49825d` (`feat(02C-04): enforce gateway action allow-list`)

## Spring AI API Notes

Context7 was queried for current Spring AI tool-calling docs. The reference docs show user-controlled tool execution through `ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)` and provider-specific OpenAI options via `OpenAiChatOptions.builder()`.

For this codebase, the final M4 lock is:

- `SpringAiLlmModelClient` calls `ChatClient.prompt().options(OpenAiChatOptions.builder()...)`.
- `OpenAiChatOptions.builder().internalToolExecutionEnabled(false)` disables Spring AI internal tool execution.
- `OpenAiChatOptions.builder().toolChoice("required")` is set when `LlmChatRequest.toolChoiceRequired()` is true.
- `LlmGatewayImpl` contains zero `OpenAiChatOptions` imports/usages, preserving the Plan 03 strict adapter boundary.

## Safety Log Shape

`LlmGatewayActionValidatorTest#emits_safety_violation_log` captures this metadata-only shape:

```text
event=llm_safety_violation tenantId=00000000-0000-0000-0000-000000000043 callSite=PREVIEW reason=SafetyViolationException
```

The test asserts the rejected function name `send`, argument key `to`, and argument value `a@b` are absent.

## Future Plan Pointers

- **Plan 05 BYOK branch:** insert the BYOK route in `LlmGatewayImpl.chat(...)` after `SanitizationContext sanitizedContext = sanitizationPipeline.sanitize(rawHtml)` and `List<LlmTool> tools = allowListedTools.tools()`, before the platform `LlmChatRequest` / `platformLlmModelClient.call(...)` construction.
- **Plan 06 credit seam:** `creditLedger.reserve / settle / release` is the outer platform-call seam. It should wrap the platform branch and account for the Plan 05 BYOK billing-skip branch without changing the validator path.

## Verification

Passed:

- `.\gradlew.bat :backend:core:test --tests "ActionValidatorTest" --tests "SafetyViolationExceptionTest"`
- `.\gradlew.bat :backend:core:test --tests "LlmGatewayActionValidatorTest" --tests "SpringAiLlmModelClientTest" --tests "ActionValidatorWave0Test" --tests "ActionValidatorTest"`
- `.\gradlew.bat :backend:core:test --tests "LlmGatewayActionValidatorTest" --tests "ActionValidatorTest" --tests "ActionValidatorWave0Test" --tests "SpringAiLlmModelClientTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayBoundaryTest"`
- `.\gradlew.bat :backend:core:test`
- `.\gradlew.bat :backend:api:test --tests "GlobalExceptionHandlerLogContentTest"`
- JetBrains `build_project` on the edited production/test files
- Git Bash grep acceptance checks for constructor count, no message constructors, validator allow-list, adapter internal execution disabled, gateway Spring AI boundary, and Wave 0 `@Disabled` removal

## Decisions Made

- Kept `toolChoice("required")` out of `LlmGatewayImpl`. The plan contains an older acceptance line expecting it in the gateway service, but the later HIGH-1 cycle-3 text correctly moves the H-5 lock to `SpringAiLlmModelClient`.
- Used package-local constructor tests for `LlmGatewayImpl`, matching existing Plan 03 gateway tests, and used Mockito only for the Spring AI `ChatClient` fluent adapter chain.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Updated stale API log test after removing the diagnostic constructor**
- **Found during:** Task 1
- **Issue:** `GlobalExceptionHandlerLogContentTest` constructed `new SafetyViolationException(String)`, directly contradicting this plan's no-message-constructor invariant.
- **Fix:** Updated the test to construct `new SafetyViolationException()` and assert only metadata is logged.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/config/GlobalExceptionHandlerLogContentTest.java`
- **Verification:** `.\gradlew.bat :backend:api:test --tests "GlobalExceptionHandlerLogContentTest"` passed.
- **Committed in:** `202dd37`

**2. [Rule 3 - Blocking] Updated observability test reflection for the new constructor dependency**
- **Found during:** Task 2 full core verification
- **Issue:** `LlmGatewayObservabilityTest` reflectively opened the old `LlmGatewayImpl` constructor, causing full `:backend:core:test` to fail with `NoSuchMethodException`.
- **Fix:** Added `ActionValidator.class` to the reflected constructor signature and supplied `new ActionValidator()`.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/llm/observability/LlmGatewayObservabilityTest.java`
- **Verification:** `.\gradlew.bat :backend:core:test` passed.
- **Committed in:** `c49825d`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both were direct consequences of the planned safety-constructor and constructor-injection changes. No BYOK routing, REST endpoint, credit, frontend, or drift work was added.

## Issues Encountered

- One acceptance criterion in the plan expected `toolChoice("required")` in `LlmGatewayImpl`, while the later HIGH-1 cycle-3 acceptance explicitly requires zero Spring AI types in `LlmGatewayImpl` and moves the lock to `SpringAiLlmModelClient`. The final implementation follows the newer adapter-boundary requirement.
- Git Bash was used for grep checks. Gradle verification was run through `gradlew.bat`, matching the Windows checkout behavior documented in Plan 03.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `02C-05a` / `02C-05b`: downstream BYOK routing and REST work can rely on a content-free safety exception, a single action validator path, and a gateway that rejects unsafe or missing tool calls before returning to callers.

## Known Stubs

None. Defensive null checks in gateway parsing are safety guards, not runtime stubs.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/02C-llm-gateway/02C-04-SUMMARY.md`
- Key files exist: `ActionValidator.java`, `SafetyViolationException.java`, `LlmGatewayActionValidatorTest.java`, `SpringAiLlmModelClientTest.java`
- Task commits exist: `fd38b6d`, `202dd37`, `beb3ab6`, `c49825d`
- Unrelated dirt remains unstaged: `.planning/config.json`, `apps/web/test-results/`

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-07*

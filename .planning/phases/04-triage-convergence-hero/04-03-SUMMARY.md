---
phase: 04-triage-convergence-hero
plan: 03
subsystem: llm
tags: [spring-ai, structured-output, semantic-intent, triage, gradle, worker]

requires:
  - phase: 04-triage-convergence-hero
    provides: "04-00 Wave 0 semantic-intent eval marker and 04-02 triage credit call sites"
  - phase: 02C-llm-gateway
    provides: "LlmGateway, sanitization pipeline, BYOK detection, and Spring AI adapter boundary"
provides:
  - "LlmGateway.evaluateSemanticIntents(...) with sanitization, pre-call budget guard, credit reserve/settle/release, and opaque failure handling"
  - "Strict JSON Schema semantic-intent classifier inside core.llm.gateway.springai with returned node-id set equality validation"
  - "SemanticIntentRequest plus TokenBudgetExceededException and LlmEvaluationFailedException"
  - "Worker triage model pin to openai/gpt-5.4-nano and semanticIntentEval Gradle task"
affects: [phase-04-plan-05, phase-04-plan-06, phase-05, llm, triage, billing]

tech-stack:
  added: []
  patterns:
    - "Pure-Java SemanticIntentEvaluator seam mirrors LlmModelClient while Spring AI implementation stays in core.llm.gateway.springai"
    - "OpenAI strict structured output uses local Spring AI M6 OpenAiChatModel.ResponseFormat builder and ChatClient options builder"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/application/SemanticIntentRequest.java
    - backend/core/src/main/java/com/zeromail/core/llm/exception/TokenBudgetExceededException.java
    - backend/core/src/main/java/com/zeromail/core/llm/exception/LlmEvaluationFailedException.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/SemanticIntentEvaluator.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentEvaluator.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentResponse.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceTest.java
    - backend/core/build.gradle.kts
    - backend/core/src/test/resources/semantic-intent-eval/README.md
    - backend/worker/src/main/resources/application.yml
    - .planning/phases/04-triage-convergence-hero/deferred-items.md

key-decisions:
  - "Add a pure-Java core.llm.service.SemanticIntentEvaluator seam so LlmGatewayImpl remains Spring-AI-free and Task 1 compiles independently."
  - "Use the pinned local Spring AI M6 API shape: OpenAiChatModel.ResponseFormat.builder() and ChatClient.options(OpenAiChatOptions.Builder)."
  - "Apply the model pin to zero-mail.llm.platform.triage-model, the property this codebase actually uses to construct the platform ChatModel."

patterns-established:
  - "Semantic LLM responses are accepted only when returned node ids exactly equal requested node ids; unknown, duplicate, or missing ids fail closed with SafetyViolationException."
  - "Semantic-intent prompt budget is checked before credit reservation using sanitized token count plus conservative intent/schema overhead."

requirements-completed: [TRG-01, TRG-04]

duration: 19 min
completed: 2026-05-11
---

# Phase 04 Plan 03: Semantic Intent Gateway Summary

**Strict structured-output semantic-intent classification behind LlmGateway with budget guarding, credit lifecycle handling, and an offline eval task.**

## Performance

- **Duration:** 19 min
- **Started:** 2026-05-11T11:33:56Z
- **Completed:** 2026-05-11T11:52:27Z
- **Tasks:** 3/3
- **Files modified:** 13

## Accomplishments

- Added `LlmGateway.evaluateSemanticIntents(...)` with JavaDoc documenting the metadata-only triage input contract.
- Added `SemanticIntentRequest`, `TokenBudgetExceededException`, `LlmEvaluationFailedException`, and a pure-Java semantic evaluator seam.
- Added the Spring AI strict JSON Schema classifier with fixed system message, `.chatResponse()` usage, and node-id set equality validation.
- Updated worker configuration for the new triage model pin, retry attempts, and prompt/completion observation flags.
- Registered `semanticIntentEval` and documented the 35-fixture cassette contract.

## Task Commits

1. **Task 1: LlmGateway.evaluateSemanticIntents interface method + impl + SemanticIntentRequest + gateway exceptions** - `632dcfe` (feat)
2. **Task 2: SemanticIntentEvaluator + SemanticIntentResponse** - `a53e0d5` (feat)
3. **Task 3: Worker model-pin bump + observation logging + semanticIntentEval task** - `1da2d0d` (chore)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` - Added the semantic-intent gateway contract and privacy/input JavaDoc.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` - Added sanitize, budget check, BYOK credit bypass, credit lifecycle, and opaque failure handling.
- `backend/core/src/main/java/com/zeromail/core/llm/service/SemanticIntentEvaluator.java` - Pure-Java seam for the Spring AI adapter.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentEvaluator.java` - Strict JSON Schema ChatClient classifier.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentResponse.java` - Required-field response record and nested node match record.
- `backend/core/build.gradle.kts` - Added `semanticIntentEval` and excluded its tag from default tests.
- `backend/worker/src/main/resources/application.yml` - Updated triage model and retry/observation settings.
- `backend/core/src/test/resources/semantic-intent-eval/README.md` - Documented fixture/cassette ownership and live-record env flags.

## Decisions Made

- Followed the existing adapter-seam pattern rather than injecting Spring AI types into `LlmGatewayImpl`.
- Adapted the structured-output code to the local Spring AI M6 dependency source after Context7 and compile verification showed `.options(...)` needs the builder and `ResponseFormat` is nested under `OpenAiChatModel`.
- Updated the actual `zero-mail.llm.platform.triage-model` property instead of adding an unused `spring.ai.openai.chat.options.model` property.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added pure-Java semantic evaluator seam**
- **Found during:** Task 1
- **Issue:** `LlmGatewayImpl` needed to delegate to a semantic evaluator while Task 1 still had to compile before the Spring AI implementation from Task 2 existed.
- **Fix:** Added `core.llm.service.SemanticIntentEvaluator` and injected it through `ObjectProvider` with a fail-fast fallback; Task 2 supplies the Spring AI bean.
- **Files modified:** `SemanticIntentEvaluator.java`, `LlmGatewayImpl.java`
- **Verification:** `:backend:core:compileJava` and `*LlmGatewayBoundaryTest` passed.
- **Committed in:** `632dcfe`

**2. [Rule 3 - Blocking] Updated existing LlmGateway test double**
- **Found during:** Task 1
- **Issue:** `RuleCompilerServiceTest.RecordingLlmGateway` no longer compiled after adding the new interface method.
- **Fix:** Added a fail-fast `evaluateSemanticIntents(...)` implementation to the test double.
- **Files modified:** `RuleCompilerServiceTest.java`
- **Verification:** `:backend:core:test --tests "*LlmGatewayBoundaryTest"` compiled test sources and passed.
- **Committed in:** `632dcfe`

**3. [Rule 3 - Blocking] Adapted Spring AI structured-output API to pinned M6**
- **Found during:** Task 2
- **Issue:** The local M6 `ChatClient.options(...)` accepts an `OpenAiChatOptions.Builder`, and `ResponseFormat` is exposed as `OpenAiChatModel.ResponseFormat`, not the exact sample shape in the plan.
- **Fix:** Used `OpenAiChatModel.ResponseFormat.builder()` and passed the options builder to `ChatClient`.
- **Files modified:** `SemanticIntentEvaluator.java`
- **Verification:** `:backend:core:compileJava` and `*LlmGatewayBoundaryTest` passed.
- **Committed in:** `a53e0d5`

**4. [Rule 3 - Blocking] Fixed Gradle task source-set lookup**
- **Found during:** Task 3
- **Issue:** `the<SourceSetContainer>()` resolved in the task scope and failed task creation.
- **Fix:** Used `project.extensions.getByType<SourceSetContainer>()["test"]`.
- **Files modified:** `backend/core/build.gradle.kts`
- **Verification:** `:backend:core:semanticIntentEval` passed.
- **Committed in:** `1da2d0d`

**Total deviations:** 4 auto-fixed blocking issues.
**Impact on plan:** All fixes preserve the planned behavior and keep the Spring AI boundary intact.

## Issues Encountered

- `./gradlew.bat :backend:core:test --console=plain` still fails on pre-existing Wave 0 future-contract presence tests for later Phase 04 services: sender safety net, orchestrator, safety policy, and undo. This is documented in `deferred-items.md` and was not changed because those services are owned by later plans.

## Verification

- `./gradlew.bat :backend:core:compileJava :backend:core:test --tests "*LlmGatewayBoundaryTest" :backend:core:semanticIntentEval --console=plain` - PASS
- JetBrains build for the touched Java files - PASS
- Task 3 config/README grep checks - PASS
- `./gradlew.bat :backend:core:test --console=plain` - FAIL on unrelated future-contract tests listed above; no `semantic-intent-eval` tagged tests exist yet, and the default test task excludes that tag.

## Known Stubs

None.

## Threat Flags

None - the new LLM prompt/response and observability surfaces were explicitly covered by the plan threat model.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 04-04/04-05. The triage orchestrator can call `LlmGateway.evaluateSemanticIntents(...)`, handle `TokenBudgetExceededException` for per-rule fanout, and rely on fail-closed node-id validation.

## Self-Check: PASSED

- Key files exist: `SemanticIntentRequest`, Spring AI `SemanticIntentEvaluator`, `SemanticIntentResponse`, `backend/core/build.gradle.kts`, and worker `application.yml`.
- Task commits found in git history: `632dcfe`, `a53e0d5`, `1da2d0d`.
- No accidental tracked file deletions were present in task commits.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*

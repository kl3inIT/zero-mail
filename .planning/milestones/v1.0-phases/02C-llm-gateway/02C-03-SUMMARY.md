---
phase: 02C-llm-gateway
plan: 03
subsystem: llm-gateway
tags: [spring-ai, spring-boot, openrouter, llm, privacy, observability, archunit]

requires:
  - phase: 02C-01
    provides: Spring AI dependency and strict LLM boundary scaffolding
  - phase: 02C-02
    provides: SanitizationPipeline and sanitizer step contract
provides:
  - LlmGateway public contract and package-private implementation skeleton
  - Platform OpenAI-compatible Spring AI adapter behind a pure Java LlmModelClient seam
  - Gateway-owned allow-listed tool surface for label, archive, and save_draft
  - Metadata-only gateway logging and observation attributes
  - Defensive logback scrubbing for apiKey, Bearer, and x-api-key values
  - LLM-04 encrypted-at-rest BYOK requirement wording
affects: [02C-04, 02C-05a, 02C-05b, 02C-06, phase-03-rules-engine, phase-04-triage]

tech-stack:
  added: []
  patterns:
    - Pure Java service seam between LlmGatewayImpl and Spring AI adapter
    - Singleton platform ChatClient with PlatformApiKey indirection
    - Metadata-only ObservationRegistry instrumentation around gateway calls
    - Repository content-ban ArchUnit tests for LLM privacy

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/model/Action.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/SystemPrompts.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/AllowListedTools.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayPlatformPathTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayMultiTenantLeakTest.java
    - backend/core/src/test/java/com/zeromail/core/llm/observability/LlmGatewayObservabilityTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/LlmRepositoryContentBanTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/ChatResponseToStringSafetyTest.java
  modified:
    - backend/api/src/main/resources/application.yml
    - backend/worker/src/main/resources/application.yml
    - backend/core/src/main/resources/logback-spring.xml
    - backend/core/src/main/java/com/zeromail/core/shared/privacy/SensitiveMarkerScrubFilter.java
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Keep LlmGatewayImpl Spring-AI-free; all org.springframework.ai imports stay in core.llm.gateway.springai."
  - "Use spring.ai.chat.client.observations.log-prompt/log-completion false pins instead of older include-* names."
  - "Apply logback secret scrub updates in the shared backend/core logback-spring.xml because api/worker module-specific logback files do not exist."

patterns-established:
  - "LlmGatewayImpl sanitizes first, prepends SystemPrompts.TRIAGE_SYSTEM_PROMPT, uses AllowListedTools, then calls LlmModelClient."
  - "SpringAiLlmModelClient translates project-local LlmTool/LlmChatRequest records into Spring AI ToolCallback/OpenAiChatOptions calls."
  - "Gateway logs and observations carry tenantId, callSite, provider, model, latency, token counts, stop reason, and truncation metadata only."

requirements-completed: [LLM-01, LLM-02, LLM-04, LLM-09]

duration: 75min
completed: 2026-05-07
---

# Phase 02C Plan 03: LLM Gateway Core Summary

**Spring AI platform gateway with a pure Java service boundary, metadata-only observability, fixed tool allow-list, and privacy hardening.**

## Performance

- **Duration:** 75 min
- **Started:** 2026-05-07T12:52:48Z
- **Completed:** 2026-05-07T14:12:00Z
- **Tasks:** 1 plan task executed as 3 TDD slices
- **Files modified:** 39

## Accomplishments

- Landed `LlmGateway` with `chat(CallSite, String)` and `driftCheck(String)` as the stable single entry point for downstream rules and triage work.
- Implemented `LlmGatewayImpl` as a Spring-AI-free service that runs `SanitizationPipeline`, applies `SystemPrompts.TRIAGE_SYSTEM_PROMPT`, injects gateway-owned `AllowListedTools`, delegates through `LlmModelClient`, and returns `ToolCallResult`.
- Added the platform Spring AI adapter: `PlatformApiKey`, `PlatformChatClientConfig`, `SpringAiLlmModelClient`, and `ZeroMailLlmProperties`.
- Updated API and worker YAML with `ZEROMAIL_LLM_PLATFORM_API_KEY:?` fail-fast config and defensive `log-prompt: false` / `log-completion: false` observation pins.
- Extended privacy hardening with repository content-ban tests, span sentinel tests, `ChatResponse.toString()` / `AssistantMessage.toString()` bans, shared Logback secret scrubbing, and metadata-only safety exception logging.

## Task Commits

The single plan task was committed as TDD RED/GREEN slices:

1. **Config and model contract RED** - `f78c737` (`test(02C-03): add failing LLM config and model contract tests`)
2. **Config and model contract GREEN** - `abe0161` (`feat(02C-03): add gateway contract and LLM configuration`)
3. **Platform gateway RED** - `a82b30f` (`test(02C-03): add failing platform gateway tests`)
4. **Platform gateway GREEN** - `e18249f` (`feat(02C-03): implement platform LLM gateway path`)
5. **Privacy hardening RED** - `79197f4` (`test(02C-03): add failing privacy hardening tests`)
6. **Privacy hardening GREEN** - `386f1d1` (`feat(02C-03): implement LLM privacy hardening`)

## Spring AI API Notes

Context7 lookups were performed for `/spring-projects/spring-ai` and `/websites/spring_io_spring-ai_reference`, plus Spring Boot `/spring-projects/spring-boot/v4.0.3`. The available versioned Spring AI Context7 snapshot was `v2.0.0-m3`; the current reference docs confirm the same builder pattern used by the M4-pinned source.

Exact import paths used:

- `org.springframework.ai.model.ApiKey` in `PlatformApiKey`
- `org.springframework.ai.openai.api.OpenAiApi` with `OpenAiApi.builder().baseUrl(...).apiKey(...).build()`
- `org.springframework.ai.openai.OpenAiChatModel` with `OpenAiChatModel.builder().openAiApi(...).defaultOptions(...).build()`
- `org.springframework.ai.openai.OpenAiChatOptions` with `OpenAiChatOptions.builder().temperature(0.0).internalToolExecutionEnabled(false).build()` and per-call `.model(...).toolChoice("required")`
- `org.springframework.ai.chat.client.ChatClient` with `ChatClient.create(platformOpenAiChatModel)`
- `org.springframework.ai.tool.ToolCallback` and `org.springframework.ai.tool.function.FunctionToolCallback` only inside the Spring AI adapter

## ArchUnit Rule Shape

The final strict Spring AI boundary rule has no `LlmGatewayImpl` exemption:

```java
noClasses()
        .that().resideOutsideOfPackage("..core.llm.gateway.springai..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.ai..")
        .because("LLM-01: Spring AI imports MUST be confined to core.llm.gateway.springai. "
                + "LlmGatewayImpl depends only on the pure-Java LlmModelClient seam; "
                + "SpringAiLlmModelClient is the single adapter that imports Spring AI types. "
                + "NO EXEMPTION.")
        .check(importedClasses);
```

Additional guards ban direct vendor SDK imports outside `core.llm.gateway.springai`, Jsoup/jtokkit outside `core.llm.gateway.sanitization`, and `ChatResponse.toString()` / `AssistantMessage.toString()` in production code.

## Metadata-Only Logs

`LlmGatewayPlatformPathTest` asserts this success-line shape without input/output bytes:

```text
event=llm_call_succeeded tenantId=00000000-0000-0000-0000-000000000042 callSite=PREVIEW provider=openai-compatible model=openai/gpt-4o-mini latencyMs=<n> promptTokens=10 completionTokens=5 stopReason=stop truncated=false
```

The same assertion rejects raw HTML, sanitized user message content, model tool args, and completion-derived labels such as `Receipts`.

## Future Plan Pointers

- **Plan 04 validator seam:** `LlmGatewayImpl.parseToolCall(...)` is the exact insertion point for `ActionValidator` and fail-closed malformed response handling.
- **Plan 05 BYOK seam:** `LlmGatewayImpl.chat(...)` has the comment `Plan 05 will branch here before platform calls to route tenant BYOK credentials`; insert BYOK lookup before the platform `LlmModelClient` call.
- **Plan 06 credit seam:** `LlmGatewayImpl.chat(...)` has the comment `Plan 06 will wrap platform calls here with CreditLedger reserve/settle/release`; wrap the platform branch only.

## Verification

Passed in this final executor pass:

- `.\gradlew.bat :backend:core:test --tests "LlmRepositoryContentBanTest" --tests "AllowListedToolsTest" --tests "LogbackScrubExtensionTest" --tests "LlmGatewayObservabilityTest" --tests "ChatResponseToStringSafetyTest"`
- `.\gradlew.bat :backend:api:test --tests "RequirementsLlm04WordingTest" --tests "GlobalExceptionHandlerLogContentTest"`
- `.\gradlew.bat :backend:core:test --tests "LlmGatewayBoundaryTest" --tests "SanitizationPipelineWave0Test" --tests "LlmGatewayWave0Test" --tests "LlmGatewayMultiTenantLeakTest"`
- `.\gradlew.bat :backend:api:bootJar :backend:worker:bootJar -x test`

Previously passed during task execution:

- `.\gradlew.bat :backend:core:test`
- `.\gradlew.bat :backend:worker:test`

Known verification limitation:

- Full `:backend:api:test` hit JVM `OutOfMemoryError` / resource pressure in this workspace. Focused API tests for this plan passed, and API/worker `bootJar` packaging passed.
- `bash -lc './gradlew ...'` could not execute this Windows checkout's Gradle script (`required file not found`), so Gradle verification was run through `gradlew.bat`. Git Bash was still used/available for shell-level checks.

## Decisions Made

- `LlmGatewayImpl` depends only on `LlmModelClient` and project-local records. `SpringAiLlmModelClient` is the only adapter translating to `ChatClient`, `OpenAiChatOptions`, and `ToolCallback`.
- Spring AI observation privacy is pinned with `spring.ai.chat.client.observations.log-prompt: false` and `log-completion: false`, plus the parallel `spring.ai.chat.observations.*` keys for defensive compatibility.
- Logback scrub hardening was applied in `backend/core/src/main/resources/logback-spring.xml`, the shared logging config on disk, because module-specific API/worker logback files referenced by the plan are not present.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Applied Logback scrub updates to the shared core config**
- **Found during:** Privacy hardening slice
- **Issue:** The plan referenced `backend/api/src/main/resources/logback-spring.xml` and the worker equivalent, but the repository only has the shared `backend/core/src/main/resources/logback-spring.xml`.
- **Fix:** Added `apiKey=...`, `Bearer ...`, and `x-api-key: ...` redaction to `SensitiveMarkerScrubFilter` and documented the patterns in the shared Logback XML.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/shared/privacy/SensitiveMarkerScrubFilter.java`, `backend/core/src/main/resources/logback-spring.xml`
- **Verification:** `LogbackScrubExtensionTest` passed.
- **Committed in:** `386f1d1`

**2. [Rule 2 - Missing Critical] Added metadata-only SafetyViolationException API handling**
- **Found during:** Privacy hardening slice
- **Issue:** The committed test required safety exceptions to log only `event` and `exceptionClass`, with no exception message content.
- **Fix:** Added `SafetyViolationException`, `ErrorCodes.LLM_SAFETY_VIOLATION`, and `GlobalExceptionHandler.onSafetyViolation(...)` with metadata-only logging.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java`, `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`, `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`
- **Verification:** `GlobalExceptionHandlerLogContentTest` passed.
- **Committed in:** `386f1d1`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both changes were required to satisfy the plan's privacy guarantees. No Wave 4 validator, BYOK routing, credit, REST, or frontend scope was implemented.

## Issues Encountered

- The plan acceptance grep `zeromail\.` falsely matches Java package values such as `springdoc.packages-to-scan: com.zeromail.api.controllers`; the YAML config namespace remains `zero-mail.*`.
- The plan acceptance check for API/worker-specific Logback XML paths does not match this repository. The shared core Logback config is the effective location.
- Full API test suite was resource-limited in this workspace; focused plan tests passed.

## User Setup Required

None for this plan. Operators must still provide `ZEROMAIL_LLM_PLATFORM_API_KEY` before starting API or worker modules with the new config.

## Next Phase Readiness

Ready for `02C-04`: the gateway contract, fixed tool list, platform model client, observation/log privacy tests, and validator insertion seam are all in place. Later plans should limit changes to the marked seams for ActionValidator, BYOK routing, and credit ledger wrapping.

## Known Stubs

None. The Plan 04/05/06 comments in `LlmGatewayImpl` are intentional seam markers, not runtime stubs.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/02C-llm-gateway/02C-03-SUMMARY.md`
- Task commits exist: `f78c737`, `abe0161`, `a82b30f`, `e18249f`, `79197f4`, `386f1d1`
- Key files exist: `LlmGateway.java`, `LlmGatewayImpl.java`, `SpringAiLlmModelClient.java`, API/worker `application.yml`, shared `logback-spring.xml`

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-07*

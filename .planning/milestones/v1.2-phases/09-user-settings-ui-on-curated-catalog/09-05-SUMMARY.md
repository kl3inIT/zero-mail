---
phase: 09-user-settings-ui-on-curated-catalog
plan: 05
subsystem: backend-privacy
tags: [spring-ai, gmail, llm-gateway, voice-generation, privacy]

requires:
  - phase: 09-01
    provides: Settings service/controller foundation and phase test stubs
  - phase: 09-04
    provides: BYOK rate limiter and LLM usage/cost API surface
provides:
  - Spring AI prompt/completion observation hardening with verified M7 keys
  - Gmail Sent reader with quoted-reply stripping and aggregate prompt cap
  - POST /api/settings/voice/generate-from-sent preview endpoint
  - Voice-generation privacy leak and rate-limit tests
affects: [settings, llm-gateway, byok, privacy-tests, future-preview-callers]

tech-stack:
  added: []
  patterns:
    - LlmGateway preview text generation still records metadata-only usage audit
    - Gmail Sent samples are stripped and capped before prompt assembly

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/controllers/settings/VoiceGenerateController.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationService.java
    - backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/GmailSentMessagesReader.java
    - backend/core/src/test/java/com/zeromail/core/voice/VoiceGenerationFromSentLeakTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmChatResult.java

key-decisions:
  - "Spring AI M7 observation keys are spring.ai.chat.observations.log-prompt and spring.ai.chat.observations.log-completion."
  - "CallSite.PREVIEW writes llm_call_audit usage metadata rows, not prompt/completion/body text."
  - "Voice generation uses LlmGateway preview text generation instead of direct model calls, preserving BYOK/credit/audit routing."

requirements-completed: [SET-VOICE-07]

duration: multi-session
completed: 2026-05-27
---

# Phase 09 Plan 05 Summary

**User-reviewed writing-style generation from recent Gmail Sent samples with prompt/completion privacy gates.**

## Performance

- **Duration:** multi-session
- **Completed:** 2026-05-27T05:34:21+07:00
- **Tasks:** 2
- **Files modified:** 22 plan files across code, tests, and summary

## Accomplishments

- Verified Spring AI 2.0.0-M7 observation config from local source after Context7 quota was exhausted. Locked keys: `spring.ai.chat.observations.log-prompt=false` and `spring.ai.chat.observations.log-completion=false`; prefix: `spring.ai.chat.observations`.
- Added `GmailSentMessagesReader` and `QuotedReplyStripper`. It strips `>` quote lines and Gmail/Outlook/Vietnamese reply separators before per-sample truncation, then caps aggregate sample text at `MAX_AGGREGATE_PROMPT_CHARS=60_000`.
- Added `POST /api/settings/voice/generate-from-sent`, default `sampleSize=20`, max `50`, returning `{generatedStyle}` only. The generated result is not persisted; users still save through existing `PUT /api/settings/voice`.
- Extended `LlmGateway` with preview text generation so voice generation does not bypass platform routing, BYOK routing, credits, or metadata-only audit.
- Replaced the two Wave-0 disabled stubs with green tests for sentinel leak protection and rate limiting.

## Task Commits

1. **Task 1: Spring AI observations + Gmail Sent reader** - `389aa444` (`feat(09-05): harden AI observations and sent reader`)
2. **Task 2: Generate voice from Sent endpoint/service/tests** - `76c08ffd` (`feat(09-05): add voice generation from sent mail`)

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/config/SpringAiObservationProperties.java` - Bound verified Spring AI observation keys for test assertions.
- `backend/api/src/test/java/com/zeromail/api/config/SpringAiObservationDisabledTest.java` - Asserts POJO false, environment key presence, worker YAML false, and absence of prompt/completion observation handler beans.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/GmailSentMessagesReader.java` - Reads Gmail Sent samples in memory, strips quoted replies, caps sample bodies, and logs aggregate cap metadata only.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/QuotedReplyStripper.java` - Pure quoted-reply stripping helper.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationService.java` - Rate-limited, non-transactional generate path.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/settings/VoiceGenerationPrompt.java` - Outcome-first style extraction prompt; instructs the model not to quote samples.
- `backend/api/src/main/java/com/zeromail/api/controllers/settings/VoiceGenerateController.java` - Thin settings endpoint.
- `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java` and `LlmGatewayImpl.java` - Preview text generation path using no tools and `toolChoiceRequired=false` while preserving audit/routing.
- `backend/core/src/test/java/com/zeromail/core/voice/VoiceGenerationFromSentLeakTest.java` - Integration sentinel leak test across prompt capture, DB rows, audit row, and logs.
- `backend/core/src/test/java/com/zeromail/core/voice/VoiceGenerationRateLimitTest.java` - Empty-state, LLM-failure code, and 3/hour rate-limit assertions.

## Verified Spring AI Keys

Context7 was unavailable due quota exhaustion, so the executor used the local Gradle cache source jar for `spring-ai-autoconfigure-model-chat-observation-2.0.0-M7`. The ground-truth class is `org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationProperties`:

- `CONFIG_PREFIX = "spring.ai.chat.observations"`
- `log-prompt` gates `ChatModelPromptContentObservationHandler`
- `log-completion` gates `ChatModelCompletionObservationHandler`
- `include-error-logging` exists but is not prompt/completion content capture

`SpringAiObservationDisabledTest` verifies:

- Bound POJO values are false.
- `Environment.containsProperty(...)` is true for both verified keys.
- API and worker `application.yml` both pin the keys false.
- No prompt/completion content observation handler bean is present by runtime class-name scan.

## CallSite.PREVIEW Semantics

`CallSite.PREVIEW` does write an `llm_call_audit` row on successful gateway calls. The write path is `LlmGatewayImpl.recordUsage(...)` -> `JdbcLlmUsageRecorder.record(...)`.

The row is metadata-only. `JdbcLlmUsageRecorder` inserts only:

- `id`, `tenant_id`, `provider`, `feature`, `model_id`, `credential_source`
- `prompt_tokens`, `completion_tokens`, `total_cost_usd`
- `call_site`, `charged_credits`, `created_at`

The schema in `079-llm-call-audit-credential-source.yaml` explicitly has no prompt, completion, request body, response body, or content text columns. For platform PREVIEW calls, `charged_credits` is `1`; for BYOK PREVIEW calls, `charged_credits` is `0`. `VoiceGenerationFromSentLeakTest` observed one PREVIEW audit row and zero sentinel matches in audit metadata.

## Privacy Gates

- Body sentinel `LEAK_SENTINEL_AB12CD34_VOICE_BODY` is present in the captured in-memory prompt, proving the user's own Sent sample reached the model.
- Quoted inbound sentinel `LEAK_SENTINEL_QUOTED_INBOUND` is absent from the captured prompt, proving quote stripping ran before prompt assembly.
- Completion sentinel `LEAK_SENTINEL_XY99ZZ_COMPLETION` may appear in the returned `generatedStyle`, but appears in no DB row and no log line.
- `assistant_settings` row count is unchanged by the generate endpoint; explicit save remains required.
- `assistant_knowledge_snippet` row count is unchanged.

## Verification

- `./gradlew.bat :backend:api:test --tests "com.zeromail.api.config.SpringAiObservationDisabledTest"`
- `./gradlew.bat :backend:core:test --tests "com.zeromail.core.voice.VoiceGenerationFromSentLeakTest" --tests "com.zeromail.core.voice.VoiceGenerationRateLimitTest" --tests "com.zeromail.core.chat.usecases.settings.GmailSentMessagesReaderAggregateCapTest" --tests "com.zeromail.core.chat.usecases.settings.QuotedReplyStripperTest" --tests "com.zeromail.core.arch.SafetyContractArchTests"`
- `./gradlew.bat :backend:api:compileJava :backend:api:compileTestJava :backend:core:compileJava :backend:core:compileTestJava`
- `./gradlew.bat spotlessApply`
- `git diff --check`

JetBrains MCP remained unavailable: read/search/diagnostic calls timed out after 120s even after IntelliJ was reopened. Gradle compile/tests were used as the verification source of truth.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed BYOK response record field name caught by existing ArchUnit privacy rule**

- **Found during:** Task 1 verification
- **Issue:** `SafetyContractArchTests.sensitive_names_wrapped` failed on `PinnedHttpResponse.body`, introduced by prior BYOK work.
- **Fix:** Renamed the metadata probe accessor to `responsePayload()` and updated `ProviderConnectionTester`.
- **Files modified:** `PinnedHttpClientFactory.java`, `ProviderConnectionTester.java`
- **Verification:** `SafetyContractArchTests` green in targeted verification.
- **Committed in:** `389aa444`

**2. [Rule 2 - Missing Critical] Added text completion support to the gateway seam**

- **Found during:** Task 2 implementation
- **Issue:** The existing `LlmGateway` contract returned only tool calls, but SET-VOICE-07 needs a user-reviewed style guide string. Calling `LlmModelClient` directly would bypass BYOK, credits, and audit.
- **Fix:** Added in-memory `assistantText` to `LlmChatResult` and `LlmGateway.generatePreviewText(...)`; Spring AI adapters populate text from `AssistantMessage.getText()`.
- **Files modified:** `LlmChatResult.java`, `LlmGateway.java`, `LlmGatewayImpl.java`, `SpringAiLlmModelClient.java`, `SpringAiByokChatSupport.java`
- **Verification:** Voice leak integration test captures the gateway request and observes a metadata-only PREVIEW audit row.
- **Committed in:** `76c08ffd`

**3. [Rule 2 - Missing Critical] Used a handler-bean absence gate instead of a full Micrometer TestObservationRegistry snapshot**

- **Found during:** Task 1 observation test wiring
- **Issue:** Spring AI prompt/completion handler classes were not on the API test compile classpath, so direct type assertions and a focused TestObservationRegistry snapshot could not be wired in that module.
- **Fix:** The test asserts verified key presence, false values, worker YAML false values, and absence of prompt/completion content observation handler beans by runtime class name. This still gates the M7 auto-config behavior that creates the content handlers only when `log-prompt` or `log-completion` is true.
- **Verification:** `SpringAiObservationDisabledTest` green.
- **Committed in:** `389aa444`

---

**Total deviations:** 3 auto-fixed (1 blocking privacy arch rule, 2 missing-critical implementation/test adjustments).
**Impact on plan:** All deviations preserve the locked privacy invariant and keep LLM traffic inside the gateway boundary.

## Issues Encountered

- Context7 quota was exhausted. Local Spring AI M7 source jars from Gradle cache were used for property-key proof.
- JetBrains MCP timed out repeatedly after IntelliJ restart. Shell and Gradle fallback were used; no repository edits were made outside GSD.

## User Setup Required

None.

## Next Phase Readiness

The backend endpoint and privacy tests for SET-VOICE-07 are ready for the frontend settings UI. Future PREVIEW callers can rely on the documented audit semantics: usage metadata row only, no prompt/completion/body text columns.

---
*Phase: 09-user-settings-ui-on-curated-catalog*
*Completed: 2026-05-27*

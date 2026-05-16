---
phase: 02C
phase_name: llm-gateway
status: all_fixed
fix_scope: critical_warning
findings_in_scope: 4
fixed: 4
skipped: 0
iteration: 1
created: 2026-05-08
---

# Phase 02C Code Review Fix

## Fixed

### WR-01: BYOK endpoint SSRF guard is separate from the actual outbound connection

Fixed by tightening compatible BYOK endpoints to an operator allowlist and by moving redirect/timeout controls into the outbound `RestClient` transport. Non-vendor compatible hosts now require both `allowNonVendorEndpoints=true` and an `allowedExtraHosts` match. The BYOK validation transport uses configured connect/read timeouts and disables redirects.

Changed files:

- `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java`
- `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java`
- `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokEndpointValidatorTest.java`
- `backend/core/src/test/java/com/zeromail/core/llm/service/ByokServiceTest.java`
- `backend/core/src/test/java/com/zeromail/core/config/RestClientConfigTest.java`

### WR-02: Google GenAI BYOK path drops required tool-call enforcement

Fixed fail-closed. Spring AI 2.0.0-M5 `GoogleGenAiChatOptions` does not expose a required tool-choice equivalent, so the Google GenAI BYOK adapter now rejects tool-required requests with `SafetyViolationException` until the provider adapter can enforce Layer 1 tool-choice semantics. A direct adapter regression test covers the behavior.

Changed files:

- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/GoogleGenAiByokModelClient.java`
- `backend/core/src/test/java/com/zeromail/core/llm/gateway/springai/GoogleGenAiByokModelClientTest.java`

### WR-03: Configured LLM/BYOK network timeouts are not wired into clients

Fixed by wiring configured timeout values into the platform OpenAI options, BYOK probe `RestClient`, and provider-specific BYOK adapter construction where the current APIs expose timeout hooks. DeepSeek receives the timeout-aware `RestClient.Builder`; OpenAI/Anthropic options set request timeout; Google GenAI receives `HttpOptions.timeout(...)`.

Changed files:

- `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/DeepSeekByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/GoogleGenAiByokModelClient.java`

### WR-04: Controllers did not consistently follow response DTO factory convention

Already fixed before this pass. The existing review documented the controller convention cleanup, so this fix report counts it as resolved in scope.

## Skipped

No critical or warning findings were skipped. `IN-01` remains out of scope because this run did not include `--all`.

## Verification

Passed:

- IntelliJ rebuild of changed backend files: success, no problems
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.llm.byok.ByokEndpointValidatorTest" --tests "com.zeromail.core.llm.service.ByokServiceTest" --tests "com.zeromail.core.llm.gateway.springai.GoogleGenAiByokModelClientTest" --tests "com.zeromail.core.config.RestClientConfigTest"`
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:api:test --tests "com.zeromail.api.controllers.llm.ByokControllerIntegrationTest"`
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:worker:test --tests "com.zeromail.worker.llm.*"`
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.arch.LlmGatewayBoundaryTest"`

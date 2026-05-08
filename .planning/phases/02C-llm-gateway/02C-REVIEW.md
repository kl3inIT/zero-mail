---
phase: 02C
phase_name: llm-gateway
status: issues_found
depth: standard
files_reviewed: 154
scope_source: SUMMARY.md plus git diff union
findings:
  critical: 0
  warning: 3
  info: 1
  total: 4
created: 2026-05-08
---

# Phase 02C Code Review

## Scope

The workflow SUMMARY extraction produced 106 existing files. A git-diff sanity pass for the phase range found 154 files, including the native BYOK provider clients from the current HEAD commit. I reviewed the wider union so this report covers the code that would ship.

Primary areas reviewed:

- BYOK credential validation, persistence, and API surface
- LLM gateway routing, tool-call safety, credit lifecycle, and observability
- Sanitization pipeline and prompt-injection fixtures
- Native Spring AI BYOK adapters
- Worker drift detection scaffold
- Web BYOK settings UI, hooks, API client, i18n, and tests

## Findings

### WR-01: BYOK endpoint SSRF guard is separate from the actual outbound connection

Severity: Warning

Files:

- `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java:167`
- `backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java:221`

`ByokEndpointValidator` resolves the user-controlled host with `InetAddress.getAllByName(...)` and rejects private addresses, but `ByokService` later gives the original URL string to `RestClient`, which performs its own DNS resolution and redirect handling. When compatible endpoints are enabled through `allowNonVendorEndpoints` or `allowedExtraHosts`, an attacker-controlled endpoint can pass validation with a public DNS answer and then rebind or redirect the actual `/models` probe toward a private or metadata address.

Impact: The advertised SSRF guard is bypassable for custom BYOK endpoints. The default config keeps non-vendor endpoints disabled, but the code and UI include compatible endpoint support, and tests explicitly exercise operator opt-in.

Recommended fix: Move SSRF enforcement to the transport layer used for the outbound request. At minimum, disable redirects for BYOK validation calls and re-check the final target. Prefer a resolver/client strategy that pins the validated IP address for the request, or limit custom endpoints to an operator-owned allowlist plus VPS egress firewall rules. Add regression tests for redirect-to-private and DNS-rebinding-style behavior.

### WR-02: Google GenAI BYOK path drops required tool-call enforcement

Severity: Warning

Files:

- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/GoogleGenAiByokModelClient.java:66`
- `backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java:12`

The gateway contract documents a two-layer safety model: wire-level `toolChoice="required"` plus fail-closed action validation. OpenAI, DeepSeek, and Anthropic BYOK clients map `request.toolChoiceRequired()` into provider options, but `GoogleGenAiByokModelClient.chatOptions(...)` only sets model, temperature, and `internalToolExecutionEnabled(false)`.

Impact: Google BYOK calls do not get the same Layer 1 enforcement as the other providers. The Java validator should still fail closed when no tool call is returned, so this is primarily a reliability and consistency gap, but it weakens the documented defense-in-depth invariant for one supported provider.

Recommended fix: If Spring AI's Google GenAI options expose an equivalent required tool-choice setting, wire it in and add a direct adapter test. If no equivalent exists, explicitly document the provider limitation and consider rejecting Google BYOK for tool-required gateway calls until the adapter can enforce the contract.

### WR-03: Configured LLM/BYOK network timeouts are not wired into clients

Severity: Warning

Files:

- `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java:107`
- `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java:135`
- `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java:13`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java:18`

The platform and BYOK properties define connect/read timeouts, and the API/worker YAML sets values for them, but the actual `RestClient.Builder` bean is a bare `RestClient.builder()` and the Spring AI chat model construction does not pass timeout-aware HTTP clients or request options. The BYOK `/models` probes build the bare RestClient for each request.

Impact: A slow or half-open upstream provider can hold validation and gateway calls far longer than the configured 5s/15s/30s budgets. That is especially risky for the save path because it performs an upstream probe inside the service call before persisting credentials.

Recommended fix: Build provider clients from timeout-aware HTTP request factories or the Spring AI client builder hooks, using `zero-mail.llm.platform.*Timeout` and `zero-mail.llm.byok.*Timeout`. Add a regression test that injects tiny timeout values and verifies validation fails with `timeout` rather than blocking on the default client behavior.

### IN-01: Native BYOK provider adapters have no direct tests

Severity: Info

Files:

- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/GoogleGenAiByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/DeepSeekByokModelClient.java`
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokModelClient.java`

The gateway routing tests mock these adapters, which is appropriate for service-level isolation, but there are no direct tests asserting each native adapter maps per-call key, endpoint, model, temperature, disabled internal tool execution, and required tool choice into provider options. WR-02 is exactly the kind of adapter-level drift that a small direct test would catch.

Recommended fix: Add adapter-focused tests or a thin factory abstraction that can be inspected without real network calls. Keep the tests secret-safe by using synthetic keys and asserting no prompt, completion, or key material is logged.

## Verification

Passed:

- IntelliJ project build: success, no problems
- `pnpm --filter web typecheck`
- `pnpm --filter web test -- ByokForm.test.tsx byok-key-handling.test.ts` (12 tests)
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.llm.service.ByokServiceTest" --tests "com.zeromail.core.llm.service.LlmGatewayByokRoutingTest" --tests "com.zeromail.core.llm.service.LlmGatewayCreditLifecycleTest"`
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:api:test --tests "com.zeromail.api.controllers.llm.ByokControllerIntegrationTest"`
- `.\gradlew.bat --no-daemon --max-workers=1 :backend:worker:test --tests "com.zeromail.worker.llm.*"`

Notes:

- The first API test command was run in parallel with the core Gradle test command and the isolated Gradle daemon disappeared. Rerunning the API target by itself passed, so I treated that first result as a runner/resource issue rather than a test failure.

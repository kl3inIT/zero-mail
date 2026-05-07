---
phase: 02C-llm-gateway
plan: 05a
type: execute
wave: 5
depends_on: [01, 03, 04]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java
autonomous: true
requirements: [LLM-03]
must_haves:
  truths:
    - "LlmGatewayImpl.chat() resolves Optional<TenantByokCredentialsEntity> via byokRepo.findByTenantId(tenantId) BEFORE the platform-path call site; if BYOK row exists, the BYOK factory is used and the platform path is skipped"
    - "BYOKChatModelFactory has 2 implementations: OpenAiCompatibleByokFactory uses per-call OpenAiApi#mutate().apiKey().baseUrl() + OpenAiChatModel#mutate(); AnthropicByokFactory uses per-request AnthropicChatOptions.builder().apiKey().baseUrl() (D-A2 asymmetric seam — RESEARCH lines 13-16)"
    - "ByokEndpointValidator (H-4) is wired into BOTH factories BEFORE client construction; rejects null/non-https/RFC1918/link-local/loopback/metadata-IP/non-vendor-host endpoints; opt-in flag zeromail.llm.byok.allow-non-vendor-endpoints defaults false; rejected message is redacted (no endpoint echo)"
    - "BYOK plaintext key NEVER persists in DB or logs; lives only in the mutate() builder argument or runtime options for the duration of one HTTP call; Arrays.fill best-effort heap zero on the way out"
    - "BYOK call path emits event=llm_byok_call_started/_succeeded with tenantId + provider + model + latencyMs + token counts only; never the decrypted key, endpoint URL, or model output content"
    - "When tenant has BYOK row, gateway call returns ToolCallResult; ledger is NOT touched (verified by Plan 06 negative-path test confirming LLM-04 — BYOK billing skip)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java"
      provides: "Strategy interface — ChatResponse call(byte[] decryptedKey, String endpoint, String model, String userMessage, List<ToolCallback> tools, String toolChoice)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java"
      provides: "@Component implementing BYOKChatModelFactory via OpenAiApi#mutate() seam (D-A2)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java"
      provides: "@Component implementing BYOKChatModelFactory via AnthropicChatOptions.builder() per-request seam (D-A2 + RESEARCH correction)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java"
      provides: "@Component SSRF allow-list — validateAnthropic / validateOpenAiCompatible reject metadata-IP / RFC1918 / link-local / loopback / non-HTTPS / non-vendor-host endpoints (H-4)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java"
      provides: "RuntimeException — no content payload; redacted message; mapped to 400 in Plan 05b GlobalExceptionHandler"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "TenantByokCredentialsRepository.findByTenantId + RefreshTokenCipher.decrypt + BYOKChatModelFactory.call"
      via: "BYOK branch inserted at the // Plan 05 modifies here marker"
      pattern: "byokRepo\\.findByTenantId"
    - from: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/{OpenAiCompatibleByokFactory,AnthropicByokFactory}.java"
      to: "backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java"
      via: "validator.validate{OpenAiCompatible,Anthropic}(endpoint) called BEFORE building the client (H-4)"
      pattern: "byokEndpointValidator\\.validate"
---

<objective>
Wave 4 BYOK gateway internals (M-2 split — Plan 05a covers gateway-side; Plan 05b covers REST surface). Add the BYOK branch to `LlmGatewayImpl` so per-tenant credentials override the platform path; wire two `BYOKChatModelFactory` implementations using the asymmetric M4 seams (OpenAI-compatible via `OpenAiApi#mutate()`, Anthropic via `AnthropicChatOptions.builder()` per-request — RESEARCH correction); ship the `ByokEndpointValidator` SSRF allow-list (H-4) so user-supplied endpoints cannot point at metadata services or RFC1918 / link-local / loopback hosts.

Purpose: this is LLM-03 (BYOK per-request key delivery via Spring AI options). After this plan, the gateway *internally* knows how to route via a BYOK row when one exists. Plan 05b (next wave-4 plan, depends_on [05a]) lands the REST surface that lets users install / replace BYOK rows. Plan 06 (LLM-04 BYOK billing skip + LLM-10 spend cap) wires the ledger gate around the platform path; the BYOK branch from this plan must already short-circuit before any ledger call.

Output: 1 strategy interface + 2 asymmetric factory impls + `ByokEndpointValidator` (H-4 SSRF allow-list) + `InvalidByokException` + `LlmGatewayImpl` modified at the // Plan 05 seam + 1 routing test file.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-RESEARCH.md
@.planning/phases/02C-llm-gateway/02C-AI-SPEC.md
@.planning/phases/02C-llm-gateway/02C-03-SUMMARY.md
@.planning/phases/02C-llm-gateway/02C-04-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
@backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
@backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
@backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java
@backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsRepository.java
@backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java
@backend/core/src/main/java/com/zeromail/core/llm/model/BYOKProvider.java

<interfaces>
<!-- From Plan 01 -->
- `TenantByokCredentialsRepository#findByTenantId(UUID) → Optional<TenantByokCredentialsEntity>`
- `TenantByokCredentialsEntity` ctor `(UUID id, UUID tenantId, BYOKProvider provider, String endpoint, byte[] encryptedKey, short keyVersion)`; mutator `replaceKey(byte[] envelope, short keyVersion)`.
- `BYOKProvider` enum `{ANTHROPIC("anthropic"), OPENAI_COMPATIBLE("openai-compatible")}`.

<!-- Existing reusable -->
- `com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher#encrypt(byte[] plaintext, String tenantId) → byte[]`
- `com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher#decrypt(byte[] envelope, String tenantId) → byte[]`
- Existing 402 mapping for `InsufficientCreditsException` in GlobalExceptionHandler lines 130-138 (DO NOT modify; preserve as-is).

<!-- Spring AI 2.0.0-M4 seams (RESEARCH lines 12-17 + Section 3 of AI-SPEC) -->
- OpenAI-compatible BYOK: `OpenAiApi.mutate().apiKey(plaintextApiKey).baseUrl(endpoint).build()` then `chatModel.mutate().openAiApi(derivedApi).build()` then `ChatClient.create(derivedModel)` — per-call, discarded after response.
- Anthropic BYOK: `AnthropicChatOptions.builder().apiKey(plaintextApiKey).baseUrl(endpoint).model(modelId).build()` passed via `chatClient.prompt().options(...)` — per-request runtime options. (RESEARCH line 15: M4 source confirms `apiKey()` + `baseUrl()` exposed on AnthropicChatOptions; AnthropicApi.mutate() is NOT documented in M4.)

<!-- Validate flow (SPEC #4) -->
- OpenAI-compatible: `GET ${endpoint}/v1/models` with `Authorization: Bearer ${apiKey}` → expect 200 + `{ data: [{id: "..."}, ...] }`. Use Spring's `RestClient` (or existing equivalent in the codebase).
- Anthropic: `POST ${endpoint or default}/v1/messages` with `x-api-key: ${apiKey}` + `anthropic-version: 2023-06-01` body `{model, max_tokens: 1, messages: [{role: "user", content: "."}]}` → 200 = valid.

<!-- Plan 03 marker comments inside LlmGatewayImpl -->
- `// Plan 05 will add: private final TenantByokCredentialsRepository byokRepo; ...`
- Branch insertion point right after `SanitizationContext sanitized = sanitizationPipeline.sanitize(rawHtml);`
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: BYOKChatModelFactory interface + 2 impls + InvalidByokException + LlmGatewayImpl BYOK branch</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java (Plan 03 + Plan 04 — find `// Plan 05 modifies here` marker)
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java (interface analog for BYOKChatModelFactory shape)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java (envelope shape, encrypt/decrypt signatures)
    - backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java (Plan 04 — analog for no-message exception class)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-A1 through D-A5 — full BYOK seam strategy)
    - .planning/phases/02C-llm-gateway/02C-RESEARCH.md (lines 12-17 RESEARCH corrections; lines 144-149 alternatives table — Anthropic seam asymmetry)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "OpenAiCompatibleByokFactory.java" + "AnthropicByokFactory.java" + "ByokService.java")
  </read_first>
  <behavior>
    - Test 1 (LlmGatewayByokRoutingTest#byok_row_routes_through_factory_not_platform): seed BYOK row for tenant A with provider=ANTHROPIC + encrypted key; mock AnthropicByokFactory.call() to return a label tool call; mock platform ChatClient (verify `verify(platformChatClient, never()).prompt()`); assert ToolCallResult returned.
    - Test 2 (LlmGatewayByokRoutingTest#no_byok_row_falls_through_to_platform): no BYOK row; mock platform path returns label; assert platform path was used.
    - Test 3 (LlmGatewayByokRoutingTest#openai_compat_byok_uses_mutate_seam): seed BYOK with provider=OPENAI_COMPATIBLE + endpoint=`https://together.xyz/v1`; assert OpenAiCompatibleByokFactory.call(decryptedKey, "https://together.xyz/v1", model, ...) was invoked.
    - Test 4 (LlmGatewayByokRoutingTest#cipher_decrypt_called_with_tenantId_aad): assert RefreshTokenCipher.decrypt(envelopeBytes, tenantIdString) is called exactly once per chat() invocation; the decrypted plaintext byte[] is the argument to factory.call().
    - Test 5 (LlmGatewayByokRoutingTest#byok_path_does_not_log_key_bytes): captured ListAppender contains no byte sequence matching the plaintext key; log lines emit `event=llm_byok_call_started tenantId={} provider={} model={}` only.
    - Test 7 (ByokEndpointValidatorTest#rejects_metadata_ip): `validator.validateOpenAiCompatible("http://169.254.169.254/v1")` throws `InvalidByokException`.
    - Test 8 (ByokEndpointValidatorTest#rejects_rfc1918): `validator.validateOpenAiCompatible("https://10.0.0.5/v1")` throws.
    - Test 9 (ByokEndpointValidatorTest#rejects_loopback): `validator.validateAnthropic("https://127.0.0.1")` throws.
    - Test 10 (ByokEndpointValidatorTest#rejects_non_https): `validator.validateAnthropic("http://api.anthropic.com")` throws.
    - Test 11 (ByokEndpointValidatorTest#anthropic_default_when_null): `validator.validateAnthropic(null)` returns `"https://api.anthropic.com"`.
    - Test 12 (ByokEndpointValidatorTest#anthropic_rejects_non_vendor_host): `validator.validateAnthropic("https://example.com")` throws.
    - Test 13 (ByokEndpointValidatorTest#openai_compat_accepts_with_operator_opt_in): with `zeromail.llm.byok.allow-non-vendor-endpoints=true`, `validator.validateOpenAiCompatible("https://together.xyz/v1")` returns the URL unchanged.
    - Test 6 (LlmGatewayByokRoutingTest#multitenant_no_key_leak): 100 concurrent virtual-thread calls, 50 tenants with BYOK row + 50 without; mock factories echo bound tenantId in args; assert no cross-tenant leak (mirror of Plan 03 leak-test pattern).
  </behavior>
  <action>
    1. **(H-4) Create `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java`** — SSRF allow-list validator that runs BEFORE either factory builds a client. Closes the surface where a user could paste a BYOK endpoint pointing at the cloud metadata IP (`169.254.169.254`), an RFC1918 IP (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local (`169.254.0.0/16`), or loopback (`127.0.0.0/8`, `::1`).

       Required behavior:
       - Null / blank endpoint → return the provider-default URL: Anthropic = `https://api.anthropic.com`, OpenAI-compatible = `https://api.openai.com`, OpenRouter = `https://openrouter.ai/api/v1`. Do NOT throw on null — fall back to the SDK default.
       - Non-`https` scheme → throw `InvalidByokException` with redacted message (no endpoint echo in the exception).
       - Host parses to RFC1918 / link-local / loopback / metadata IP → throw `InvalidByokException`.
       - Provider-specific allow-list:
         - Anthropic provider: host MUST match `*.anthropic.com` (or be the exact provider default).
         - OpenAI-compatible provider: host MUST match `*.openai.com` OR `openrouter.ai` OR a `zeromail.llm.byok.allowed-extra-hosts` config list. For self-hosted dev (vLLM, Together.ai, Fireworks), gate on operator opt-in flag `zeromail.llm.byok.allow-non-vendor-endpoints=false` (default `false`); when `true`, accept any HTTPS public-IP host that is NOT RFC1918 / link-local / loopback / metadata.
       - On rejection, log `event=byok_validate_failed tenantId={} provider={} reason=endpoint_rejected` (D-I2 — no endpoint URL echoed).

       Wire the validator into `OpenAiCompatibleByokFactory.create(...)` and `AnthropicByokFactory.create(...)` as the FIRST call inside the factory, BEFORE building the client. (Also wire into `ByokService.validate(...)` in Plan 05b — but that's 05b's job; this plan exposes the validator as a `@Component` so 05b can inject it.)

    8. **Create `backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java`** — mirror SafetyViolationException shape; no message constructor; carries no content. Two-line class, public no-arg ctor only. Used by ByokService when validate fails or save is attempted before validate.

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java`** — strategy interface:
       ```java
       package com.zeromail.core.llm.gateway.springai;
       import java.util.List;
       import org.springframework.ai.chat.model.ChatResponse;
       import org.springframework.ai.tool.ToolCallback;

       public interface BYOKChatModelFactory {
           ChatResponse call(byte[] decryptedKey, String endpoint, String model,
                             String userMessage, List<ToolCallback> tools,
                             String toolChoice);   // "required"
       }
       ```
       Single signature for both providers per D-A3 — implementation strategy differs internally per provider.

    3. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java`** — `@Component implements BYOKChatModelFactory`. Per D-A2: per-call `OpenAiApi#mutate().apiKey(new SimpleApiKey(new String(decryptedKey, UTF_8))).baseUrl(endpoint).build()` then `chatModel.mutate().openAiApi(derivedApi).build()` then `ChatClient.create(derivedModel)`. Wrap call in try-finally that nulls local refs to encourage GC of the plaintext key. The factory injects the parent `OpenAiChatModel` bean (from Plan 03's `PlatformChatClientConfig.platformOpenAiChatModel`).
       
       Variable names: `decryptedKey` (not `key`), `derivedApi` (not `api`), `derivedModel` (not `m`). No Lombok.

    4. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java`** — `@Component implements BYOKChatModelFactory`. Per RESEARCH correction line 15: use `AnthropicChatOptions.builder().apiKey(new String(decryptedKey, UTF_8)).baseUrl(endpoint).model(model).toolChoice(/* AnthropicChatOptions tool-choice ANY */).build()`, pass via `parentAnthropicChatClient.prompt().options(perCallOptions).user(userMessage).toolCallbacks(tools).call().chatResponse()`. Verify exact M4 names via Context7 query `/spring-projects/spring-ai` "AnthropicChatOptions builder apiKey baseUrl 2.0.0-M4".

    5. **Modify `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`** at the `// Plan 05 modifies here` markers:
       
       (a) Add fields + constructor params:
       ```java
       private final TenantByokCredentialsRepository byokRepo;
       private final RefreshTokenCipher refreshTokenCipher;
       private final OpenAiCompatibleByokFactory openAiCompatByokFactory;
       private final AnthropicByokFactory anthropicByokFactory;
       ```
       
       (b) Inside `chat()`, after `SanitizationContext sanitized = sanitizationPipeline.sanitize(rawHtml);`, add the BYOK branch BEFORE the platform-path call:
       ```java
       Optional<TenantByokCredentialsEntity> byok = byokRepo.findByTenantId(tenantId);
       if (byok.isPresent()) {
           // BYOK path — Plan 06 will skip credit ledger here per LLM-05
           return callViaByokFactory(byok.get(), sanitized, callSite, tools);
       }
       // Plan 06 will add: ReservationId reservation = creditLedger.reserve(tenantId, callSite);
       // try {
       //   platform call below
       //   creditLedger.settle(reservation);
       //   return result;
       // } catch (...) { creditLedger.release(reservation); throw; }
       // [existing platform path from Plan 03 unchanged]
       ```
       
       (c) Add private helper:
       ```java
       private ToolCallResult callViaByokFactory(TenantByokCredentialsEntity byokRow,
                                                 SanitizationContext sanitized,
                                                 CallSite callSite,
                                                 List<ToolCallback> tools) {
           UUID tenantId = byokRow.getTenantId();
           String model = llmProperties.modelByCallSite().get(callSite);
           BYOKChatModelFactory factory = switch (byokRow.getProvider()) {
               case ANTHROPIC -> anthropicByokFactory;
               case OPENAI_COMPATIBLE -> openAiCompatByokFactory;
           };
           byte[] decryptedKey = refreshTokenCipher.decrypt(byokRow.getEncryptedKey(), tenantId.toString());
           try {
               long startNanos = System.nanoTime();
               log.info("event=llm_byok_call_started tenantId={} provider={} model={}",
                       tenantId, byokRow.getProvider(), model);

               ChatResponse chatResponse = factory.call(
                       decryptedKey,
                       byokRow.getEndpoint(),
                       model,
                       sanitized.content(),
                       tools,
                       "required");

               ToolCallResult result = parseToolCall(chatResponse);   // Same Plan 04 validator path

               long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
               Usage usage = chatResponse.getMetadata().getUsage();
               log.info("event=llm_byok_call_succeeded tenantId={} provider={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}",
                       tenantId, byokRow.getProvider(), latencyMs,
                       usage.getPromptTokens(), usage.getGenerationTokens(),
                       chatResponse.getResults().get(0).getMetadata().getFinishReason(),
                       sanitized.truncated());
               return result;
           } finally {
               // Best-effort plaintext zero — JVM may have copies in interned String pool if cipher
               // returned String, but byte[] heap reference becomes unreachable after this method.
               java.util.Arrays.fill(decryptedKey, (byte) 0);
           }
       }
       ```
       Variable names: `byokRow` (not `b`/`row`), `decryptedKey` (not `key`), `chatResponse` (not `resp`).

    6. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java`** — `@SpringBootTest` with `@MockBean BYOKChatModelFactory` (both impls), `@MockBean` for the platform ChatClient (so we can `verify(...).prompt()` was never called on BYOK path). Persists actual BYOK entity rows via `TenantByokCredentialsRepository`; encrypts test keys via the actual RefreshTokenCipher bean. Tests 1–6 above.

    7. **Update `LlmGatewayBoundaryTest`** ArchUnit rule from Plan 01 to keep `org.springframework.ai.openai..` and `org.springframework.ai.anthropic..` and `org.springframework.ai.tool..` confined to `core.llm.gateway.springai` (this is already the case from Plan 03's exemption — no change needed unless `AnthropicChatOptions` import path differs in M4; verify in Plan 04 summary).
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "LlmGatewayByokRoutingTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayBoundaryTest"</automated>
  </verify>
  <acceptance_criteria>
    - File `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java` exists; `grep -c 'ChatResponse call' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java` returns `1`.
    - Files `OpenAiCompatibleByokFactory.java` and `AnthropicByokFactory.java` both exist; `grep -c 'OpenAiApi.*mutate' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java` returns `>= 1`; `grep -c 'AnthropicChatOptions.builder' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java` returns `>= 1`.
    - `grep -c 'byokRepo.findByTenantId' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c 'refreshTokenCipher.decrypt' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c 'event=llm_byok_call_started\|event=llm_byok_call_succeeded' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 2`.
    - `grep -E 'log\.(info|warn|error|debug).*decryptedKey' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns no matches.
    - `grep -c 'Arrays.fill(decryptedKey' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1` (best-effort zero).
    - `./gradlew :backend:core:test --tests "LlmGatewayByokRoutingTest"` exits 0 — all 6 tests pass.
    - `./gradlew :backend:core:test --tests "ByokEndpointValidatorTest"` exits 0 — all 7 SSRF tests pass (H-4).
    - File `backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java` exists; `grep -E "169\.254\.169\.254|10\.0\.0\.0|172\.16\.0\.0|192\.168\.0\.0|127\.0\.0\.0|::1" backend/core/src/main/java/com/zeromail/core/llm/byok/ByokEndpointValidator.java | wc -l` returns `>= 4` (RFC1918 + link-local + loopback + metadata blocked).
    - `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayMultiTenantLeakTest"` exits 0 (Plan 03/04 tests still pass).
    - `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest"` exits 0 (Spring AI imports still confined).
  </acceptance_criteria>
  <done>
    BYOK strategy interface + 2 asymmetric impls land. LlmGatewayImpl branches on TenantByokCredentialsEntity presence; cipher decrypts the envelope per call; plaintext key never logged or persisted; `Arrays.fill` best-effort zero on the decrypted byte[]. Tests prove BYOK path skips platform ChatClient and that no key bytes leak under 100-tenant concurrency.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → POST /api/llm/byok/validate | Raw API key crosses; backend issues outbound probe; key is in-memory only for the request scope. |
| ByokService → upstream provider (OpenAI-compat / Anthropic) | Outbound HTTP carries the plaintext key in Authorization / x-api-key header. Validate response body MUST NOT leak into application logs or error responses. |
| RefreshTokenCipher boundary | Plaintext key crosses only into the cipher and back into the per-call `mutate()` builder argument; never persisted in plaintext. |
| BYOK row in tenant_byok_credentials → LlmGatewayImpl | Encrypted envelope is decrypted per call; plaintext exists for the duration of one chat() invocation; `Arrays.fill(decryptedKey, 0)` best-effort zero on the way out. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-03 | Information Disclosure (BYOK key leakage in logs / DB / error traces / metrics) | ByokService + LlmGatewayImpl + GlobalExceptionHandler | mitigate | (1) `event=byok_validate_attempted tenantId={} provider={}` — no endpoint, no key. (2) GlobalExceptionHandler logs `exception.getClass().getSimpleName()` only — never the exception object or its message. (3) BYOK `encrypted_key BYTEA` always encrypted via `RefreshTokenCipher` envelope (`[key_version:int32 | nonce:12 | ciphertext]`, tenantId AAD). (4) `Arrays.fill(decryptedKey, 0)` best-effort heap zero on the BYOK path. (5) `ByokServiceTest#validate_openai_compatible_failure` asserts the upstream response body is NOT in the reason field. (6) Logback scrub filter from Phase 1 covers `apiKey=`, `Bearer`, `x-api-key=` patterns — verify in Plan 07; extend if gaps found. |
| T-2C-cipher-aad-mismatch | Tampering | RefreshTokenCipher reuse | mitigate | `tenantId.toString()` is the AAD passed to encrypt + decrypt — same value both sides per RefreshTokenCipher contract. ByokServiceTest#save + LlmGatewayByokRoutingTest#cipher_decrypt_called_with_tenantId_aad both assert this. If the AAD is wrong (e.g., another tenant's UUID), decrypt throws `AEADBadTagException` and the gateway call fails — zero risk of reading another tenant's key. |
| T-2C-byok-host-leak-in-current | Information Disclosure | ByokCurrentResponse | mitigate | Endpoint URL is parsed via `URI.create(endpoint).getHost()` — only the host (`together.xyz`), not the full URL with paths/queries that could include tenant identifiers in some setups. ByokServiceTest#current_returns_metadata_only asserts. |
| T-2C-09 | Spoofing / SSRF | ByokEndpointValidator + factories | mitigate | H-4 — `ByokEndpointValidator` runs FIRST inside both `OpenAiCompatibleByokFactory.create(...)` and `AnthropicByokFactory.create(...)` BEFORE building the client. Rejects null/blank (falls back to provider default), non-`https`, RFC1918 (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`), loopback (`127/8`, `::1`), and metadata IP (`169.254.169.254`). Provider-specific allow-list: Anthropic must be `*.anthropic.com`; OpenAI-compatible gated to `*.openai.com` + `openrouter.ai` unless operator opt-in flag `zeromail.llm.byok.allow-non-vendor-endpoints=true`. Rejection message is redacted (no endpoint echo). Tests in Task 2 cover metadata-IP / RFC1918 / non-vendor / opt-in. |
</threat_model>

<verification>
> Run all grep / shell acceptance checks via Git Bash (bash.exe), not PowerShell.

- `./gradlew :backend:core:test --tests "LlmGatewayByokRoutingTest" --tests "LlmGateway*"` exits 0
- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 — full module test suite green
- ArchUnit `LlmGatewayBoundaryTest` + `DomainBoundaryArchTests` both pass — Spring AI imports stay confined to `core.llm.gateway.springai`; the new `core.llm.byok` package adds `ByokEndpointValidator` and is reachable from `core.llm.gateway.springai` per Modulith allowedDependencies (verify at execute-phase)
- ByokEndpointValidator unit tests cover metadata-IP / RFC1918 / non-HTTPS / non-vendor-host rejection paths (H-4)
</verification>

<success_criteria>
- BYOK strategy interface + 2 asymmetric impls (OpenAI-compat via `OpenAiApi#mutate()`, Anthropic via `AnthropicChatOptions.builder()`) land per RESEARCH correction.
- `ByokEndpointValidator` (H-4) wired into both factories BEFORE client construction. SSRF allow-list rejects metadata IP / RFC1918 / link-local / loopback / non-HTTPS / non-vendor-host endpoints; opt-in flag `zeromail.llm.byok.allow-non-vendor-endpoints` defaults `false`.
- LlmGatewayImpl branches on BYOK presence; cipher decrypts envelope per call; plaintext zeroed via `Arrays.fill` on exit.
- BYOK call path emits `event=llm_byok_call_started/_succeeded` with no key bytes, no endpoint URL, no model output content.
- All 6 LlmGatewayByokRoutingTest assertions pass.
- ArchUnit `LlmGatewayBoundaryTest` + `DomainBoundaryArchTests` continue to pass.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-05a-SUMMARY.md` documenting:
- Final M4 import paths used for `OpenAiApi.mutate()`, `AnthropicChatOptions.builder().apiKey().baseUrl()`, `OpenAiChatModel.mutate()`, `ChatClient.create()` — verified via Context7 at execution
- Whether RefreshTokenCipher was relocated or referenced from `core.gmail.persistence.crypto` (default per Plan 01: keep in gmail, allowedDependencies edge already declared)
- The exact regex used by `URI.create(endpoint).getHost()` extraction for `endpointHost` (verify on edge cases like trailing-slash, missing scheme)
- Whether the Logback scrub filter needed extending to cover `Bearer ` / `x-api-key=` / `apiKey=` patterns (Plan 07 follow-up if so)
- Pointer for Plan 06: `creditLedger.reserve / settle / release` wrapping is the OUTER seam in chat() — wraps the platform-path call site only (BYOK branch already returns early before the reserve point — by design per LLM-05 BYOK billing skip)
- Pointer for Plan 08: `apps/web/lib/api/schema.d.ts` should be regenerated via `pnpm generate:api` to pick up `/api/llm/byok/{validate,(save),(current)}` types
</output>

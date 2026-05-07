---
phase: 02C-llm-gateway
plan: 05
type: execute
wave: 4
depends_on: [01, 03, 04]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokCurrentResponse.java
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/ByokServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokControllerIntegrationTest.java
autonomous: true
requirements: [LLM-04, LLM-05]
must_haves:
  truths:
    - "LlmGatewayImpl.chat() resolves Optional<TenantByokCredentialsEntity> via byokRepo.findByTenantId(tenantId) BEFORE the platform-path call site; if BYOK row exists, the BYOK factory is used and the platform path is skipped"
    - "POST /api/llm/byok/validate accepts {provider, endpoint?, apiKey} and issues a server-side probe (GET /v1/models for openai-compatible, POST /v1/messages with max_tokens=1 for anthropic); returns {ok, models?, reason?}; the browser NEVER issues the validate call directly"
    - "POST /api/llm/byok saves only after the same payload validates; encrypts the key via existing RefreshTokenCipher (envelope = [key_version:int32 | nonce:12 | ciphertext], tenantId-bound AAD), upserts into tenant_byok_credentials"
    - "GET /api/llm/byok returns provider, optional endpoint host (masked path: just the host, no path/query), saved timestamp; NEVER returns decrypted key bytes"
    - "BYOKChatModelFactory has 2 implementations: OpenAiCompatibleByokFactory uses per-call OpenAiApi#mutate().apiKey().baseUrl() + OpenAiChatModel#mutate(); AnthropicByokFactory uses per-request AnthropicChatOptions.builder().apiKey().baseUrl() (D-A2 asymmetric seam — RESEARCH lines 13-16)"
    - "BYOK plaintext key NEVER persists in DB or logs; lives only in the mutate() builder argument or runtime options for the duration of one HTTP call"
    - "GlobalExceptionHandler maps SafetyViolationException → 500 LLM_SAFETY_VIOLATION; SanitizationException → 500 LLM_SANITIZATION_FAILED; InvalidByokException → 400 LLM_BYOK_INVALID; existing 402 InsufficientCreditsException mapping is preserved"
    - "ErrorCodes constants added: LLM_SAFETY_VIOLATION, LLM_SANITIZATION_FAILED, LLM_BYOK_INVALID, LLM_BYOK_VALIDATE_FAILED"
    - "When tenant has BYOK row, gateway call returns ToolCallResult; ledger is NOT touched (verified by Plan 06 once it lands; Plan 05 stubs the ledger call with a TODO marker that Plan 06 wires)"
    - "Privacy log on BYOK validate: event=byok_validate_attempted tenantId={} provider={} (no endpoint URL, no key bytes); event=byok_validate_succeeded tenantId={} provider={} modelsCount={}; event=byok_validate_failed tenantId={} provider={} reason={opaqueClass}"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/BYOKChatModelFactory.java"
      provides: "Strategy interface — ChatResponse call(byte[] decryptedKey, String endpoint, String model, String userMessage, List<ToolCallback> tools)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/OpenAiCompatibleByokFactory.java"
      provides: "@Component implementing BYOKChatModelFactory via OpenAiApi#mutate() seam (D-A2)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/AnthropicByokFactory.java"
      provides: "@Component implementing BYOKChatModelFactory via AnthropicChatOptions.builder() per-request seam (D-A2 + RESEARCH correction)"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java"
      provides: "@Service — validate(tenantId, payload) probes upstream provider; save(tenantId, payload) encrypts + upserts; current(tenantId) returns metadata-only DTO"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java"
      provides: "RuntimeException — no content payload; mapped to 400"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java"
      provides: "Thin REST controller — delegates everything to ByokService"
      contains: "@RequestMapping(\"/api/llm/byok\")"
    - path: "backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java"
      provides: "4 new constants: LLM_SAFETY_VIOLATION, LLM_SANITIZATION_FAILED, LLM_BYOK_INVALID, LLM_BYOK_VALIDATE_FAILED"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "TenantByokCredentialsRepository.findByTenantId + RefreshTokenCipher.decrypt + BYOKChatModelFactory.call"
      via: "BYOK branch inserted at the // Plan 05 modifies here marker"
      pattern: "byokRepo\\.findByTenantId"
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java"
      to: "RefreshTokenCipher (existing core.gmail.persistence.crypto bean)"
      via: "encrypt(plaintextKey, tenantId) on save; ciphertext stored as encrypted_key BYTEA"
      pattern: "refreshTokenCipher\\.encrypt"
    - from: "backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java"
      to: "backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java"
      via: "InvalidByokException + SafetyViolationException + SanitizationException mappings"
      pattern: "@ExceptionHandler"
---

<objective>
Wave 4 BYOK persistence + factories + REST surface. Add the BYOK branch to `LlmGatewayImpl` so per-tenant credentials override the platform path; wire two `BYOKChatModelFactory` implementations using the asymmetric M4 seams (OpenAI-compatible via `OpenAiApi#mutate()`, Anthropic via `AnthropicChatOptions.builder()` per-request — RESEARCH correction); land `ByokService` (validate / save / current), `ByokController` (3 endpoints), 5 DTOs, 4 ErrorCodes constants, and 3 GlobalExceptionHandler mappings (SafetyViolationException, SanitizationException, InvalidByokException — InsufficientCreditsException already mapped from Phase 2B).

Purpose: this is LLM-04 + LLM-05. After this plan, a tenant with a BYOK row gets routed via their own key + endpoint with zero credit ledger touch. Plaintext keys never persist in DB or logs — encrypted-at-rest via the existing `RefreshTokenCipher` envelope, decrypted only into the per-call `mutate()` argument (D-A5).

Output: 3 production classes (1 interface + 2 factory impls) + ByokService + InvalidByokException + LlmGatewayImpl modified at the // Plan 05 seam + 5 DTOs + ByokController + GlobalExceptionHandler additions + ErrorCodes + 3 test files.
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
    - Test 6 (LlmGatewayByokRoutingTest#multitenant_no_key_leak): 100 concurrent virtual-thread calls, 50 tenants with BYOK row + 50 without; mock factories echo bound tenantId in args; assert no cross-tenant leak (mirror of Plan 03 leak-test pattern).
  </behavior>
  <action>
    1. **Create `backend/core/src/main/java/com/zeromail/core/llm/model/InvalidByokException.java`** — mirror SafetyViolationException shape; no message constructor; carries no content. Two-line class, public no-arg ctor only. Used by ByokService when validate fails or save is attempted before validate.

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
    - `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayMultiTenantLeakTest"` exits 0 (Plan 03/04 tests still pass).
    - `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest"` exits 0 (Spring AI imports still confined).
  </acceptance_criteria>
  <done>
    BYOK strategy interface + 2 asymmetric impls land. LlmGatewayImpl branches on TenantByokCredentialsEntity presence; cipher decrypts the envelope per call; plaintext key never logged or persisted; `Arrays.fill` best-effort zero on the decrypted byte[]. Tests prove BYOK path skips platform ChatClient and that no key bytes leak under 100-tenant concurrency.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: ByokService + ByokController + 5 DTOs + GlobalExceptionHandler mappings + ErrorCodes + integration test</name>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (controller analog — PATTERNS.md "ByokController.java")
    - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentRequest.java (DTO record analog)
    - backend/api/src/main/java/com/zeromail/api/dto/billing/TopupIntentResponse.java (DTO record analog)
    - backend/api/src/main/java/com/zeromail/api/dto/billing/BillingBalanceResponse.java (current-config DTO analog — PATTERNS.md "ByokCurrentResponse.java")
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java (lines 105-148 — privacy-safe exception logging + 402 mapping for InsufficientCreditsException to PRESERVE)
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java (existing constants — append pattern)
    - backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java (service analog with outbound HTTP call shape — PATTERNS.md "ByokService.java")
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java (controller integration test analog with WireMock-like upstream stub)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-D1 through D-D6 — though those are frontend; D-A5 cipher reuse; D-I2 byok logs)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "ByokController.java" + "GlobalExceptionHandler.java (modify)" + S-2 thin-controller)
    - .planning/phases/02C-llm-gateway/02C-UI-SPEC.md (Section "Copywriting Contract" + "i18n Keys" — required errors.llm.* keys for Plan 08 to consume)
  </read_first>
  <behavior>
    - Test 1 (ByokServiceTest#validate_openai_compatible_calls_v1_models): WireMock stub at `https://together.xyz/v1/models` returning 200 with `{data: [{id: "model-a"}, {id: "model-b"}]}`; ByokService.validate(tenantId, payload) returns `ByokValidateResponse(ok=true, models=["model-a","model-b"], reason=null)`.
    - Test 2 (ByokServiceTest#validate_openai_compatible_failure): WireMock returns 401 → returns `ByokValidateResponse(ok=false, models=null, reason="upstream_rejected")` — reason is opaque (no upstream body bytes).
    - Test 3 (ByokServiceTest#validate_anthropic_calls_v1_messages): WireMock at `/v1/messages` POST with `max_tokens=1` body returning 200 → returns `ByokValidateResponse(ok=true, models=null, reason=null)`.
    - Test 4 (ByokServiceTest#save_encrypts_key_via_refresh_token_cipher): given a valid payload, save() stores a row whose encrypted_key BYTEA is NOT the plaintext key bytes; manual decrypt via RefreshTokenCipher returns the original plaintext.
    - Test 5 (ByokServiceTest#save_upserts_existing_row): tenant already has BYOK row → second save() updates encrypted_key + key_version + provider, does NOT create a duplicate (UNIQUE constraint enforced from Plan 01).
    - Test 6 (ByokServiceTest#current_returns_metadata_only): tenant with BYOK row → ByokCurrentResponse contains provider, optional endpointHost (e.g., `together.xyz` extracted from URL — no path/query), savedAt; encrypted_key bytes NEVER returned.
    - Test 7 (ByokControllerIntegrationTest#post_validate_returns_200_for_valid_key): full HTTP test via RestClient + LocalServerPort + TenantContext-binding test filter; asserts 200 + body shape.
    - Test 8 (ByokControllerIntegrationTest#post_save_returns_400_when_invalid_byok_exception_thrown): mock service to throw InvalidByokException → assert 400 + body code=`error.llm.byok.invalid`.
    - Test 9 (ByokControllerIntegrationTest#safety_violation_handler_returns_500): synthetic SafetyViolationException through a stub controller → assert 500 + body code=`error.llm.safety_violation`.
    - Test 10 (ByokControllerIntegrationTest#sanitization_failed_handler_returns_500): synthetic SanitizationException → 500 + code=`error.llm.sanitization_failed`.
    - Test 11 (ByokControllerIntegrationTest#insufficient_credits_still_returns_402): existing Phase 2B mapping preserved.
  </behavior>
  <action>
    1. **Create 5 DTOs** in `backend/api/src/main/java/com/zeromail/api/dto/llm/`:
       - `ByokValidateRequest(BYOKProvider provider, String endpoint, String apiKey)` — with `@NotNull` on provider + apiKey; endpoint nullable. JSR-380 validation.
       - `ByokValidateResponse(boolean ok, List<String> models, String reason)` — record with defensive copy on models; reason is opaque (e.g., `"upstream_rejected"`, `"connection_failed"`, `"timeout"`) — never raw upstream body.
       - `ByokSaveRequest(BYOKProvider provider, String endpoint, String apiKey)` — same shape as ByokValidateRequest; could share, but UI-SPEC says save and validate are separate endpoints with potentially different validation.
       - `ByokSaveResponse(boolean ok, Instant savedAt)` — minimal.
       - `ByokCurrentResponse(BYOKProvider provider, String endpointHost, Instant savedAt)` — endpointHost is extracted via `URI.create(endpoint).getHost()`; null if no BYOK row (controller returns null body or 204).
       
       All as Java records per CLAUDE.md Conventions §2.

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java`** — `@Service`. Methods:
       - `validate(UUID tenantId, ByokValidateRequest payload)` — Issues outbound HTTP via Spring `RestClient` (see `BillingTopupService` for analog). Branch on provider:
         - OPENAI_COMPATIBLE → `GET ${payload.endpoint()}/v1/models` with `Authorization: Bearer ${payload.apiKey()}`. On 200 with valid `{data: [...]}` body, parse `id` fields → return `ByokValidateResponse(true, modelIds, null)`. On non-2xx or parse failure → `ByokValidateResponse(false, null, "upstream_rejected" | "connection_failed")`.
         - ANTHROPIC → `POST ${endpoint or "https://api.anthropic.com"}/v1/messages` with `x-api-key`, `anthropic-version: 2023-06-01`, body `{model: "claude-3-haiku-20240307", max_tokens: 1, messages: [{role: "user", content: "."}]}`. On 200 → ok=true.
         - All exceptions caught and translated to opaque reason — NEVER include upstream response body bytes in reason.
         - Privacy logs: `event=byok_validate_attempted tenantId={} provider={}`, `event=byok_validate_succeeded tenantId={} provider={} modelsCount={}`, `event=byok_validate_failed tenantId={} provider={} reason={}` (where reason is the opaque tag, NOT exception message).
       - `save(UUID tenantId, ByokSaveRequest payload)` — encrypts `payload.apiKey().getBytes(UTF_8)` via `refreshTokenCipher.encrypt(plaintext, tenantId.toString())`; upserts entity (use repository: find existing → mutate via `replaceKey(envelope, keyVersion)` or save new). Returns `ByokSaveResponse(true, Instant.now())`. Throws `InvalidByokException` if validation has not happened (project policy: each save must be preceded by validate, but server cannot verify that across requests; UI-SPEC says client enforces this. Server-side, save accepts unconditionally — relies on UI). **Decision: server-side accepts save without re-validate** (UI-SPEC line 217 + Validation State Machine — client gates the save button; server-side re-validate would double the cost). Document in service Javadoc.
       - `current(UUID tenantId)` — returns `Optional<ByokCurrentResponse>`; null if no row. Extracts host from endpoint via `URI.create(...).getHost()`. Never decrypts the key (no need — current only reports metadata).
       
       **`@Transactional`** on save (mutation) and current (read consistency). Validate is non-transactional (no DB writes; outbound HTTP only).

    3. **Create `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java`** — `@RestController @RequestMapping("/api/llm/byok") @Tag(name="llm-byok")`. Per PATTERNS.md verbatim shape — thin controller, 3 endpoints (`POST /validate`, `POST` (save), `GET` (current)), `TenantContext.currentOrThrow()` per call, `byokService.{validate,save,currentForTenant}(...)`. NO `@Transactional`, NO repository injection.

    4. **Modify `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`** — append 3 new `@ExceptionHandler` methods after the existing InsufficientCreditsException one (line ~138). PATTERNS.md "GlobalExceptionHandler.java (modify)" gives the pattern verbatim. Privacy invariant: `log.error("event=... reason={}", exception.getClass().getSimpleName())` — NEVER pass the exception object itself, NEVER pass `.getMessage()` (could leak content).
       - `SafetyViolationException` → 500, code `LLM_SAFETY_VIOLATION`
       - `SanitizationException` → 500, code `LLM_SANITIZATION_FAILED`
       - `InvalidByokException` → 400, code `LLM_BYOK_INVALID`
       
       Preserve the existing `InsufficientCreditsException → 402` mapping verbatim (Plan 06 will rely on it).

    5. **Modify `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`** — append 4 constants:
       ```java
       public static final String LLM_SAFETY_VIOLATION = "error.llm.safety_violation";
       public static final String LLM_SANITIZATION_FAILED = "error.llm.sanitization_failed";
       public static final String LLM_BYOK_INVALID = "error.llm.byok.invalid";
       public static final String LLM_BYOK_VALIDATE_FAILED = "error.llm.byok.validate_failed";
       ```

    6. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/ByokServiceTest.java`** — Tests 1–6 above. Uses WireMock (or Spring's `MockRestServiceServer`) for outbound HTTP stubs. Test 5 verifies UNIQUE constraint upsert behavior. Test 6 asserts `ByokCurrentResponse.endpointHost()` = `"together.xyz"` for input endpoint `"https://together.xyz/v1"` (host extraction).

    7. **Create `backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokControllerIntegrationTest.java`** — RestClient + LocalServerPort + TenantContext-binding test pattern (per CLAUDE.md STATE.md Phase 1.5 OAuth pattern: "Use RestClient + LocalServerPort (not MockMvc.webAppContextSetup) for backend tests requiring TenantContext ScopedValue"). Tests 7–11 above. Test 11 specifically asserts the existing 402 mapping for InsufficientCreditsException is unaffected by the new mappings.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "ByokServiceTest" :backend:api:test --tests "ByokControllerIntegrationTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 5 DTOs exist under `backend/api/src/main/java/com/zeromail/api/dto/llm/`.
    - `grep -c 'public record ' backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateRequest.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateResponse.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveRequest.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java backend/api/src/main/java/com/zeromail/api/dto/llm/ByokCurrentResponse.java` returns `5`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` exists; `grep -c 'refreshTokenCipher.encrypt' backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` returns `>= 1`.
    - `grep -E 'log\.(info|warn|error|debug).*payload\.apiKey\(\)|log\.(info|warn|error|debug).*payload\.endpoint\(\)' backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java` returns no matches (no key/endpoint URL in logs).
    - File `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` exists; `grep -c '@RequestMapping.*api/llm/byok' backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` returns `1`; `grep -c '@PostMapping.*validate\|@PostMapping\b\|@GetMapping' backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` returns `>= 3`.
    - `grep -c '@ExceptionHandler(SafetyViolationException.class)\|@ExceptionHandler(SanitizationException.class)\|@ExceptionHandler(InvalidByokException.class)' backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` returns `3`.
    - `grep -c '@ExceptionHandler(InsufficientCreditsException.class)' backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` returns `1` (preserved).
    - `grep -c 'LLM_SAFETY_VIOLATION\|LLM_SANITIZATION_FAILED\|LLM_BYOK_INVALID\|LLM_BYOK_VALIDATE_FAILED' backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` returns `4`.
    - `./gradlew :backend:core:test --tests "ByokServiceTest"` exits 0.
    - `./gradlew :backend:api:test --tests "ByokControllerIntegrationTest"` exits 0.
    - `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 (full suite).
    - `pnpm -C apps/web generate:api` (if executor has Node) regenerates `apps/web/lib/api/schema.d.ts` with new BYOK endpoints — defer to Plan 08 if Node not available; document expected paths in summary.
  </acceptance_criteria>
  <done>
    BYOK service + controller + 5 DTOs + 4 ErrorCodes constants + 3 GlobalExceptionHandler mappings land. Server-side validate flow probes upstream provider with no body leakage; save encrypts via existing RefreshTokenCipher; current returns metadata only. Frontend (Plan 08) can now consume the typed schema.
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
| T-2C-validate-flow-amplification | DoS | POST /api/llm/byok/validate | accept | Endpoint is authenticated (TenantContext required) and rate-limited at the existing API filter chain. Upstream provider (OpenAI / Anthropic) imposes their own rate limits. No additional gateway-side rate limit in v1; revisit in Phase 5 if needed. |
| T-2C-cross-tenant-byok-row-read | Information Disclosure | TenantByokCredentialsRepository.findByTenantId | mitigate | Repository inherits `@TenantId` filter from `AbstractTenantOwnedEntity` (Phase 1.2.1) — Hibernate auto-filters by `tenant_id` on every query. ByokServiceTest does NOT explicitly verify this (covered by FND-05 multi-tenant leak test pattern); LlmGatewayByokRoutingTest#multitenant_no_key_leak provides additional defense-in-depth on the gateway path. |
| T-2C-globalexceptionhandler-content-leak | Information Disclosure | New @ExceptionHandler methods | mitigate | All 3 new mappings follow the existing Phase 2B / Phase 1.1 pattern: `log.error("event=... reason={}", exception.getClass().getSimpleName())`. ByokControllerIntegrationTest#safety_violation_handler_returns_500 asserts the response body code is `error.llm.safety_violation` (no rejected action name, no model output). |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "*Byok*" --tests "LlmGateway*"` exits 0
- `./gradlew :backend:api:test --tests "ByokControllerIntegrationTest"` exits 0 (RestClient + LocalServerPort pattern verified)
- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 — full module test suite green
- ArchUnit `LlmGatewayBoundaryTest` + `DomainBoundaryArchTests` both pass — Spring AI imports stay confined; new ByokController + DTOs don't introduce cross-module repository dependencies
- The existing 402 mapping for InsufficientCreditsException is unchanged (test asserts)
</verification>

<success_criteria>
- BYOK strategy interface + 2 asymmetric impls (OpenAI-compat via `OpenAiApi#mutate()`, Anthropic via `AnthropicChatOptions.builder()`) land per RESEARCH correction.
- LlmGatewayImpl branches on BYOK presence; cipher decrypts envelope per call; plaintext zeroed via `Arrays.fill` on exit.
- 3 endpoints (`POST /validate`, `POST` save, `GET` current) exposed under `/api/llm/byok`; thin controller delegates to ByokService.
- 4 ErrorCodes + 3 GlobalExceptionHandler mappings preserve privacy invariant (logger gets class name only).
- BYOK call path emits `event=llm_byok_call_started/_succeeded` with no key bytes, no endpoint URL, no model output content.
- All 11 LlmGatewayByokRoutingTest + ByokServiceTest + ByokControllerIntegrationTest assertions pass.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-05-SUMMARY.md` documenting:
- Final M4 import paths used for `OpenAiApi.mutate()`, `AnthropicChatOptions.builder().apiKey().baseUrl()`, `OpenAiChatModel.mutate()`, `ChatClient.create()` — verified via Context7 at execution
- Whether RefreshTokenCipher was relocated or referenced from `core.gmail.persistence.crypto` (default per Plan 01: keep in gmail, allowedDependencies edge already declared)
- The exact regex used by `URI.create(endpoint).getHost()` extraction for `endpointHost` (verify on edge cases like trailing-slash, missing scheme)
- Whether the Logback scrub filter needed extending to cover `Bearer ` / `x-api-key=` / `apiKey=` patterns (Plan 07 follow-up if so)
- Pointer for Plan 06: `creditLedger.reserve / settle / release` wrapping is the OUTER seam in chat() — wraps the platform-path call site only (BYOK branch already returns early before the reserve point — by design per LLM-05 BYOK billing skip)
- Pointer for Plan 08: `apps/web/lib/api/schema.d.ts` should be regenerated via `pnpm generate:api` to pick up `/api/llm/byok/{validate,(save),(current)}` types
</output>

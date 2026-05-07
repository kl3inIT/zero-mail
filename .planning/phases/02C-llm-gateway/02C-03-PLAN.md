---
phase: 02C-llm-gateway
plan: 03
type: execute
wave: 2
depends_on: [01, 02]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/Action.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ZeroMailLlmProperties.java
  - backend/api/src/main/resources/application.yml
  - backend/worker/src/main/resources/application.yml
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayWave0Test.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayPlatformPathTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayMultiTenantLeakTest.java
autonomous: true
requirements: [LLM-01, LLM-07, LLM-08, LLM-09]
must_haves:
  truths:
    - "All LLM traffic flows through LlmGateway interface; ArchUnit test from Plan 01 still passes after impl lands"
    - "LlmGatewayImpl.chat(callSite, rawHtml, tools) calls SanitizationPipeline first, then issues a Spring AI ChatClient call against the platform path with per-call-site model pin"
    - "Platform path uses singleton ChatClient + dynamic PlatformApiKey reading TenantContext (D-A1) — resolved at HTTP send time, not bean construction"
    - "ZEROMAIL_LLM_PLATFORM_API_KEY env var fail-fast at boot via :? syntax in both api/application.yml and worker/application.yml"
    - "spring.ai.chat.client.observations.log-prompt: false AND log-completion: false pinned in both api/application.yml and worker/application.yml (D-I5) — no prompt or completion text in observation spans"
    - "Privacy log lines emit event=llm_call_started/_succeeded/_failed with tenantId + callSite + provider + model + latencyMs + promptTokens + completionTokens + stopReason + truncated; never content (D-I1, S-1)"
    - "Multi-tenant leak integration test: 100 concurrent virtual-thread calls from N tenants with mock ChatModel echoing tenantId — every result correlates to its own tenant"
    - "Plan 01 Wave 0 LlmGatewayWave0Test @Disabled removed and now passes"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java"
      provides: "Public interface — single chokepoint contract for Phase 3/4 callers"
      exports: ["chat(CallSite, String, List<ToolCallback>) -> ToolCallResult", "driftCheck(String) -> ToolCallResult"]
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      provides: "Package-private @Service implementation; sanitize → ChatClient call → response parse → ToolCallResult"
      contains: "implements LlmGateway"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/model/Action.java"
      provides: "Allow-listed action enum {LABEL, ARCHIVE, SAVE_DRAFT}; IdentifiedEnum + fromId fail-loud + functionName accessor"
      contains: "implements IdentifiedEnum"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java"
      provides: "Record (Action, Map<String,Object> args) with defensive-copy compact ctor"
      contains: "Map.copyOf"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java"
      provides: "Spring AI ApiKey impl; getValue() returns config-resolved api-key per HTTP send"
      contains: "implements ApiKey"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java"
      provides: "@Configuration that builds the singleton OpenAI-compatible ChatClient pointing at OpenRouter; uses PlatformApiKey + base-url + ChatClient.Builder.defaultOptions(temperature=0.0)"
      contains: "@Configuration"
    - path: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ZeroMailLlmProperties.java"
      provides: "@ConfigurationProperties('zero-mail.llm.platform') record (provider, baseUrl, apiKey, compileModel, driftModel, triageModel)"
      contains: "@ConfigurationProperties"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java"
      via: "constructor injection + sanitize(rawHtml) call as first step in chat(...)"
      pattern: "sanitizationPipeline\\.sanitize"
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java"
      via: "constructor injection of platformChatClient bean"
      pattern: "ChatClient platformChatClient"
    - from: "backend/api/src/main/resources/application.yml + backend/worker/src/main/resources/application.yml"
      to: "ZEROMAIL_LLM_PLATFORM_API_KEY env var"
      via: "${ZEROMAIL_LLM_PLATFORM_API_KEY:?...} fail-fast"
      pattern: "ZEROMAIL_LLM_PLATFORM_API_KEY:\\?"
    - from: "backend/api/src/main/resources/application.yml + backend/worker/src/main/resources/application.yml"
      to: "Spring AI observation toggles"
      via: "spring.ai.chat.client.observations.log-prompt: false + .log-completion: false"
      pattern: "log-prompt: false"
---

<objective>
Wave 2 gateway core. Land the public `LlmGateway` interface, the package-private `LlmGatewayImpl` skeleton (sanitize → ChatClient call → minimal tool-call parse → return), the platform-path Spring AI wiring (`PlatformApiKey`, `PlatformChatClientConfig`, `ZeroMailLlmProperties`), the `Action` enum + `ToolCallResult` record (consumed by Plan 04 for the validator), and the application.yml configuration with `ZEROMAIL_LLM_PLATFORM_API_KEY:?` fail-fast + Spring AI observation `log-prompt/log-completion: false` defensive pins.

Purpose: this is the LLM-01 single-gateway abstraction landing point. After this plan: any caller (drift job, future Phase 3/4) can call `LlmGateway.chat(callSite, content, tools)` and get a `ToolCallResult` back. Plans 04/05/06 will modify `LlmGatewayImpl` to add tool-call validation (04), BYOK branch (05), and credit ledger wiring (06). To keep the public contract stable across those edits, this plan locks the interface signature and the call site sequence; later plans only insert logic at marked seams.

Output: 7 production files (interface + impl + 2 model records + 3 springai wiring) + 2 application.yml updates + 3 test files (Wave 0 turned green + happy-path platform test + multi-tenant leak test).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-SPEC.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-RESEARCH.md
@.planning/phases/02C-llm-gateway/02C-AI-SPEC.md
@backend/api/src/main/resources/application.yml
@backend/worker/src/main/resources/application.yml
@backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java
@backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
@backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java

<interfaces>
<!-- Spring AI 2.0.0-M4 — verify via Context7 `/spring-projects/spring-ai` if any uncertainty. -->

`org.springframework.ai.openai.api.OpenAiApi` — primary OpenAI-compatible client.
- Builder: `OpenAiApi.builder().baseUrl(String).apiKey(ApiKey).build()`
- `ApiKey` interface: `interface ApiKey { String getValue(); }` — implement to read TenantContext per call.

`org.springframework.ai.openai.OpenAiChatModel` — chat model wrapping OpenAiApi.
- Builder: `OpenAiChatModel.builder().openAiApi(OpenAiApi).defaultOptions(OpenAiChatOptions).build()`

`org.springframework.ai.openai.OpenAiChatOptions` — per-call options.
- Builder: `OpenAiChatOptions.builder().model(String).temperature(Double).toolChoice(String).build()`
- For Plan 03 `defaultOptions`: `temperature(0.0)` only. Tool callbacks + toolChoice="required" added in Plan 04 per call.

`org.springframework.ai.chat.client.ChatClient` — high-level fluent client.
- `ChatClient.create(ChatModel)` returns a builder.
- Per-call: `chatClient.prompt().system(String).user(String).options(OpenAiChatOptions).call().chatResponse()` returns `ChatResponse`.

`org.springframework.ai.chat.model.ChatResponse` — model output.
- `chatResponse.getResults()` returns `List<Generation>`.
- `generation.getOutput()` returns `AssistantMessage` with `.getText()` and `.getToolCalls(): List<ToolCall>`.

`org.springframework.ai.tool.ToolCallback` — function/tool registration.
- For Plan 03: tools list is passed THROUGH (not yet enforced); Plan 04 adds toolChoice="required" + internalToolExecutionEnabled(false).

<!-- From Plan 02 (already on disk) -->
`com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline#sanitize(String) → SanitizationContext`

<!-- From Plan 01 (already on disk) -->
`com.zeromail.core.llm.persistence.TenantByokCredentialsRepository#findByTenantId(UUID) → Optional<TenantByokCredentialsEntity>`
`com.zeromail.core.llm.model.BYOKProvider` enum {ANTHROPIC, OPENAI_COMPATIBLE}

<!-- From existing repo -->
`com.zeromail.core.tenant.TenantContext.currentOrThrow() → String` (returns tenantId UUID-as-String).
`com.zeromail.core.tenant.TenantContext.TENANT` — `ScopedValue<String>` for `ScopedValue.where(...)` binding.
`com.zeromail.core.billing.model.CallSite` enum {TRIAGE("triage"), DRAFT("draft"), PREVIEW("preview")}.

<!-- application.yml current shape (api side, lines 67-86 per RESEARCH) -->
- `zeromail.refresh-token-key-base64: ${REFRESH_TOKEN_KEY_BASE64:?...}` — exact `:?` shape to mirror.
- `zeromail.pubsub.*` — already present from Phase 2A.
- `zeromail.billing.sepay.webhook-api-key: ${SEPAY_WEBHOOK_API_KEY}` — alternative bare-placeholder shape (do NOT mirror; use `:?` instead per CONTEXT D-G1).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Action enum + ToolCallResult record + LlmGateway interface + ZeroMailLlmProperties + application.yml updates</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java (IdentifiedEnum + functionName analog — PATTERNS.md "Action.java and BYOKProvider.java")
    - backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java (record with defensive-copy ctor — PATTERNS.md "ToolCallResult.java, SanitizationContext.java")
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java (interface + Javadoc cross-phase contract analog — PATTERNS.md "LlmGateway.java")
    - backend/api/src/main/resources/application.yml (line 67-86 — existing :? pattern for REFRESH_TOKEN_KEY_BASE64)
    - backend/worker/src/main/resources/application.yml (current shape — must mirror api yml additions)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-A1 platform-key path, D-E1 model pin, D-I5 observation pins)
    - .planning/phases/02C-llm-gateway/02C-RESEARCH.md (lines 12-17 — three corrections + Anthropic asymmetry; line 87 — Logback scrub filter status; lines 90-99 — Claude's Discretion notes including Action.id() lower-snake recommendation)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "LlmGateway.java" + "Action.java and BYOKProvider.java" + "application.yml")
  </read_first>
  <behavior>
    - Test 1 (ActionEnumTest#fromId_returns_correct_enum_for_label_archive_save_draft): `Action.fromId("label") == Action.LABEL`; same for "archive"/SAVE_DRAFT pair `("save_draft", SAVE_DRAFT)`.
    - Test 2 (ActionEnumTest#fromId_throws_on_unknown_id): `Action.fromId("send")` throws `NoSuchElementException("Unknown Action id: send")`.
    - Test 3 (ActionEnumTest#functionName_returns_lower_snake_case): `Action.LABEL.functionName() == "label"`; `Action.SAVE_DRAFT.functionName() == "save_draft"`.
    - Test 4 (ToolCallResultTest#defensive_copies_args_map): pass mutable map → mutate it → ToolCallResult.args() does not reflect mutation.
    - Test 5 (ApplicationYmlBootTest — at integration level via Spring boot test): if `ZEROMAIL_LLM_PLATFORM_API_KEY` is unset, application context fails to start with a clear message containing `ZEROMAIL_LLM_PLATFORM_API_KEY` (verify via `@SpringBootTest` with `@DynamicPropertySource` removing the env var temporarily — or simpler: assert on the rendered yml string).
    - Test 6 (ZeroMailLlmPropertiesTest): bind a test ApplicationContext with `zero-mail.llm.platform.provider: openai-compatible`, `compile-model: openai/gpt-4o-mini`, etc.; assert `props.compileModel().equals("openai/gpt-4o-mini")`.
  </behavior>
  <action>
    1. **Create `backend/core/src/main/java/com/zeromail/core/llm/model/Action.java`** per PATTERNS.md verbatim shape, with the recommended `id() == name()` invariant AND a separate `functionName()` accessor returning lower-snake (RESEARCH Claude's Discretion line 93). 3 members: `LABEL("label")`, `ARCHIVE("archive")`, `SAVE_DRAFT("save_draft")`. `implements IdentifiedEnum`. Two static factories: `fromId(String) → Action` (throws `NoSuchElementException`) and `fromFunctionName(String) → Action` (throws same).

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java`** per PATTERNS.md verbatim:
       ```java
       public record ToolCallResult(Action action, Map<String, Object> args) {
           public ToolCallResult {
               java.util.Objects.requireNonNull(action, "action");
               args = args == null ? Map.of() : Map.copyOf(args);
           }
       }
       ```

    3. **Create `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java`** — public interface with full Javadoc (mirror `CreditLedger.java` Javadoc style + cross-phase contract). Two methods:
       ```java
       /**
        * Single chokepoint for all LLM traffic in Zero Mail. Phase 3 (Rules Engine) and
        * Phase 4 (Triage) import this interface verbatim.
        *
        * <p><b>Cross-phase contract.</b> chat(callSite, rawHtml, tools) sanitizes input
        * (Plan 02 pipeline), enforces tool-call allow-list (Plan 04 ActionValidator),
        * routes via BYOK or platform path (Plan 05), and reserves/settles/releases credits
        * via Phase 2B CreditLedger on the platform path (Plan 06).
        *
        * <p><b>Privacy invariant.</b> Implementations MUST NOT log, persist, or expose
        * the rawHtml content, prompt text, or completion text. Observation spans carry
        * metadata only (provider, model, tokenCount, latencyMs, stopReason).
        */
       public interface LlmGateway {
           ToolCallResult chat(CallSite callSite, String rawHtml, List<ToolCallback> tools);
           ToolCallResult driftCheck(String prompt);  // D-E3 — bypasses ledger; pinned to driftModel
       }
       ```
       Note: `ToolCallback` is `org.springframework.ai.tool.ToolCallback`. ArchUnit `LlmGatewayBoundaryTest` (Plan 01) covers this — `core.llm.service` is OUTSIDE `core.llm.gateway.springai`, so importing `ToolCallback` here would fail. **Decision per RESEARCH lines 12-17 + AI-SPEC framework section**: define a project-local `LlmTool` shape OR keep `ToolCallback` and EXEMPT `core.llm.service` from the ArchUnit rule.

       **Resolution**: Use the second option — modify Plan 01's `LlmGatewayBoundaryTest#spring_ai_only_in_gateway_springai` to allow `core.llm.service` to import `org.springframework.ai.tool.ToolCallback` ONLY (not the broader `org.springframework.ai..` packages). Restate the rule as: outside `core.llm.gateway.springai`, the only Spring AI class importable is `org.springframework.ai.tool.ToolCallback`. Update the rule's `.because()` clause to reflect this exemption.

    4. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ZeroMailLlmProperties.java`** as a record with `@ConfigurationProperties("zero-mail.llm.platform")`:
       ```java
       @ConfigurationProperties("zero-mail.llm.platform")
       public record ZeroMailLlmProperties(
               BYOKProvider provider,
               String baseUrl,
               String apiKey,
               String compileModel,
               String driftModel,
               String triageModel) {

           // Defensive defaults via canonical ctor — null check on apiKey since fail-fast is at yml :?
           public ZeroMailLlmProperties {
               java.util.Objects.requireNonNull(apiKey, "zero-mail.llm.platform.api-key");
               java.util.Objects.requireNonNull(provider, "zero-mail.llm.platform.provider");
           }

           public Map<CallSite, String> modelByCallSite() {
               return Map.of(
                   CallSite.TRIAGE, triageModel,
                   CallSite.DRAFT, compileModel,    // SPEC has no previewModel; compile-model serves rule-compile + preview both (D-E1)
                   CallSite.PREVIEW, compileModel
               );
           }
       }
       ```
       Wire as a Spring bean via `@EnableConfigurationProperties(ZeroMailLlmProperties.class)` on `PlatformChatClientConfig` (Task 2).

    5. **Modify `backend/api/src/main/resources/application.yml` — append after existing `zeromail:` block** (mirror exact `:?` shape from line 70-74):
       ```yaml
       # Phase 02C — LLM Gateway
       zero-mail:
         llm:
           platform:
             provider: openai-compatible
             base-url: https://openrouter.ai/api/v1
             api-key: ${ZEROMAIL_LLM_PLATFORM_API_KEY:?ZEROMAIL_LLM_PLATFORM_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
             compile-model: openai/gpt-4o-mini
             drift-model: openai/gpt-4o-mini
             triage-model: openai/gpt-4o-mini

       spring:
         ai:
           chat:
             client:
               observations:
                 log-prompt: false
                 log-completion: false
             observations:
               log-prompt: false
               log-completion: false
       ```

    6. **Modify `backend/worker/src/main/resources/application.yml`** — append the SAME `zero-mail.llm.platform.*` block AND the same `spring.ai.chat.*.observations` block. Worker also needs the platform key for `DriftDetectionJob` (Plan 07). Additionally append:
       ```yaml
       zero-mail:
         llm:
           drift:
             enabled: ${ZEROMAIL_LLM_DRIFT_ENABLED:false}
       ```

    7. **Update Plan 01's `LlmGatewayBoundaryTest#spring_ai_only_in_gateway_springai`** to allow `org.springframework.ai.tool.ToolCallback` in `core.llm.service`:
       ```java
       @Test
       void spring_ai_only_in_gateway_springai_except_tool_callback() {
           noClasses()
                   .that().resideOutsideOfPackage("..core.llm.gateway.springai..")
                       .and().resideOutsideOfPackage("..core.llm.service..")
                   .should().dependOnClassesThat()
                       .resideInAnyPackage("org.springframework.ai..")
                   .check(/* importedClasses */);

           // Separately: core.llm.service may ONLY import ToolCallback, not the broader package
           noClasses()
                   .that().resideInAPackage("..core.llm.service..")
                   .should().dependOnClassesThat()
                       .resideInAnyPackage("org.springframework.ai..")
                       .and().haveNameNotMatching("org\\.springframework\\.ai\\.tool\\.ToolCallback")
                   .check(/* importedClasses */);
       }
       ```
       The exemption is documented in the rule's `.because()` clause: "LLM-01: gateway.springai owns Spring AI imports; LlmGateway public interface needs ToolCallback in its method signature so Phase 3/4 callers can compose tools — exemption is single-class wide."
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "ActionEnumTest" --tests "ToolCallResultTest" --tests "ZeroMailLlmPropertiesTest" --tests "LlmGatewayBoundaryTest"</automated>
  </verify>
  <acceptance_criteria>
    - File `backend/core/src/main/java/com/zeromail/core/llm/model/Action.java` exists; `grep -c 'LABEL\|ARCHIVE\|SAVE_DRAFT' backend/core/src/main/java/com/zeromail/core/llm/model/Action.java` returns `>= 3`; `grep -c 'NoSuchElementException' backend/core/src/main/java/com/zeromail/core/llm/model/Action.java` returns `>= 1`; `grep -c 'functionName' backend/core/src/main/java/com/zeromail/core/llm/model/Action.java` returns `>= 1`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java` exists; `grep -c 'Map.copyOf' backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java` returns `>= 1`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` exists; `grep -c 'ToolCallResult chat' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` returns `>= 1`; `grep -c 'driftCheck' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` returns `>= 1`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ZeroMailLlmProperties.java` exists; `grep -c '@ConfigurationProperties' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/ZeroMailLlmProperties.java` returns `>= 1`.
    - `grep -c 'ZEROMAIL_LLM_PLATFORM_API_KEY:?' backend/api/src/main/resources/application.yml` returns `1`.
    - `grep -c 'ZEROMAIL_LLM_PLATFORM_API_KEY:?' backend/worker/src/main/resources/application.yml` returns `1`.
    - `grep -c 'log-prompt: false' backend/api/src/main/resources/application.yml` returns `>= 2` (chat.client.observations + chat.observations both set).
    - `grep -c 'log-completion: false' backend/api/src/main/resources/application.yml` returns `>= 2`.
    - `grep -c 'log-prompt: false' backend/worker/src/main/resources/application.yml` returns `>= 2`.
    - `grep -c 'ZEROMAIL_LLM_DRIFT_ENABLED' backend/worker/src/main/resources/application.yml` returns `1`.
    - `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest"` exits 0 (rule updated to allow ToolCallback in service package).
    - `./gradlew :backend:api:compileJava :backend:worker:compileJava :backend:core:compileJava` exits 0.
  </acceptance_criteria>
  <done>
    Action enum + ToolCallResult + LlmGateway interface + properties record exist. application.yml fail-fast + observation pins are in place across api + worker. ArchUnit rule exempts the single ToolCallback import in `core.llm.service`. Plan 04 can now wire ActionValidator + tool-call enforcement against the locked interface.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: PlatformApiKey + PlatformChatClientConfig + LlmGatewayImpl skeleton + happy-path test + multi-tenant leak test + Wave 0 turned green</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java (impl pattern + privacy log shape — PATTERNS.md "LlmGateway.java" `LlmGatewayImpl` block)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCryptoConfig.java (@Configuration analog — PATTERNS.md "PlatformChatClientConfig.java")
    - backend/core/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java (StructuredTaskScope pattern — PATTERNS.md "LlmGatewayMultiTenantLeakTest.java")
    - backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java (currentOrThrow + TENANT ScopedValue)
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayWave0Test.java (Plan 01 @Disabled scaffold)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-A1, D-E1, D-E2, D-I1 log shapes)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (Shared Patterns S-1, S-6 + sections "PlatformChatClientConfig" + "LlmGatewayImpl wiring")
    - .planning/phases/02C-llm-gateway/02C-AI-SPEC.md (sections 2-3 — Spring AI M4 ChatClient + ApiKey + OpenAiApi.builder + OpenAiChatModel.builder usage)
  </read_first>
  <behavior>
    - Test 1 (PlatformApiKeyTest#getValue_reads_properties_at_call_time): set ZeroMailLlmProperties.apiKey to "key-A"; call getValue() → returns "key-A". Mutate the bean (or use a fake props bean returning a different value) → next getValue() returns the new value (proves dynamic resolution, not constructor-cached).
    - Test 2 (LlmGatewayPlatformPathTest — @SpringBootTest with MockBean ChatModel): bind TenantContext.TENANT to a fixed UUID, call `gateway.chat(CallSite.PREVIEW, "<p>hi</p>", List.of())` with mock ChatModel returning a stub ChatResponse with one tool call `{action: "label", args: {value: "Receipts"}}`; assert ToolCallResult(LABEL, {value=Receipts}) is returned. Verify SanitizationPipeline ran first (mock or assertion on intermediate state — at minimum, verify the call would have failed if sanitization were skipped on a hostile input).
    - Test 3 (LlmGatewayPlatformPathTest#emits_privacy_log_on_success): captured ListAppender contains `event=llm_call_succeeded tenantId={...} callSite=PREVIEW provider=openai-compatible model=openai/gpt-4o-mini latencyMs={...} promptTokens={...} completionTokens={...} stopReason={...} truncated=false` — and contains NEITHER `<p>` (input fragment) NOR any model output content.
    - Test 4 (LlmGatewayMultiTenantLeakTest#concurrent_virtual_thread_requests_never_cross_tenant): 100 concurrent calls via StructuredTaskScope, each with `ScopedValue.where(TenantContext.TENANT, tenant_i.toString()).call(() -> gateway.chat(CallSite.PREVIEW, "hello", List.of()))`. Mock ChatModel echoes the bound tenantId in its tool-call args. Assert seeds[i] sees its own UUID back — never another tenant's.
    - Plan 01 Wave 0 LlmGatewayWave0Test @Disabled annotation removed; the assertion `gateway.chat(CallSite.PREVIEW, "hi", List.of()).action() != null` passes.
  </behavior>
  <action>
    1. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java`** — `@Component` implementing `org.springframework.ai.chat.model.ApiKey` (verify exact import path via Context7 — could be `org.springframework.ai.openai.api.ApiKey` in M4):
       ```java
       @Component
       class PlatformApiKey implements ApiKey {
           private final ZeroMailLlmProperties llmProperties;
           PlatformApiKey(ZeroMailLlmProperties llmProperties) { this.llmProperties = llmProperties; }
           @Override public String getValue() { return llmProperties.apiKey(); }
       }
       ```
       Per CLAUDE.md no Lombok, explicit ctor. Variable named `llmProperties` (not `props`/`cfg`). Per D-A1: resolved at HTTP-send time via Spring AI's per-call `ApiKey.getValue()` invocation — NOT cached at bean construction.

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java`** — `@Configuration @EnableConfigurationProperties(ZeroMailLlmProperties.class)`:
       ```java
       @Configuration
       @EnableConfigurationProperties(ZeroMailLlmProperties.class)
       class PlatformChatClientConfig {

           @Bean
           OpenAiApi platformOpenAiApi(ZeroMailLlmProperties llmProperties, PlatformApiKey platformApiKey) {
               return OpenAiApi.builder()
                       .baseUrl(llmProperties.baseUrl())
                       .apiKey(platformApiKey)            // dynamic — D-A1
                       .build();
           }

           @Bean
           OpenAiChatModel platformOpenAiChatModel(OpenAiApi platformOpenAiApi) {
               return OpenAiChatModel.builder()
                       .openAiApi(platformOpenAiApi)
                       .defaultOptions(OpenAiChatOptions.builder().temperature(0.0).build())
                       .build();
           }

           @Bean
           ChatClient platformChatClient(OpenAiChatModel platformOpenAiChatModel) {
               return ChatClient.create(platformOpenAiChatModel);
           }
       }
       ```
       Verify M4 builder API names via Context7 `/spring-projects/spring-ai` query "OpenAiApi.builder ApiKey 2.0.0-M4" before finalizing — RESEARCH lines 12-17 confirm `apiKey(ApiKey)` is documented.

    3. **Create `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`** — package-private `@Service class LlmGatewayImpl implements LlmGateway`. Skeleton in this plan covers ONLY the platform-path happy case + sanitization + privacy logging + tool-call extraction (no validator, no BYOK, no ledger — those are Plan 04/05/06 modifications). Skeleton:
       ```java
       @Service
       class LlmGatewayImpl implements LlmGateway {
           private static final Logger log = LoggerFactory.getLogger(LlmGatewayImpl.class);

           private final ChatClient platformChatClient;
           private final SanitizationPipeline sanitizationPipeline;
           private final ZeroMailLlmProperties llmProperties;
           // Plan 04 will add: private final ActionValidator actionValidator;
           // Plan 05 will add: private final TenantByokCredentialsRepository byokRepo;
           //                   private final BYOKChatModelFactory openAiCompatByokFactory;
           //                   private final BYOKChatModelFactory anthropicByokFactory;
           //                   private final RefreshTokenCipher refreshTokenCipher;
           // Plan 06 will add: private final CreditLedger creditLedger;

           LlmGatewayImpl(ChatClient platformChatClient,
                          SanitizationPipeline sanitizationPipeline,
                          ZeroMailLlmProperties llmProperties) {
               this.platformChatClient = platformChatClient;
               this.sanitizationPipeline = sanitizationPipeline;
               this.llmProperties = llmProperties;
           }

           @Override
           public ToolCallResult chat(CallSite callSite, String rawHtml, List<ToolCallback> tools) {
               UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
               String model = llmProperties.modelByCallSite().get(callSite);
               String provider = llmProperties.provider().id();

               long startNanos = System.nanoTime();
               log.info("event=llm_call_started tenantId={} callSite={} provider={} model={}",
                       tenantId, callSite, provider, model);

               SanitizationContext sanitized = sanitizationPipeline.sanitize(rawHtml);

               try {
                   ChatResponse chatResponse = platformChatClient.prompt()
                           .user(sanitized.content())
                           .toolCallbacks(tools)
                           .options(OpenAiChatOptions.builder().model(model).build())
                           .call().chatResponse();

                   ToolCallResult result = parseToolCall(chatResponse);  // Plan 04 replaces with validator-backed parse

                   long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
                   Usage usage = chatResponse.getMetadata().getUsage();
                   log.info("event=llm_call_succeeded tenantId={} callSite={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}",
                           tenantId, callSite, latencyMs,
                           usage.getPromptTokens(), usage.getGenerationTokens(),
                           chatResponse.getResults().get(0).getMetadata().getFinishReason(),
                           sanitized.truncated());
                   return result;
               } catch (RuntimeException callFailure) {
                   log.warn("event=llm_call_failed tenantId={} callSite={} reason={}",
                           tenantId, callSite, callFailure.getClass().getSimpleName());
                   throw callFailure;
               }
           }

           @Override
           public ToolCallResult driftCheck(String prompt) {
               // D-E3 — bypasses ledger; pinned to driftModel
               UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
               String model = llmProperties.driftModel();
               // [shape mirrors chat() but uses driftModel; full implementation reuses parseToolCall()]
               // Implementation here calls platformChatClient.prompt().user(prompt).options(OpenAiChatOptions.builder().model(model).build()).call().chatResponse()
               // and returns parseToolCall(chatResponse). Privacy-safe log line: event=llm_drift_call_succeeded.
               // ...
           }

           private ToolCallResult parseToolCall(ChatResponse chatResponse) {
               // Plan 03 minimal: extract first tool call's function name + args; fallback throws.
               // Plan 04 replaces with ActionValidator-backed parse + Layer 1/Layer 2 enforcement.
               AssistantMessage message = chatResponse.getResults().get(0).getOutput();
               if (message.getToolCalls().isEmpty()) {
                   throw new IllegalStateException("No tool call returned");
               }
               AssistantMessage.ToolCall toolCall = message.getToolCalls().get(0);
               // Minimal parse — Plan 04 wraps with ActionValidator
               Action action = Action.fromFunctionName(toolCall.name());
               // Args parsing is JSON; use Jackson via a private helper or inline ObjectMapper
               Map<String, Object> args = parseJsonArgs(toolCall.arguments());
               return new ToolCallResult(action, args);
           }

           private Map<String, Object> parseJsonArgs(String argumentsJson) { /* Jackson parse to Map */ }
       }
       ```
       Critical: **mark the parseToolCall and BYOK seam locations with `// Plan 04/05/06 modifies here` comments** so future executors edit at the right spot without disrupting the structure.

       Variable names per CLAUDE.md enterprise readability: `chatResponse` (not `resp`), `callFailure` (not `e`/`ex`), `tenantId` (not `tid`), `latencyMs` (not `lat`).

    4. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayPlatformPathTest.java`** — `@SpringBootTest` with `@MockBean ChatModel` (mock OpenAiChatModel via test slice) returning a synthetic ChatResponse containing one tool call `{name: "label", arguments: '{"value":"Receipts"}'}`. Bind TenantContext via `ScopedValue.where(...)`. Assert:
       - Returns `ToolCallResult(LABEL, {value=Receipts})`.
       - Privacy log assertion via Logback `ListAppender` — captured line matches `event=llm_call_succeeded` regex AND does not contain the input bytes (`<p>` / `hi` / `Receipts` if Receipts is the model output — wait, args ARE returned, so `Receipts` lives in the result; the log line MUST NOT include args content per D-I1).
       - Wait: D-I1 explicitly says NO tool-call args content in logs. So assert log line does NOT contain `Receipts`.

    5. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayMultiTenantLeakTest.java`** per PATTERNS.md "LlmGatewayMultiTenantLeakTest.java" verbatim shape:
       - 100 seeded tenants, each binding a different UUID.
       - Mock ChatModel returns a tool call where args contains `{boundTenantId: "<the bound tenant id>"}` — implementation reads `TenantContext.currentOrThrow()` from inside the mock.
       - StructuredTaskScope.open() forks all 100 calls.
       - After scope.join, iterate results and assert `result.args().get("boundTenantId").equals(seeds[i].tenantId().toString())`.

    6. **Modify Plan 01's `LlmGatewayWave0Test.java`** — remove `@Disabled("Plan 03 lands LlmGateway")` annotation. Test body asserts the gateway is wired and returns a ToolCallResult under a TenantContext-bound mock.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "PlatformApiKeyTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayWave0Test" --tests "LlmGatewayBoundaryTest"</automated>
  </verify>
  <acceptance_criteria>
    - File `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java` exists; `grep -c 'implements ApiKey' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformApiKey.java` returns `1`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java` exists; `grep -c '@Configuration' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java` returns `>= 1`; `grep -c 'OpenAiApi.builder()' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java` returns `>= 1`; `grep -c 'ChatClient.create' backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/PlatformChatClientConfig.java` returns `>= 1`.
    - File `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` exists; `grep -c 'implements LlmGateway' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `1`; `grep -c 'sanitizationPipeline.sanitize' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`; `grep -c 'event=llm_call_started\|event=llm_call_succeeded\|event=llm_call_failed' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 3`.
    - `grep -E 'log\.(info|warn|error|debug).*\.content\(\)|log\.(info|warn|error|debug).*rawHtml|log\.(info|warn|error|debug).*chatResponse[^.]' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java | grep -v '//' ` returns no matches (no content/prompt/completion in log lines).
    - `grep -c '// Plan 04 ' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1` (Plan 04 seam markers present).
    - `grep -c '// Plan 05 ' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c '// Plan 06 ' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -v '^\s*//' backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayWave0Test.java | grep -c '@Disabled'` returns `0`.
    - `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayWave0Test"` exits 0.
    - `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest"` exits 0 (Spring AI imports stay confined to gateway.springai + the single ToolCallback exemption in service).
    - `./gradlew :backend:core:test` exits 0 (all module tests green).
  </acceptance_criteria>
  <done>
    LlmGatewayImpl skeleton is wired end-to-end on the platform path. Sanitization pipeline runs first; privacy log lines emit metadata only. Multi-tenant leak test proves no cross-tenant bleed under 100 concurrent virtual-thread calls. Wave 0 LlmGatewayWave0Test from Plan 01 is green. Plan 04/05/06 seam markers are documented in code comments so the next executors know where to insert tool-call validation, BYOK branching, and credit-ledger wiring.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| LlmGateway public interface → Phase 3/4 callers | Public contract; ToolCallback is the single Spring AI type that crosses. |
| LlmGatewayImpl → Spring AI ChatClient | All vendor-SDK usage isolated inside gateway.springai (ArchUnit-pinned). |
| Application boot → ZEROMAIL_LLM_PLATFORM_API_KEY | Fail-fast at Spring context init; missing env var = process refuses to start. |
| LlmGatewayImpl logs → Logback appenders | Privacy invariant: no email body, prompt, completion, or tool-call args content in any log line. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-06 | Tampering (direct ChatClient bypass of gateway) | Repo-wide imports | mitigate | Plan 01 ArchUnit `LlmGatewayBoundaryTest` confines `org.springframework.ai..` to `core.llm.gateway.springai`. This plan updates the rule to allow ONLY `ToolCallback` in `core.llm.service` (LlmGateway interface signature requirement). All other Spring AI types remain pinned. |
| T-2C-05 | Information Disclosure (PII / body / prompt / completion in logs / spans / DB) | LlmGatewayImpl + application.yml | mitigate | Privacy log lines emit `event=llm_call_{started,succeeded,failed} tenantId={} callSite={} provider={} model={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}` — metadata only (D-I1). Spring AI `chat.client.observations.log-prompt: false` + `log-completion: false` pinned in BOTH api + worker yml (D-I5). LlmGatewayPlatformPathTest asserts log lines contain no input/output bytes. |
| T-2C-secret-leak-on-boot | Information Disclosure | application.yml | mitigate | `ZEROMAIL_LLM_PLATFORM_API_KEY:?<message>` fail-fast — missing env var halts boot with a clear error referring to the deployment secret source (Docker secret / systemd credential / env file). Mirror of REFRESH_TOKEN_KEY_BASE64 pattern from Phase 1.5. |
| T-2C-platform-key-cached | Information Disclosure | PlatformApiKey | mitigate | `getValue()` reads from `ZeroMailLlmProperties#apiKey()` per call — no caching at bean construction (D-A1). PlatformApiKeyTest verifies dynamic resolution. |
| T-2C-cross-tenant-cache-leak | Information Disclosure (T-2C-08) | LlmGatewayImpl singleton | mitigate | LlmGatewayImpl holds NO per-tenant state — `TenantContext.currentOrThrow()` is read PER CALL inside chat(). Singleton ChatClient is the only shared resource, and it dispatches to the dynamic PlatformApiKey on every HTTP send. LlmGatewayMultiTenantLeakTest exercises 100 concurrent virtual-thread calls and asserts no cross-tenant bleed. Per-tenant ChatModel cache deferred per D-A4 — explicitly noted in code comment. |
| T-2C-tool-callback-exemption | Tampering | LlmGatewayBoundaryTest exemption | accept | Single-class exemption (`org.springframework.ai.tool.ToolCallback`) in `core.llm.service` only. Documented in rule's `.because()` clause. Required because Phase 3/4 callers need to compose tool callbacks at the LlmGateway public surface; alternative (project-local LlmTool wrapper) would require duplicating Spring AI's ToolCallback shape and bridge layer — higher complexity, more places for the M4→GA churn to bite. |
</threat_model>

<verification>
- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 — full suite green
- `./gradlew :backend:api:bootRun -x test` (no actual run; verify with `./gradlew bootJar`) — application context starts when ZEROMAIL_LLM_PLATFORM_API_KEY is set; fails with clear error when unset
- ArchUnit `LlmGatewayBoundaryTest` passes with the ToolCallback exemption in place
- Plan 01 + Plan 02 Wave 0 scaffolds (SanitizationPipelineWave0Test, LlmGatewayWave0Test) both green
- LlmGatewayMultiTenantLeakTest exits 0 across 100 concurrent virtual-thread calls
</verification>

<success_criteria>
- Public `LlmGateway` interface (with `chat` + `driftCheck` methods) is the single entry point.
- LlmGatewayImpl skeleton wires SanitizationPipeline + ChatClient + ZeroMailLlmProperties; privacy logs are metadata-only.
- Platform-path Spring AI wiring (PlatformApiKey + PlatformChatClientConfig) provides a singleton ChatClient with per-call dynamic API key resolution.
- application.yml updates land ZEROMAIL_LLM_PLATFORM_API_KEY:? fail-fast and Spring AI observation log-prompt/log-completion: false in both api + worker.
- LlmGatewayMultiTenantLeakTest passes (FND-05-style test for the gateway).
- Plan 04/05/06 seam markers are present in LlmGatewayImpl source comments.
- Plan 01 Wave 0 LlmGatewayWave0Test no longer @Disabled.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-03-SUMMARY.md` documenting:
- Exact M4 import paths used for `ApiKey`, `OpenAiApi.builder()`, `OpenAiChatOptions.builder()`, `ChatClient.create()` (verified via Context7 if needed)
- Final ArchUnit rule shape after the ToolCallback exemption (paste the actual rule text)
- Sample log lines from LlmGatewayPlatformPathTest run (proof of metadata-only)
- Pointer for Plan 04: where in LlmGatewayImpl `parseToolCall(...)` is the seam to inject ActionValidator
- Pointer for Plan 05: where in `chat(...)` to insert the BYOK-branch lookup before falling through to platform path
- Pointer for Plan 06: where in `chat(...)` to wrap the platform call with `creditLedger.reserve / settle / release`
</output>

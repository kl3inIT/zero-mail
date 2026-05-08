# Phase 2C: LLM Gateway - Research

**Researched:** 2026-05-07
**Domain:** Spring AI 2.0.0-M5 gateway abstraction, prompt-injection hardening, BYOK encrypted-at-rest, multi-tenant LLM spend caps, drift detection scaffold
**Confidence:** HIGH on the Spring AI M5 adapter seam now compiled in repo (`OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(builder)`, `AnthropicChatOptions.builder().apiKey().baseUrl().model(...)`), HIGH on jtokkit truncation API, HIGH on existing in-repo patterns (CallSite, CreditLedger, RefreshTokenCipher, ShedLock pattern), MEDIUM-HIGH on M5 -> GA churn risk.

## Summary

Phase 2C ships the single `LlmGateway` abstraction that gates all outgoing LLM traffic for Zero Mail. The current M5 design uses provider-specific Spring AI builders inside `core.llm.gateway.springai`: **(a) platform OpenAI-compatible path = singleton `ChatClient` backed by `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(...).apiKey(...).model(...).build())`; (b) OpenAI-compatible BYOK = one-call `OpenAiChatModel` built with the tenant key/baseUrl/model; (c) Anthropic BYOK = parent `AnthropicChatModel` plus per-call `AnthropicChatOptions.builder().apiKey(...).baseUrl(...).model(...)` passed as a builder to `ChatClient.prompt().options(...)`.** This is the M5-compatible provider-builder seam that replaced the old low-level client-cloning approach.

Research surfaced three substantive corrections to the original pre-M5 CONTEXT:

1. **Liquibase floor is 018, not 017.** Phase 2B's worker plan claimed changeset `017-shedlock-table.yaml` (verified in `db.changelog-master.yaml`). CONTEXT D-G1 names the BYOK changeset `017-tenant-byok-credentials.yaml` — that ID is taken. Use **`018-tenant-byok-credentials.yaml`**.
2. **Spring AI M5 `ChatClient.prompt().options(...)` takes a builder**, not a built `ChatOptions` instance, so adapter code and tests must pass `OpenAiChatOptions.Builder` / `AnthropicChatOptions.Builder`.
3. **OpenAI-compatible vs Anthropic BYOK seam asymmetry.** OpenAI-compatible BYOK now derives a one-call `OpenAiChatModel` with `OpenAiChatOptions.builder().apiKey(...).baseUrl(...).model(...)`. Anthropic BYOK keeps the per-request runtime-options seam with `AnthropicChatOptions.builder().apiKey(...).baseUrl(...).model(...)`. Both stay inside `core.llm.gateway.springai`; service/domain code sees only `ByokLlmModelClient`.

**Primary recommendation:** Treat Spring AI **2.0.0-M5** as the baseline. Keep all direct Spring AI imports in `core.llm.gateway.springai`, disable unused Spring AI auto-model beans in runnable `application.yml`, use builder-style runtime options, and persist BYOK `model` end-to-end.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| LLM call orchestration (prompt → tool-call → result) | Backend (`backend/core` `core.llm`) | — | Synchronous service-call from Phase 3/4 callers; Java 25 virtual-thread blocking is fine. Browser must never see raw model content. |
| Prompt sanitization pipeline (Jsoup → NFC → tag-strip → jtokkit) | Backend (`backend/core` `core.llm.gateway.sanitization`) | — | Privacy invariant: sanitization runs before content ever leaves the JVM. Browser cannot be trusted; Phase 4 worker calls gateway directly without crossing browser. |
| Tool-call enforcement (allow-list) | Backend (`core.llm.service` `ActionValidator`) | — | Defense-in-depth requires post-parse validation in same JVM as the gateway — not deferrable to caller. |
| BYOK key storage (encrypted-at-rest) | Backend / DB (`tenant_byok_credentials` PostgreSQL table) | — | Plaintext keys must never reach browser; AES-GCM envelope at app layer with key from VPS deployment secret. |
| BYOK Validate flow (network probe to user-supplied endpoint) | Backend (`backend/api` `ByokController` → `BYOKValidationService`) | — | Browser MUST NOT issue the validate call — it would expose the key to client-side instrumentation, devtools, and any browser extension. SPEC.md Constraints line 109. |
| BYOK form UI | Frontend (`apps/web/features/llm/components/ByokForm.tsx`) | — | Settings surface; uses raw shadcn primitives + uncontrolled `<input type="password">`. |
| Credit cap enforcement | Backend (`core.billing.CreditLedger.reserve`) | Frontend (top-up prompt UI) | Backend rejects with 402; frontend localizes the error code into a top-up CTA banner. |
| Per-call-site model pin | Backend (`@ConfigurationProperties("zero-mail.llm.platform")`) | — | Operator config — neither browser nor end-user picks the platform model. |
| Drift detection job | Backend worker (`backend/worker` `core.llm.drift`) | — | Scheduled cron, ShedLock-locked, mirrors Phase 2A `GmailWatchScheduler` pattern. |
| Privacy logging contract | Backend (Logback structured layout + `Sensitive<T>` ArchUnit) | — | Frontend log forwarding (Sentry) is post-v1; for now privacy enforcement is JVM-side only. |

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-A1..D-I4 — see 02C-CONTEXT.md for full text)

**A. BYOK per-request key seam:**
- D-A1: Platform path = singleton `ChatClient` backed by `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(...).apiKey(...).model(...).build())`.
- D-A2: BYOK path = provider-specific `ByokLlmModelClient` implementations. OpenAI-compatible builds a one-call `OpenAiChatModel` from tenant `apiKey/baseUrl/model`; Anthropic passes `AnthropicChatOptions.Builder` with tenant `apiKey/baseUrl/model` per request.
- D-A3: BYOK provider abstraction = `ByokLlmModelClient` interface in `core.llm.service`. Two implementations: `OpenAiCompatibleByokModelClient`, `AnthropicByokModelClient`.
- D-A4: Caching of derived BYOK ChatClients deferred to Phase 4.
- D-A5: BYOK encryption reuses `RefreshTokenCipher` verbatim (same `REFRESH_TOKEN_KEY_BASE64`, same envelope, tenantId-bound AAD).

**B. Sanitization pipeline composition:**
- D-B1: Bean-chain via `List<Sanitizer>` ordered by `@Order`; orchestrator `SanitizationPipeline` folds `(ctx, step) → step.apply(ctx)`.
- D-B2: `SanitizationContext` record carries `(content, tokenCount, truncated, stepMetadata)`.
- D-B3: Fail-fast — any sanitizer exception → `SanitizationException(stepName, cause)` aborts gateway call.
- D-B4: jtokkit `cl100k_base` for all providers, hard cap 3896 (4096 budget − 200 Anthropic safety headroom).
- D-B5: `SanitizationAdvisor` deferred to v2.

**C. Tool-call wrapping + safety enforcement:**
- D-C1: Layer 1 = `ToolCallback` + `toolChoice="required"` (OpenAI) / `ToolChoiceAny` (Anthropic) + `internalToolExecutionEnabled(false)`.
- D-C2: Layer 2 = post-parse `ActionValidator` calling `Action.fromId()` (fail-loud) + `EnumSet.of(LABEL,ARCHIVE,SAVE_DRAFT).contains(...)`.
- D-C3: Gateway return type = `record ToolCallResult(Action action, Map<String,Object> args)`.
- D-C4: `SafetyViolationException` is `RuntimeException` under `core.llm.model`; `GlobalExceptionHandler` maps to HTTP 500 + `code=LLM_SAFETY_VIOLATION`.
- D-C5: Test seam = mock `ChatModel` returning synthetic `ChatResponse` w/ unknown function name.

**D. BYOK form architecture:**
- D-D1: `apps/web/features/llm/` triplet (`api/llm-api.ts`, `components/ByokForm.tsx`, `hooks/use-byok.ts`). Mounted on existing `/settings`.
- D-D2: Uncontrolled `<input type="password">` with `useRef<HTMLFormElement>`; raw key never enters React state.
- D-D3: Two-step Validate-then-Save UX. Save disabled until `validateByok.data?.ok === true`.
- D-D4: Raw shadcn primitives (Card, RadioGroup, Input, Button, Alert) — no wrappers.
- D-D5: i18n Vietnamese-first. Co-located in `apps/web/features/llm/messages.ts`; merged into `apps/web/i18n/messages/{vi,en}.json` build-time.
- D-D6: `frontend-design` skill MUST be invoked before writing UI code.

**E. Per-call-site model pin:**
- D-E1: `@ConfigurationProperties("zero-mail.llm.platform")` exposes `compileModel`, `driftModel`, `triageModel`. `Map<CallSite, String>` resolution at gateway entry.
- D-E2: `LlmGateway.chat(CallSite, SanitizedContent, ToolCallbacks) → ToolCallResult`. Synchronous return.
- D-E3: `driftCheck(prompt) → ToolCallResult` is a separate gateway entry point bypassing `CallSite`.

**F. In-memory cache for prompts/completions:**
- D-F1: NO Caffeine cache in Phase 2C. Sanitized content lives on the request stack only.

**G. Liquibase changeset ordering:**
- D-G1: BYOK table changeset name in CONTEXT is `017-tenant-byok-credentials.yaml`. **CORRECTION: floor is now 018** (017 is taken by `017-shedlock-table.yaml`). Schema otherwise unchanged.

**H. Drift detection scaffold:**
- D-H1: Golden-set fixture at `backend/core/src/main/resources/llm/golden-set.json` (~20 synthetic emails, no PII).
- D-H2: Baseline `backend/core/src/main/resources/llm/golden-baseline.json` committed to repo.
- D-H3: `DriftDetectionJob` `@Scheduled(cron="0 0 6 * * *")` gated on `zero-mail.llm.drift.enabled` (default `false`); ShedLock-locked.
- D-H4: CI mock test pattern with `MockBean ChatModel`.

**I. Privacy-safe logging contract:**
- D-I1..D-I3: Gateway/BYOK/sanitization logs all follow `event=opaque tenantId={}` format. NO content, NO token-content, NO tool-call args content.
- D-I4: Logback scrub filter extension — verify before assuming. Phase 1's `SensitiveMarkerScrubFilter` only catches the literal `Sensitive(` token; it does NOT scrub `apiKey=`, `Bearer`, `x-api-key`. The `Sensitive<String>` typing is the actual enforcement (ArchUnit deny-list `prompt`, `completion`, `body` already enforced from Phase 1).

### Claude's Discretion

- Exact Spring AI M5 builder APIs — **VERIFIED by compile/test in implementation.**
- `OpenAiChatOptions` vs `AnthropicChatOptions` `toolChoice` exact builder method names — **VERIFIED by adapter tests.**
- `ByokLlmModelClient` interface signature — **implemented as `call(byte[] decryptedKey, String endpoint, LlmChatRequest request)`.**
- `Action` enum `id()` lower-snake-case vs raw enum name — recommend lower-snake (`LABEL.id() == "label"`).
- Exact `SanitizationContext.stepMetadata` map key conventions.
- jtokkit version to pin — **VERIFIED 1.1.0 latest stable on Maven Central as of 2024-07-19; nothing newer exists.**
- ShedLock for `DriftDetectionJob` — reuse Phase 2A pattern (`shedlock-spring 7.7.0` already in `libs.versions.toml`). **VERIFIED.**
- ByokForm Alert success copy + Validate result models[] rendering.
- i18n key spelling for `error.llm.safety_violation`, `error.llm.byok_validate_failed`, `byok.validate_button`.

### Deferred Ideas (OUT OF SCOPE — see 02C-CONTEXT.md)

BYOK ChatClient caching; sealed `ToolCallResult` interface; `SanitizationAdvisor`; per-call-site BYOK provider pin; PII redaction sanitizer step; `RefreshTokenCipher` relocation to `core.shared.crypto` (researcher recommendation: defer — see Plan Implications); production drift cron go-live; soft-warn at low-balance threshold; streaming responses; vector store / embeddings / RAG; Anthropic precise tokenizer; admin probe endpoint; refresh-token-style key rotation drill for BYOK; Zustand for cross-component UI state.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LLM-00 | Admin platform-provider config (env/secret only) | `@ConfigurationProperties("zero-mail.llm.platform")` shape verified — Spring Boot 4 + records pattern. `:?` fail-fast pattern from Phase 1.5 CR-04 (CR-04 already applied in api+worker for `REFRESH_TOKEN_KEY_BASE64`). |
| LLM-01 | Single gateway abstraction; ArchUnit denies direct Spring AI use elsewhere | `BillingDomainBoundaryArchTest` is the verified template — same `noClasses().that().resideOutsideOfPackage(...).should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..")` pattern. |
| LLM-02 | OpenRouter via OpenAI-compatible default | Verified: OpenRouter exposes OpenAI-compatible `/v1/chat/completions`; model id format `provider/model-name` (e.g., `openai/gpt-4o-mini`). |
| LLM-03 | BYOK provider UI + validate flow | `apps/web` already has `Card/RadioGroup/Input/Button/Alert` shadcn primitives. React 19 uncontrolled-input pattern with `useRef<HTMLFormElement>` is idiomatic. |
| LLM-04 | BYOK encrypted-at-rest, AES-GCM | `RefreshTokenCipher` exists at `core.gmail.persistence.crypto`; envelope `[key_version:int32 \| nonce:12 \| ciphertext]` with tenantId-bound AAD verified in source. Reusable verbatim. |
| LLM-05 | HTML sanitization via Jsoup | `jsoup 1.22.2` already in `libs.versions.toml`. `Jsoup.clean(content, Safelist.none())` is the standard text-only stripper. |
| LLM-06 | Unicode hardening — NFC + tag-strip U+E0000–U+E007F | Java built-in `java.text.Normalizer.normalize(content, Form.NFC)` + regex strip. No new dep. |
| LLM-07 | Tool-call wrapping with allow-list | Spring AI M5 `ToolCallback` + `ChatClient.prompt().toolCallbacks(...)` + builder-style runtime options + `internalToolExecutionEnabled(false)` + `toolChoice("required")`. |
| LLM-08 | Token budget ≤4k via jtokkit | jtokkit 1.1.0 `Encoding#encode(String, int) → EncodingResult{tokens, isTruncated, lastProcessedCharacterIndex}` — character-boundary truncation handles multi-byte/emoji safely. |
| LLM-09 | No persistence of bodies/prompts/completions | Spring AI observation properties `spring.ai.chat.client.observations.log-prompt: false` + `.log-completion: false` and `spring.ai.chat.observations.*` pinned defensively. `Sensitive<String>` ArchUnit deny-list already enforces `prompt`/`completion`/`body` field naming since Phase 1. |
| LLM-10 | Per-tenant credit cap, hard reject + 402 | `CreditLedger.reserve(tenantId, CallSite.TRIAGE)` interface verified in `core.billing.service.CreditLedger`. `InsufficientCreditsException → 402` already mapped in `GlobalExceptionHandler` from Phase 2B. Phase 2C only ADDS the BYOK-skip check before calling `reserve`. |
| LLM-11 | Drift detection scaffold | `CreditReserveWatchdog` pattern (`@Scheduled(fixedRate=...)` + `@SchedulerLock(name=..., lockAtLeastFor=PT30S, lockAtMostFor=PT2M)`) is the exact template to mirror. |

## Standard Stack

### Core (already in repo)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Framework | Locked |
| Spring AI starter (OpenAI) | 2.0.0-M5 | LLM orchestration via OpenAI-compatible (OpenRouter default) | `springAi = "2.0.0-M5"` is the current locked milestone in `libs.versions.toml`. |
| Jsoup | 1.22.2 | HTML stripping | Already in `libs.versions.toml`; `Safelist.none()` is the standard text-only mode |
| ShedLock Spring | 7.7.0 | `@SchedulerLock` | Phase 2A/2B already on this version; works with Spring Boot 4 + JVM 17+ |
| Liquibase | 5.0.2 | YAML changesets | Existing pattern — see `backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml` |

### Supporting (NEW in Phase 2C)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `org.springframework.ai:spring-ai-starter-model-openai` | 2.0.0-M5 | OpenAI-compatible client (OpenRouter default + OpenAI-compat BYOK) | Pulls `spring-ai-bom`. Pin via BOM only — never explicit version. |
| `org.springframework.ai:spring-ai-bom` | 2.0.0-M5 | BOM for transitive Spring AI deps | Imported in `backend/core/build.gradle.kts`. |
| `org.springframework.ai:spring-ai-starter-model-anthropic` | 2.0.0-M5 | Direct Anthropic adapter for BYOK Anthropic provider | Included so native Anthropic BYOK can be supported beside OpenRouter. |
| `com.knuddels:jtokkit` | 1.1.0 | Token counting + boundary truncation | Latest stable on Maven Central (2024-07-19). Add to `[versions]` and `[libraries]`. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One global OpenAI-compatible `ChatClient` with only per-request headers | One-call `OpenAiChatModel` built from tenant `apiKey/baseUrl/model` | Required because custom OpenAI-compatible BYOK can vary both endpoint and model; M5 exposes these on `OpenAiChatOptions.builder()`. |
| Anthropic global auto-config model | Parent `AnthropicChatModel` inside adapter + per-call `AnthropicChatOptions.builder().apiKey(...).baseUrl(...).model(...)` | Avoids requiring a deployment-wide Anthropic key while still using Spring AI's native Anthropic client for BYOK calls. |
| jtokkit (cl100k_base) for Anthropic | Anthropic `POST /v1/messages/count_tokens` | SPEC.md explicitly out-of-scope — extra HTTP round trip, leaks content to Anthropic for non-BYOK case (privacy violation). jtokkit estimate ±10–20% with 200-token safety headroom is sufficient. |
| Sealed `ToolCallResult` interface w/ per-action records | `record ToolCallResult(Action, Map<String,Object>)` | Sealed gives strongest typing but couples gateway to Phase 4 action arg shapes that don't exist yet. Defer to v2 (CONTEXT-deferred). |
| `RefreshTokenCipher` relocation to `core.shared.crypto` | Inject existing bean from `core.gmail.persistence.crypto` via Modulith `allowedDependencies` edge | **Researcher recommends defer relocation.** Reusing existing bean adds one Modulith edge (`core.llm` → `gmail`) but avoids touching Phase 1.5 OAuth callers. Relocation is mechanical refactoring that belongs in a focused tech-debt phase, not scoped into 2C. |

**Installation (Gradle Kotlin DSL):**
```kotlin
// In libs.versions.toml [versions]
springAi = "2.0.0-M5"  // current baseline
jtokkit = "1.1.0"       // NEW

// In libs.versions.toml [libraries]
spring-ai-bom = { module = "org.springframework.ai:spring-ai-bom", version.ref = "springAi" }
spring-ai-starter-openai = { module = "org.springframework.ai:spring-ai-starter-model-openai", version.ref = "springAi" }
spring-ai-starter-anthropic = { module = "org.springframework.ai:spring-ai-starter-model-anthropic", version.ref = "springAi" }
jtokkit = { module = "com.knuddels:jtokkit", version.ref = "jtokkit" }
```

**Version verification (executed during research):**
- `spring-ai 2.0.0-M5` is the current project baseline; M5 builder/runtime-options signatures were compile-tested in this repo.
- `jtokkit 1.1.0` confirmed latest on Maven Central (2024-07-19). Zero deps; thread-safe.
- `shedlock-spring 7.7.0` works with JVM 17+, tested on Spring 7.0/Boot 4.x per ShedLock README.

## Architecture Patterns

### System Architecture Diagram

```
                     [HTTP request from apps/web]
                              │
                              ▼
            ┌─────────────────────────────────┐
            │  ByokController (backend/api)   │   (BYOK validate + save endpoints)
            │  - POST /api/llm/byok/validate  │
            │  - POST /api/llm/byok           │
            │  - GET  /api/llm/byok           │
            └─────────────┬───────────────────┘
                          │ (delegates to)
                          ▼
            ┌─────────────────────────────────┐
            │  BYOKValidationService          │
            │  + BYOKCredentialsService       │   (network probe + AES-GCM encrypt + persist)
            └────┬────────────────────────────┘
                 │
       ┌─────────┘
       │ (Phase 3/4 callers)
       ▼
┌──────────────────────────────────────────────────────────────────┐
│  LlmGateway interface  (core.llm.service)                       │
│  ───────────────────────────────────────────────────────────    │
│  chat(CallSite, content, toolCallbacks) → ToolCallResult        │
│  driftCheck(prompt) → ToolCallResult                            │
└────────────────────────────────────┬─────────────────────────────┘
                                     │
                                     ▼
                   ┌──────────────────────────────┐
                   │  LlmGatewayImpl              │  (core.llm.gateway.springai)
                   │  ───────────────────────     │
                   │  1. resolve tenant BYOK?     │ ─────▶  TenantByokCredentialsRepository
                   │  2. credit reserve (if no    │ ─────▶  CreditLedger.reserve(tenantId, callSite)
                   │     BYOK)                    │
                   │  3. sanitize via pipeline    │ ─────▶  SanitizationPipeline (4 ordered Sanitizer beans)
                   │  4. resolve ChatClient:      │
                   │     - BYOK row? → factory    │ ─────▶  BYOKChatModelFactory.derive(decryptedKey, endpoint)
                   │     - else: singleton w/     │
                   │       PlatformApiKey         │ ─────▶  PlatformApiKey.getValue() reads TenantContext
                   │  5. ChatClient.prompt()      │
                   │     .toolCallbacks(...)      │
                   │     .options(toolChoice=     │
                   │       "required" /           │
                   │       ToolChoiceAny)         │
                   │     .options(internalTool    │
                   │       ExecutionEnabled=false)│
                   │     .call()                  │ ─────▶  Spring AI ChatModel (OpenAI / Anthropic)
                   │  6. ChatResponse →           │              │
                   │     hasToolCalls?            │              ▼
                   │     extract first ToolCall   │       [outbound HTTPS to OpenRouter / Anthropic / BYOK endpoint]
                   │  7. ActionValidator.validate │
                   │     (action ∈ allow-list?)   │
                   │  8. settle / release ledger  │ ─────▶  CreditLedger.settle(reservationId)
                   │  9. emit Micrometer metadata │
                   │     (no content)             │
                   │ 10. return ToolCallResult    │
                   └──────────────────────────────┘

       Async / scheduled path  (backend/worker, separate process)
       ─────────────────────────────────────────────────────────
                   ┌──────────────────────────────┐
                   │  DriftDetectionJob           │
                   │  @Scheduled(cron=…)          │
                   │  @SchedulerLock(...)         │
                   │  - reads golden-set.json     │
                   │  - calls gateway.driftCheck  │
                   │    (uses platform path,      │
                   │     pinned driftModel)       │
                   │  - compares against          │
                   │    golden-baseline.json      │
                   │    (action = JsonNode.equals,│
                   │     args = Levenshtein > 20%)│
                   │  - logs event=drift_check    │
                   │    _run total={} drifted={}  │
                   └──────────────────────────────┘
```

### Recommended Project Structure

```
backend/core/src/main/java/com/zeromail/core/llm/
├── package-info.java                         # @ApplicationModule(displayName="LLM",
│                                             #   allowedDependencies={"billing","tenant",
│                                             #     "gmail","shared.persistence","shared.lang",
│                                             #     "shared.privacy"})
├── model/
│   ├── Action.java                           # IdentifiedEnum: LABEL("label"), ARCHIVE("archive"), SAVE_DRAFT("save_draft")
│   ├── BYOKProvider.java                     # IdentifiedEnum: ANTHROPIC("anthropic"), OPENAI_COMPATIBLE("openai-compatible")
│   ├── SanitizedContent.java                 # record (Sensitive<String> content, int tokenCount, boolean truncated)
│   ├── ToolCallResult.java                   # record (Action action, Map<String,Object> args)
│   ├── SanitizationContext.java              # record (Sensitive<String> content, int tokenCount, boolean truncated, Map<String,Object> stepMetadata)
│   ├── SanitizationException.java            # RuntimeException(stepName, cause)
│   └── SafetyViolationException.java         # RuntimeException (no message content)
├── service/
│   ├── LlmGateway.java                       # interface
│   ├── ActionValidator.java                  # validate(String functionName) → Action; fail-loud
│   └── (impl is in gateway/springai/)
├── persistence/
│   ├── TenantByokCredentialsEntity.java      # extends AbstractTenantOwnedEntity
│   └── TenantByokCredentialsRepository.java
├── gateway/
│   ├── springai/
│   │   ├── LlmGatewayImpl.java               # @Service implementing LlmGateway
│   │   ├── PlatformChatClientConfig.java     # @Configuration with platform OpenAI-compatible ChatClient
│   │   ├── ByokLlmModelClient.java           # provider adapter interface
│   │   ├── OpenAiCompatibleByokModelClient.java # one-call OpenAiChatModel with tenant key/baseUrl/model
│   │   └── AnthropicByokModelClient.java     # AnthropicChatOptions.Builder per-request seam
│   └── sanitization/
│       ├── Sanitizer.java                    # interface
│       ├── SanitizationPipeline.java         # @Service composing List<Sanitizer>
│       ├── JsoupHtmlStripSanitizer.java      # @Component @Order(10)
│       ├── NfcNormalizeSanitizer.java        # @Component @Order(20)
│       ├── UnicodeTagStripSanitizer.java     # @Component @Order(30)
│       └── JtokkitTruncateSanitizer.java     # @Component @Order(40)
└── config/
    └── ZeroMailLlmPlatformProperties.java    # @ConfigurationProperties("zero-mail.llm.platform")
                                              # Records: ZeroMailLlmPlatformProperties(
                                              #   String provider, String baseUrl, String apiKey,
                                              #   String compileModel, String driftModel, String triageModel)

backend/core/src/main/resources/
├── db/changelog/changes/018-tenant-byok-credentials.yaml   # NEW (renumbered from CONTEXT 017 — 017 is taken)
├── db/changelog/db.changelog-master.yaml                   # appended include
└── llm/
    ├── golden-set.json                                     # ~20 synthetic emails
    └── golden-baseline.json                                # generated once at scaffold-build time

backend/api/src/main/java/com/zeromail/api/
├── controllers/llm/
│   └── ByokController.java                  # @RestController on /api/llm/byok/*
├── dto/llm/
│   ├── ByokValidateRequest.java             # record
│   ├── ByokValidateResponse.java            # record (ok, models?, reason?)
│   ├── ByokSaveRequest.java                 # record
│   ├── ByokSaveResponse.java                # record (ok, savedAt)
│   └── ByokCurrentResponse.java             # record (provider?, endpoint?, savedAt?) — never returns the key
└── config/GlobalExceptionHandler.java       # ADD @ExceptionHandler for SafetyViolationException, SanitizationException

backend/worker/src/main/java/com/zeromail/worker/llm/
├── DriftDetectionJob.java                   # @Scheduled + @SchedulerLock — mirror CreditReserveWatchdog
└── DriftCheckBatch.java                     # transactional collaborator (matching Watchdog/WatchdogBatch split)

apps/web/features/llm/
├── api/llm-api.ts                           # validateByok, saveByok, getByokCurrent
├── components/ByokForm.tsx                  # uncontrolled <input type="password"> + raw shadcn primitives
├── hooks/use-byok.ts                        # useValidateByok, useSaveByok, useByokCurrent
└── messages.ts                              # i18n co-location (vi/en)

apps/web/i18n/messages/{vi,en}.json          # merged: byok.*, error.llm.*
```

### Pattern 1: Spring AI M5 BYOK seam — OpenAI-compatible

**What:** Build a one-call `OpenAiChatModel` from tenant `apiKey`, canonical `baseUrl`, and required `model`, then wrap it in `ChatClient.create(...)` inside the adapter package and discard after the call.
**When to use:** OpenAI-compatible BYOK with a custom endpoint (OpenRouter preset, Together.ai, Fireworks, self-hosted vLLM, raw OpenAI-compatible proxy).
**Source:** Verified against Spring AI M5 compile/tests in this repo and Context7 `/spring-projects/spring-ai` docs showing `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().apiKey(...).model(...).build())`.

```java
// Spring AI 2.0.0-M5 pattern
@Service
public final class OpenAiCompatibleByokModelClient implements ByokLlmModelClient {
    @Override
    public LlmChatResponse call(byte[] decryptedApiKey, String endpoint, String model, LlmChatRequest request) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
            .apiKey(new String(decryptedApiKey, StandardCharsets.UTF_8))
                .baseUrl(endpoint)
                .model(model)
                .temperature(0.0)
                .toolChoice("required")
                .internalToolExecutionEnabled(false)
                .build())
            .build();
        return callWith(ChatClient.create(chatModel), request);
    }
}
```

### Pattern 2: Spring AI M5 BYOK seam — Anthropic (per-request options builder)

**What:** Anthropic BYOK uses `AnthropicChatOptions.builder().apiKey(...).baseUrl(...).model(...)` per request. In M5+, pass the builder to `ChatClient.prompt().options(...)`, not a built options object.
**When to use:** Anthropic BYOK — runtime options carry the per-request override.
**Source:** Verified in Spring AI M5 compile/tests and Context7 Spring AI upgrade notes: `ChatClient.options(...)` takes a `ChatOptions.Builder` runtime delta.

```java
// Spring AI 2.0.0-M5 pattern
@Service
public final class AnthropicByokModelClient implements ByokLlmModelClient {
    private final AnthropicChatModel parentChatModel;

    @Override
    public LlmChatResponse call(byte[] decryptedApiKey, String endpoint, String model, LlmChatRequest request) {
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
            .apiKey(new String(decryptedApiKey, StandardCharsets.UTF_8))
            .baseUrl(endpoint)  // null OK — defaults to https://api.anthropic.com
            .model(model)
            .toolChoice(new AnthropicApi.ToolChoiceAny())
            .toolCallbacks(toolCallbacks.toArray(ToolCallback[]::new))
            .internalToolExecutionEnabled(false)
            .maxTokens(1024);

        ChatResponse response = ChatClient.create(parentChatModel)
            .prompt()
            .user(request.userMessage())
            .options(options)
            .call()
            .chatResponse();
        return map(response);
    }
}
```

**Plan implication:** `ByokLlmModelClient` should expose a higher-level `call(byte[] decryptedKey, String endpoint, String model, LlmChatRequest request)` contract so each implementation can pick the correct M5 seam internally. Do not force OpenAI-compatible and Anthropic into a fake symmetric factory.

### Pattern 3: Platform OpenAI-compatible ChatClient

**What:** Singleton `ChatClient` backed by `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(...).apiKey(...).model(...).build())`.
**When to use:** Default platform path (no BYOK row).
**Source:** Verified in Context7 Spring AI docs for creating `OpenAiChatModel` with `OpenAiChatOptions`, and compile-tested after upgrading the repo to M5.

```java
@Bean
ChatClient platformChatClient(ZeroMailLlmPlatformProperties properties) {
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
            .baseUrl(properties.baseUrl())
            .apiKey(properties.apiKey())
            .model(properties.triageModel())
            .temperature(0.0)
            .build())
        .build();
    return ChatClient.create(chatModel);
}
```

**Note:** The platform key is admin-scoped, not tenant-scoped. Tenant-specific key/model/endpoint behavior belongs only in the BYOK branch.

### Pattern 4: Tool-call enforcement — `internalToolExecutionEnabled(false)`

**What:** Register tool callbacks on `ChatClient.prompt()`, set `internalToolExecutionEnabled(false)`, force `toolChoice="required"` (OpenAI string form) or `ToolChoiceAny` (Anthropic), then iterate `chatResponse.hasToolCalls()` → `chatResponse.getResult().getOutput().getToolCalls()`.
**When to use:** Always — Phase 2C never lets the model emit free text.
**Source:** Verified in [Spring AI reference — User-Controlled Tool Execution](https://docs.spring.io/spring-ai/reference/api/tools.html) and `AssistantMessage.ToolCall(id, type, name, arguments)` record per docs.

```java
// Source: https://docs.spring.io/spring-ai/reference/api/tools.html
ToolCallback labelTool = FunctionToolCallback.builder("label", new LabelToolHandler())
    .description("Add a Gmail label to the message")
    .inputType(LabelArgs.class)  // record (String value)
    .build();
ToolCallback archiveTool = FunctionToolCallback.builder("archive", new ArchiveToolHandler())
    .description("Skip inbox and archive the message")
    .inputType(ArchiveArgs.class)
    .build();
ToolCallback saveDraftTool = FunctionToolCallback.builder("save_draft", new SaveDraftToolHandler())
    .description("Save a draft reply to the message")
    .inputType(SaveDraftArgs.class)  // record (String body)
    .build();

ChatResponse response = ChatClient.create(chatModel)
    .prompt()
    .user(sanitizedContent.content().value())  // unwraps Sensitive<String>
    .options(OpenAiChatOptions.builder()
        .model(props.triageModel())
        .toolChoice("required")                // String form — OpenRouter compatible per issue #1899
        .internalToolExecutionEnabled(false)   // critical: gateway parses, not Spring AI
        .build())
    .toolCallbacks(labelTool, archiveTool, saveDraftTool)
    .call()
    .chatResponse();

if (!response.hasToolCalls()) {
    // Model returned free text despite toolChoice=required — model exploit attempt or M5 churn
    throw new SafetyViolationException();
}
AssistantMessage.ToolCall toolCall = response.getResult().getOutput().getToolCalls().getFirst();
Action action = actionValidator.validate(toolCall.name());  // throws SafetyViolationException on unknown
Map<String, Object> args = objectMapper.readValue(toolCall.arguments(), MAP_TYPE);
return new ToolCallResult(action, args);
```

### Pattern 5: jtokkit truncation with character boundary

**Source:** [JTokkit usage docs](https://github.com/knuddelsgmbh/jtokkit/blob/main/docs/docs/getting-started/usage.md).

```java
// Source: jtokkit 1.1.0 docs
EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

EncodingResult result = encoding.encode(content, 3896);  // hard cap from CONTEXT D-B4
// result.getTokens()                       — IntArrayList
// result.isTruncated()                     — boolean
// result.getLastProcessedCharacterIndex()  — int (for cleaner logging)

String truncatedContent = encoding.decode(result.getTokens());
return new SanitizationContext(
    new Sensitive<>(truncatedContent),
    result.getTokens().size(),
    result.isTruncated(),
    Map.of()  // stepMetadata for future steps
);
```

### Pattern 6: ShedLock-locked scheduled job (mirror Phase 2A `CreditReserveWatchdog`)

**Source:** `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java`.

```java
@Component
public class DriftDetectionJob {
    private static final Logger log = LoggerFactory.getLogger(DriftDetectionJob.class);
    private final DriftCheckBatch batch;
    private final boolean enabled;

    public DriftDetectionJob(DriftCheckBatch batch,
                             @Value("${zero-mail.llm.drift.enabled:false}") boolean enabled) {
        this.batch = batch;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 0 6 * * *")  // 6 AM daily
    @SchedulerLock(name = "driftDetectionJob",
                   lockAtLeastFor = "PT1M",
                   lockAtMostFor = "PT15M")
    public void scheduledTick() {
        if (!enabled) return;
        tick();
    }

    public void tick() {
        var report = batch.runGoldenSetCheck();
        log.info("event=drift_check_run total={} drifted={}",
                 report.total(), report.drifted());
    }
}
```

### Anti-Patterns to Avoid

- **Hand-rolling RestClient/HTTP for BYOK.** Keep BYOK outbound calls inside Spring AI adapter classes. The M5 implementation may build a short-lived provider model/client inside the adapter for custom OpenAI-compatible endpoints, but it must not bypass Spring AI with ad hoc `RestClient` calls to Anthropic/OpenAI HTTP endpoints.
- **Using stateless JWT for the BYOK validate endpoint authorization.** Phase 1 D-G* locked Spring Session + Redis-backed cookie sessions. ByokController endpoints use the same session — DO NOT introduce a separate JWT path.
- **Storing the validated key in browser local/sessionStorage.** Server-side AES-GCM encrypt-and-persist on the validate-then-save round trip; browser must drop the form value after submit.
- **Logging the BYOK endpoint URL.** D-I2 forbids it (could be sensitive — e.g., a self-hosted vLLM endpoint reveals internal infrastructure).
- **Persisting `prompt`/`completion`/`messageBody` field names anywhere.** ArchUnit `SafetyContractArchTests.sensitive_names_wrapped` already enforces — use `Sensitive<String>` wrapping.
- **Adding a `BYOK` member to `CallSite` enum.** Phase 2B `CallSiteEnumMembershipArchTest` actively forbids this. Skip-the-ledger logic lives in `LlmGatewayImpl`, NOT in the enum.
- **Re-entering the LLM gateway in the rules-engine compile path with the model itself.** Phase 3 will use `gateway.chat(CallSite.PREVIEW, ...)` — the only entry point. No nested LLM-from-LLM calls.
- **Putting raw bodies into Liquibase fixtures or test fixtures.** `golden-set.json` is synthetic only (CONTEXT D-H1).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multi-tenant LLM client per request | Custom factory creating new `RestClient` + `ObjectMapper` per call | Spring AI M5 provider builders: one-call `OpenAiChatModel` for OpenAI-compatible endpoints; `AnthropicChatOptions.builder().apiKey()` runtime delta for Anthropic | Keeps provider calls in Spring AI and localizes M5→GA churn to `core.llm.gateway.springai`. |
| HTML-to-text stripping | Regex-based tag stripper | `Jsoup.clean(content, Safelist.none())` | Jsoup handles malformed HTML, embedded CSS/JS, comment edge cases that hand-rolled regex misses. Already in `libs.versions.toml`. |
| Tokenization | OpenAI's tiktoken via JS or per-byte estimation | jtokkit `Encoding#encode(text, maxTokens)` | jtokkit gives exact token counts for cl100k_base + character-boundary truncation that doesn't split UTF-8 multi-byte chars / emojis. |
| AES-GCM envelope cipher | Hand-rolled `Cipher.getInstance("AES/GCM/NoPadding")` per call | Reuse `core.gmail.persistence.crypto.RefreshTokenCipher` | Already AAD-bound to tenantId, version-versioned envelope, audited for nonce reuse. |
| Tool-call argument JSON parsing | Hand-rolled regex on the arguments string | `ObjectMapper.readValue(toolCall.arguments(), MAP_TYPE)` (already on classpath via Jackson 3.x) | Spring AI returns `arguments` as JSON String per `AssistantMessage.ToolCall` record; Jackson handles escaping correctly. |
| Levenshtein distance for drift comparison | Implement Wagner-Fischer manually | Add `org.apache.commons:commons-text` for `LevenshteinDistance.getDefaultInstance().apply(s1, s2)` | Stable transitive of nothing currently in repo; `commons-text` is ~280KB single jar, zero compile risk. **Add to `libs.versions.toml`.** |
| ScheduledLock pattern for drift cron | Custom DB-row locking | `shedlock-spring 7.7.0` already in `libs.versions.toml` + `017-shedlock-table.yaml` already migrated | Phase 2A/2B already use this — DriftDetectionJob mirrors `CreditReserveWatchdog`. |
| Scope checks via `@PreAuthorize` for BYOK endpoints | Hand-rolled annotation | Spring Security 7's existing session principal check (the same the rest of the protected API uses) | All `/api/llm/byok/*` endpoints are tenant-scoped via session — no extra annotation needed. |
| ApiKey rotation drill | Custom rotation script | DEFER (CONTEXT-deferred — captured in STATE.md Blockers) | Same umbrella as `REFRESH_TOKEN_KEY_BASE64` rotation drill. Schema is already version-aware via envelope. |

**Key insight:** Spring AI M5 provides every seam Phase 2C needs (`OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(ChatOptions.Builder)`, `internalToolExecutionEnabled(false)`, `toolChoice("required")`, `ToolChoiceAny`). The Phase 2C value-add is composition — sanitization pipeline, ledger reserve, BYOK key encryption, ArchUnit isolation. **Do not extend Spring AI; wrap it.**

## Common Pitfalls

### Pitfall 1: M5 → GA churn breaks provider builder or tool-call APIs

**What goes wrong:** Spring AI 2.0.0-GA drops or renames `OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(ChatOptions.Builder)`, `internalToolExecutionEnabled`, or `ToolCall` record fields. Production stops compiling on a routine BOM upgrade.

**Why it happens:** M5 → GA can rename builder/runtime-option methods while the milestone line settles.

**How to avoid:**
- ArchUnit rule isolates ALL Spring AI imports to `core.llm.gateway.springai.*` and `core.llm.gateway.sanitization.*` (sanitization needs jtokkit + Jsoup, not Spring AI). One package, ~5 classes — when GA lands, only this surface needs updating.
- Pin BOM version explicitly in `libs.versions.toml`; do NOT float to latest.
- During any version bump, re-run Context7 lookup against the target docs and compile the focused Spring AI adapter tests before changing wider code.

**Warning signs:** New build error mentioning `OpenAiChatModel.Builder`, `OpenAiChatOptions.Builder`, `ChatClient.options`, or `internalToolExecutionEnabled`.

### Pitfall 2: `toolChoice="required"` ignored by upstream provider routed via OpenRouter

**What goes wrong:** Some non-OpenAI providers behind OpenRouter (e.g., certain open-source models) silently ignore `toolChoice="required"` and emit free text instead of a tool call. `chatResponse.hasToolCalls()` returns false; gateway throws `SafetyViolationException` on every call.

**Why it happens:** OpenRouter passes `toolChoice` through but cannot enforce on the underlying model.

**How to avoid:**
- Layer 2 (`ActionValidator`) IS the defense — `SafetyViolationException` thrown when `hasToolCalls()` is false fails closed. This is the correct behavior; the operator sees the error code and either changes the platform model or accepts that this provider is unusable for triage.
- During drift-baseline generation (one-shot, build-time), require all golden-set fixtures to produce `hasToolCalls() == true` — surfaces incompatible models early.

**Warning signs:** `event=llm_safety_violation` log spike when switching `compileModel` or `triageModel` config.

### Pitfall 3: Sanitization pipeline order matters — Jsoup AFTER NFC normalization is insufficient

**What goes wrong:** If NFC normalization runs BEFORE Jsoup, malicious Unicode-encoded HTML (`%uFEFF<script>`) bypasses Jsoup's tag detection.

**Why it happens:** Jsoup's safelist parser doesn't handle pre-composed Unicode-encoded HTML entities the same way it handles UTF-8.

**How to avoid:** Lock the order at `@Order(10)` Jsoup → `@Order(20)` NFC → `@Order(30)` tag-strip → `@Order(40)` truncate, exactly as CONTEXT D-B1 specifies. Add an integration test that feeds a Unicode-encoded `<script>` into the pipeline and asserts the output has no `<` byte.

**Warning signs:** Any `@Order(<10)` sanitizer added later that runs before Jsoup.

### Pitfall 4: `TenantContext.currentOrThrow()` inside async / virtual-thread spawn

**What goes wrong:** `LlmGateway.chat(...)` is called from a request that fans out to a worker via raw `Thread.ofVirtual().start(...)`; the new thread has no `TenantContext` binding; gateway's `PlatformApiKey.getValue()` (or any tenantId-based logging) throws.

**Why it happens:** ScopedValue does NOT propagate through raw thread spawns. Phase 1 D-B3 introduced `TenantAwareTaskScope` for this exact reason.

**How to avoid:**
- ArchUnit rule from Phase 1 already forbids `Thread.ofVirtual().start(...)` outside the allow-listed scope helper.
- Drift detection job runs in `backend/worker` which has NO request-scope tenant binding to start with — so `DriftDetectionJob.tick()` must explicitly bind `TenantContext` via `ScopedValue.where(TenantContext.TENANT, syntheticDriftTenantId).run(...)` before calling `gateway.driftCheck(...)`.

**Warning signs:** `IllegalStateException: TenantContext not bound` in worker logs during drift run.

### Pitfall 5: `tenant_byok_credentials` insert without acquiring tenant write lock

**What goes wrong:** Two concurrent `POST /api/llm/byok` calls (e.g., user double-clicks) both pass validate-then-save, both insert; UNIQUE on `tenant_id` causes one to fail with `DataIntegrityViolationException`; user sees `500` instead of `409 Conflict`.

**Why it happens:** No application-level concurrency control on the write path.

**How to avoid:**
- Add `@ExceptionHandler(DataIntegrityViolationException.class)` mapping to `409` in `GlobalExceptionHandler` (already exists from Phase 2B's `DataIntegrityViolationException → 409` for SePay replay).
- Service-level: use `INSERT ... ON CONFLICT (tenant_id) DO UPDATE SET ...` (PostgreSQL upsert) — supported by Liquibase via `sql:` step or by Spring Data JPA via `@SQLInsert`.

**Warning signs:** Sporadic 500s with `DataIntegrityViolationException` and `uq_tenant_byok_credentials_tenant_id` in stack trace.

### Pitfall 6: BYOK plaintext key bytes lingering in heap after gateway call

**What goes wrong:** `OpenAiChatOptions.builder().apiKey(plaintextKeyString).build()` retains the string in the per-call `OpenAiChatModel` options; a parent/client reference or response object could keep the key string reachable until GC.

**Why it happens:** Java strings are interned; `String#getBytes()` does not zero original char[]. There is no Java idiom for true secret zeroing on String.

**How to avoid:**
- Do NOT cache derived `ChatClient` per tenant in Phase 2C (CONTEXT D-A4 — caching deferred to Phase 4). Each call re-derives, so the derived client falls out of scope at end of `chat()` method.
- Treat heap presence between `decrypt → build provider options → call → return` as acceptable for BYOK. Hardening the Java string handling is a Phase 6 / dedicated security hardening exercise.
- DO NOT log the key. DO NOT include it in toString of any record.

**Warning signs:** Heap dump in production showing reachable plaintext key strings older than active request.

## Code Examples

### Example 1: Sanitization pipeline composition (D-B1)

```java
// Source: idiomatic Spring + CONTEXT D-B1
public interface Sanitizer {
    SanitizationContext apply(SanitizationContext context);
}

@Component
@Order(10)
public class JsoupHtmlStripSanitizer implements Sanitizer {
    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String stripped = Jsoup.clean(context.content().value(), Safelist.none());
        return context.withContent(new Sensitive<>(stripped));
    }
}

@Component
@Order(20)
public class NfcNormalizeSanitizer implements Sanitizer {
    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String normalized = Normalizer.normalize(context.content().value(), Form.NFC);
        return context.withContent(new Sensitive<>(normalized));
    }
}

@Component
@Order(30)
public class UnicodeTagStripSanitizer implements Sanitizer {
    private static final Pattern TAG_CHARS = Pattern.compile("[\\x{E0000}-\\x{E007F}]");

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        String stripped = TAG_CHARS.matcher(context.content().value()).replaceAll("");
        return context.withContent(new Sensitive<>(stripped));
    }
}

@Component
@Order(40)
public class JtokkitTruncateSanitizer implements Sanitizer {
    private static final int HARD_CAP = 3896;
    private final Encoding encoding =
        Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public SanitizationContext apply(SanitizationContext context) {
        EncodingResult result = encoding.encode(context.content().value(), HARD_CAP);
        String truncated = encoding.decode(result.getTokens());
        return new SanitizationContext(
            new Sensitive<>(truncated),
            result.getTokens().size(),
            result.isTruncated(),
            context.stepMetadata()
        );
    }
}

@Service
public class SanitizationPipeline {
    private final List<Sanitizer> sanitizers;  // Spring auto-sorts by @Order

    public SanitizationPipeline(List<Sanitizer> sanitizers) {
        this.sanitizers = sanitizers;
    }

    public SanitizationContext sanitize(String rawHtml) {
        SanitizationContext initial = new SanitizationContext(
            new Sensitive<>(rawHtml), 0, false, Map.of());
        SanitizationContext result = initial;
        for (Sanitizer step : sanitizers) {
            try {
                result = step.apply(result);
            } catch (RuntimeException stepFailure) {
                throw new SanitizationException(step.getClass().getSimpleName(), stepFailure);
            }
        }
        return result;
    }
}
```

### Example 2: ArchUnit boundary rule for `core.llm` (mirror BillingDomainBoundaryArchTest)

```java
// Source: backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java pattern
@Test
void spring_ai_only_in_gateway_springai() {
    var importedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.zeromail");

    noClasses()
            .that().resideOutsideOfPackage("..core.llm.gateway.springai..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.ai..")
            .because("LLM-01: Spring AI imports isolated to one adapter package")
            .check(importedClasses);
}

@Test
void jsoup_jtokkit_only_in_sanitization() {
    var importedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.zeromail");

    noClasses()
            .that().resideOutsideOfPackage("..core.llm.gateway.sanitization..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.jsoup..", "com.knuddels.jtokkit..")
            .because("Phase 2C: sanitization library imports isolated for swap-ability")
            .check(importedClasses);
}

@Test
void core_llm_only_depends_on_allowed_packages() {
    var importedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.zeromail");

    classes()
            .that().resideInAPackage("..core.llm..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..core.llm..",
                    "..core.config..",
                    "..core.tenant..",
                    "..core.billing..",
                    "..core.gmail.persistence.crypto..",  // RefreshTokenCipher reuse
                    "..core.shared.persistence..",
                    "..core.shared.lang..",
                    "..core.shared.privacy..",            // Sensitive<T>
                    "java..",
                    "jakarta..",
                    "org.springframework..",
                    "org.springframework.ai..",
                    "org.jsoup..",
                    "com.knuddels.jtokkit..",
                    "org.hibernate..",
                    "org.slf4j..",
                    "tools.jackson..")
            .because("Modulith boundary for core.llm")
            .check(importedClasses);
}
```

### Example 3: BYOK form (uncontrolled inputs)

```tsx
// Source: React 19 uncontrolled-input idiom + CONTEXT D-D2 / D-D4
"use client";
import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useValidateByok, useSaveByok } from "../hooks/use-byok";

export function ByokForm() {
  const formRef = useRef<HTMLFormElement>(null);
  const [provider, setProvider] = useState<"anthropic" | "openai-compatible">("openai-compatible");
  const validate = useValidateByok();
  const save = useSaveByok();

  const onValidate = () => {
    const form = formRef.current;
    if (!form) return;
    const apiKey = (form.elements.namedItem("apiKey") as HTMLInputElement).value;
    const endpoint = provider === "openai-compatible"
      ? (form.elements.namedItem("endpoint") as HTMLInputElement).value
      : undefined;
    // Raw key passed once, then dropped from this scope.
    validate.mutate({ provider, apiKey, endpoint });
  };

  const onSave = () => {
    const form = formRef.current;
    if (!form || validate.data?.ok !== true) return;
    const apiKey = (form.elements.namedItem("apiKey") as HTMLInputElement).value;
    const endpoint = provider === "openai-compatible"
      ? (form.elements.namedItem("endpoint") as HTMLInputElement).value
      : undefined;
    save.mutate({ provider, apiKey, endpoint }, {
      onSuccess: () => {
        form.reset();
        validate.reset();
      },
    });
  };

  return (
    <Card className="p-6">
      <form ref={formRef} onSubmit={(e) => e.preventDefault()}>
        <RadioGroup value={provider} onValueChange={(v) => setProvider(v as typeof provider)}>
          <div className="flex items-center gap-2">
            <RadioGroupItem value="openai-compatible" id="provider-oai" />
            <Label htmlFor="provider-oai">OpenAI Compatible</Label>
          </div>
          <div className="flex items-center gap-2">
            <RadioGroupItem value="anthropic" id="provider-anthropic" />
            <Label htmlFor="provider-anthropic">Anthropic</Label>
          </div>
        </RadioGroup>

        {provider === "openai-compatible" && (
          <Input name="endpoint" type="text" placeholder="https://openrouter.ai/api/v1"
                 autoComplete="off" />
        )}
        <Input name="apiKey" type="password" placeholder="sk-..."
               autoComplete="off" />

        <div className="flex gap-2">
          <Button type="button" onClick={onValidate} disabled={validate.isPending}>
            Validate
          </Button>
          <Button type="button" onClick={onSave}
                  disabled={validate.data?.ok !== true || save.isPending}>
            Save
          </Button>
        </div>

        {validate.data?.ok === true && (
          <Alert variant="default">
            <AlertDescription>
              {validate.data.models?.length
                ? `Validated. ${validate.data.models.length} models available.`
                : "Validated successfully."}
            </AlertDescription>
          </Alert>
        )}
        {validate.data?.ok === false && (
          <Alert variant="destructive">
            <AlertDescription>{validate.data.reason ?? "Validation failed"}</AlertDescription>
          </Alert>
        )}
      </form>
    </Card>
  );
}
```

### Example 4: BYOK Liquibase changeset (renumbered to 018)

```yaml
# backend/core/src/main/resources/db/changelog/changes/018-tenant-byok-credentials.yaml
databaseChangeLog:
  - changeSet:
      id: 018-tenant-byok-credentials
      author: zeromail
      changes:
        - createTable:
            tableName: tenant_byok_credentials
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints: { primaryKey: true, nullable: false }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
                    foreignKeyName: fk_tenant_byok_credentials_tenant
                    references: tenants(id)
                    deleteCascade: true
              - column:
                  name: provider
                  type: varchar(32)
                  constraints: { nullable: false }
              - column:
                  name: endpoint
                  type: varchar(512)
                  constraints: { nullable: true }
              - column:
                  name: encrypted_key
                  type: bytea
                  constraints: { nullable: false }
              - column:
                  name: key_version
                  type: smallint
                  constraints: { nullable: false }
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints: { nullable: false }
              - column:
                  name: updated_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints: { nullable: false }
        - addUniqueConstraint:
            tableName: tenant_byok_credentials
            columnNames: tenant_id
            constraintName: uq_tenant_byok_credentials_tenant_id
        - sql:
            comment: Check constraint for the allowed BYOK providers.
            sql: ALTER TABLE tenant_byok_credentials ADD CONSTRAINT ck_tenant_byok_credentials_provider CHECK (provider IN ('anthropic','openai-compatible'))
      rollback:
        - dropTable:
            tableName: tenant_byok_credentials
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Legacy prompt-observation key | `spring.ai.chat.client.observations.log-prompt` | Spring AI 1.1.x line (carried into 2.0.0-M5) | Pin the new key in `application.yml`. Default false is not the privacy control; explicit false is. |
| Legacy completion-observation key | `spring.ai.chat.client.observations.log-completion` | Same | Same |
| `FunctionCallback.builder().function(name, handler)` | `FunctionToolCallback.builder(name, handler)` | Spring AI 1.0 → M3 | Use `FunctionToolCallback` to register tool-callbacks (Spring AI deprecated FunctionCallback). |
| Per-request raw OpenAI HTTP client rebuild | M5 one-call `OpenAiChatModel` with `OpenAiChatOptions.builder().apiKey(...).baseUrl(...).model(...)` inside adapter | Spring AI 2.0.0-M5 | Keeps BYOK custom endpoint/model selection inside Spring AI without ad hoc HTTP. |
| Anthropic per-request key via `httpHeaders(Map.of("x-api-key", key))` | `AnthropicChatOptions.builder().apiKey(key).baseUrl(url)` | Spring AI 1.1.x | Cleaner; Spring AI sets the correct `x-api-key` header internally. |

**Deprecated/outdated:**
- **Spring AI `FunctionCallback`**: deprecated in M3 in favor of `FunctionToolCallback`. Do not use.
- **Passing built options into `ChatClient.prompt().options(...)` on M5+**: use the builder for ChatClient runtime deltas. Built options are still valid for `Prompt` construction or model defaults, but `ChatClient.options(...)` expects `ChatOptions.Builder` in M5+.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring AI 2.0.0-GA will retain the M5 provider-builder seams used by `OpenAiCompatibleByokModelClient` and `AnthropicByokModelClient` | Pattern 1, Pitfall 1 | Plan-time risk; ArchUnit isolation makes the migration localized. Re-fetch via Context7 and run adapter tests before any version bump. |
| A2 | `AnthropicChatOptions.builder().apiKey(...).baseUrl(...).model(...)` per-request override remains idiomatic for native Anthropic BYOK | Pattern 2 | LOW — verified by M5 compile/tests and Context7 upgrade notes. |
| A3 | `commons-text` (~280KB) is the simplest Levenshtein dep choice | Don't Hand-Roll table | LOW — alternative is hand-rolled Wagner-Fischer (~30 LOC). Plan-phase can choose either. |
| A4 | OpenRouter `/v1/models` endpoint requires API key (not public) — works for BYOK Validate | LLM-03 support | LOW — confirmed via OpenRouter docs (Sources). |
| A5 | Anthropic `/v1/messages` with `max_tokens: 1` is a valid no-token-cost validation probe | LLM-03 support | LOW — confirmed via Anthropic docs (Sources). Note: `max_tokens=1` still costs the input prompt tokens (negligible for "test" payload). |
| A6 | jtokkit `cl100k_base` ±10–20% accuracy on Anthropic models is acceptable per SPEC.md constraint | LLM-08 | Already locked by SPEC. |
| A7 | `RefreshTokenCipher` relocation from `core.gmail.persistence.crypto` to `core.shared.crypto` should be DEFERRED (not done in 2C) | Don't Hand-Roll table | LOW — researcher recommendation; planner can override. The Modulith allowedDependencies edge `core.llm → core.gmail.persistence.crypto` is the cost of deferral; it is one extra entry in the allow-list. |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed. **(Not the case here — A1 has real M5→GA churn risk; mitigation = re-verify on any Spring AI version bump.)**

## Open Questions (RESOLVED)

1. **`PlatformApiKey` placement: `core.llm.gateway.springai` package vs. its own subpackage?**
   - What we know: Single class implementing `org.springframework.ai.openai.api.ApiKey`. ArchUnit allows it inside `core.llm.gateway.springai.*`.
   - What's unclear: Whether to put it in a `auth` sub-package for organization.
   - RESOLVED: Top-level of `core.llm.gateway.springai/` is fine — it's only one class. Defer sub-package split until N≥3 auth/key implementations exist.

2. **`tenant_byok_credentials.endpoint` for Anthropic provider — null vs. fixed `https://api.anthropic.com`?**
   - What we know: CONTEXT D-G1 spec is "endpoint VARCHAR(512) NULL (only set for openai-compatible)". For Anthropic provider, the gateway needs a baseUrl somewhere.
   - What's unclear: If null, the `AnthropicByokFactory` falls back to either `application.yml` config OR Spring AI default. Either is acceptable; pick one and lock.
   - RESOLVED: Anthropic factory uses Spring AI's default baseUrl when `endpoint` is null (Spring AI defaults to `https://api.anthropic.com`). NEVER read this from user-controlled config — keep the Anthropic baseUrl hard-coded to the SDK default unless `endpoint` is explicitly provided AND validated to match `*.anthropic.com` or a known Anthropic-compatible proxy.

3. **`golden-baseline.json` regeneration semantics?**
   - What we know: D-H2 says "generated once at scaffold-build time by running golden-set through gateway against pinned `driftModel`; committed to repo".
   - What's unclear: When `driftModel` changes (e.g., GPT-4o-mini → GPT-4o), regenerate the baseline? Manual? CI?
   - RESOLVED: Phase 2C ships a `BaselineRegenerationCli` Spring Boot ApplicationRunner (executed via `./gradlew :backend-core:bootRun --args='--spring.profiles.active=baseline-regen'`) that runs the golden-set, writes the baseline JSON, exits. Operator runs manually when changing `driftModel`. NOT a CI step (would burn LLM tokens on every build).

4. **i18n key spelling for `error.llm.*` and `byok.*`?**
   - What we know: Phase 1.1 locked the `code` field on `ApiError` as dotted-camel (e.g., `error.auth.unauthorized`).
   - What's unclear: Specific keys.
   - RESOLVED: Plan-phase locks: `error.llm.safety_violation`, `error.llm.sanitization_failed`, `error.llm.byok_validate_failed`, `error.llm.byok_save_conflict` (409), `byok.title`, `byok.provider.openai_compatible`, `byok.provider.anthropic`, `byok.endpoint.label`, `byok.endpoint.placeholder`, `byok.api_key.label`, `byok.validate.button`, `byok.save.button`, `byok.validate.success_with_models`, `byok.validate.success_no_models`. Vietnamese copy by frontend-design skill at execute-phase.

5. **Phase 3/4 dependency: when does Phase 2C need to be plan-checkable as "not blocked by Phase 3/4"?**
   - What we know: Phase 3 calls `gateway.chat(CallSite.PREVIEW, ...)`; Phase 4 calls `gateway.chat(CallSite.TRIAGE, ...)`. Phase 2C is a hard gate for both.
   - What's unclear: Does Phase 2C ship a no-op stub triage caller for integration testing?
   - RESOLVED: NO. Phase 2C tests use `MockBean ChatModel` — pure Spring AI mock. The gateway's `chat()` method is exercised by golden-set drift fixtures + dedicated MockMvc/RestClient integration tests. No "stub Phase 4 caller" exists.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 LTS | Build/runtime | ✓ | (project toolchain) | — |
| PostgreSQL 17.6 | `tenant_byok_credentials` schema | ✓ | (Testcontainers in CI; VPS install in prod) | — |
| Spring AI 2.0.0-M5 starter on Spring Milestones repo | LLM gateway | ✓ | 2.0.0-M5 | None — locked by user directive |
| jtokkit 1.1.0 on Maven Central | Sanitization pipeline truncate step | ✓ | 1.1.0 | None — only stable Java tokenizer for cl100k_base; no viable alt |
| ShedLock 7.7.0 | DriftDetectionJob lock | ✓ | (already in libs.versions.toml) | — |
| Jsoup 1.22.2 | Sanitization HTML strip | ✓ | (already in libs.versions.toml) | — |
| OpenRouter API access (for live integration tests) | NOT a build/CI dep — used only in manual end-to-end smoke tests | n/a | n/a | WireMock for unit/integration; live calls are operator-side smoke testing |
| Anthropic API access | Same | n/a | n/a | Same |
| `commons-text` (Levenshtein) | Drift comparison | ✗ | — | Add to `libs.versions.toml` (~280KB jar) OR hand-roll ~30 LOC Wagner-Fischer in `core.llm.drift`. Plan-phase chooses. |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** `commons-text` — fallback is a 30-LOC hand-rolled Levenshtein. Recommend adding the dep for clarity + future PII-similarity / fuzzy-matching needs.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test 4.0.6 + Testcontainers 1.21.3 + ArchUnit 1.4.2 + Vitest (frontend) + Playwright (UI flows) |
| Config file | `backend/core/build.gradle.kts` (test config), `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| Quick run command (backend) | `./gradlew :backend-core:test --tests '*Llm*'` |
| Quick run command (frontend) | `pnpm --filter web test --run features/llm` |
| Full suite command | `./gradlew check && pnpm -w test && pnpm -w typecheck` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LLM-00 | `ZEROMAIL_LLM_PLATFORM_API_KEY` unset → boot fails with clear stderr | integration | `./gradlew :backend-api:test --tests 'PlatformApiKeyFailFastTest'` | ❌ Wave 0 |
| LLM-01 | ArchUnit denies Spring AI imports outside gateway package | arch | `./gradlew :backend-core:test --tests 'LlmGatewayBoundaryArchTest'` | ❌ Wave 0 |
| LLM-02 | Gateway picks `compileModel` for `CallSite.PREVIEW`, `triageModel` for `CallSite.TRIAGE` | unit | `./gradlew :backend-core:test --tests 'LlmGatewayImplModelResolutionTest'` | ❌ Wave 0 |
| LLM-03 | BYOK Validate flow — OpenAI-compat success returns models, bad-key returns ok:false | integration (WireMock) | `./gradlew :backend-api:test --tests 'ByokValidationIntegrationTest'` | ❌ Wave 0 |
| LLM-03 | BYOK form Playwright smoke — paste/validate/save round trip | e2e | `pnpm --filter web exec playwright test e2e/byok-flow.spec.ts` | ❌ Wave 0 |
| LLM-04 | BYOK row exists → gateway uses BYOK key + skips ledger reserve; no row → uses platform + decrements ledger | integration | `./gradlew :backend-core:test --tests 'LlmGatewayBYOKRoutingIntegrationTest'` | ❌ Wave 0 |
| LLM-04 | `RefreshTokenCipher` AES-GCM AAD binding rejects cross-tenant decrypt | unit | `./gradlew :backend-core:test --tests 'RefreshTokenCipherCrossTenantTest'` | already covered by Phase 1 (verify) |
| LLM-05 | `Jsoup.clean(<script>...</script><p>hi</p>, none)` → `hi` | unit | `./gradlew :backend-core:test --tests 'JsoupHtmlStripSanitizerTest'` | ❌ Wave 0 |
| LLM-06 | NFC normalization + tag-strip (U+E0041) test | unit | `./gradlew :backend-core:test --tests 'NfcNormalizeSanitizerTest', 'UnicodeTagStripSanitizerTest'` | ❌ Wave 0 |
| LLM-07 | Mock `ChatResponse` with `name="send"` → `SafetyViolationException`; `name="label"` → success | unit | `./gradlew :backend-core:test --tests 'ActionValidatorTest', 'LlmGatewayImplToolCallParseTest'` | ❌ Wave 0 |
| LLM-08 | 10k-token input → ≤4096-token output, character-boundary truncation | unit | `./gradlew :backend-core:test --tests 'JtokkitTruncateSanitizerTest'` | ❌ Wave 0 |
| LLM-09 | Spring AI observation spans contain no prompt/completion content | integration | `./gradlew :backend-core:test --tests 'LlmGatewayObservationPrivacyTest'` | ❌ Wave 0 |
| LLM-09 | ArchUnit: no `*Repository` accepts `prompt`/`completion`/`emailBody` parameter | arch | extend Phase 1 `SafetyContractArchTests` (already enforces field deny-list) | ❌ Wave 0 (extension) |
| LLM-10 | Tenant 0 credits + no BYOK → 402 from any gateway-fronted endpoint | integration | `./gradlew :backend-api:test --tests 'LlmGatewayCreditCapIntegrationTest'` | ❌ Wave 0 |
| LLM-11 | DriftDetectionJob with mocked baseline match → `driftCount=0`; mocked drift → flagged | integration | `./gradlew :backend-worker:test --tests 'DriftDetectionJobTest'` | ❌ Wave 0 |
| LLM-11 | DriftDetectionJob is `@Scheduled` but gated on `enabled=false` (default) | unit | (annotation reflection check inside the above test) | — |

### Sampling Rate

- **Per task commit:** `./gradlew :backend-core:test --tests '*Llm*'` + `pnpm --filter web test --run features/llm` (≤30s combined for unit subset).
- **Per wave merge:** `./gradlew :backend-core:test :backend-api:test :backend-worker:test` + `pnpm -w test` (full suite).
- **Phase gate:** Full suite green + ArchUnit boundary tests + Playwright BYOK e2e + manual smoke check that `application.yml` boot fails on missing `ZEROMAIL_LLM_PLATFORM_API_KEY` before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/llm/LlmGatewayBoundaryArchTest.java` — covers LLM-01.
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/{Jsoup,Nfc,UnicodeTag,JtokkitTruncate}SanitizerTest.java` — covers LLM-05, LLM-06, LLM-08.
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipelineIntegrationTest.java` — covers ordered fold + fail-fast.
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorTest.java` — covers LLM-07 layer 2.
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/gateway/springai/LlmGatewayImpl{ModelResolution,ToolCallParse,BYOKRouting,CreditCap,ObservationPrivacy}Test.java` — covers LLM-02, LLM-04, LLM-07, LLM-09, LLM-10.
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokValidationIntegrationTest.java` — covers LLM-03 backend.
- [ ] `backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobTest.java` — covers LLM-11.
- [ ] `apps/web/e2e/byok-flow.spec.ts` — covers LLM-03 e2e.
- [ ] `apps/web/__tests__/features/llm/use-byok.test.ts` — covers TanStack hook contract.
- [ ] `backend/api/src/test/.../PlatformApiKeyFailFastTest.java` — covers LLM-00.
- [ ] Test fixtures: `golden-set.json` + `golden-baseline.json` + WireMock fixtures for OpenRouter `/v1/models` + Anthropic `/v1/messages`.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | ArchUnit isolation of Spring AI imports + per-domain Modulith package shape; PR template requires reviewer to confirm new BYOK code stays inside `core.llm.gateway.springai` |
| V2 Authentication | partial | Existing Spring Security session — BYOK endpoints reuse session principal; no new auth surface |
| V3 Session Management | yes | Reuse existing Spring Session (Redis-backed); BYOK validate/save endpoints sit on the same session — no JWT, no Bearer |
| V4 Access Control | yes | All `/api/llm/byok/*` are tenant-scoped via session principal + `TenantContext.currentOrThrow()`; multi-tenant leak test mirrors `MultiTenantLeakIntegrationTest` |
| V5 Input Validation | yes | DTO validation via Jakarta Validation 3.1 (`@NotNull`, `@Size`, `@Pattern`); `BYOKProvider.fromId` + `Action.fromId` fail-loud on invalid enum input; Jsoup `Safelist.none()` is the canonical HTML allow-list |
| V6 Cryptography | yes | Reuse `RefreshTokenCipher` AES-GCM-256 with version envelope + tenantId AAD; `REFRESH_TOKEN_KEY_BASE64` 32-byte-base64 already validated in Phase 1; **NEVER hand-roll** |
| V7 Error Handling | yes | `GlobalExceptionHandler` adds `SafetyViolationException → 500 LLM_SAFETY_VIOLATION`, `SanitizationException → 500 LLM_SANITIZATION_FAILED`; no exception messages contain content |
| V8 Data Protection | yes | `Sensitive<String>` ArchUnit deny-list already enforces `prompt`/`completion`/`body` field types; pin `spring.ai.chat.client.observations.log-{prompt,completion}=false` defensively |
| V9 Communications | yes | All outbound LLM calls are HTTPS — Spring AI's RestClient enforces; BYOK endpoint validation rejects non-HTTPS scheme via `@Pattern(regexp="^https://...")` on the `endpoint` DTO field |
| V10 Malicious Code | yes | Tool-call allow-list `{label, archive, save_draft}` with two-layer enforcement (toolChoice=required + post-parse ActionValidator); Action.send is **not in the enum**, so `fromId("send")` fails loud |
| V12 Files and Resources | partial | `golden-set.json` is shipped read-only as classpath resource; no user-controlled file paths |
| V13 API and Web Service | yes | OpenAPI spec includes new `/api/llm/byok/*` endpoints; CORS allow-list pinned via existing `ZEROMAIL_CORS_*` env; CSRF token applied (POST endpoints) |
| V14 Configuration | yes | `:?` fail-fast for `ZEROMAIL_LLM_PLATFORM_API_KEY` in both `backend/api` and `backend/worker` `application.yml`; mirror Phase 1.5 CR-04 + Phase 2B D-F1 pattern |

### Known Threat Patterns for Spring Boot 4 + Spring AI M5 + Multi-Tenant LLM

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection — adversary-crafted email body steers model to call `send` | Tampering | Sanitization pipeline (Jsoup → NFC → tag-strip → truncate) + tool-choice=required + post-parse ActionValidator (defense-in-depth, two layers) |
| Cross-tenant key leak — Tenant A's BYOK key used for Tenant B's call | Information Disclosure | `RefreshTokenCipher` AAD = `tenantId.getBytes(UTF_8)` makes cross-tenant decrypt fail with `AEADBadTagException`; gateway resolves BYOK row via `tenantId = TenantContext.currentOrThrow()`; multi-tenant leak integration test |
| BYOK key plaintext in spans/logs | Information Disclosure | `spring.ai.chat.client.observations.log-prompt=false` + `Sensitive<String>` ArchUnit deny-list + Logback `SensitiveMarkerScrubFilter` |
| BYOK key plaintext in heap dump | Information Disclosure | Accepted residual risk in 2C — derived ChatClient is short-lived (no Caffeine cache); Phase 6 / dedicated security hardening can revisit secret-zero approaches |
| Validate-endpoint SSRF — adversary supplies internal IP as endpoint | Information Disclosure / Denial | DTO validation `@Pattern(regexp="^https://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")` + reject `localhost`, `127.0.0.1`, `10.*`, `172.16-31.*`, `192.168.*`, `169.254.*` server-side before issuing the validate call |
| OpenRouter response replay — attacker MITMs response to make tool-call appear "label" when it was "send" | Spoofing / Tampering | HTTPS + cert validation (Spring AI default); two-layer enforcement also catches malformed responses |
| Credit-cap bypass via concurrent calls | Authorization | Phase 2B `pg_advisory_xact_lock(hashtext(tenant_id))` + `Propagation.REQUIRES_NEW` on `reserve` already enforce serialization |
| Drift detection feedback loop — operator changes baseline to mask real drift | Repudiation | `golden-baseline.json` is committed to git; baseline regen requires git commit; reviewer checks diff |
| Unbounded prompt size DoS | Denial of Service | jtokkit hard-cap 3896 tokens before send; Jsoup parses HTML in bounded memory |
| Spring AI M5 → GA churn breaks gateway silently | Tampering / Denial | ArchUnit isolation + version pin in BOM + Context7 re-fetch on version bump + integration tests covering tool-call parsing path |

## Sources

### Primary (HIGH confidence) — Context7-fetched + GitHub source verification

- **Spring AI 2.0.0-M5 release notes**: `https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M5` — current project baseline.
- **Spring AI 2.0.0-M5 announcement**: `https://spring.io/blog/2026/04/27/spring-ai-1-0-6-1-1-5-2-0-0-M5-available-now/` — confirms the M5 release line.
- **Spring AI source/docs via Context7 `/spring-projects/spring-ai`** — verified M5+ `ChatClient.options(...)` builder runtime-delta semantics and `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().apiKey(...).model(...).build())` pattern.
- **Spring AI reference docs — OpenAI Chat**: `https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html` — OpenAI-compatible properties and `OpenAiChatOptions`.
- **Spring AI reference docs — Tools (User-Controlled Execution)**: `https://docs.spring.io/spring-ai/reference/api/tools.html` — `internalToolExecutionEnabled(false)` + `chatResponse.hasToolCalls()` parsing pattern.
- **Spring AI reference docs — Anthropic Chat (Tool Choice)**: `https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html` — `ToolChoiceAny`, `ToolChoiceTool`, `ToolChoiceAuto`, `ToolChoiceNone` enum values.
- **Spring AI reference docs — Observability**: `https://docs.spring.io/spring-ai/reference/observability/index.html` — `spring.ai.chat.client.observations.log-prompt: false` (default) + `log-completion: false` (default); older prompt/completion observation key names are deprecated.
- **Spring AI reference docs — Upgrade Notes**: `https://docs.spring.io/spring-ai/reference/upgrade-notes.html` — confirms M5+ `ChatClient.options(...)` builder semantics and the observation key history.
- **JTokkit usage docs**: `https://github.com/knuddelsgmbh/jtokkit/blob/main/docs/docs/getting-started/usage.md` — `Encoding#encode(String, int)` with character-boundary truncation; latest 1.1.0.
- **JTokkit Maven Central listing**: `https://central.sonatype.com/artifact/com.knuddels/jtokkit` — version 1.1.0 confirmed latest.
- **OpenRouter API docs — List Models**: `https://openrouter.ai/docs/api/api-reference/models/get-models` — `GET /api/v1/models` requires Bearer auth.
- **OpenRouter API docs — Authentication**: `https://openrouter.ai/docs/api/reference/authentication` — Bearer token via `Authorization` header.
- **Anthropic Messages API docs**: `https://platform.claude.com/docs/en/api/messages` — minimal POST body shape, `max_tokens=1` valid; required headers `x-api-key`, `anthropic-version`, `content-type`.
- **ShedLock README**: `https://github.com/lukas-krecan/ShedLock` — version 7.x compatibility matrix (Spring 7.0, Boot 4.x, JVM 17+).
- **Spring AI `AssistantMessage.ToolCall` JavaDoc**: `https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/messages/AssistantMessage.ToolCall.html` — record with components `id`, `type`, `name`, `arguments` (String JSON).
- **Spring AI `OpenAiChatOptions.Builder` JavaDoc (1.1.x)**: `https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatOptions.Builder.html` — `toolChoice(Object)` accepts String.
- **Spring AI Issue #1899**: `https://github.com/spring-projects/spring-ai/issues/1899` — `toolChoice` as Object resolved.
- **Spring AI Issue #477**: `https://github.com/spring-projects/spring-ai/issues/477` — historical dynamic API-key discussion; M5 implementation now uses provider builder/options seams.
- **Spring AI Issue #3409**: `https://github.com/spring-projects/spring-ai/issues/3409` — `OpenAiChatOptions.httpHeaders` for Authorization Bearer override pattern.

### Secondary (MEDIUM confidence) — In-repo verification

- **`backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java`** — interface contract verified, BYOK exemption Javadoc matches CONTEXT D-A2.
- **`backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java`** — TRIAGE/DRAFT/PREVIEW members; `IdentifiedEnum.fromId()` fail-loud verified.
- **`backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java`** — AES-GCM envelope `[key_version:int32 | nonce:12 | ciphertext]` + tenantId AAD verified.
- **`backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`** — confirms `017-shedlock-table.yaml` is taken; **Phase 2C BYOK changeset must be `018+`**.
- **`backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java`** — ArchUnit boundary template.
- **`backend/core/src/test/java/com/zeromail/core/arch/SafetyContractArchTests.java`** — `Sensitive<T>` deny-list `prompt`/`completion`/`body` already enforced.
- **`backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java`** — `@Scheduled(fixedRate=...)` + `@SchedulerLock(name=..., lockAtLeastFor=PT30S, lockAtMostFor=PT2M)` template for `DriftDetectionJob`.
- **`backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`** — `InsufficientCreditsException → 402` already mapped (line 130); `DataIntegrityViolationException → 409` already mapped.
- **`backend/api/src/main/resources/application.yml`** + **`backend/worker/src/main/resources/application.yml`** — `:?` fail-fast pattern verified for `REFRESH_TOKEN_KEY_BASE64` (line 74 / 29) and `SEPAY_WEBHOOK_API_KEY` (line 83 / 36).

### Tertiary (LOW confidence — single source, marked for re-verification at execute-phase)

- React 19 uncontrolled input idiom — sourced from web search aggregate; pattern is well-established but no canonical "official" doc. Mitigation: code example in this RESEARCH is straightforward; reviewer can confirm.
- WireMock-based BYOK validate fixture format — researcher recommends synthesizing from OpenRouter response shape; live OpenRouter call during smoke test confirms.

## Plan Implications

Concrete task hooks the planner should use:

1. **First wave: build + library + Liquibase + Modulith scaffolding (Wave 0)**
   - Add Spring AI BOM + OpenAI + Anthropic starter + jtokkit + commons-text to `libs.versions.toml`.
   - Create `core.llm.package-info.java` with `@ApplicationModule(displayName="LLM", allowedDependencies={"billing", "tenant", "gmail", "shared.persistence", "shared.lang", "shared.privacy"})`.
   - Create the 6 sub-packages (`model`, `service`, `persistence`, `gateway/springai`, `gateway/sanitization`, `config`) each with `package-info.java`.
   - Liquibase changeset **`018-tenant-byok-credentials.yaml`** (NOT 017) + append to master.
   - Add `LlmGatewayBoundaryArchTest` and `JsoupJtokkitIsolationArchTest` (skeleton, allowEmptyShould(true) — the bodies fill out as code lands).

2. **Second wave: sanitization pipeline (Wave 1)**
   - 4 sanitizer beans + `SanitizationPipeline` orchestrator + `SanitizationContext`/`SanitizationException`/`SafetyViolationException` records & exceptions in `core.llm.model`.
   - Per-step unit tests + pipeline integration test.

3. **Third wave: gateway core + validator + tool-call parsing (Wave 2)**
   - `LlmGateway` interface, `LlmGatewayImpl` (platform-only path first; BYOK in next wave), `ActionValidator`, `Action`/`BYOKProvider` enums.
   - `PlatformApiKey`, `PlatformLlmConfig` bean wiring.
   - `@ConfigurationProperties("zero-mail.llm.platform")` + `application.yml` `:?` fail-fast for `ZEROMAIL_LLM_PLATFORM_API_KEY`.
   - Integration test: tenant 0 credits → 402; happy path → success; mock `ChatModel`.

4. **Fourth wave: BYOK persistence + factories + routing (Wave 3)**
   - `TenantByokCredentialsEntity` + repository.
   - `BYOKChatModelFactory` interface + `OpenAiCompatibleByokFactory` + `AnthropicByokFactory`.
   - Gateway routing: BYOK row check → factory derive vs. platform path.
   - Integration test: BYOK row exists → no ledger touch.

5. **Fifth wave: BYOK API + UI (Wave 4)**
   - `ByokController` (validate, save, current).
   - `BYOKValidationService` (network probe to `/v1/models` for OpenAI-compat, `POST /v1/messages max_tokens=1` for Anthropic, both via Spring AI's outbound RestClient).
   - DTO records.
   - `GlobalExceptionHandler` extensions for `SafetyViolationException`, `SanitizationException`.
   - Frontend `apps/web/features/llm/` — invoke `frontend-design` skill BEFORE writing UI code.
   - Playwright e2e.

6. **Sixth wave: drift scaffold (Wave 5)**
   - `golden-set.json` + `golden-baseline.json` (synthetic).
   - `DriftDetectionJob` in `backend/worker` (gated, ShedLock-locked).
   - `DriftCheckBatch` collaborator (mirroring `CreditReserveWatchdogBatch` split for transactional correctness).
   - CI mock tests (matching baseline → no drift; mismatched → drift flagged).

7. **Phase gate (after all waves)**
   - Full ArchUnit suite green.
   - `REQUIREMENTS.md` LLM-04 wording update committed in same plan as `018-tenant-byok-credentials.yaml`.
   - Manual smoke: boot fails with missing `ZEROMAIL_LLM_PLATFORM_API_KEY`; boots with secret set; one drift run succeeds against synthetic baseline.
   - Run `pnpm generate:api` to regenerate `apps/web/lib/api/schema.d.ts` after `springdoc-openapi` picks up new `/api/llm/byok/*` endpoints.

**Critical gotchas for the planner:**

- **Liquibase floor is 018, not 017.** CONTEXT D-G1 is wrong — `017-shedlock-table.yaml` already exists.
- **Property names are `log-prompt` / `log-completion`.** Pin in `application.yml` defensively even though defaults are already `false`.
- **`AnthropicByokModelClient` uses `AnthropicChatOptions.builder().apiKey().baseUrl().model()` as a per-request builder.** `OpenAiCompatibleByokModelClient` builds a one-call `OpenAiChatModel` with tenant key/baseUrl/model. Do not force symmetry; let each provider adapter pick the simplest M5 seam.
- **`commons-text` must be added to `libs.versions.toml`** (or implement Levenshtein in 30 LOC). Researcher recommends adding the dep.
- **`golden-baseline.json` regeneration is a manual operator-side script**, not a CI step. Document the regen command in the phase outcome notes.
- **`RefreshTokenCipher` relocation is DEFERRED.** `core.llm` declares `gmail` in `allowedDependencies` to import `core.gmail.persistence.crypto.RefreshTokenCipher`. This is one extra entry; cleaner long-term refactor belongs in a tech-debt phase.
- **Drift cron starts disabled** (`zero-mail.llm.drift.enabled: false`). Production go-live deferred to Phase 5+ ops phase.
- **Frontend MUST use `frontend-design` skill** before writing `ByokForm.tsx`. Pass the rule into any executor subagent.
- **DriftDetectionJob in `backend/worker` has no request-scope tenant** — must explicitly bind `TenantContext` via `ScopedValue.where(TenantContext.TENANT, syntheticDriftTenantId).run(...)` before calling `gateway.driftCheck(...)`.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring AI BOM + jtokkit + Jsoup + ShedLock all confirmed against Maven Central + M5 docs/compile.
- Architecture: HIGH — every Spring AI seam Phase 2C uses (`OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(builder)`, `internalToolExecutionEnabled`, `toolChoice`, `ToolChoiceAny`) is documented and compile-tested at v2.0.0-M5.
- Pitfalls: MEDIUM — M5→GA churn risk is real but mitigated by ArchUnit isolation; concrete pitfalls (sanitization order, AAD binding, `@SchedulerLock` reuse) are all in-repo verified.
- BYOK key handling: MEDIUM — happy-path is HIGH; heap-residue secret-zeroing is an accepted residual risk for v1.

**Research date:** 2026-05-07
**Valid until:** 2026-06-07 (30 days for Spring AI M5 — re-fetch via Context7 before any M5→GA bump, since builder methods can still move).

---

*Phase: 02C-llm-gateway*
*Researched: 2026-05-07*

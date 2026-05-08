# Phase 2C: LLM Gateway - Context

**Gathered:** 2026-05-07
**Updated:** 2026-05-08 (Spring AI 2.0.0-M5 baseline, BYOK model field, OpenRouter first-class UI preset)
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2C ships the single `LlmGateway` abstraction on Spring AI 2.0.0-M5 that all LLM traffic must traverse: a `core.llm` Spring Modulith package with a `LlmGateway` interface (planted in `core.llm.model`/`core.llm.service`, with all direct Spring AI usage isolated in `core.llm.gateway.springai`), a 4-step Spring-bean sanitization pipeline (Jsoup → NFC → Unicode-tag-strip → jtokkit truncate ≤3896) returning a `SanitizationContext` carrying token-count + truncation flag for metadata-only observability, defense-in-depth tool-call enforcement (Spring AI ToolCallback + `toolChoice=required` AND post-parse `ActionValidator`) returning `record ToolCallResult(Action action, Map<String,Object> args)`, BYOK with per-tenant provider endpoint **and model** persisted, provider-specific M5 seams (`OpenAiChatModel.builder().options(...)` and `ChatClient.prompt().options(builder)`), encrypted-at-rest `tenant_byok_credentials` table reusing the existing `RefreshTokenCipher` envelope, a `features/llm/` BYOK form in `apps/web` using uncontrolled `<input type="password">` (raw key never enters React state) plus model input/datalist, platform credit-cap wired to Phase 2B `CreditLedger.reserve` with HTTP 402 on insufficient + UI top-up prompt, and a drift-detection scaffold (golden-set + baseline + `@Scheduled` job gated `enabled=false` + CI mock test). UI rendering of credit balance, payment-pending polling, and rules-engine tool-call args parsing are explicit Phase 3+ / Phase 5 territory.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**12 requirements are locked.** See `02C-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `02C-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- `LlmGateway` interface + impl (Spring AI 2.0.0-M5 wrapper)
- ArchUnit rule banning direct `ChatClient` / vendor SDK use outside `com.zeromail.core.llm.gateway.springai`
- Sanitization pipeline: Jsoup → NFC → tag-strip → jtokkit truncate ≤3896 hard cap (4096 budget − 200 Anthropic safety headroom)
- Tool-call wrapping with allow-list (`{ label, archive, save_draft }`)
- Platform admin config via `@ConfigurationProperties("zero-mail.llm.platform")` + env/secret only
- BYOK feature: provider preset selector (`OpenRouter` | `Anthropic` | `Custom OpenAI Compatible`), model input/datalist, endpoint + key form, **Validate** button (backend-only network call), encrypted-at-rest storage (AES-GCM, reuse `REFRESH_TOKEN_KEY_BASE64`)
- Hard-reject credit cap wired to Phase 2B ledger (HTTP 402 + UI top-up prompt)
- Metadata-only observability (provider, model, token count, latency, stop reason — no content)
- Drift detection scaffold (fixture + disabled cron + baseline + mock CI test)
- Tokenizer dep (jtokkit) added to `libs.versions.toml`
- `REQUIREMENTS.md` LLM-04 wording update (encrypted-at-rest allowed)

**Out of scope (from SPEC.md):**
- DB-backed admin config + admin UI
- Multi-provider routing / fallback chain
- Per-call-site BYOK provider pin
- Time-window USD/day cap independent of ledger
- Soft-warn at 90% / configurable thresholds
- Production drift cron + Sentry/Slack alert (scaffold only)
- Anthropic `count_tokens` API for precise non-OpenAI count
- Admin probe endpoint (`POST /admin/llm/probe`)
- Refresh-token-style key rotation drill for BYOK (STATE.md Blockers)
- Vector store / embeddings / RAG
- Streaming responses (SSE)

</spec_lock>

<decisions>
## Implementation Decisions

### A. BYOK per-request key seam (combo: A for platform, B for BYOK)

- **D-A1: Platform-key path uses a singleton Spring AI `ChatClient` configured from `zero-mail.llm.platform`.** Spring AI 2.0.0-M5 uses the provider model builder surface here, so platform OpenAI-compatible traffic is wired with `OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(...).apiKey(...).model(...).build())` and exposed as one `ChatClient` bean. The tenant-specific key remains out of this path; BYOK uses the provider-specific clients below.
- **D-A2: BYOK path is asymmetric per provider (M5 source verified).** OpenAI-compatible BYOK derives a one-call `OpenAiChatModel` with `OpenAiChatOptions.builder().apiKey(plaintextKey).baseUrl(canonicalEndpoint).model(model).internalToolExecutionEnabled(false)` and wraps it in `ChatClient.create(...)` inside `core.llm.gateway.springai` only. Anthropic BYOK keeps a parent Anthropic model inside the adapter and passes `AnthropicChatOptions.builder().apiKey(plaintextKey).baseUrl(canonicalEndpoint).model(Model.of(model))` through `ChatClient.prompt().options(builder)`. M5 `ChatClient.options(...)` takes builders as runtime deltas, so pass builders rather than fully built options at call sites. Do not use the legacy low-level client-cloning workaround in current M5 code.
- **D-A3: BYOK provider abstraction lives behind `ByokLlmModelClient` in `core.llm.service`, with implementations isolated in `core.llm.gateway.springai`.** `OpenAiCompatibleByokModelClient` and `AnthropicByokModelClient` both expose `call(byte[] decryptedKey, String endpoint, LlmChatRequest request)`, but each implementation uses the Spring AI M5 seam its provider exposes. Gateway entry resolves `Optional<TenantByokCredentialsEntity>` to the correct client and never imports provider SDK classes outside the adapter package.
- **D-A4: Caching of derived BYOK ChatClients deferred to Phase 4.** Drift loop only hits platform path (no BYOK row for the synthetic tenant). Phase 4 triage may add a Caffeine cache keyed by `(tenantId, provider, key_version)` if profiling shows mutate-allocation cost matters; not premature in Phase 2C.
- **D-A5: BYOK encryption reuses `RefreshTokenCipher` verbatim.** Same `REFRESH_TOKEN_KEY_BASE64` env, same envelope `[key_version:int32 | nonce:12 | ciphertext]`, tenantId-bound AAD. New `tenant_byok_credentials.encrypted_key BYTEA` column stores the envelope. Ciphertext decryption at gateway entry; plaintext key lives only in the Spring AI provider options/client derivation and the heap for the duration of one HTTP call.

### B. Sanitization pipeline composition

- **D-B1: Bean-chain composition (`List<Sanitizer>` ordered by `@Order`).** Interface `Sanitizer` with single method `SanitizationContext apply(SanitizationContext)`. Four beans in `com.zeromail.core.llm.gateway.sanitization`: `JsoupHtmlStripSanitizer @Order(10)`, `NfcNormalizeSanitizer @Order(20)`, `UnicodeTagStripSanitizer @Order(30)`, `JtokkitTruncateSanitizer @Order(40)`. Orchestrator `SanitizationPipeline` injects `List<Sanitizer>` (Spring auto-sorts by `@Order`) and folds: `pipeline.sanitize(rawHtml) → contexts.iterator().reduce(initialContext, (ctx, step) → step.apply(ctx))`. Rationale: each bean has a zero-arg unit test (no Spring context); idiomatic Spring (20-yr `OrderComparator` pattern); per-step Micrometer trivial via decorating wrapper bean; new step (PII redaction in v2) = add bean + `@Order(50)` with no orchestrator change. SPEC.md "passing unit test per step" falls out naturally.
- **D-B2: `SanitizationContext` record carries pipeline metadata.** `record SanitizationContext(String content, int tokenCount, boolean truncated, Map<String, Object> stepMetadata)`. Token count populated by the truncate step; `truncated` flag set when input exceeded budget; `stepMetadata` open-ended for future steps to attach diagnostics (e.g., `{"pii_redacted_count": 3}`). Gateway emits `tokenCount` + `truncated` to Micrometer / observation span attributes — satisfies LLM-09 metadata-only observability without coupling each step to metrics. **No content is ever logged or stored** — `content` lives only in the gateway's request stack.
- **D-B3: Failure semantics — fail-fast, don't continue.** If any `Sanitizer.apply` throws, the orchestrator wraps in `SanitizationException(stepName, cause)` and aborts the gateway call. Rationale: a sanitizer failure is a privacy-safety violation (e.g., Jsoup OOM on hostile HTML) — silent fallback to unsanitized content is unacceptable. ArchUnit rule: any caller of `pipeline.sanitize(...)` must be inside `core.llm.gateway` package.
- **D-B4: jtokkit `cl100k_base` encoding for all providers.** Anthropic estimate (~10–20% off) accepted per SPEC.md constraint. Truncation budget = 3896 hard cap (4096 budget − 200 Anthropic safety headroom). Use `encode(text, 3896)` which returns `EncodingResult` with character-boundary truncation handling multi-byte UTF-8 cleanly.
- **D-B5: `SanitizationAdvisor` deferred.** A future thin Spring AI `CallAdvisor` shim wrapping the bean chain (option D from research) is a one-class addition if Phase 4 needs `ChatClient.Builder.defaultAdvisors(...)` registration. Phase 2C wires the pipeline as a direct service call from gateway impl — not as an advisor — keeping privacy-critical logic outside the M5-churn-prone advisor API.

### C. Tool-call wrapping + safety enforcement (defense-in-depth)

- **D-C1: Layer 1 — Spring AI `ToolCallback` + `toolChoice=required` schema-level enforcement.** Gateway registers three `ToolCallback`s named `label`, `archive`, `save_draft` on the `ChatClient.prompt()` builder for each call. Set `OpenAiChatOptions.builder().toolChoice("required")` (OpenAI string form) and equivalent `AnthropicChatOptions` `ToolChoiceAny` for Anthropic. Use `internalToolExecutionEnabled(false)` so Spring AI returns the tool-call without auto-executing — gateway parses the call, validates, returns to caller. Prevents the model from emitting free-text or unknown function names at the wire level.
- **D-C2: Layer 2 — post-parse `ActionValidator` enum check throwing `SafetyViolationException`.** Class `ActionValidator` in `core.llm.service` with single method `Action validate(String functionName)` that calls `Action.fromId(functionName)` (project IdentifiedEnum convention #3 — fail-loud `NoSuchElementException`) AND additionally checks `EnumSet.of(LABEL, ARCHIVE, SAVE_DRAFT).contains(action)`. Both layers must independently fail-open for `send` to leak through. Catches Spring AI M5→GA churn that might silently disable `toolChoice=required`, plus OpenRouter-routed providers that ignore `toolChoice`.
- **D-C3: Gateway return type — `record ToolCallResult(Action action, Map<String, Object> args)`.** Lives in `core.llm.model`. `Action` is an `IdentifiedEnum` enum with members `LABEL("label")`, `ARCHIVE("archive")`, `SAVE_DRAFT("save_draft")` and fail-loud `fromId` per project convention #3. `args` is `Map<String, Object>` (weakly-typed) — Phase 3 rules engine and Phase 4 triage each parse the map into their own typed records on their side of the boundary; gateway is NOT the package that owns Phase 4 action arg shapes. Rationale: ArchUnit-clean (no Spring AI types leak to callers), enum is single source of truth shared with `ActionValidator`, records align with project convention #2, sealed interface (option iii from research) deferred until v2 adds many actions.
- **D-C4: `SafetyViolationException` is a `RuntimeException` under `core.llm.model`.** `GlobalExceptionHandler` maps to HTTP 500 + `code=LLM_SAFETY_VIOLATION` (operator-visible signal — should not happen in normal flow; if it does, indicates either model exploit attempt or M5 churn). Not 4xx — this is a programming-/safety-error class. Privacy invariant: exception message MUST NOT contain the rejected action name or any model output content; `event=llm_safety_violation tenantId={} callSite={}` log only.
- **D-C5: Test seam — mock `ChatModel` returns synthetic `ChatResponse` w/ unknown function name → assert `SafetyViolationException`.** SPEC.md acceptance unit test targets the validator (Layer 2), so the test passes even if Layer 1 (`toolChoice=required`) is mocked off at the raw response level. Second test: mock returns `{action: "label", args: {value: "Receipts"}}` → assert success + correct `ToolCallResult`. Both tests sit in `backend/core/src/test/java/.../llm/gateway/`.

### D. BYOK form architecture (apps/web)

- **D-D1: `features/llm/` triplet (api/components/hooks).** New folder `apps/web/features/llm/` with `api/llm-api.ts` (two functions: `validateByok(payload)` calling `POST /api/llm/byok/validate`, `saveByok(payload)` calling `POST /api/llm/byok` — both via existing typed `openapi-fetch` client), `components/ByokForm.tsx` (the form), `hooks/use-byok.ts` (two TanStack Query mutations: `useValidateByok`, `useSaveByok`). NO `features/billing/byok/` (semantic mismatch — BYOK is gateway config, not money), NO `features/settings/byok/` (settings is a route, not a feature domain). Mounted on existing `/settings` page via `<Card>` section. Zero new deps.
- **D-D2: Uncontrolled inputs for raw-key handling.** `ByokForm.tsx` uses `useRef<HTMLFormElement>` for the form element. On Validate click: `formRef.current.elements.namedItem('apiKey').value` reads the raw key once into a local `const`, passes to `validateByok`, drops the reference. `<input type="password" name="apiKey" autoComplete="off" />`. Provider radio + endpoint visibility are controlled (`useState`) because they affect rendering; ONLY the secret stays uncontrolled. Raw key never enters React state, never appears in TanStack Query cache key (mutation only — no cache), never reaches DevTools.
- **D-D3: Two-step Validate-then-Save UX.** Save button is `disabled` until `validateByok.data?.ok === true`. Validate result rendered as a raw shadcn `<Alert variant="success|destructive">` showing `models[]` where available. On Save success: form reset (provider preset, model, refs nulled, success alert). On Save failure: error alert, form preserved for retry. Backend `POST /api/llm/byok/validate` accepts `{provider, endpoint?, model, apiKey}` and returns `{ok, models?, reason?}`; `POST /api/llm/byok` returns `{ok, savedAt}`.
- **D-D4: Raw shadcn primitives — no wrappers.** Composition inside one `<Card>`: `<RadioGroup>` for provider preset (`OpenRouter`, `Anthropic`, `Custom OpenAI Compatible`), `<Input type="text">` for model with datalist examples, `<Input type="url">` for endpoint only for custom OpenAI-compatible, `<Input type="password">` for key, `<Button onClick={validate}>` (Validate) + `<Button onClick={save} disabled>` (Save), `<Alert>` for validate result. Memory rule: rule-of-three not met — no `ByokFormCard` / `ValidationResultAlert` wrappers.
- **D-D5: i18n — Vietnamese-first.** Copy keys live in `apps/web/features/llm/messages.ts` (co-located per flat-folder rule) with `{vi, en}` shape; merged into top-level `apps/web/i18n/messages/{vi,en}.json` build-time. `pnpm i18n:check` STRICT must pass.
- **D-D6: Frontend-design skill MUST be invoked before writing UI code.** Per memory rule. Pass this rule into any executor subagent that writes ByokForm.tsx.

### E. Per-call-site model pin policy

- **D-E1: `@ConfigurationProperties("zero-mail.llm.platform")` exposes `compileModel`, `driftModel`, `triageModel`.** Map<CallSite, String> resolution at gateway entry: gateway accepts `CallSite callSite` parameter (Phase 2B enum), looks up the model id via internal `Map.of(CallSite.TRIAGE, props.triageModel(), CallSite.DRAFT, props.compileModel(), CallSite.PREVIEW, props.compileModel())` — note: SPEC has no `previewModel`; compile-model serves rule-compile + preview both. Drift detection uses the dedicated `driftModel` (passed via internal API, not via `CallSite` since drift is not a billable user call site).
- **D-E2: `LlmGateway.chat(CallSite, SanitizedContent, ToolCallbacks)` signature.** `chat(callSite, content, tools) → ToolCallResult`. Caller passes Phase 2B `CallSite` (drives ledger reserve cost + model pin). Gateway internally resolves model from config, sanitizes via pipeline, builds `ChatClient.prompt()` with tools, calls, validates response, returns `ToolCallResult`. Synchronous return (Java 25 virtual threads → no need for `CompletableFuture`).
- **D-E3: Drift call uses dedicated internal entry point — `driftCheck(prompt) → ToolCallResult`.** Bypasses `CallSite`-based ledger reserve (drift is a platform-cost operation, not user-billable). Pinned to `driftModel` config. Lives next to `LlmGateway` in `core.llm.service` as a separate method to avoid abusing `CallSite`.

### F. In-memory cache for prompts/completions

- **D-F1: NO Caffeine cache in Phase 2C.** SPEC.md says "in-memory cache (e.g. Caffeine) sized to current request scope only". Re-read: "current request scope only" → request-scoped bean OR no cache, never application-scoped Caffeine. Phase 2C ships with NO cache — sanitized content lives on the request stack only, prompts/completions pass through gateway without storage. If Phase 4 triage shows duplicate-call patterns (same email body sent multiple times), revisit. Rationale: zero cache = zero privacy footprint = simplest correctness story for Phase 2C.

### G. Liquibase changeset ordering

- **D-G1: BYOK table changeset = `018-tenant-byok-credentials.yaml`.** Phase 2B claimed `014/015/016` and the worker plan added `017-shedlock-table.yaml` (verified in `db.changelog-master.yaml`). Phase 2C floor is therefore `018`. Schema:
  - `id UUID PRIMARY KEY`
  - `tenant_id UUID NOT NULL` (FK → `tenants(id)` ON DELETE CASCADE)
  - `provider VARCHAR(32) NOT NULL` (`anthropic` | `openai-compatible`)
  - `endpoint VARCHAR(512) NULL` (only set for `openai-compatible`)
  - `encrypted_key BYTEA NOT NULL` (envelope `[key_version:int32 | nonce:12 | ciphertext]`)
  - `key_version SMALLINT NOT NULL`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - UNIQUE on `(tenant_id)` — one BYOK row per tenant (no per-call-site BYOK in v1, per SPEC out-of-scope)
  - B-tree on `tenant_id` (covered by UNIQUE)

### H. Drift detection scaffold

- **D-H1: Golden-set fixture lives at `backend/core/src/main/resources/llm/golden-set.json`.** ~20 synthetic emails covering: Stripe receipt, GitHub PR notification, calendar invite, newsletter, plain-text personal email, HTML newsletter w/ tracking pixel, multilingual (EN+VI) email, Unicode tag-injection attempt, hidden-text prompt-injection attempt, generic transactional. NO PII (use synthesized addresses, no real subjects). Each entry: `{id, subject, from, htmlBody, expectedAction, expectedArgs}`.
- **D-H2: Baseline `backend/core/src/main/resources/llm/golden-baseline.json`.** Generated once at scaffold-build time by running golden-set through gateway against pinned `driftModel`; committed to repo. Format: `{id → {action, argsJson}}`.
- **D-H3: `DriftDetectionJob` `@Scheduled(cron="0 0 6 * * *")` gated on `zero-mail.llm.drift.enabled` (default `false`).** Located in `backend/worker/src/main/java/com/zeromail/worker/llm/`. When enabled, runs golden-set, compares each output against baseline. Drift comparison: `JsonNode.equals()` for action; Levenshtein distance > 20% on `argsJson` flags drift. ShedLock `@SchedulerLock` mirrors Phase 2A `GmailWatchScheduler` pattern. Privacy log: `event=drift_check_run total={} drifted={}` — no per-email content.
- **D-H4: CI mock test pattern.** Two `@SpringBootTest` tests with `MockBean ChatModel`: (1) returns baseline outputs verbatim → assert `driftCount == 0`; (2) returns mutated outputs (Levenshtein > 20%) → assert `driftCount > 0` and synthetic alert event logged. Both pass without external LLM calls.

### I. Privacy-safe logging contract

- **D-I1: Gateway logs**: `event=llm_call_started tenantId={} callSite={} provider={} model={}`, `event=llm_call_succeeded tenantId={} callSite={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}`, `event=llm_call_failed tenantId={} callSite={} reason={opaqueClassName}`. NO content, NO token-content, NO tool-call args content.
- **D-I2: BYOK validate endpoint logs**: `event=byok_validate_attempted tenantId={} provider={}`, `event=byok_validate_succeeded tenantId={} provider={} modelsCount={}`, `event=byok_validate_failed tenantId={} provider={} reason={opaqueClassName}`. NO endpoint URL (could be sensitive), NO key (obviously), NO error body from upstream provider.
- **D-I3: Sanitization step logs**: NONE per step (avoid step-by-step traces of email content even via timing inference). Pipeline-level only: `event=sanitization_completed tenantId={} truncated={} tokenCount={}`.
- **D-I4: Logback scrub filter extension (verify before assuming)**: existing scrub patterns from Phase 1 should already cover `prompt=`, `completion=`. Verify they also cover `apiKey=`, `bearer=`, `x-api-key=`. Plan-phase: extend if gaps found.
- **D-I5: Spring AI observation property pin (M5 verified)**: explicitly pin `spring.ai.chat.client.observations.log-prompt: false` and `spring.ai.chat.client.observations.log-completion: false` in BOTH `backend/api/src/main/resources/application.yml` AND `backend/worker/src/main/resources/application.yml`. Also pin `spring.ai.chat.observations.log-prompt: false` / `log-completion: false` for lower-level chat-model observations. Defaults are not a privacy control; the project pins the keys defensively so prompt/completion capture cannot appear during M5→GA churn.

### Claude's Discretion

- Exact Spring AI M5 provider-builder APIs — verified by compile/tests; re-check on any M5→GA bump.
- `OpenAiChatOptions` vs `AnthropicChatOptions` `toolChoice` exact builder method names — verified by adapter tests; re-check on any M5→GA bump.
- `BYOKChatModelFactory` interface signature pinned in D-A3: `ChatResponse call(byte[] decryptedKey, String endpoint, String model, String userMessage, List<ToolCallback> toolCallbacks)` — uniform shape, asymmetric impl per D-A2
- `Action` enum `id()` lower-snake-case vs raw enum name (recommend lower-snake to match function names — `LABEL.id() == "label"`)
- Exact `SanitizationContext.stepMetadata` map key conventions (planner can pick)
- jtokkit version to pin — recommend latest stable (currently `1.1.x`; verify before plan)
- ShedLock for `DriftDetectionJob` — reuse Phase 2A pattern (`shedlock-spring` already in `libs.versions.toml`)
- ByokForm Alert success copy + Validate result models[] rendering (frontend-design skill picks)
- i18n key spelling for `error.llm.safety_violation`, `error.llm.byok_validate_failed`, `byok.validate_button`, etc. — copywriter pass at plan-phase

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-specific (locked)
- `.planning/phases/02C-llm-gateway/02C-SPEC.md` — Locked requirements (12), boundaries (in/out), acceptance criteria (14). MUST read before planning.

### Project-level (in-repo, locked)
- `CLAUDE.md` §Constraints, §Backend Code Style, §Conventions, §"Hard do not use" list — Java 25, Spring Boot 4.0.6, Spring AI 2.0.0-M5, no Lombok, thin controllers + service-owned `@Transactional`, `IdentifiedEnum`/`fromId` fail-loud, privacy log format `event=opaque tenantId={}`, enterprise-readability variable naming, adapter-only Spring AI usage, and no Spring AI imports outside one adapter.
- `CLAUDE.md` §Project Skills + Memory rules — `frontend-design` skill MUST be invoked before writing UI code; raw shadcn primitives first (no wrappers unless rule-of-three + 3+ stacked); flat folder structure preferred.
- `.planning/PROJECT.md` — Privacy posture ("trust is the product"), no auto-send write-action allow-list, BYOK supported, OpenRouter default routing.
- `.planning/REQUIREMENTS.md` — `LLM-01..LLM-11` rows (status flip target + LLM-04 wording update).
- `.planning/research/STACK.md` — Spring AI 2.0.0-M5 starter, OpenRouter base-url, Spring AI observation `log-prompt: false / log-completion: false`.

### Prior-phase context (decisive for this phase)
- `.planning/phases/01-foundation-safety-infrastructure/01-CONTEXT.md` — Tenant isolation primitives (`TenantContext` ScopedValue, multi-tenant leak test pattern); Logback scrub filter; AES-GCM envelope cipher pattern.
- `.planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/01.5-SECURITY.md` — `:?` fail-fast pattern for `REFRESH_TOKEN_KEY_BASE64` (reused for `ZEROMAIL_LLM_PLATFORM_API_KEY` in Phase 2C).
- `.planning/phases/02A-mail-ingestion/02A-CONTEXT.md` — `OncePerRequestFilter @Order(1)` pattern; worker `@Scheduled` + ShedLock pattern (mirror for `DriftDetectionJob`); `RestClient + LocalServerPort` testing pattern when `TenantContext` ScopedValue must bind.
- `.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md` — `CreditLedger` interface contract (D-D1 lifecycle: `reserve` → try → `settle`/`release`); `CallSite` enum membership locked (TRIAGE/DRAFT/PREVIEW; no BYOK member); 2B claimed Liquibase changesets `014/015/016` + worker plan added `017-shedlock-table.yaml` → 2C floor is `018`; ArchUnit `DomainBoundaryArchTests` per-domain rule pattern; SePay-style explicit-secret `:?` fail-fast for `SEPAY_WEBHOOK_API_KEY` mirrors what `ZEROMAIL_LLM_PLATFORM_API_KEY` needs.
- `.planning/phases/01.2-domain-owned-persistence-restructuring/01.2-CONTEXT.md` — Modulith per-domain `{model, service, persistence, persistence.lowlevel}` shape — apply to `core.llm` (with extra `gateway/springai/` and `gateway/sanitization/` sub-packages for ArchUnit isolation).
- `.planning/phases/01.2.1-shared-base-entity-and-enum-standard/01.2.1-CONTEXT.md` — `AbstractTenantOwnedEntity` (extended by `TenantByokCredentialsEntity`); `IdentifiedEnum` interface + `fromId` fail-loud (used by `Action` and `BYOKProvider` enums).

### In-code anchors (current state to extend)
- `backend/core/src/main/java/com/zeromail/core/` — new `llm/` sibling to `account/`, `billing/`, `gmail/`, `onboarding/`, `tenant/`, `shared/`. Sub-packages: `model`, `service`, `persistence`, `gateway/springai`, `gateway/sanitization`.
- `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java` — Phase 2C `LlmGatewayImpl` injects this interface; calls `reserve(tenantId, callSite)` on platform-key path, skips on BYOK path, calls `settle(reservationId)` on success, `release(reservationId)` on exception. Pattern locked in CreditLedger Javadoc.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java` — accepted as gateway entry parameter; Phase 2C does NOT modify this enum (locked by 2B ArchUnit rule).
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — reused verbatim for BYOK key encryption. NEW package: relocate to `core.shared.crypto` if planner judges it now cross-cutting (defer if doing so requires touching Phase 1.5 callers — alternatively, inject the existing bean from `gmail` package; planner decides).
- `backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java` — `TenantByokCredentialsEntity` extends.
- `backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java` — `Action`, `BYOKProvider` implement; `fromId` fail-loud.
- `backend/core/src/main/resources/db/changelog/changes/` — next-free is `018` (2B claimed `014/015/016`; 2B worker plan added `017-shedlock-table.yaml`); allocation `018-tenant-byok-credentials.yaml`.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — append the new include.
- `backend/core/src/main/resources/llm/golden-set.json` (NEW) + `backend/core/src/main/resources/llm/golden-baseline.json` (NEW) — drift fixtures.
- `backend/api/src/main/java/com/zeromail/api/controllers/` — new `llm/ByokController.java` (`POST /api/llm/byok/validate`, `POST /api/llm/byok`, `GET /api/llm/byok` for read-current-config). Mirror `billing/BillingController.java` sub-folder grouping pattern.
- `backend/api/src/main/java/com/zeromail/api/dto/` — new `llm/` sub-package: `ByokValidateRequest`, `ByokValidateResponse`, `ByokSaveRequest`, `ByokSaveResponse`, `ByokCurrentResponse`.
- `backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java` — add `SafetyViolationException → 500 LLM_SAFETY_VIOLATION`, `SanitizationException → 500 LLM_SANITIZATION_FAILED` (the 402 mapping for `InsufficientCreditsException` already exists from Phase 2B).
- `backend/api/src/main/resources/application.yml` AND `backend/worker/src/main/resources/application.yml` — both add `ZEROMAIL_LLM_PLATFORM_API_KEY:?` fail-fast (api uses for sync calls, worker uses for `DriftDetectionJob`); also `zero-mail.llm.platform.{provider, base-url, compile-model, drift-model, triage-model}` defaults.
- `backend/worker/src/main/java/com/zeromail/worker/llm/` (new package) — `DriftDetectionJob`.
- `gradle/libs.versions.toml` — add `springAi = "2.0.0-M5"` version + libraries (`spring-ai-bom`, `spring-ai-starter-model-openai`, optional `spring-ai-starter-model-anthropic`, `jtokkit`).
- `apps/web/features/llm/` (new feature folder) — `api/llm-api.ts`, `components/ByokForm.tsx`, `hooks/use-byok.ts`, `messages.ts` (i18n co-location).
- `apps/web/i18n/messages/{vi,en}.json` — add `byok.*` and `error.llm.*` keys.
- `apps/web/lib/api/schema.d.ts` — regenerated after `springdoc-openapi` task picks up `/api/llm/*` endpoints.

### External specs (re-fetch via Context7 or `gsd-research-phase` at plan-phase)
- **Spring AI 2.0.0-M5 reference docs** — `https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html`, `.../chat/anthropic-chat.html`, `.../chatclient.html` (Multiple OpenAI-Compatible Endpoints `MultiModelService` example), `.../tools.html`. Verify: `OpenAiChatModel.builder()` / `AnthropicChatOptions.builder()` BYOK seams, `ChatClient.options(builder)` runtime delta semantics, `ToolCallback` registration on `ChatClient.prompt()`, `internalToolExecutionEnabled(false)`, `toolChoice` per-provider semantics.
- **Spring AI issue #477** — `https://github.com/spring-projects/spring-ai/issues/477` — historical dynamic API-key discussion. M5 implementation uses provider builders/options instead of the old client-cloning workaround.
- **Spring AI issue #1899** — `https://github.com/spring-projects/spring-ai/issues/1899` — `OpenAiChatOptions.toolChoice` String type accepted (pinned for OpenRouter compatibility).
- **OpenRouter API docs** — `https://openrouter.ai/docs` — verify OpenAI-compatible `/v1/chat/completions` endpoint, model id naming convention (`openai/gpt-4o-mini`, `anthropic/claude-3.5-sonnet`), `/v1/models` for BYOK Validate flow.
- **Anthropic API docs** — `https://docs.anthropic.com/` — verify `POST /v1/messages` with `max_tokens: 1` for Validate flow; tool-use response shape; `ToolChoiceAny`.
- **jtokkit usage** — `https://github.com/knuddelsgmbh/jtokkit/blob/main/docs/docs/getting-started/usage.md` — `cl100k_base` encoding, `encode(text, maxTokens)` for char-boundary truncation.
- **OpenAI structured outputs / function calling** — `https://platform.openai.com/docs/guides/function-calling` — defense-in-depth pattern (`strict:true` schema + post-validate).

### Local references
- `D:/study-materials-summer-2026/inbox-zero/` — reference repo. Inbox-Zero's LLM gateway pattern is in `apps/web/utils/ai/` (TypeScript / Vercel AI SDK). UX reference for BYOK form copy + validate flow only — DO NOT port code shapes (different language, different SDK abstractions).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`RefreshTokenCipher`** (`core.gmail.persistence.crypto.RefreshTokenCipher`): AES-GCM-256 envelope `[key_version:int32 | nonce:12 | ciphertext]` with tenantId-bound AAD; reused verbatim for BYOK key encryption. Plan-phase decides: (a) inject existing bean from `gmail` package (cross-package dependency edge — needs Modulith `allowedDependencies` update), or (b) relocate cipher to `core.shared.crypto` (cleaner long-term but touches Phase 1.5 callers).
- **`CreditLedger`** + **`CallSite`** (`core.billing`): Phase 2C `LlmGatewayImpl` injects `CreditLedger` interface; pattern locked in CreditLedger Javadoc D-D1 — reserve/settle/release with explicit settle on success and release on exception.
- **`AbstractTenantOwnedEntity`** (`core.shared.persistence`): `TenantByokCredentialsEntity` extends — automatic `tenant_id` discriminator + audit columns + `@TenantId` filter.
- **`IdentifiedEnum`** (`core.shared.lang`): `Action`, `BYOKProvider` (and any `LlmCallSite` if added — but reuse Phase 2B `CallSite`) implement — provides `id()`, fail-loud `fromId`.
- **`TenantContext.currentOrThrow()`** (`core.tenant`): all `LlmGateway` calls + `BYOK*` controller methods + `PlatformApiKey.getValue()` resolve tenant via this ScopedValue.
- **`PubSubOidcAuthFilter`** + **`OncePerRequestFilter @Order(1)`** pattern (Phase 2A): NOT directly used in Phase 2C (no inbound webhook for LLM); listed for awareness so plan-phase doesn't accidentally introduce a new filter chain.
- **`GmailWatchScheduler`** + ShedLock `@SchedulerLock` (Phase 2A `backend/worker`): mirror for `DriftDetectionJob`.
- **`MultiTenantLeakIntegrationTest`** (Phase 1 FND-05): pattern for tenant-isolation test on `LlmGateway.chat(...)` — virtual-thread concurrent calls from two tenants asserting no cross-leak (especially important for BYOK path — must not use Tenant A's BYOK key for Tenant B's call).

### Established Patterns
- **Per-domain Modulith package shape**: `model/`, `service/`, `persistence/`, `persistence/lowlevel/` — locked Phase 1.2/1.2.1, applied to gmail/account/onboarding/tenant/billing. Apply verbatim to `core.llm` PLUS extra `gateway/springai/` and `gateway/sanitization/` sub-packages for ArchUnit isolation of vendor-SDK imports.
- **Liquibase changeset numbering**: monotonic; current floor is `016` from Phase 2B; 2C claims `017`.
- **`:?` fail-fast for deployment secrets** (Phase 1.5 CR-04, Phase 2B D-F1): `${ZEROMAIL_LLM_PLATFORM_API_KEY:?clear-message}` in `application.yml`; `@DynamicPropertySource` in test base supplies `test-platform-api-key`.
- **Thin controllers + service-owned `@Transactional`** (CLAUDE.md Conventions §1): `ByokController.validate()` calls `byokService.validate(...)`; controller never opens a transaction; no repository injection in controllers.
- **Records-for-DTOs / classes-for-entities Lombok-free** (CLAUDE.md Conventions §2): `ByokValidateRequest`, `ByokValidateResponse`, `ToolCallResult`, `SanitizationContext` are records; `TenantByokCredentialsEntity` is mutable class with `protected` no-args constructor.
- **`event=opaque tenantId={}` privacy log format** (CLAUDE.md Conventions §4): all gateway/sanitization/byok/drift logs follow.
- **ArchUnit `DomainBoundaryArchTests` per-domain rule pattern** (Phase 1.2 D-Plan 06): add `core.llm` rule + extra rules: (a) `org.springframework.ai.*` only importable inside `core.llm.gateway.springai`; (b) `com.openai.*` and `com.anthropic.*` only importable there too; (c) `org.jsoup.*` and `com.knuddels.jtokkit.*` only importable inside `core.llm.gateway.sanitization`.
- **TanStack Query mutation triplet** (apps/web `features/account/`, `features/gmail/`): `api/` + `components/` + `hooks/`. ByokForm follows verbatim.

### Integration Points
- **Phase 2C → Phase 2B**: `LlmGatewayImpl` imports `core.billing.CreditLedger` interface + `core.billing.CallSite` enum. Modulith `allowedDependencies` for `core.llm` includes `billing`.
- **Phase 2C → Phase 1 (gmail.persistence.crypto OR core.shared.crypto if relocated)**: `RefreshTokenCipher` reuse. Modulith `allowedDependencies` edge.
- **Phase 3 → Phase 2C**: Rules engine NL→matcher AST will call `gateway.chat(CallSite.PREVIEW, ...)` (compile model). Phase 3 plan-phase will define matcher AST records — Phase 2C `ToolCallResult.args` map decoded by Phase 3 into typed records.
- **Phase 4 → Phase 2C**: Triage orchestrator calls `gateway.chat(CallSite.TRIAGE, ...)`. Phase 4 owns the action-execution layer (Gmail label/archive/draft writes) — Phase 2C only RETURNS the structured action; Phase 4 decides whether to execute.
- **`GlobalExceptionHandler`** (`backend/api`): adds two new mappings — `SafetyViolationException → 500 LLM_SAFETY_VIOLATION`, `SanitizationException → 500 LLM_SANITIZATION_FAILED`. Uses Phase 1.1 `ApiError` contract; frontend localizes via i18n keys.
- **`springdoc-openapi-gradle-plugin`** (Phase 1.2.1 D-Plan 04): hermetic spec emit picks up new `/api/llm/byok/*` endpoints. After plan, run `pnpm generate:api` in `apps/web` to regenerate `schema.d.ts`.

</code_context>

<specifics>
## Specific Ideas

- **Spring AI M5 milestone caveat**: M5 → GA churn is real (M7→M8 silently broke `tools()`). All direct Spring AI usage isolated in `core.llm.gateway.springai/` — ArchUnit enforces. Use documented M5 builder seams (`OpenAiChatModel.builder().options(...)`, `ChatClient.prompt().options(builder)`) over interceptors or undocumented internals.
- **Defense-in-depth as project posture**: Core Value is "AI auto-triage that users TRUST with their real Gmail inbox" — a single leaked `send` action in Phase 4 against a real Gmail mailbox is end-of-product. Two independent enforcement layers (`toolChoice=required` + post-parse validator) is OpenAI's own recommended pattern — not over-engineering.
- **BYOK custom endpoint user story**: User picks OpenRouter as a first-class preset or pastes any OpenAI-compatible URL (Together.ai, Fireworks, self-hosted vLLM, raw OpenAI-compatible proxy, etc.) into the `Custom OpenAI Compatible` endpoint field, then provides the required model id. This is why endpoint + model are persisted with the tenant key and why the M5 OpenAI-compatible adapter builds a one-call model with tenant `apiKey/baseUrl/model`.
- **Frontend form library policy (future-locked)**: For Zero Mail UI work going forward —
  - Small or secret-sensitive forms (BYOK, password change, OAuth-key paste): plain uncontrolled `<input>` + `useRef`. Raw secrets NEVER enter React state.
  - Complex/heavy forms (rules editor in Phase 3, prompt template editor, multi-step wizards): **TanStack Form + Zod** (NOT react-hook-form — TanStack Form pairs with TanStack Query already in the stack).
  - Cross-component UI state (multi-step wizard progress, dirty-tracking across navigation): **Zustand** only when meaningfully complex; useState + props otherwise.
  This is a project-level policy, not a Phase 2C decision.
- **Drift fixtures synthetic-only**: No real PII in `golden-set.json`. Synthesized addresses (`alice@example.com`), invented subjects, no real company names. Privacy invariant — fixture is checked into the repo.
- **Anthropic optional in v1**: `spring-ai-starter-model-anthropic` is optional. If admin only configures OpenAI-compatible (OpenRouter), Anthropic adapter not registered. BYOK Anthropic flow still works via OpenRouter pass-through if user pastes OpenRouter URL + key (uses `openai-compatible` provider radio).
- **Drift cron go-live deferred**: `zero-mail.llm.drift.enabled` defaults `false`. SPEC.md says "production cron go-live deferred to Phase 5 or dedicated ops phase". Phase 2C ships the scaffold + manual-run capability + CI mock tests; production operator flips the flag once a stable baseline is recorded.

</specifics>

<deferred>
## Deferred Ideas

- **BYOK ChatClient caching (Caffeine, keyed by tenantId+provider+key_version)** — premature in Phase 2C (drift loop = only caller; no real BYOK calls). Revisit when Phase 4 triage profiling shows mutate-allocation cost. Cache invalidation must hook BYOK rotation/revoke.
- **Sealed `ToolCallResult` interface with per-action records** (`Label`, `Archive`, `SaveDraft`) — strongest typing now, but couples gateway to Phase 4 action arg shapes that don't exist yet. v2 candidate when allow-list grows.
- **`SanitizationAdvisor` Spring AI `CallAdvisor` shim** — one-class addition wrapping the bean chain (option D from research). Phase 4 may want it for `ChatClient.Builder.defaultAdvisors(...)` registration; Phase 2C wires the pipeline as a direct service call, keeping privacy-critical logic outside the M5-churn-prone advisor API.
- **Per-call-site BYOK provider pin** (e.g., user wants Anthropic for triage but OpenAI for draft) — out-of-scope per SPEC. v2 candidate.
- **PII redaction sanitizer step** — anticipated future bean (`@Order(50)`) in the pipeline. Out of scope for Phase 2C.
- **`RefreshTokenCipher` relocation to `core.shared.crypto`** — cleaner long-term once it has 2+ consumers (Phase 1.5 OAuth + Phase 2C BYOK). Plan-phase decides whether to relocate now (touches Phase 1.5 callers + Modulith config) or defer (cross-package edge declared via `allowedDependencies`). Lean toward relocate-now since Phase 2C is a natural moment.
- **Production drift cron go-live + Sentry/Slack alerts** — Phase 5 or dedicated ops phase.
- **Soft-warn at low-balance threshold for credit cap** — hard-reject only in v1 per SPEC.
- **Streaming responses (SSE)** — non-streaming sufficient for compile + drift; reconsider when Phase 4 triage UX needs it.
- **Vector store / embeddings / RAG for triage context** — privacy lock from PROJECT.md forbids embeddings of user mail.
- **Anthropic precise tokenizer (`POST /v1/messages/count_tokens`)** — out-of-scope; jtokkit estimate w/ 200-token Anthropic safety headroom is sufficient.
- **Admin probe endpoint** (`POST /admin/llm/probe`) — drift job is the only end-to-end caller in 2C per SPEC.
- **Refresh-token-style key rotation drill for BYOK + platform key** — captured in STATE.md Blockers under same umbrella as `REFRESH_TOKEN_KEY_BASE64` rotation drill.
- **Zustand for cross-component UI state** — not used in apps/web yet. Future policy: introduce only when complex multi-component state genuinely exceeds props + useState capability.

</deferred>

---

*Phase: 02C-llm-gateway*
*Context gathered: 2026-05-07*

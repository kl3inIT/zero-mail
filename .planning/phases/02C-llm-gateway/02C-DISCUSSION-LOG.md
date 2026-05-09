# Phase 2C: LLM Gateway - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 02C-llm-gateway
**Areas discussed:** BYOK per-request key seam, Sanitization pipeline shape, Tool-call wrapping + safety enforcement, BYOK form architecture (apps/web)
**Mode:** advisor (USER-PROFILE.md present, calibration tier `full_maturity` — thorough-evaluator)

---

## Area 1 — BYOK per-request key seam

Spring AI 2.0.0-M4 verification (via Context7 + Spring AI issues #477 / #2731): `OpenAiChatOptions` does NOT expose `apiKey()` runtime override. Documented seams: (1) custom `ApiKey` interface impl, (2) `OpenAiApi#mutate()` + `OpenAiChatModel#mutate()`. CLAUDE.md "do not use" prohibition on "manually-built ChatClient per request" forbids hand-rolled `RestClient`/HTTP rebuilds, NOT Spring AI's documented `mutate()`.

| Option | Description | Selected |
|--------|-------------|----------|
| A. Dynamic `ApiKey` impl reading from `TenantContext` ScopedValue | One singleton `ChatClient`; `ApiKey.getValue()` resolves at HTTP send. Cannot vary `baseUrl`. | Partial (platform-key path only) |
| B. Per-call `mutate()` derive-and-discard `ChatModel` | Documented M4 pattern (`MultiModelService` example); covers key + baseUrl + per-tenant headers. Identical for OpenAI + Anthropic. | Partial (BYOK path only) |
| C. Two cached `ChatClient`s per tenant (Caffeine) | Amortizes mutate() cost. Stale-key risk after rotation; OOM with high tenant count; premature for 2C. | Deferred |
| D. `RestClient.Builder` interceptor injecting `Authorization` | Fights Spring AI's own `ApiKey` abstraction. Two interceptors (Bearer vs x-api-key). High M4→GA churn risk. | Reject |
| **A+B combo (Rec)** | **Platform path = singleton + dynamic ApiKey; BYOK path = mutate() derive-and-discard. Defer caching.** | **✓** |

**User's choice:** A+B combo (Recommended).
**Notes:** Platform path stays singleton (drift loop hits this every time). BYOK path forks per-call via `OpenAiApi#mutate()` / `AnthropicApi#mutate()` — covers both `apiKey` and `baseUrl` (BYOK custom endpoints — Together.ai, Fireworks, vLLM, etc.). Caching deferred until Phase 4 triage profiling justifies it.

---

## Area 2 — Sanitization pipeline composition

Spring AI verification: `RequestAdvisor`/`CallAdvisor` chain operates on `ChatClientRequest` (already-formed prompts), not raw email body — wrong layer for sanitization. jtokkit `encode(text, maxTokens)` handles char-boundary truncation w/ multi-byte UTF-8.

| Option | Description | Selected |
|--------|-------------|----------|
| A. Single `SanitizationPipeline` class, 4 private methods | Simplest; one file. Step-level unit tests need package-private accessors. | |
| **B. `List<Sanitizer>` beans + `@Order` (Rec)** | **Each step = bean w/ zero-arg unit test; Spring `OrderComparator` idiom; per-step Micrometer trivial; new step (PII redaction) = add bean.** | **✓** |
| C. Spring AI `RequestAdvisor` chain (M4) | Wrong layer; M4→GA churn; drags privacy logic across ArchUnit boundary. | Reject |
| D. Hybrid: bean chain (B) wrapped by thin `SanitizationAdvisor` shim | Plain-Java pipeline as truth source + 10-line shim for `ChatClient.Builder.defaultAdvisors(...)`. Best for future Spring AI integration. | Deferred |

**User's choice:** B — `List<Sanitizer>` beans + `@Order`.
**Notes:** Four ordered beans `JsoupHtmlStripSanitizer @Order(10)` → `NfcNormalizeSanitizer @Order(20)` → `UnicodeTagStripSanitizer @Order(30)` → `JtokkitTruncateSanitizer @Order(40)`. Future PII redaction = `@Order(50)` add. Option D `SanitizationAdvisor` is a one-class future addition if Phase 4 needs it.

### Follow-up — Sanitizer return type

| Option | Description | Selected |
|--------|-------------|----------|
| **SanitizationContext record (Rec)** | **`record SanitizationContext(String content, int tokenCount, boolean truncated, Map<String,Object> stepMetadata)`. Last step writes tokenCount + truncated. Gateway emits to Micrometer.** | **✓** |
| Plain String + side-channel metrics | Simpler interface but each bean wires its own Micrometer counter. | |
| String + final SanitizationResult wrapper at orchestrator | One extra jtokkit encode pass at the end. | |

**User's choice:** SanitizationContext record.
**Notes:** Carries metadata required by LLM-09 (token count + truncation flag) without coupling each step to metrics infrastructure.

---

## Area 3 — Tool-call wrapping + allow-list enforcement

Spring AI 2.0.0-M4 verification: `internalToolExecutionEnabled(false)` returns tool-calls without auto-execute (perfect for gateway-as-validator pattern). `toolChoice` provider-specific: OpenAI string `"required"`, Anthropic `ToolChoiceAny`. M7→M8 silently broke `tools()` — churn risk real. OpenAI's own guidance: `strict:true` + post-validate.

### Enforcement seam

| Option | Description | Selected |
|--------|-------------|----------|
| A. Schema-only (ToolCallback + `toolChoice=required` only) | Single point of failure if M4→GA changes ToolCallback API. | |
| B. Validator-only (free-form structured output + post-parse `Action` enum check) | Decoupled from Spring AI tool-call churn. Model CAN emit `send` — relies on validator catching. | |
| **C. Defense-in-depth: A + B (Rec)** | **OpenAI's recommended pattern. Two independent failure modes. One extra `ActionValidator` class.** | **✓** |

### Return type

| Option | Description | Selected |
|--------|-------------|----------|
| (i) Raw Spring AI `ChatResponse` | Violates ArchUnit boundary. | Reject |
| **(ii) `record ToolCallResult(Action action, Map<String,Object> args)` (Rec)** | **ArchUnit-clean; `Action` enum is SSOT shared with validator.** | **✓** |
| (iii) Sealed `ToolCallResult` w/ per-action records | Strongest typing but couples gateway to Phase 4 action arg shapes prematurely. | Defer to v2 |

**User's choice:** C + (ii) — defense-in-depth + `ToolCallResult` record.
**Notes:** Phase 4 `send` is catastrophic — two layers required. `Action` enum is `IdentifiedEnum` (project convention #3) shared between Layer 1 ToolCallback names and Layer 2 `ActionValidator`. Args stay `Map<String,Object>` — Phase 3/4 each parse their own typed records.

---

## Area 4 — BYOK form architecture (apps/web)

Project state verified: no `react-hook-form` installed; existing `features/account/`, `features/onboarding/`, `features/gmail/` all use `api/` + `components/` + `hooks/` triplet w/ TanStack Query mutations + plain forms. Memory rules: flat folders, raw shadcn primitives.

| Option | Description | Selected |
|--------|-------------|----------|
| A. `features/llm/` + uncontrolled `<input type="password" ref>` + Validate button + raw shadcn primitives | Matches existing TanStack pattern; raw key never in React state; explicit Validate UX maps 1:1 to `/validate` + `/byok` endpoints; zero new deps. | Used as base |
| B. `features/billing/byok/` + RHF + zodResolver + shadcn `form` primitive | 2 new deps for one form; RHF stores raw key in internal store; "billing" semantic mismatch. | |
| C. `features/settings/byok/` + useState-controlled + RQ mutation chain | Raw key in React state per keystroke (worst secret handling); violates flat-folder rule. | Reject |
| D. `features/llm/` + RHF + `shouldUnregister: true` + Validate button | Adds 2 deps; `shouldUnregister` only on unmount. | Defer |

**User's choice (Other / freeform):** "Use plain uncontrolled inputs for small or secret-sensitive forms; adopt TanStack Form + Zod for complex/heavy forms, and use Zustand only when cross-component UI state becomes meaningfully complex (for future consideration)".

**Reflect-back confirmed:**
- Phase 2C ByokForm = uncontrolled inputs in `features/llm/` (essentially Option A path: `useRef<HTMLFormElement>` for key, useState for provider radio + endpoint visibility, Validate button + Save button, raw shadcn primitives, zero new deps Phase 2C).
- Future-policy (locked as project-level specifics, not Phase 2C scope):
  - Complex/heavy forms → **TanStack Form + Zod** (NOT react-hook-form — TanStack Form pairs with TanStack Query already in stack).
  - Cross-component UI state → **Zustand** only when meaningfully complex.
  - Secret-sensitive forms always stay uncontrolled regardless of complexity.

**Notes:** Lock both the Phase 2C decision AND the project-level future policy. CONTEXT.md `<specifics>` documents the future policy so downstream planners (Phase 3 rules editor, Phase 5 settings deep-dive) know the choice.

## Claude's Discretion

Areas listed in CONTEXT.md `<decisions>` "Claude's Discretion" subsection — exact M4 API method names (verify via Context7 at plan-phase), `BYOKChatModelFactory` signature shape, `Action.id()` casing, `SanitizationContext.stepMetadata` map key conventions, jtokkit version pin, ShedLock reuse, Alert success copy, i18n key spelling — researcher/planner/executor have flexibility within CLAUDE.md, SPEC.md, and the locked decisions above.

## Deferred Ideas

Captured in CONTEXT.md `<deferred>`:
- BYOK ChatClient caching (Caffeine) — Phase 4 if profiling justifies
- Sealed `ToolCallResult` interface — v2 when allow-list grows
- `SanitizationAdvisor` shim — Phase 4 if Spring AI integration point needed
- Per-call-site BYOK provider pin — out-of-scope per SPEC; v2 candidate
- PII redaction sanitizer step — future bean
- `RefreshTokenCipher` relocation to `core.shared.crypto` — plan-phase decides whether to relocate now or defer
- Production drift cron go-live + Sentry/Slack alerts — Phase 5 or ops phase
- Soft-warn at low-balance — hard-reject only v1
- Streaming responses (SSE) — Phase 4 reconsider
- Vector store / embeddings / RAG — privacy lock forbids
- Anthropic precise tokenizer — out-of-scope
- Admin probe endpoint — out-of-scope per SPEC
- BYOK + platform key rotation drill — STATE.md Blockers
- Zustand introduction — future policy lock; introduce only when meaningfully complex

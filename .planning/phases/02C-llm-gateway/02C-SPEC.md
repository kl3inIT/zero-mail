# Phase 2C: LLM Gateway — Specification

**Created:** 2026-05-05
**Ambiguity score:** 0.15
**Requirements:** 12 locked

## Goal

Ship the single `LlmGateway` abstraction on Spring AI 2.0.0-M4 that all LLM traffic must traverse, with admin-configurable platform provider, full prompt-injection hardening, BYOK with encrypted-at-rest user keys + UI validate flow, metadata-only observability, per-tenant credit cap (wired to Phase 2B ledger) with hard reject + top-up prompt, and a drift detection scaffold ready to flip on in production.

## Background

The codebase has zero LLM infrastructure today: no Spring AI dependency in `gradle/libs.versions.toml`, no `ChatClient` / `ChatModel` usage anywhere, no `LlmGateway` abstraction, no tokenizer dependency, no BYOK schema, no provider config surface. Phase 1 (foundation) has already shipped Jsoup 1.22.2 and the AES-GCM envelope pattern for OAuth refresh tokens (`REFRESH_TOKEN_KEY_BASE64`), both reused here. Phase 2C is a hard gate for Phase 3 (Rules Engine) and Phase 4 (Triage) — without the gateway's safety wall, no LLM call may run.

## Requirements

1. **Admin platform-provider config (LLM-00, NEW)**: SaaS operator configures the platform default LLM provider via env/secret only — no DB row, no admin UI in v1.
   - Current: No platform LLM config exists. No `application.yml` keys for Spring AI.
   - Target: `@ConfigurationProperties("zero-mail.llm.platform")` with `provider` (`openai-compatible` | `anthropic`), `base-url`, `api-key` (env-only via Docker secret / systemd credential / locked-down env file — never in repo), and per-call-site model pins (`compile-model`, `drift-model`, `triage-model`). Fail-fast at boot if `api-key` missing.
   - Acceptance: App boot fails with clear stderr message when `ZEROMAIL_LLM_PLATFORM_API_KEY` is unset; boots successfully when set; `LlmGateway` reads provider/base-url/model from `@ConfigurationProperties` only.

2. **Single gateway abstraction (LLM-01)**: All LLM traffic flows through `LlmGateway`; ArchUnit fails build on any direct `ChatClient` / vendor SDK use elsewhere.
   - Current: No `LlmGateway` class. No ArchUnit rule for LLM boundary.
   - Target: `com.zeromail.core.llm.LlmGateway` interface + impl wrapping Spring AI `ChatClient`. ArchUnit test in `backend/core/src/test/java/.../arch/LlmGatewayBoundaryTest.java` denies imports of `org.springframework.ai.chat.client.ChatClient`, `org.springframework.ai.openai.*`, `org.springframework.ai.anthropic.*`, `com.openai.*`, `com.anthropic.*` from any package outside `com.zeromail.core.llm.gateway`.
   - Acceptance: ArchUnit test passes for compliant code; planted-violation test (added then reverted) confirms test fails on violation.

3. **OpenRouter via OpenAI-compatible default (LLM-02)**: Default platform traffic routes through OpenAI-compatible `base-url` (OpenRouter when admin pastes its URL); model pin configurable per call site.
   - Current: No routing logic.
   - Target: `application.yml` defaults `provider: openai-compatible`, `base-url: https://openrouter.ai/api/v1`. Model IDs follow OpenRouter convention (`openai/gpt-4o-mini`, `anthropic/claude-3.5-sonnet`). Per-call-site pins resolved at gateway entry (compile vs drift vs triage may use different models).
   - Acceptance: Integration test with WireMock'd OpenRouter responds correctly when called via `LlmGateway.chat(callSite=COMPILE)` and the model recorded in observation matches `zero-mail.llm.platform.compile-model`.

4. **BYOK provider UI + validate flow (LLM-03)**: User picks `Anthropic` or `OpenAI Compatible` in settings, pastes endpoint (OpenAI-compat only) + key, clicks **Validate** before save.
   - Current: No BYOK UI. No `tenant_byok_credentials` table.
   - Target: `apps/web/features/billing/components/ByokForm.tsx` (or equivalent feature folder under `llm/`) with provider radio, conditional endpoint field, key field, Validate button. `POST /api/llm/byok/validate` (backend-only, browser never holds raw key past form submit) calls `GET /v1/models` for OpenAI-compatible and `POST /v1/messages` with `max_tokens: 1` for Anthropic; returns `{ ok, models?, reason? }`. `POST /api/llm/byok` saves only after validate ok. UI prevents save with unvalidated key.
   - Acceptance: Playwright test paste-validates a stub OpenAI-compatible endpoint via WireMock and saves; bad-key flow shows error and blocks save; Anthropic flow uses fixed endpoint and only key field is shown.

5. **BYOK encrypted-at-rest, AES-GCM (LLM-04, supersedes original wording)**: User-provided keys persist encrypted in DB across sessions; BYOK calls bypass platform credit deduction. **NOTE:** This supersedes the original `REQUIREMENTS.md` LLM-04 phrasing "no server-side persistence of the key beyond the request scope" — the new wording: "no server-side persistence of the **plaintext** key; encrypted-at-rest with envelope key from deployment secret is allowed and required for usable BYOK UX." `REQUIREMENTS.md` must be updated in the same SPEC commit or a follow-up plan.
   - Current: No BYOK schema, no encryption hook for non-OAuth secrets.
   - Target: New table `tenant_byok_credentials` (Liquibase changeset `014-tenant-byok-credentials.yaml`): `tenant_id` (FK), `provider` (`anthropic` | `openai-compatible`), `endpoint` (nullable for Anthropic), `encrypted_key` (BYTEA), `key_version` (SMALLINT), `created_at`, `updated_at`. Encryption reuses `REFRESH_TOKEN_KEY_BASE64` envelope pattern from Phase 1.5. Gateway resolves per-request: BYOK row exists → load + decrypt + use → skip credit ledger; no row → use platform key + decrement ledger.
   - Acceptance: Integration test creates BYOK row, calls gateway, asserts credit ledger unchanged; deletes row, calls gateway, asserts ledger decremented. ArchUnit denies plaintext-key logging via Logback scrub filter.

6. **HTML sanitization via Jsoup (LLM-05)**: All email content is HTML-sanitized before reaching any LLM.
   - Current: Jsoup 1.22.2 already in `libs.versions.toml`; not yet used in any LLM path (no LLM path exists).
   - Target: Gateway pre-call pipeline applies `Jsoup.clean(content, Safelist.none())` first.
   - Acceptance: Unit test with HTML `<script>alert(1)</script><p>hi</p>` produces output `hi` before any further pipeline step.

7. **Unicode hardening (LLM-06)**: Content is NFC-normalized; Unicode tag characters U+E0000–U+E007F stripped.
   - Current: No normalization.
   - Target: Pipeline step after Jsoup: `Normalizer.normalize(content, Form.NFC)` then regex-strip `[0-F]`.
   - Acceptance: Unit test with input containing `U+E0041` (tag character "A") produces output without it; pre-composed and decomposed forms of "ñ" produce identical bytes after the step.

8. **Tool-call wrapping with allow-list (LLM-07)**: Untrusted content wrapped in structured tool-call schema; non-allow-listed actions rejected at gateway.
   - Current: No tool-call wrapping.
   - Target: Gateway emits structured prompts with Spring AI tool-calling APIs; response parser only accepts actions in allow-list `{ label, archive, save_draft }`. Any other action (notably `send`) returns `SafetyViolationException` and never reaches caller.
   - Acceptance: Mock LLM returning `{action: "send"}` causes gateway to throw `SafetyViolationException`; mock returning `{action: "label", value: "Receipts"}` returns successfully.

9. **Token budget ≤4k via jtokkit (LLM-08)**: Content truncated to ≤4k tokens before any LLM call.
   - Current: No tokenizer dependency.
   - Target: Add `com.knuddels:jtokkit:1.x` (latest stable) to `libs.versions.toml`. Pipeline step after Unicode strip: count tokens with `cl100k_base` encoding; if > 4096, truncate by token boundary (not char) before send. For Anthropic models, jtokkit count is treated as estimate (~10–20% off acceptable).
   - Acceptance: Unit test with 10k-token input produces ≤4096-token output; truncation cut point falls on token boundary (decoded string is well-formed UTF-8).

10. **No persistence of bodies/prompts/completions (LLM-09)**: Raw email body, LLM prompt, and LLM completion live only in short-lived in-memory cache; nothing in DB or logs.
    - Current: Logback scrub filter from Phase 1 already in place; no LLM code yet.
    - Target: Spring AI observation config: `include-prompt: false`, `include-completion: false`. In-memory cache (e.g. Caffeine) sized to current request scope only — never written to disk, never serialized to Redis. ArchUnit rule: no `*Repository` accepts a parameter named `prompt`, `completion`, `messageBody`, or `emailBody`.
    - Acceptance: Integration test inspects observation spans and asserts no prompt/completion content in any span attribute; Logback test confirms scrub filter strips `prompt=...` patterns from log lines.

11. **Per-tenant credit cap, hard reject + 402 (LLM-10)**: When tenant credit ledger (Phase 2B) reaches zero for platform-key traffic, gateway rejects with HTTP 402 + UI top-up prompt; BYOK traffic is exempt; no separate time-window cap in v1.
    - Current: No credit ledger (Phase 2B in flight); no cap logic.
    - Target: Gateway pre-call: if no BYOK for tenant → call `Phase2B.CreditLedger.reserve(tenant, callSite.cost())`; on insufficient → throw `InsufficientCreditsException` mapped to HTTP 402 by global exception handler; UI shows "top up to continue" banner. No separate `daily_spend_cap_usd` table or column. The exact unit (1 credit per call vs USD-pegged) is owned by Phase 2B SPEC; 2C consumes whatever interface 2B exposes.
    - Acceptance: Integration test with tenant having 0 credits returns 402 from any gateway-fronted endpoint; same flow with valid BYOK row succeeds and ledger remains at 0; insufficient flow audit log shows `skipped_insufficient_credit`.

12. **Drift detection scaffold (LLM-11)**: Ship the fixture + scheduled job class + baseline JSON + CI mock test, with the live cron disabled by `application.yml` flag in v1.
    - Current: No drift detection.
    - Target: Fixture `backend/core/src/main/resources/llm/golden-set.json` with ~20 synthetic emails (no PII). `@Scheduled(cron="0 0 6 * * *")` job class `DriftDetectionJob` gated on `zero-mail.llm.drift.enabled` (default `false`). Baseline `backend/core/src/main/resources/llm/golden-baseline.json` committed to repo. CI test runs `DriftDetectionJob` with mocked `ChatClient` returning baseline output, asserts no drift detected; second test with mocked drift output asserts drift threshold (>20% Levenshtein on JSON tool-call args) flagged.
    - Acceptance: Both CI tests pass; `application.yml` flag flip-on documented in `02C-SPEC.md` follow-up note (production cron go-live deferred to Phase 5 or dedicated ops phase).

## Boundaries

**In scope:**
- `LlmGateway` interface + impl (Spring AI 2.0.0-M4 wrapper)
- ArchUnit rule banning direct `ChatClient` / vendor SDK use
- Sanitization pipeline: Jsoup → NFC → tag-strip → jtokkit truncate ≤4k
- Tool-call wrapping with allow-list (`{ label, archive, save_draft }`)
- Platform admin config via `@ConfigurationProperties` + env/secret only
- BYOK feature: provider selector (`Anthropic` | `OpenAI Compatible`), endpoint + key form, **Validate** button (backend-only network call), encrypted-at-rest storage (AES-GCM, reuse `REFRESH_TOKEN_KEY_BASE64`)
- Hard-reject credit cap wired to Phase 2B ledger (HTTP 402 + UI top-up prompt)
- Metadata-only observability (provider, model, token count, latency, stop reason — no content)
- Drift detection scaffold (fixture + disabled cron + baseline + mock CI test)
- Tokenizer dep (jtokkit) added to `libs.versions.toml`
- `REQUIREMENTS.md` LLM-04 wording update (encrypted-at-rest allowed)

**Out of scope:**
- DB-backed admin config + admin UI — env/secret only for v1; admin UI to revisit if hot-reload becomes a need
- Multi-provider routing / fallback chain — single platform provider for v1; per-call-site pin handles experiment cases
- Per-call-site BYOK provider pin — BYOK is per-tenant, not per-call-site
- Time-window USD/day cap independent of ledger — ledger IS the cap; rate-limiting handles abuse
- Soft-warn at 90% / configurable thresholds — hard reject only in v1; threshold tuning is Phase 5 territory
- Production drift cron + Sentry/Slack alert — scaffold only; live alerts deferred (no baseline data yet)
- Anthropic `count_tokens` API for precise non-OpenAI count — jtokkit estimate is sufficient given content-leak trade-off
- Admin probe endpoint (`POST /admin/llm/probe`) — drift job is the only end-to-end caller in 2C
- Refresh-token-style key rotation drill for BYOK — captured in STATE.md Blockers, separate phase
- Vector store / embeddings / RAG — privacy lock from PROJECT.md
- Streaming responses (SSE) — non-streaming sufficient for compile + drift; reconsider when triage UX needs it

## Drift Check Path Policy (MEDIUM cycle-3 clarification)

`LlmGateway.driftCheck(rawEmailFixture)` (Plan 03 + Plan 06):

| Concern | Decision | Rationale |
|---|---|---|
| Sanitization pipeline | RUNS (same Jsoup → NFC → tag-strip → jtokkit truncate as `chat()`) | Golden-set fixtures contain hostile HTML + Unicode tag injection by design (LLM-06 corpus); skipping sanitization would create the bypass surface that `chat()` defends against. (Codex cycle-1 HIGH `driftCheck() bypasses sanitization` — closed in Plan 03.) |
| System prompt | RUNS (same `SystemPrompts.TRIAGE_SYSTEM_PROMPT`) | Drift baseline assumes the model sees the production system prompt; otherwise drift detection compares against a different prompt regime than production. |
| Tool allow-list | RUNS (same `AllowListedTools` fixed `{label, archive, save_draft}` set) | Same rationale — drift must run against the production safety surface. |
| ActionValidator (Layer 2) | RUNS | A drift run that returns `send` is a regression — `SafetyViolationException` is the right signal. |
| Credit ledger (`reserve/settle/release`) | SKIPPED | D-E3: drift is a platform-cost operation, not user-billable. The `driftCheck()` method is invoked by `DriftDetectionJob` (worker) under a synthetic fixed tenant id (Plan 07); never by user-facing endpoints. Wrapping it in `creditLedger.reserve(...)` would either (a) fail with `InsufficientCreditsException` for the synthetic tenant, or (b) require seeding credits for a non-real tenant — both are anti-patterns. |

The asymmetry (sanitization runs, ledger doesn't) is intentional: sanitization protects the model from adversarial input, the ledger protects the platform from user cost. Drift is platform-internal — only the first concern applies.

## Endpoint Path Policy (HIGH-3 cycle-3 lock)

**Stored BYOK endpoint always includes the API version path.** Validation appends ONLY the resource segment — `/models` (OpenAI-compatible) or `/messages` (Anthropic) — NEVER `/v1/models` or `/v1/messages`.

| Provider | Stored canonical endpoint              | Validate URL                              | Forbidden malformed URL          |
|----------|----------------------------------------|-------------------------------------------|----------------------------------|
| OpenAI-compatible (OpenRouter) | `https://openrouter.ai/api/v1` | `https://openrouter.ai/api/v1/models` | `.../api/v1/v1/models`           |
| OpenAI                          | `https://api.openai.com/v1`    | `https://api.openai.com/v1/models`    | `.../v1/v1/models`               |
| OpenAI-compatible (other host)  | `https://together.xyz/v1`      | `https://together.xyz/v1/models`      | `.../v1/v1/models`               |
| Anthropic                       | `https://api.anthropic.com/v1` | `https://api.anthropic.com/v1/messages`| `.../v1/v1/messages`             |

**Trailing-slash normalization:** validator strips any single trailing `/` so `https://openrouter.ai/api/v1/` and `https://openrouter.ai/api/v1` both canonicalize to `https://openrouter.ai/api/v1`.

**Implementation locks:**
- `ByokEndpointValidator.validate{OpenAiCompatible,Anthropic}` (Plan 05a) returns the canonical URL with version path included and trailing slash stripped.
- `ByokService.validate / save` (Plan 05b) calls a centralized `joinPath(canonicalEndpoint, suffix)` helper that appends `/models` or `/messages` only.
- `BYOK chat path` (Plan 05a `OpenAiCompatibleByokModelClient` / `AnthropicByokModelClient`) passes the same canonical endpoint into Spring AI `OpenAiApi.mutate().baseUrl(...)` / `AnthropicChatOptions.baseUrl(...)` — Spring AI handles the chat-completion/messages path appending internally; do NOT pre-pend `/v1/chat/completions` etc.
- Regression tests pinned in Plan 05b: `openrouter_validate_does_not_double_prefix_v1`, `openai_validate_uses_v1_models`, `trailing_slash_does_not_change_outbound_url`.

## Constraints

- **Spring AI 2.0.0-M4 milestone caveat**: M4 → GA churn likely. Keep all direct Spring AI usage isolated in `com.zeromail.core.llm.gateway.springai` package; nothing else imports `org.springframework.ai.*`. ArchUnit enforces.
- **Secret handling**: `ZEROMAIL_LLM_PLATFORM_API_KEY` and `REFRESH_TOKEN_KEY_BASE64` resolved from VPS deployment secrets only (Docker secret / systemd credential / locked-down env file). No `:?` fallback to plain env in prod profile (parity with Phase 1.5 + worker fail-fast pattern).
- **Validate endpoint not exposed to browser raw**: Browser POSTs `{provider, endpoint, key}` to `POST /api/llm/byok/validate`; backend issues outbound call. Raw key never ends up in any client-side persistent storage; cleared from React state on success/failure.
- **Tokenizer accuracy trade-off**: jtokkit accurate for OpenAI models; ~10–20% off for Anthropic. Truncation budget set to 4096 tokens with 200-token safety headroom for Anthropic estimates. Pipeline truncates to 3896 hard cap.
- **Drift threshold defaults**: JSON struct equality required; on tool-call args, Levenshtein > 20% counts as drift. CI tests pin the threshold; production tuning deferred.
- **No content in spans/logs**: Spring AI observation `include-prompt: false`, `include-completion: false`. Logback scrub filter from Phase 1 must extend coverage to `prompt=...`, `completion=...`, `messageBody=...` patterns.

## Acceptance Criteria

- [ ] `LlmGateway` interface exists at `com.zeromail.core.llm.LlmGateway` with at least `chat(callSite, content)` method
- [ ] ArchUnit test fails build when any package outside `com.zeromail.core.llm.gateway.springai` imports Spring AI / vendor SDK classes
- [ ] App boot fails with clear stderr message when `ZEROMAIL_LLM_PLATFORM_API_KEY` is unset
- [ ] `@ConfigurationProperties("zero-mail.llm.platform")` exposes `provider`, `base-url`, `api-key`, `compile-model`, `drift-model`, `triage-model`
- [ ] Sanitization pipeline (Jsoup → NFC → tag-strip → jtokkit truncate ≤3896 hard cap) runs in order on every gateway call; each step has a passing unit test
- [ ] Tool-call response with action `send` causes `SafetyViolationException`; only `{label, archive, save_draft}` are accepted
- [ ] Liquibase changeset `014-tenant-byok-credentials.yaml` creates the BYOK table with FK to tenants
- [ ] `POST /api/llm/byok/validate` returns `{ok: true, models: [...]}` for valid OpenAI-compatible endpoint and `{ok: false, reason: ...}` for invalid; same shape for Anthropic with `models` omitted
- [ ] BYOK row exists for tenant → gateway uses BYOK key + skips credit ledger; no BYOK row → gateway uses platform key + decrements ledger
- [ ] Tenant with zero credits + no BYOK → gateway returns HTTP 402 from any consuming endpoint
- [ ] No span attribute or log line contains prompt or completion content (verified by Logback test + observation inspection)
- [ ] `golden-set.json` + `golden-baseline.json` exist; `DriftDetectionJob` is `@Scheduled` but gated on `zero-mail.llm.drift.enabled` (default `false`)
- [ ] CI test runs `DriftDetectionJob` with mocked `ChatClient` matching baseline → no drift; second test with mismatched output → drift flagged
- [ ] `REQUIREMENTS.md` LLM-04 wording updated (committed in same plan as BYOK schema changeset, or follow-up plan referenced in `02C-SPEC.md` Interview Log)
- [ ] `gradle/libs.versions.toml` adds `spring-ai-bom` (2.0.0-M4), `spring-ai-starter-model-openai`, `spring-ai-starter-model-anthropic` (optional), and `jtokkit`

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                                                                |
|--------------------|-------|------|--------|----------------------------------------------------------------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | Gateway core + admin config + BYOK + spend cap + drift all locked                                                    |
| Boundary Clarity   | 0.85  | 0.70 | ✓      | Explicit out-of-scope list with reasoning; deferred items noted                                                      |
| Constraint Clarity | 0.80  | 0.65 | ✓      | jtokkit chosen, sanitization rules pinned, ledger pattern locked; M4 churn caveat acknowledged                       |
| Acceptance Criteria| 0.80  | 0.70 | ✓      | 14 pass/fail checkboxes; ArchUnit + Liquibase + CI assertions                                                        |
| **Ambiguity**      | 0.15  | ≤0.20| ✓      |                                                                                                                      |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

**No dimensions below minimum.** All requirements have current state, target state, and acceptance criteria.

**Note on cross-phase coupling:** LLM-10 (credit cap) consumes the Phase 2B ledger interface. Phase 2C must not block on Phase 2B's exact ledger schema — gateway calls `CreditLedger.reserve(tenant, cost)` and lets Phase 2B own the unit precision (1 credit per call vs USD-pegged). If Phase 2B SPEC chooses a unit, Phase 2C call sites adopt it; if Phase 2C ships before Phase 2B, plan-phase introduces a thin `CreditLedger` interface with a stub impl that returns `OK` (with a TODO marker), to be replaced when 2B lands.

## Interview Log

| Round | Perspective       | Question summary                                              | Decision locked                                                                                                                          |
|-------|-------------------|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| 1     | Researcher        | First end-to-end LLM call site in 2C scope?                   | Drift golden-set job only — no admin probe endpoint                                                                                      |
| 2     | Researcher (cont) | Layer 0 admin config storage?                                 | Spring `@ConfigurationProperties` + env/secret only — no DB row, no admin UI                                                             |
| 2     | Researcher (cont) | BYOK key lifecycle?                                           | Encrypted at rest with AES-GCM (reuse `REFRESH_TOKEN_KEY_BASE64`); supersedes original LLM-04 wording                                    |
| 2     | Researcher (cont) | Validate button endpoint?                                     | `GET /v1/models` for OpenAI-compatible (free); `POST /v1/messages max_tokens=1` for Anthropic                                            |
| 2     | Researcher (cont) | No-BYOK fallback?                                             | Platform key + credit deduction (Phase 2B ledger)                                                                                        |
| 2     | Researcher (cont) | UI provider selector shape?                                   | `{Anthropic, OpenAI Compatible}` — drop "OpenRouter" name; user pastes OpenRouter URL into OpenAI-compatible endpoint field              |
| 3     | Simplifier        | Tokenizer choice?                                             | jtokkit for all (Anthropic ~10–20% estimate accepted; no external `count_tokens` API call)                                               |
| 3     | Simplifier        | Cap behavior at zero credit / cap reached?                    | Hard reject + HTTP 402 + UI top-up prompt; no soft warn in v1                                                                            |
| 3     | Simplifier        | Time-window cap in addition to ledger?                        | No — ledger IS the cap; abuse handled by rate limiting                                                                                   |
| 3     | Simplifier        | Drift detection scope?                                        | Scaffold + manual run + disabled `@Scheduled` flag; production cron + alerts deferred to Phase 5                                         |

**Cross-phase note on Phase 2B sequencing:** During interview, user opted to ship Phase 2B before Phase 2C. Spec is written assuming 2B ledger interface exists by 2C plan-phase; if not, 2C plan introduces stub `CreditLedger` interface (see Ambiguity Report cross-phase coupling note).

---

*Phase: 02C-llm-gateway*
*Spec created: 2026-05-05*
*Next step: `/gsd-discuss-phase 2C` — implementation decisions (Spring AI M4 builder API per call site, jtokkit encoding choice, BYOK UI component composition with shadcn primitives, ledger interface seam)*

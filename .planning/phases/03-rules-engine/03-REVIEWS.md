---
phase: 3
reviewers: [claude, opencode]
reviewed_at: 2026-05-09T18:33:38.7403059Z
plans_reviewed:
  - 03-00-PLAN.md
  - 03-01-PLAN.md
  - 03-02-PLAN.md
  - 03-03-PLAN.md
  - 03-04-PLAN.md
  - 03-05-PLAN.md
  - 03-06-PLAN.md
  - 03-07-PLAN.md
  - 03-08-PLAN.md
  - 03-09-PLAN.md
---

# Cross-AI Plan Review - Phase 3

## Invocation Notes

- Claude review used the CLI default model configuration.
- OpenCode default model invocation returned a provider error for `nemotron-3-super-free`; the review was retried successfully through the OpenCode CLI with `opencode/minimax-m2.5-free`.

## the agent Review

# Phase 3 Rules Engine — Cross-AI Plan Review

## Summary

This is a strong, mature plan set for a safety-critical phase. The architecture is correctly framed around three load-bearing invariants — gateway-only LLM access, deterministic tri-state evaluation, and zero raw mail/prompt persistence — and the wave structure (0 tests → 1 foundations → 2 core → 3 preview/templates → 4 API → 5 frontend → 6 closure) cleanly matches dependency order. The Plan 02 gateway-extension approach is the right call for resolving the `AllowListedTools` action-vs-AST mismatch surfaced in research. However, several concrete risks remain: ambiguity-detection semantics are under-specified, the JSONB schema lacks an explicit `lastPreviewedVersion` reset rule on edit, optimistic-locking and reorder concurrency are mentioned but not designed, and Plan 02 changes a public LLM gateway record (`ToolCallResult`) that is consumed by Phase 2C, drift jobs, and the worker — risk of churn that should be isolated more carefully.

---

## Plan 03-00 — Wave 0 Tests

**Strengths**
- Wave 0 spine encodes acceptance criteria as compile-time and arch-time gates before any production code lands.
- Activates `RulesBoundaryArchTest` and extends `DomainBoundaryArchTests` immediately — these can't be `@Disabled` because they protect against drift during Waves 1-5.
- Playwright spec sets the contract for the full create→preview→enable→reorder→edit→delete flow on both desktop and mobile.

**Concerns**
- **MEDIUM**: `@Disabled` scaffolds for symbols that don't yet exist will fail to compile, not just be skipped. The plan says "tests must compile" but tests referencing future records (e.g. `RuleCompileResult`, `MatcherNode`) cannot compile if those types don't exist. Either land empty model stubs in this plan or accept that some test classes are scaffolded as `// TODO` comment blocks until Plan 01.
- **LOW**: `RulePreviewServiceWave0Test` describing "fail-if-called write fake" is good, but no concrete test for the *absence* of a `GmailWriteService` field on `RulePreviewService` via reflection/ArchUnit.
- **LOW**: No Wave 0 test for the `SEMANTIC_INTENT` deferred-only invariant at the *evaluator* level (only at the model constructor level).

**Suggestions**
- Clarify policy: either (a) Plan 00 lands minimal compiling stubs of `MatcherNode`/`ActionIntent`/etc. so all tests compile, or (b) Wave 0 test files are committed with bodies but classes referencing future types are `// FUTURE:` comments. The current plan blurs the line.
- Add an ArchUnit rule: classes in `core.rules.service` named `*PreviewService*` cannot have fields whose type matches `*Gmail*Write*` or `*Action*Executor*`.

**Risk: LOW** — even if scaffolds need rework, this plan is reversible.

---

## Plan 03-01 — Foundations (Schema + Model)

**Strengths**
- Correctly extends `AbstractTenantOwnedEntity`, uses Liquibase YAML, JSONB with explicit `schema_version`, and seeds template catalog in the same changeset.
- Adds D-D1 boundary extension (`rules_no_cross_domain_repos`) — closes the cross-domain-repo loophole prophylactically.
- Includes `last_previewed_version` and `customized` columns up front, which the management/preview services need.

**Concerns**
- **HIGH**: `schema_version` is on the *row*, but no plan describes migration policy when the AST schema evolves to `rules.v2`. JSONB without a forward-compat plan invites the Pitfall 2 bomb. Even if v1 is locked, the entity should probably reject reads of unknown schema versions on load.
- **MEDIUM**: GIN indexes on JSONB are listed as a SHOULD but the plan defers to "if supported by Liquibase SQL blocks." For Phase 3 read patterns (list rules by tenant ordered by `order_index`), a JSONB GIN index isn't strictly needed — but the `(tenant_id, enabled, order_index)` btree IS needed for Phase 4 hot-path. Recommend dropping the JSONB GIN ambiguity and locking the btree indexes only.
- **MEDIUM**: `RuleEntity` has both `version` (optimistic-lock) and `last_previewed_version` (preview eligibility). These are distinct concepts but use the same word. Concrete confusion risk for executors. Consider `entity_version` and `previewed_rule_version` or similar.
- **LOW**: Template seed in same changeset as table creation — fine, but if template seed changes later, re-running migrations becomes awkward. Consider splitting into `021-rules-engine.yaml` (table) + `022-rule-templates-seed.yaml` (data).

**Suggestions**
- Lock the schema-version validation policy explicitly: rejecting unknown versions, or migrating in service layer on read.
- Rename `version` → `entityVersion` or use a clearer name to distinguish from `lastPreviewedVersion`.
- Split table DDL and seed data into two changelog entries — easier to amend seed without forcing rollback of table.

**Risk: MEDIUM** — schema decisions are durable; the version-naming overlap is a real foot-gun.

---

## Plan 03-02 — Gateway Compile Tool Profile

**Strengths**
- Correctly identifies the contract gap from research and resolves it inside `core.llm` rather than parsing free-text in `core.rules`.
- `LlmToolProfile` enum gates which tools are allowed at which call site — defense in depth.
- Preserves backward compatibility with existing `chat(callSite, rawHtml)` callers.

**Concerns**
- **HIGH**: Refactoring `ToolCallResult` is risky. It's consumed by `LlmGatewayImpl`, `SpringAiLlmModelClient`, `DriftDetectionJob`, `LlmGatewayCreditLifecycleTest`, and likely Phase 2C BYOK paths. The plan says "keep convenience constructor for backward compatibility" but a record refactor that adds `toolName` while keeping `Action action` as a derived getter is subtle. If `action()` throws "fail loud" for `rule_compile` results, every existing caller that calls `.action()` unconditionally on a `chat(...)` result will break if a profile mismatch occurs at runtime. The blast radius is wider than the plan acknowledges.
- **MEDIUM**: The new compile tool returns `clarificationQuestion` as part of the schema, which means the *model* decides ambiguity. But CONTEXT D-A3 says "If compile output is ambiguous, the UI asks one focused clarification question" — this delegates ambiguity detection to the LLM. There's no spec for what makes output "ambiguous" and how to validate the model's `clarificationQuestion` field is itself safe (no prompt injection, length-bounded, language-matched). Plan 03 inherits this gap.
- **MEDIUM**: System prompt for compile is described in prose but not committed to a fixture/test. Without a checked-in prompt baseline, drift between what the test expects and what the gateway sends is invisible.
- **LOW**: `ActionValidator` → `ToolCallValidator` rename touches existing safe-action validation. Worth a one-paragraph migration note for Phase 2C consumers.

**Suggestions**
- Consider an alternative: leave `ToolCallResult` alone for `SAFE_ACTIONS`, and introduce a separate `RuleCompileResult` record returned by a *new* gateway method `compileRule(CallSite, String)`. Avoids refactoring a load-bearing type. This trades a small API expansion for a much smaller blast radius.
- Lock the compile system prompt as a checked-in resource (`backend/core/src/main/resources/prompts/rule-compile.txt`) with a regression test asserting the file hasn't drifted.
- Spec the ambiguity contract: when may the model populate `clarificationQuestion`? What length limits? What language-match validation? Document in 03-02-SUMMARY.

**Risk: MEDIUM-HIGH** — refactoring `ToolCallResult` is the biggest single architectural risk in the phase.

---

## Plan 03-03 — Compiler + Management Service

**Strengths**
- Cleanly separates compile (LLM-touching) from management (DB-only).
- Enable gate `lastPreviewedVersion == version` is the right invariant.
- Reorder explicitly tenant-qualified.

**Concerns**
- **HIGH**: The plan does not specify what happens to `lastPreviewedVersion` on update. It says "increment version, clear preview eligibility for that version" — clearing means setting to null or to old `version - 1`? This is the central preview-before-enable invariant; one ambiguous sentence is not enough.
- **MEDIUM**: Reorder concurrency. "Use version checks or transaction locking" is hedge wording. Two users (or two tabs) reordering simultaneously can either race or deadlock. Pick one: optimistic via per-rule `entity_version` increments + retry, or pessimistic via `SELECT ... FOR UPDATE` on tenant's rules. The plan says both are options without deciding.
- **MEDIUM**: "Mark template-derived rules customized on update" — what counts as customization? Source-text edit only? Or any update including enable/disable? The latter would mark every onboarding rule customized after first preview, breaking D-C3.
- **LOW**: No mention of rule-name uniqueness or empty-name validation. Probably fine for v1, but worth confirming.

**Suggestions**
- Lock the preview-eligibility reset rule: on `update(...)`, set `lastPreviewedVersion = null` AND increment `version`. Document this explicitly in the management service's contract test.
- Pick reorder concurrency: recommend optimistic with `entity_version` array sent in reorder payload; reject the entire reorder if any version mismatches.
- Define "customization": only `source_text` edits flip `customized = true`; enable/disable/reorder do not.

**Risk: MEDIUM** — these are correctness concerns, not architecture concerns.

---

## Plan 03-04 — Evaluator + Action Merger

**Strengths**
- Tri-state result type baked in from day one (Pitfall 4 mitigation).
- `RuleEvaluationInput` is metadata-only with `bodyEvidencePresent` flag instead of body text — good privacy posture.
- Evaluator tests forbid `LlmGateway` injection.

**Concerns**
- **MEDIUM**: Regex matchers — plan says "regex safety: reject invalid regex at compile validation; evaluator handles only prevalidated bounded patterns." But where is the bound enforced? Java's `Pattern` has no built-in timeout. ReDoS via catastrophic backtracking is possible even with bounded length. Recommend `RE2J` (`com.google.re2j`) or `Pattern.compile(...).matcher(...).useTransparentBounds(false)` with a Future timeout.
- **LOW**: "Repeated evaluation over same input is stable" — what about hash-based or HashMap iteration order in matched-evidence collection? Use `LinkedHashMap` or sort.
- **LOW**: Conflict detection is described loosely ("multiple labels with different labels plus archive/save_draft combinations"). Worth listing the exact conflict types in 03-04-SUMMARY.

**Suggestions**
- Replace `java.util.regex.Pattern` with `com.google.re2j.Pattern` for user-controlled regex evaluation. Linear-time guaranteed.
- Lock conflict-type enumeration as a sealed interface with named cases: `ConflictType.MULTIPLE_DIFFERENT_LABELS`, `ConflictType.ARCHIVE_AND_SAVE_DRAFT`, etc.

**Risk: LOW-MEDIUM** — regex DoS is a real concern but mitigations are well-known.

---

## Plan 03-05 — Preview

**Strengths**
- Read-only Gmail service is a separate class with no write client injection — correctness by construction.
- Privacy test with sentinel strings is the right gate.
- Marks `lastPreviewedVersion` on success, threading the enable invariant cleanly.

**Concerns**
- **MEDIUM**: Gmail API quota and latency. Fetching 50 messages × per-tenant Gmail metadata is 50 round-trips minimum (Gmail batch API helps but isn't mentioned). Preview p95 < 500ms after fetch is achievable; the *fetch* itself can blow that. No mention of `users.messages.batchGet` or Gmail batch HTTP.
- **MEDIUM**: Disconnected tenant case. If `GmailConnectionService` returns `DISCONNECTED`, what does preview return? Plan 07 says "Gmail preview unavailable" error code, but Plan 05 doesn't describe the integration. Risk of executor improvising.
- **LOW**: `bodyEvidencePresent` flag in evaluation input — when does this get set? Plan says "fetch body-derived evidence only when matcher needs it" but evaluation happens *after* fetch. Does the data service inspect matchers to decide whether to fetch body? If so, that coupling needs spec.

**Suggestions**
- Use Gmail `users.messages.batchGet` or HTTP batch for the 25/50-message fan-out. Add a budget in 03-05-SUMMARY (e.g., max 5s wall-clock per preview).
- Add explicit error path: `RulePreviewService` throws `GmailPreviewUnavailableException` on disconnect; controller maps to `error.rules.gmail.unavailable` with reconnect prompt.
- Spec the body-fetch capability: data service receives the compiled matcher AST, computes `requiresBodyEvidence` upfront, and conditionally fetches.

**Risk: MEDIUM** — the latency story is the one thing that could make Phase 3 feel broken in production.

---

## Plan 03-06 — Templates

**Strengths**
- Adds `OnboardingService.selectedEnabledTemplateKeys(...)` facade — closes D-D1 boundary properly instead of letting `core.rules` reach into `core.onboarding.persistence`.
- Customized-preservation rule is explicit.
- Idempotency proven by "second run creates zero duplicates" test.

**Concerns**
- **MEDIUM**: Race on first `GET /api/rules` for a fresh tenant. Two concurrent requests both see "no materialized rules" and both try to insert. Need either a unique constraint on `(tenant_id, template_key)` to make insert idempotent at DB level, or advisory lock. Plan doesn't say which.
- **LOW**: "Latest active catalog row" — versioning logic is hand-wavy. If `template_version=2` is added, do existing tenants on `template_version=1` get migrated? Plan correctly defers this but should explicitly say "template-derived rules pin to materialization-time version; later versions do not retroactively apply."

**Suggestions**
- Add `UNIQUE (tenant_id, template_key)` constraint on `rules` table for template-derived rules (partial index `WHERE template_key IS NOT NULL`). Race-loser gets a constraint violation, retries, sees the row exists, skips.
- Document version-pin policy in 03-06-SUMMARY explicitly.

**Risk: LOW-MEDIUM** — the race is real but easy to fix with a partial unique index.

---

## Plan 03-07 — API

**Strengths**
- Endpoint set is complete and matches the SPEC acceptance criteria.
- Error code namespace `error.rules.*` follows project convention.
- OpenAPI regen is part of the verify step.

**Concerns**
- **MEDIUM**: `GET /api/rules` performs template materialization as a side effect. This makes a GET non-idempotent on first call (creates rows). REST purists would object; more importantly, GET is cacheable by intermediaries. Recommend either (a) explicit `POST /api/rules/initialize` called by the frontend on first mount, or (b) accept the non-idempotency but document and ensure caching headers prevent shared caches. CONTEXT D-C2 locked "first GET materializes templates idempotently" so this is a locked decision — but flag the HTTP-semantics caveat.
- **MEDIUM**: `PUT /api/rules/reorder` — what's the request shape? Full ordered list of `{ruleId, expectedVersion}`? Plan 03 says "validate version" but Plan 07 doesn't describe the DTO. Risk of executor inventing an unsafe shape.
- **LOW**: Insufficient-credits maps to 402 (re-using `error.billing.insufficient`). But compile is the only billable operation here; preview is *not* billable per CONTEXT (it uses deterministic evaluator, no LLM call). Confirm: only `/compile` returns 402, never `/preview`.

**Suggestions**
- Lock the reorder DTO shape in this plan: `record RuleReorderRequest(List<RuleOrderEntry> entries)` where `RuleOrderEntry(UUID ruleId, int entityVersion)`.
- Add `Cache-Control: no-store` on `GET /api/rules` to prevent intermediary caching given the side effect.

**Risk: MEDIUM** — the GET-with-side-effect is a locked product decision but worth surfacing for ops/caching review.

---

## Plan 03-08 — Frontend

**Strengths**
- Mirrors existing `features/llm` pattern faithfully.
- Optimistic reorder with rollback explicitly specified.
- Clarification UX as part of compile loop matches CONTEXT D-A3.
- Mobile viewport coverage in Playwright.

**Concerns**
- **MEDIUM**: "Inline clarification" UX is one focused question, but the plan doesn't say *where* the question appears (under the textarea? as an Alert above the save button?), how the user answers (same textarea? inline input field?), or whether the original source text persists during clarification. UI-SPEC describes the contract abstractly; this plan defers detail. Risk of executor producing something off-contract.
- **LOW**: Three tasks in one plan (API+hooks, components, Playwright) is heavier than the other plans. Could split components vs. browser test, but workable.
- **LOW**: No mention of error toast/inline error UX for compile failures vs. preview failures vs. credit-insufficient — UI-SPEC has the copy, but the *placement* is unspecified.

**Suggestions**
- Add a UI sketch (even ASCII) in 03-08-SUMMARY for the clarification flow before implementation: original source text in textarea (read-only or editable?), clarification question in an Alert with a single-line input, "Answer clarification" CTA recompiles with combined payload.
- Consider splitting Playwright into Task 4 to isolate browser-env setup from component work.

**Risk: LOW-MEDIUM** — frontend deviation is recoverable but easy to spend executor cycles on.

---

## Plan 03-09 — Closure

**Strengths**
- Real verification: traceability table with file/test/browser-evidence per requirement.
- Explicit Phase 4 handoff section.
- Updates to `REQUIREMENTS.md` / `ROADMAP.md` / `STATE.md` gated on evidence — no premature green check.

**Concerns**
- **LOW**: `./gradlew clean check` + full pnpm suite + Playwright will be 10+ minutes. Acceptable for closure but specify timeouts so a hung Playwright doesn't appear as a green pass.
- **LOW**: No mention of running the AI-SPEC reference dataset (30 examples per AI-SPEC §5). The AI-SPEC says "Start with 30 examples before implementation close-out" — this plan should run those fixtures, not just unit tests.

**Suggestions**
- Add a Task 4: run the reference dataset evaluation per AI-SPEC §5 and record results in 03-VERIFICATION.md. Even informally — without promptfoo CI integration — having human-labeled fixture results is the AI-SPEC closure gate.

**Risk: LOW** — closure plan is solid; just incomplete on AI-SPEC eval coverage.

---

## Cross-Cutting Observations

### Strengths
- **Wave structure is correct.** Dependencies are honored; Wave 0 catches drift; closure is gated on evidence.
- **Privacy posture is consistent.** Sentinel-string tests, repository field bans, and ArchUnit imports cover the three durable layers (DB / logs / code).
- **Gateway boundary is preserved.** No plan tempts `core.rules` to import Spring AI.
- **Tri-state evaluator and `lastPreviewedVersion` invariant** are the right architectural decisions to make Phase 4 triage safe.

### Cross-Cutting Concerns
- **HIGH — Plan 02 ToolCallResult refactor blast radius.** Worth re-evaluating the lower-risk alternative (separate `compileRule` gateway method) before committing.
- **MEDIUM — Concurrency stories under-specified.** Reorder race (Plan 03), template-materialization race (Plan 06), and `lastPreviewedVersion` reset semantics (Plan 03) need locked decisions, not hedged language.
- **MEDIUM — Schema-versioning policy.** `schema_version: "rules.v1"` is on every row but there's no policy for v2 migration. Now's the time to write that down.
- **MEDIUM — Regex DoS.** No mitigation specified. Use `com.google.re2j.Pattern`.
- **MEDIUM — Gmail batch fetch.** Preview latency could exceed budget without `batchGet`.
- **LOW — AI-SPEC reference dataset.** Closure plan doesn't run it.

### Scope Creep / Over-Engineering
- Mostly disciplined. The DB-backed template catalog (D-C4) is arguably overkill for v1 (3 starter templates), but locked by CONTEXT and preserves admin-UI optionality. Acceptable.
- The `LlmToolProfile` enum is the right amount of generality — not over-engineered, leaves room for Phase 4 semantic-eval profile.

### Security Considerations
- Prompt-injection hardening is delegated to existing gateway sanitization — appropriate.
- No mention of rule-name XSS mitigation in the frontend (rule display names render in lists / preview chips). Probably handled by React's default escaping, but worth a Vitest assertion.
- BYOK insufficient-credit path: covered through `LlmGateway`, not duplicated.

### Performance Implications
- Preview Gmail fetch is the biggest concern (see Plan 05).
- Reorder optimistic UI is fine.
- JSONB read/write performance is fine for v1 volumes.

---

## Overall Risk Assessment: **MEDIUM**

**Justification:** The phase is well-architected and well-decomposed, with strong privacy/safety boundaries. The MEDIUM risks are concentrated in three places: (1) the `ToolCallResult` refactor in Plan 02 has a wider blast radius than acknowledged and deserves a lower-risk alternative (separate gateway method), (2) several concurrency and reset-semantics decisions are hedged rather than locked, and (3) preview latency depends on Gmail batch-fetch decisions not yet specified. None of these are showstoppers; all are addressable with targeted plan edits before execution. The Wave 0 spine and ArchUnit coverage make recovery from any executor drift cheap.

**Top 3 actions before execution:**
1. Reconsider Plan 02's `ToolCallResult` refactor; prefer a new `compileRule` gateway method to isolate blast radius.
2. Lock the `lastPreviewedVersion` reset rule, reorder concurrency strategy, and template-materialization race protection (partial unique index).
3. Spec Gmail batch-fetch and the disconnected-tenant preview path in Plan 05.

---

## OpenCode Review

# Phase 3: Rules Engine Plan Review

## Summary

This is a comprehensive 10-plan phase for implementing a natural-language rules engine that compiles user-written rules into deterministic matcher ASTs, provides side-effect-free preview, and delivers a protected frontend Rules page. The plans are well-structured across 6 waves with strong attention to privacy, architecture boundaries, and the critical constraint that `core.rules` must never import Spring AI directly.

## Strengths

- **Architecture boundary enforcement**: Plans correctly enforce that `core.rules` consumes only `LlmGateway` with gateway-owned tool profiles, preventing vendor SDK leakage
- **Privacy-by-construction**: Consistent emphasis on sanitized display summaries, no raw Gmail content persistence, and privacy-safe logging throughout
- **Tri-state evaluator design**: Explicit `MATCHED`/`NOT_MATCHED`/`DEFERRED` semantics prevent accidental semantic node evaluation
- **Optimistic frontend updates**: TanStack Query reorder with proper rollback pattern
- **Proper dependency ordering**: Waves build correctly from test contracts (00) through foundations (01-02), core behavior (03-04), preview/templates (05-06), API (07), frontend (08), closure (09)
- **Template idempotency**: Clear approach for one-time materialization with customized preservation

## Concerns

### HIGH Severity

1. **03-03 clarification flow complexity** (Plan 03-03): The plan describes returning "one clarification question" but doesn't specify the UX contract - will the frontend receive a dedicated clarification response type? How does the frontend differentiate between "needs clarification" vs "compile failed"? Risk: confusion between clarification prompts and validation errors could cause poor UX.

2. **03-05 body-derived evidence boundary** (Plan 03-05): The plan states "fetch body-derived evidence only when matcher needs it" but the Phase 3 matcher vocabulary in 03-SPEC doesn't explicitly declare `requiresBodyEvidence`. Risk: evaluators may request full body content when only headers suffice, violating header-first principle.

3. **03-05 preview dependency on write-client isolation** (Plan 03-05): The plan asserts "no Gmail write APIs are referenced" but doesn't explain HOW this is enforced programmatically. Risk: future developer adds a write-client import and tests don't catch it. Suggestion: add `@AssertFalse` test for write-client class names in preview service.

### MEDIUM Severity

4. **03-02 ToolCallResult migration** (Plan 03-02): Refactoring `ToolCallResult` to handle both action results and compile results risks breaking existing callers in `backend/worker`. The plan acknowledges updating drift tests but doesn't address runtime callers. Risk: production break if worker code uses `.action()` helper on compile results.

5. **03-06 unknown template key handling** (Plan 03-06): Materialization skips "unknown/deprecated selection key" but the plan doesn't specify whether the tenant sees any feedback about skipped templates. Risk: user enabled a template in onboarding that was deprecated, but sees no indication in the rules UI.

6. **03-07 endpoint count** (Plan 03-07): 11 endpoints is dense. Risk: unclear API surface may cause frontend integration confusion. Suggestion: consider organizing under `/api/rules/compile`, `/api/rules/templates` sub-paths for clarity.

7. **03-01 source language detection** (Plan 03-01): Plan creates `RuleLanguage` enum but doesn't specify how the compiler detects EN vs VI. Is it based on rule text analysis or explicit user selection? Risk: ambiguity in language detection causes poor compile results.

### LOW Severity

8. **03-08 Playwright CI complexity**: Frontend verification requires a running dev server which adds CI complexity. The plan acknowledges this but doesn't specify the test execution environment.

9. **03-04 conflict warning UX**: Plan states "conflicts warn but do not block enablement" but doesn't define what a "conflict" is exactly. Multiple labels? Label + archive? Risk: unclear conflict definition causes inconsistent UX.

10. **03-01 template seed keys**: The plan specifies seed templates but doesn't verify these match the onboarding selection keys (`archive-receipts`, `label-newsletters`, `pin-calendar`). Risk: mismatch between onboarding and template catalog causes materialization to fail silently.

## Suggestions

1. **Add clarification response type** (03-03): Create a distinct `RuleCompileResult.clarificationRequired(Question)` record that frontend can render as an inline prompt, differentiating from validation errors.

2. **Add body evidence flag to matchers** (03-01, 03-04): Add `boolean requiresBodyEvidence()` to `MatcherNode` sealed interface; implement false for all deterministic matchers, true only for future semantic matchers.

3. **Enforce write-client exclusion programmatically** (03-05): Add a test that scans preview service bytecode for Gmail write class references, not just source imports.

4. **Document language detection strategy** (03-01): Specify whether `RuleLanguage` is derived from explicit user selection (preferred) or detected from rule text (fallback).

5. **Add skipped template notification** (03-06): Return a `skippedTemplates` list in the materialization response so the UI can show "Some selected templates are no longer available."

6. **Define conflict taxonomy** (03-04): Document explicit conflict rules: (a) multiple different labels, (b) archive + save_draft, (c) conflicting label + category actions.

7. **Verify template key alignment** (03-01, 03-06): Ensure onboarding selection keys (`archive-receipts`, `label-newsletters`, `pin-calendar`) exactly match template catalog keys before seed data is committed.

## Risk Assessment

**MEDIUM-HIGH**

The plans are architecturally sound and well-ordered, but carry elevated risk due to:
- The clarification flow complexity (HIGH) requiring careful frontend contract
- Privacy boundary enforcement (HIGH) requiring programmatic guarantees, not just assertions
- Frontend verification dependencies (HIGH) on dev server availability

Mitigations are well-described (ArchUnit boundary tests, privacy sentinel assertions, optimistic UI updates), but execution must prove the boundaries hold. The plans achieve Phase 3 goals conditional on these concerns being addressed during implementation.

---

## Consensus Summary

Both reviewers agree that Phase 3 is well-structured and technically plausible. The wave order is coherent, the privacy posture is unusually explicit, and the plans preserve the intended boundary that rules code consumes the LLM gateway rather than Spring AI or provider SDKs directly. The main remaining risks are not broad scope problems; they are a small set of contract decisions that should be locked before execution so implementers do not improvise around safety-critical behavior.

### Agreed Strengths

- The wave sequence is sound: tests and architecture gates first, then persistence/gateway foundations, then compiler/evaluator/preview/templates, API, frontend, and closure.
- The LLM boundary is correctly placed in core.llm, keeping core.rules free of Spring AI/vendor SDK imports.
- The deterministic evaluator and tri-state MATCHED / NOT_MATCHED / DEFERRED model are the right foundation for Phase 4 triage.
- Privacy constraints are consistently reflected in persistence, logging, preview, API, and frontend plans.
- The frontend plan follows existing feature-folder, typed API, TanStack Query, i18n, and Playwright conventions.

### Agreed Concerns

- The ToolCallResult refactor in Plan 03-02 has a wider blast radius than the plan acknowledges. Both reviewers flag the risk of breaking existing Phase 2C callers and suggest isolating rule compilation behind a separate gateway result or method.
- The ambiguity and clarification contract needs more detail. The plans should distinguish clarification-required results from validation errors, constrain the question payload, and define how the frontend renders and answers a single focused clarification question.
- Preview body-evidence handling is underspecified. The plans need an explicit matcher capability such as equiresBodyEvidence and a concrete rule for when transient Gmail body-derived evidence may be fetched.
- Preview write isolation should be enforced mechanically, not only by design intent. Add an ArchUnit/reflection/source-scan style test preventing preview services from depending on Gmail write clients or action executors.
- Concurrency and version semantics need locked decisions: lastPreviewedVersion reset on edit, reorder conflict handling, and template materialization race protection.

### Divergent Views

- Claude rated schema-version migration policy, ToolCallResult blast radius, and preview-version reset semantics as HIGH. OpenCode rated the clarification UX contract, body-evidence boundary, and preview write isolation as HIGH.
- Claude emphasized operational/performance concerns such as Gmail batch fetch for preview and regex DoS mitigation. OpenCode emphasized API/frontend contract clarity and template-key feedback.
- OpenCode considered endpoint density a concern; Claude treated the API surface as complete but asked for sharper DTO contracts and cache controls around GET /api/rules template materialization.

### Current Unresolved HIGH Concerns

- schema_version exists on rules rows, but the plans do not define unknown-version rejection or migration policy for future AST schema versions.
- Plan 03-02's ToolCallResult refactor may break existing Phase 2C gateway, worker, BYOK, drift, and credit lifecycle callers; a separate rule-compile gateway contract may be safer.
- Plan 03-03 does not unambiguously define the update rule for lastPreviewedVersion, weakening the preview-before-enable invariant.
- The clarification-required contract is incomplete: response shape, distinction from validation errors, frontend rendering, answer handling, payload safety, and language matching are not fully specified.
- The preview body-derived evidence boundary is incomplete: matcher vocabulary does not explicitly declare when body-derived evidence is required, creating privacy and over-fetch risk.
- Preview write-client isolation is asserted but not yet backed by a mechanical guard that fails if Gmail write/action executor dependencies enter preview code.


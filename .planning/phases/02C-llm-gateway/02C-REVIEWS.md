---
phase: 2C
reviewers: [codex, opencode]
reviewed_at: 2026-05-07T09:26:55Z
plans_reviewed:
  - 02C-01-PLAN.md
  - 02C-02-PLAN.md
  - 02C-03-PLAN.md
  - 02C-04-PLAN.md
  - 02C-05a-PLAN.md
  - 02C-05b-PLAN.md
  - 02C-06-PLAN.md
  - 02C-07-PLAN.md
  - 02C-08-PLAN.md
models:
  codex: cx/gpt-5.5 (Codex CLI default)
  opencode: openai/gpt-5.5
---

# Cross-AI Plan Review — Phase 2C: LLM Gateway

## Codex Review

## Summary

The plans are unusually thorough and mostly aligned with Phase 2C's safety-first intent: they decompose the LLM gateway into sensible waves, add strong test gates, and treat privacy, BYOK, tenant isolation, and billing as first-class concerns. However, as written I would not execute them unchanged. Several issues can break core goals: the Spring AI boundary exemption weakens LLM-01, BYOK enum persistence/JSON mapping appears inconsistent with the DB schema, server-side BYOK save does not actually require successful validation, config/YAML edits are risky, drift may bypass the sanitization contract, and the frontend i18n generation could overwrite existing translations. Overall plan quality is high, but execution risk is high until those are corrected.

## Strengths

- The wave ordering is mostly sound: foundation → sanitization → gateway → allow-list → BYOK → credit lifecycle → drift/frontend.
- Sanitization is well factored: ordered beans, per-step tests, corpus tests, fail-fast behavior, and metadata-only logging.
- Defense-in-depth is the right posture: `toolChoice=required` plus post-parse `ActionValidator`.
- BYOK threat modeling is strong: encrypted-at-rest, AAD-bound cipher reuse, key zeroing, no key logging, SSRF thinking, tenant leak tests.
- Credit lifecycle plan is good: reserve before platform call, settle on success, release on failure, BYOK/drift skip billing.
- Drift scaffold is scoped correctly for v1: disabled cron, synthetic fixtures, baseline comparison, CI mock tests.
- Frontend plan correctly calls out uncontrolled secret input and mutation-only handling for raw API keys.

## Concerns

- **HIGH: Spring AI boundary is compromised.** `LlmGatewayImpl` in `core.llm.service` is exempted from the ArchUnit rule and imports Spring AI types. The `ToolTranslator` returning `Object` plus cast back to `List<ToolCallback>` is a brittle workaround. This weakens the "all direct Spring AI usage isolated in one adapter package" goal.

- **HIGH: `BYOKProvider` persistence likely fails.** The plan uses `@Enumerated(EnumType.STRING)` while the Liquibase check allows lowercase IDs like `'anthropic'` and `'openai-compatible'`. JPA will persist `ANTHROPIC` / `OPENAI_COMPATIBLE` unless a converter is added. The same issue affects JSON/OpenAPI payloads unless `@JsonValue` / `@JsonCreator` are used.

- **HIGH: Core service depends on API DTOs in Plan 05b.** `ByokService` lives in `backend/core` but accepts `backend/api` DTO records. That inverts the module dependency. Core should expose its own command/result records or accept primitives; the controller maps API DTOs to core commands.

- **HIGH: BYOK save trusts the client-side validation state.** The SPEC says save only after validate ok. Plan 05b explicitly skips server-side upstream validation on save. A caller can POST directly to save an unvalidated or invalid key. Re-run validation on save or issue a short-lived validation token/nonce.

- **HIGH: Config edits may break boot.** Plans say "append" new `spring:` and `zero-mail:` blocks. Duplicate top-level YAML keys can override prior config. Also `zero-mail.*` and `zeromail.*` are both used across plans. Merge into existing blocks and add binding tests for API and worker.

- **HIGH: BYOK endpoint validation is incomplete unless DNS is resolved.** Blocking literal private IPs is not enough. A hostname can resolve to `127.0.0.1`, RFC1918, link-local, or metadata IP. The validator also needs exact host-suffix logic to avoid `evil-anthropic.com`.

- **HIGH: OpenAI-compatible endpoint path handling is ambiguous.** UI placeholder is `https://openrouter.ai/api/v1`, but validate calls `${endpoint}/v1/models`, producing `/api/v1/v1/models`. Define whether stored endpoint includes `/v1`, then join URLs safely.

- **HIGH: `driftCheck(prompt)` may bypass sanitization.** The drift prompt includes subject/from/body and fixtures include hostile HTML/unicode. Every LLM call must traverse the same sanitization/truncation pipeline, including drift.

- **HIGH: Gateway lets callers supply arbitrary or empty tools.** Phase 2C should own the allow-listed tool schema. Tests calling `gateway.chat(..., List.of())` conflict with `toolChoice=required`. Either gateway supplies the default `{label, archive, save_draft}` tools or rejects missing/non-allow-listed tools before model call.

- **HIGH: Plan 08 i18n generation can erase existing translations.** If `merge-feature-i18n.ts` emits only `features/**/messages.ts`, existing `vi.json` / `en.json` keys outside feature files may disappear. It must merge with an existing base or migrate all messages first.

- **MEDIUM: Platform `anthropic` provider is specified but not really implemented.** Config allows platform provider `anthropic`, but platform wiring appears OpenAI-compatible only. Either remove platform Anthropic from v1 config or implement it.

- **MEDIUM: API/provider calls need explicit timeouts.** BYOK validation should set connect/read timeouts. Otherwise validate endpoints can hang worker threads.

- **MEDIUM: response parsing is under-specified.** Empty results, null usage metadata, null tool arguments, malformed JSON, and missing required args should map to a typed malformed-response exception, not arbitrary runtime errors.

- **MEDIUM: privacy verification is incomplete.** Plans test some logs, but Phase 2C also needs span inspection and static checks preventing repositories from accepting prompt/body/completion fields.

- **MEDIUM: Plan 06 settlement failure path needs care.** If `creditLedger.settle(reservation)` partially succeeds then throws, the catch block may call `release`. This needs explicit idempotency semantics or a narrower catch around model/parse only.

- **MEDIUM: frontend browser verification is deferred.** Project instructions require real-browser verification for frontend changes. Plan 08 should include a Playwright run against `/settings`, not leave it as manual follow-up.

- **LOW: the plan set has drifted from "8 plans" to 9 plan files.** Splitting 05a/05b is sensible, but roadmap/validation docs should be updated.

- **LOW: grep-heavy acceptance gates are fragile.** Keep them for smoke checks, but rely on compile/tests/ArchUnit for real guarantees.

## Suggestions

- Move all Spring AI calls into `core.llm.gateway.springai`. A clean shape is:
  - `LlmGatewayImpl` in `core.llm.service` depends on a pure Java `LlmModelClient`.
  - `SpringAiLlmModelClient` in `gateway.springai` returns `RawToolCall(functionName, argsJson, usageMetadata)`.
  - `ActionValidator` stays in service and validates the pure Java result.
  - Remove the ArchUnit exemption and remove `ToolTranslator<Object>`.

- Add `AttributeConverter<BYOKProvider, String>` and `@JsonValue` / `@JsonCreator` for `BYOKProvider`. Do the same for any enum crossing JSON boundaries.

- Replace API DTO use in core with core command records, e.g. `ByokValidationCommand`, `ByokSaveCommand`, `ByokValidationResult`.

- Make `save()` server-enforced:
  - simplest: call `validate()` again before encrypt/upsert;
  - better: `validate()` returns a short-lived signed validation token and `save()` requires it.

- Normalize endpoints with a single policy:
  - store provider base URL including version path, then validate calls `${base}/models`, not `${base}/v1/models`; or
  - store origin only and append `/v1/...`.
  Document it and test OpenRouter, OpenAI, Anthropic, and an opt-in custom endpoint.

- Harden SSRF validation:
  - require HTTPS;
  - reject userinfo, query, fragment;
  - resolve DNS and reject private/link-local/loopback/metadata addresses;
  - validate exact host or safe suffix `.anthropic.com`, `.openai.com`;
  - re-check at request time to reduce DNS rebinding risk.

- Ensure `driftCheck` uses the same sanitization pipeline and token cap as `chat`.

- Let the gateway own default tools. If future phases need call-site-specific tools, accept a call-site enum and derive the allow-list internally.

- Add missing requirement maintenance to a plan:
  - update `REQUIREMENTS.md` LLM-04 wording;
  - reconcile "daily spend cap" vs "ledger is the cap";
  - update acceptance references from changeset `014` to `018`.

- Fix Plan 08 i18n by preserving existing messages or first migrating all messages to feature-owned sources. Add a test that existing non-LLM keys remain after `pnpm i18n:build`.

- Add Playwright verification to Plan 08's required verification, including mobile width and a validation/save mocked flow.

## Per-Plan Notes

| Plan | Assessment |
|---|---|
| 02C-01 | Good foundation, but fix enum persistence before executing. The ArchUnit exemption should be removed by redesigning the adapter boundary. |
| 02C-02 | Strong sanitization plan. Confirm `TenantContext.currentOrThrow()` type and avoid making simple unit tests require tenant binding unless the pipeline log truly needs tenant context. |
| 02C-03 | Highest architecture risk. Keep public gateway pure Java and move Spring AI response handling into the adapter package. Merge YAML instead of appending duplicate blocks. |
| 02C-04 | Good validator design. Add malformed-response handling and keep safety logs payload-free. |
| 02C-05a | Good BYOK direction, but endpoint normalization, DNS-based SSRF defense, exact Spring AI Anthropic seams, and boundary imports need tightening. |
| 02C-05b | Needs module-boundary fix and server-side validation-on-save. Also add outbound timeouts and avoid core importing API DTOs. |
| 02C-06 | Good lifecycle coverage. Clarify settle-failure behavior and keep reserve after sanitization. |
| 02C-07 | Useful scaffold. Ensure drift goes through sanitization and consider mocking `LlmGateway` in comparator tests instead of `ChatModel`. |
| 02C-08 | Good secret-handling UX. Biggest gaps are i18n overwrite risk and missing Playwright verification. |

## Risk Assessment

**Overall risk: HIGH as written.** The plans are comprehensive, but several high-severity issues can either break execution outright or violate the phase's core guarantees: enum DB mismatch, server-side BYOK validation gap, Spring AI boundary leakage, YAML/config ambiguity, endpoint SSRF/path handling, drift sanitization ambiguity, and i18n overwrite risk. After correcting those, the residual risk drops to **MEDIUM**, mainly due to Spring AI 2.0.0-M4 API churn and the inherent complexity of BYOK/provider-specific tool calling.

Docs checked via Context7: Spring AI ToolCallback/runtime tools and OpenAI-compatible `mutate()` patterns from the Spring AI reference, plus jtokkit truncation behavior from the JTokkit docs:
- https://docs.spring.io/spring-ai/reference/api/tools.html
- https://docs.spring.io/spring-ai/reference/api/chatclient.html
- https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
- https://github.com/knuddelsgmbh/jtokkit/blob/main/docs/docs/getting-started/usage.md

---

## OpenCode Review

## Summary
The plan set is thorough and security-conscious, but currently too risky to execute as-is. It covers most Phase 2C goals on paper, especially sanitization, BYOK routing, credit lifecycle, and drift scaffolding. The biggest issues are architectural boundary violations, a too-flexible tool schema that lets callers pass arbitrary tools, backend layering mistakes between `core` and `api`, incomplete privacy/observability verification for LLM-09, and several likely Spring AI / jtokkit API assumptions that need proof before implementation.

## Strengths
- Strong phase decomposition: foundation → sanitization → gateway → allow-list → BYOK → credits → drift → UI is a sensible dependency chain.
- Good defense-in-depth mindset for prompt injection: sanitization plus `toolChoice="required"` plus post-parse `ActionValidator`.
- BYOK threat model is detailed and correctly focuses on key leakage, endpoint SSRF, tenant isolation, and billing bypass.
- Credit lifecycle plan correctly calls out reserve/settle/release and BYOK/drift ledger bypass.
- Drift detection scope is appropriately scaffold-only with cron disabled by default.
- Frontend plan correctly requires uncontrolled password input and avoids storing raw keys in React state.
- Validation map is strong: most tasks have concrete automated verification commands.

## Concerns
- **HIGH: `LlmGatewayImpl` violates the stated Spring AI isolation boundary.** The SPEC and context say direct Spring AI usage must live in `core.llm.gateway.springai`, but Plans 03/04/05/06 exempt `LlmGatewayImpl` in `core.llm.service`. This weakens the main LLM-01 contract and normalizes future exemptions.
- **HIGH: Gateway accepts caller-provided `List<LlmTool>`, which can include unsafe tools.** The model may be shown a `send` tool or arbitrary schema even if `ActionValidator` rejects the result later. The gateway should own the fixed allow-list tools `{label, archive, save_draft}` or strictly validate the requested tools before sending them to the model.
- **HIGH: `core` service appears to depend on API DTOs in Plan 05b.** `ByokService` in `backend/core` cannot safely use `backend/api` DTO records such as `ByokValidateRequest`; that reverses module layering. The controller should map API DTOs to core command records.
- **HIGH: BYOK save does not actually enforce "validated before save" server-side.** Plan 05b says `save()` re-runs endpoint validation but does not re-run the upstream key probe. A malicious or buggy client can call `POST /api/llm/byok` directly with an unvalidated key.
- **HIGH: `REQUIREMENTS.md` LLM-04 update is missing from the execution plans.** The SPEC explicitly requires updating the original "no server-side persistence" wording to encrypted-at-rest BYOK. None of the plans include that artifact.
- **HIGH: LLM-09 privacy verification is incomplete.** Plans pin Spring AI observation flags, but do not fully implement or verify repository bans, span attribute inspection, prompt/completion log scrub extension, or no content in `ChatResponse.toString()` paths.
- **HIGH: YAML snippets risk duplicate top-level keys.** Plans say "append" new `zero-mail:` and `spring:` blocks. If existing `application.yml` already has those keys, duplicate YAML keys may override earlier config or fail parsing depending on parser behavior.
- **HIGH: Tool-call system prompt is missing from the gateway implementation.** The plans call `.user(sanitized.content())` but do not consistently add the fixed system prompt that tells the model email content is data and only allowed tools may be called.
- **MEDIUM: Spring AI API usage is speculative in multiple places.** `ApiKey` package, `OpenAiApi#mutate()`, `OpenAiChatModel#mutate()`, `internalToolExecutionEnabled(false)`, `toolCallbacks(...)`, and synthetic `ChatResponse` construction are all version-sensitive in 2.0.0-M4.
- **MEDIUM: jtokkit API assumptions may be wrong.** The plans assume `Encoding#encode(String, int)` returns `EncodingResult` with `isTruncated()`. This must be verified against `jtokkit 1.1.0`.
- **MEDIUM: Sanitization claims overstate NFC behavior.** NFC normalization does not fold Cyrillic/Latin homoglyphs. Tests should not claim homoglyph phishing is solved by NFC.
- **MEDIUM: `ToolTranslator` returning `Object` is a design smell.** It hides type-safety and exists only to work around the package boundary exemption. Better to keep all Spring AI types and translation inside the adapter package.
- **MEDIUM: Credit release on `settle()` failure is ambiguous.** If `settle(reservation)` throws after partially committing or due to transient DB issues, blindly calling `release(reservation)` may double-adjust or mask an unknown ledger state unless Phase 2B guarantees idempotent state transitions.
- **MEDIUM: Drift job tests should mock `LlmGateway`, not `ChatModel`.** The job depends on `LlmGateway.driftCheck()`. Mocking lower-level `ChatModel` makes tests fragile and couples worker tests to gateway internals.
- **MEDIUM: `ByokEndpointValidator` config is underspecified.** Plans reference `zeromail.llm.byok.allow-non-vendor-endpoints` and `allowed-extra-hosts`, but no properties class/application.yml wiring is planned.
- **MEDIUM: Frontend i18n merge pipeline is scope creep.** Plan 08 introduces a new generated-i18n architecture for one feature. That is larger than needed and risks breaking existing i18n workflows.
- **MEDIUM: Frontend browser verification is manual-only.** Project instructions require real browser verification for frontend changes before declaring done. Plan 08 should include an automated or explicit Playwright verification step, not only a deferred manual walk.
- **LOW: Plan 01 `files_modified` omits some files later created.** Example: temporary `SanitizationContext.java` is introduced in actions but absent from frontmatter.
- **LOW: Drift fixtures allow real company domains like `stripe.com` and `github.com` while saying "no real company names" elsewhere.** This is not PII, but the plan should be internally consistent.
- **LOW: `InvalidByokException` with no message makes debugging harder.** Privacy is correct, but an internal enum reason can be stored safely if never exposed/logged as raw endpoint/key content.

## Suggestions
- Move the concrete Spring AI gateway implementation into `core.llm.gateway.springai`; expose only pure Java `LlmGateway` from `core.llm.service`.
- Remove the `LlmGatewayImpl` ArchUnit exemption. If parsing Spring AI `ChatResponse` needs framework types, parse inside the adapter and return a pure internal result.
- Replace caller-provided arbitrary `List<LlmTool>` with gateway-owned allowed tools, or validate `LlmTool.name()` against `ActionValidator` before sending tools to the model.
- Add a fixed system prompt in every gateway call path, including `chat()` and `driftCheck()`.
- Split API DTOs from core commands: `ByokController` maps `ByokValidateRequest` → `ByokValidateCommand`, and `ByokService` only depends on core model records.
- Enforce server-side validate-before-save by re-running the upstream probe in `save()`, or issuing a short-lived validation token that `save()` must present.
- Add an explicit plan task to update `REQUIREMENTS.md` LLM-04 wording.
- Add LLM-09 tests: span attribute inspection, Logback scrub extension for `prompt`, `completion`, `messageBody`, `apiKey`, `Bearer`, `x-api-key`, and an ArchUnit rule preventing repositories from accepting content/prompt/completion parameters.
- Merge `application.yml` changes into existing top-level keys rather than appending duplicate `zero-mail:` / `spring:` blocks.
- Verify Spring AI and jtokkit APIs before implementation, ideally with a tiny compile-only spike committed or referenced in Plan 01.
- Treat credit `settle()` failure separately from model-call failure unless the ledger contract explicitly guarantees safe release after failed settle.
- Mock `LlmGateway` in drift job tests and keep lower-level gateway tests in `backend/core`.
- Keep Plan 08 i18n minimal unless the generated feature-message pipeline is already a project direction. Otherwise, directly add keys to existing vi/en bundles.
- Add Playwright verification for `/settings` BYOK form to Plan 08 completion criteria.

## Risk Assessment
**Overall risk: HIGH.**

The plans are comprehensive, but several core invariants are not actually enforced as written: Spring AI isolation is weakened, unsafe tools can be supplied to the model, core/api layering is likely broken, BYOK save can bypass upstream validation, and LLM-09 privacy verification is incomplete. These are fixable before execution, but they should be addressed at plan level rather than discovered mid-implementation.

---

## Consensus Summary

Both reviewers independently rate Phase 2C **HIGH risk as written** despite agreeing the decomposition, threat modeling, and verification rigor are above average. The convergence on architectural and security gaps is striking — the same root issues surface from two independent perspectives, which means they are real plan defects, not reviewer noise.

### Agreed Strengths
- Wave decomposition (foundation → sanitization → gateway → allow-list → BYOK → credits → drift → UI) is sound and dependency-correct.
- Defense-in-depth on prompt injection: sanitization pipeline + `toolChoice=required` + post-parse `ActionValidator`.
- BYOK threat model addresses key leakage, SSRF, tenant isolation, and billing bypass with strong intent.
- Credit lifecycle (reserve/settle/release; BYOK + drift skip ledger) is correctly scoped.
- Drift scaffold is appropriately minimal for v1 (synthetic fixtures, cron disabled by default).
- Frontend plan correctly mandates uncontrolled secret input and avoids React state for raw keys.

### Agreed Concerns (HIGH — blockers before execution)

1. **Spring AI boundary violation.** `LlmGatewayImpl` lives in `core.llm.service` and imports Spring AI types under an ArchUnit exemption. The `ToolTranslator<Object>` cast confirms the design smell. Both reviewers want the impl moved into `core.llm.gateway.springai` behind a pure-Java `LlmModelClient` seam, with the exemption removed.
2. **Gateway accepts caller-supplied tools.** `gateway.chat(..., List<LlmTool>)` lets call sites send unsafe or empty tool sets to the model. Both reviewers want the gateway to own the fixed `{label, archive, save_draft}` allow-list (or validate caller tools against `ActionValidator` before they reach the model).
3. **`backend/core` ↔ `backend/api` layering inversion.** `ByokService` in core consumes API DTO records (`ByokValidateRequest`, etc.). Both reviewers require core to expose its own command/result records and have the controller map API DTOs to core commands.
4. **BYOK save does not enforce server-side validation.** Plan 05b lets a client `POST /api/llm/byok` directly without re-running the upstream key probe. Both reviewers require either re-running `validate()` on save or a short-lived validation token/nonce.
5. **YAML "append" pattern risks duplicate top-level keys.** Plans append new `spring:` and `zero-mail:` (and inconsistently `zeromail:`) blocks instead of merging into existing ones. Both reviewers require merge + binding tests for API and worker.
6. **LLM-09 privacy verification is incomplete.** Spring AI observation flags are pinned, but neither span-attribute inspection, repository bans (ArchUnit), Logback scrubber extension for `prompt`/`completion`/`messageBody`/`apiKey`/`Bearer`, nor `ChatResponse.toString()` path checks are wired in. (Codex flagged as MEDIUM, OpenCode as HIGH — treating as HIGH for the consensus given LLM-09 is a phase invariant.)

### Agreed Concerns (MEDIUM — fix before execution but not blockers in isolation)

- **`settle()` failure handling.** Catching from `settle()` and unconditionally calling `release()` may double-adjust the ledger; needs idempotency guarantees from Phase 2B or a narrower catch around model/parse.
- **Drift job tests should mock `LlmGateway`, not `ChatModel`.** Mocking too low couples worker tests to gateway internals.
- **Frontend Playwright verification is missing from Plan 08.** Project rule requires real-browser verification before declaring done.
- **`ToolTranslator` returning `Object`.** Design smell that disappears once the Spring AI boundary is fixed.

### Divergent Views

- **`driftCheck` sanitization** — Codex flags as HIGH ("drift may bypass sanitization"); OpenCode does not call it out explicitly (covered indirectly under "fixed system prompt missing"). Worth investigating: does `driftCheck(prompt)` traverse the full sanitize → NFC → tag-strip → truncate pipeline? If not, this is a phase-invariant break.
- **BYOK endpoint SSRF depth (Codex HIGH, OpenCode silent).** Codex calls out DNS resolution + private/link-local/loopback/metadata IP checks + DNS rebinding + exact host-suffix matching. OpenCode does not address SSRF depth specifically. Codex's hardening list should be adopted.
- **OpenAI-compatible endpoint path handling (Codex HIGH, OpenCode silent).** Codex flagged the `/api/v1` + `/v1/models` double-prefix bug. Worth a one-line policy decision in SPEC.
- **Plan 08 i18n risk shape.** Codex frames as "may erase existing translations" (HIGH); OpenCode frames as "scope creep" (MEDIUM). The merge-vs-overwrite question must be answered concretely; the scope question is secondary.
- **`REQUIREMENTS.md` LLM-04 update missing (OpenCode HIGH, Codex LOW under "missing requirement maintenance").** Both noticed; OpenCode treats as a blocker, Codex as housekeeping. Add a maintenance task to one of the existing plans.
- **Tool-call system prompt missing (OpenCode HIGH, Codex silent).** OpenCode notes that `.user(sanitized.content())` lacks a fixed system prompt declaring email content as data and constraining tool use. This is a real defense-in-depth gap; should be addressed in Plan 03.
- **Spring AI 2.0.0-M4 / jtokkit API speculation (OpenCode MEDIUM, Codex implicit).** OpenCode wants a compile-only spike. Codex relies on Context7-verified Spring AI docs but does not address jtokkit. A quick compile-only spike in Plan 01 would close both gaps.

### Recommended Next Step

The `--reviews` re-plan cycle should focus on closing the seven agreed-HIGH concerns first, then the four agreed-MEDIUM ones, then resolve the divergent items (drift sanitization, SSRF depth, endpoint path policy, i18n strategy, LLM-04 wording, system prompt, API spike). After that pass, residual risk should drop to MEDIUM (Spring AI M4 churn + BYOK provider asymmetry).

# Phase 3: Rules Engine - Specification

**Created:** 2026-05-09
**Ambiguity score:** 0.14 (gate: <= 0.20)
**Requirements:** 10 locked

## Goal

Users can manage natural-language Gmail automation rules in an authenticated Rules page, compile each rule into a stored structured matcher AST, preview matches against recent Gmail messages without side effects, and enable safe rule actions for Phase 4 triage.

## Background

The codebase has no `core.rules` domain, no rules schema, no rules controller, no rules feature folder, and no authenticated rules page. Existing adjacent pieces are ready for Phase 3: Phase 2C exposes `LlmGateway.chat(CallSite.PREVIEW, rawHtml)` for NL-to-structured extraction, Phase 2B exposes the `CreditLedger` cost model through `CallSite.PREVIEW`, Phase 2A persists `mail_message_observed` rows with stable Gmail IDs, thread IDs, label IDs, and internal dates, and Phase 1 onboarding already stores placeholder template selections in `onboarding_selections`. Phase 1 explicitly expects Phase 3 to materialize those onboarding selections into real rule rows on first Rules-page visit.

Inbox Zero was used as a reference product for scope, but Zero Mail narrows the v1 rules surface to match this project's safety boundary: natural-language and manual rule creation are in scope, DB rules are the source of truth, static deterministic conditions are preferred, and testing rules against email is required. Inbox Zero actions that exceed Zero Mail v1 policy, such as send, forward, spam, webhooks, and delayed actions, are explicitly excluded.

## Requirements

1. **Rules domain and persistence**: A tenant-scoped `core.rules` domain stores rules as ordered, versioned records with source text, structured matcher AST, safe action intents, enabled state, and audit timestamps.
   - Current: No rules domain, rules table, matcher AST type, or rule order exists. Only onboarding template selections exist.
   - Target: Liquibase creates tenant-owned rule persistence for rule identity, display name, source text, matcher AST JSONB, action intents JSONB, enabled flag, zero-based or one-based ordering, version, created/updated timestamps, and template origin when applicable.
   - Acceptance: A tenant can create multiple rules, retrieve them in order, update one without corrupting order, and a cross-tenant integration test proves Tenant A cannot read or mutate Tenant B rules.

2. **Natural-language compile**: User-authored natural-language rules compile through `LlmGateway.chat(CallSite.PREVIEW, ...)` into the structured matcher AST and safe action intents; free-form LLM output is never used at runtime.
   - Current: Phase 2C returns `ToolCallResult(Action action, Map<String,Object> args)` but no Phase 3 compiler consumes it.
   - Target: A rule compiler service calls only `LlmGateway` for compile, validates the returned structure against Phase 3 AST schemas, rejects unknown matcher/action nodes, and persists only the validated AST plus source text.
   - Acceptance: Given "Archive receipts from Stripe and label them Finance", compile persists a rule with deterministic sender/domain or subject/receipt matchers plus `archive` and `label` action intents; a mock LLM response with unknown matcher/action keys is rejected and not persisted.

3. **Matcher vocabulary**: The v1 AST supports deterministic matcher nodes for sender email/domain, recipients (`to`/`cc`), subject contains/equals/regex, Gmail label/category present or absent, has attachment, list-unsubscribe/newsletter indicators, message age/date, boolean groups (`all`/`any`/`not`), and a `SEMANTIC_INTENT` marker.
   - Current: No matcher vocabulary exists; Phase 2A stores only message IDs, labels, and dates.
   - Target: The AST has explicit typed nodes for the listed deterministic matchers and a `SEMANTIC_INTENT` node that can be stored and displayed but is marked deferred.
   - Acceptance: Unit tests cover each matcher type against synthetic message metadata; `SEMANTIC_INTENT` evaluates to "deferred" rather than true/false and is not sent to an LLM during Phase 3 preview.

4. **Deterministic evaluator**: Preview and future triage use a deterministic evaluator for all non-semantic matchers without making an LLM call.
   - Current: No evaluator exists.
   - Target: Evaluation accepts a compiled AST and transient message preview data, returns match/no-match/deferred plus matched node IDs or reason codes, and never calls `LlmGateway`.
   - Acceptance: A test double that fails on `LlmGateway` invocation remains unused during evaluator tests; deterministic match results are stable across repeated runs for the same AST and message data.

5. **On-demand preview data**: Rule preview fetches recent Gmail messages on demand and treats headers, snippets, and bodies as transient request data only.
   - Current: `mail_message_observed` persists Gmail IDs, thread IDs, label IDs, and internal dates; it does not persist subject/from/to/cc/snippets/bodies.
   - Target: Preview selects recent observed inbox message IDs, fetches needed Gmail metadata/content for the current request, sanitizes/truncates any content before compile or display where applicable, and persists no raw headers, bodies, snippets, prompts, or completions.
   - Acceptance: Preview of the last 25 messages returns match results and safe display summaries to the browser, while database/log assertions show zero persisted raw header, body, snippet, prompt, or completion content.

6. **Preview controls and limits**: Preview is side-effect-free, supports sample sizes 10, 25, and 50, defaults to 25, and never calls Gmail write APIs.
   - Current: No rules preview endpoint or UI exists.
   - Target: Users can preview a draft or saved rule against recent inbox messages and see which messages would match and which safe actions would be proposed, with a hard maximum of 50 messages per preview.
   - Acceptance: Browser and API tests prove preview does not create labels, archive messages, save drafts, or change rule enabled state; invalid sample sizes are rejected or normalized to the allowed set.

7. **Authenticated Rules page**: `apps/web` ships an authenticated Rules page where users can create, edit, enable, disable, reorder, delete, preview, and enable template rules.
   - Current: Authenticated protected routes exist for onboarding/settings only; no `features/rules` folder or route exists.
   - Target: A protected route, feature-owned API functions, hooks/query keys, and components implement the full rule management workflow with typed OpenAPI client calls and Vietnamese/English i18n coverage.
   - Acceptance: Playwright covers create -> preview -> save -> enable/disable -> reorder -> edit -> delete on desktop and mobile; typecheck, lint, i18n check, and relevant Vitest suites pass.

8. **Template materialization**: Existing onboarding template selections materialize into real rules exactly once per tenant on first Rules-page visit or first rules API read.
   - Current: `onboarding_selections` stores template keys such as `archive-receipts`, `label-newsletters`, and `pin-calendar`; no real rule rows are created from them.
   - Target: Phase 3 maps each enabled onboarding selection to a corresponding template rule with source text, compiled AST, action intents, and template origin; repeated visits are idempotent.
   - Acceptance: A tenant with three onboarding selections gets exactly three template-derived rules after first rules load; running the materialization again creates zero duplicates and preserves user edits to already materialized rules.

9. **Safe action intents only**: Phase 3 stores and previews only `label`, `archive`, and `save_draft` action intents; it does not execute Gmail writes.
   - Current: Phase 2C allow-list exposes `label`, `archive`, and `save_draft`; no rules action persistence exists.
   - Target: Rule action schema accepts only the three safe intents and rejects `send`, `forward`, `spam`, webhook, delayed action, move folder, or arbitrary tool/action names.
   - Acceptance: Backend tests reject every excluded action at create/update/compile boundaries; preview displays "would label/archive/save draft" but no Gmail write request is emitted.

10. **API and OpenAPI contract**: Backend exposes typed rule CRUD, reorder, compile, preview, and template-enable APIs consumed by the frontend through generated OpenAPI types.
    - Current: OpenAPI generation exists and the frontend consumes generated schemas, but no rules endpoints are present.
    - Target: `backend/api` controllers remain thin, service-owned transactions live in `backend/core`, endpoints follow the existing API error/i18n contract, and `apps/web/lib/api/schema.d.ts` includes all rules request/response types.
    - Acceptance: Regenerated OpenAPI contains every rules endpoint; frontend feature API modules use generated path types; controller integration tests cover success, validation errors, insufficient credits for compile/preview, and tenant isolation.

## Boundaries

**In scope:**
- `core.rules` Modulith domain with model, service, persistence, and package boundary declarations.
- Liquibase YAML changelog for rule persistence and JSONB matcher/action storage.
- Natural-language compile to structured matcher AST through `LlmGateway.chat(CallSite.PREVIEW, ...)`.
- Deterministic evaluator for v1 matcher vocabulary.
- `SEMANTIC_INTENT` storage/display/deferred marker, without runtime LLM evaluation.
- On-demand Gmail preview fetch for recent messages with transient-only raw content handling.
- Side-effect-free preview for sample sizes 10/25/50, default 25, max 50.
- Authenticated Rules page in `apps/web` with create/edit/enable/disable/reorder/delete/preview/template-enable flows.
- Template materialization from `onboarding_selections` into real rules, exactly once per tenant.
- OpenAPI regeneration and typed frontend client use.
- Privacy/logging tests proving no raw headers, bodies, snippets, prompts, or completions are persisted or logged.

**Out of scope:**
- Runtime triage orchestration on new messages - Phase 4 owns applying rules to live mail.
- Gmail write execution (`label`, `archive`, `save_draft`) - Phase 4 owns side effects and audit.
- Undo, immutable triage audit log, shadow mode, and sender safety net - Phase 4 requirements.
- Runtime LLM evaluation for `SEMANTIC_INTENT` - Phase 4 handles batched semantic evaluation.
- Auto-send, Gmail send scope, or any send action - permanently excluded from v1 safety policy.
- Forwarding, marking spam, moving folders, webhooks, delayed actions, and arbitrary tool calls - excluded to keep v1 safe and within the existing action allow-list.
- Prompt-file sync, learned patterns, inbox personalization memory, embeddings, vector DB, or RAG over mail - incompatible with v1 privacy constraints.
- Full in-app mail client or long-term storage of fetched message content - Gmail remains the source of truth.
- Template design expansion beyond the common v1 starter set - this phase materializes and enables templates, not a marketplace.

## Constraints

- Use Java 25, Spring Boot 4, Gradle Kotlin DSL, Liquibase YAML, PostgreSQL JSONB, and existing package-by-domain conventions.
- All direct Spring AI usage remains inside the Phase 2C gateway adapter; Phase 3 imports `LlmGateway` only.
- Compile and preview are billable platform calls when no BYOK row exists and must respect Phase 2B insufficient-credit behavior; BYOK bypasses platform credit deduction through the gateway.
- Preview raw message data is request-scoped only. Persist rule definitions, stable Gmail IDs/thread IDs, matcher/action metadata, template origin, and timestamps only.
- Matchers must be deterministic except `SEMANTIC_INTENT`, which is explicitly deferred and must not silently evaluate true.
- Rules are tenant-scoped and ordered per tenant; all bulk updates/reorders must be tenant-qualified.
- Frontend follows existing feature folder conventions: `features/rules/api`, `components`, `hooks`, query keys, generated OpenAPI types, and VI/EN i18n parity.
- Browser verification is required for frontend changes before Phase 3 is declared done.

## Acceptance Criteria

- [ ] `core.rules` exists with tenant-scoped rule persistence, JSONB matcher/action columns, enabled state, ordering, version, timestamps, and template origin.
- [ ] Rules cannot be read, edited, reordered, deleted, or previewed across tenant boundaries.
- [ ] Natural-language compile calls `LlmGateway.chat(CallSite.PREVIEW, ...)` and persists only validated structured AST/action data.
- [ ] Unknown matcher/action keys from compiler output are rejected and leave no partial rule row.
- [ ] Deterministic evaluator covers sender, recipient, subject, label/category, attachment, newsletter/list-unsubscribe, date/age, boolean group, and deferred semantic nodes.
- [ ] `SEMANTIC_INTENT` nodes are stored and displayed as deferred; Phase 3 preview never calls an LLM to evaluate them.
- [ ] Preview defaults to 25 messages, allows 10/25/50, caps at 50, and is side-effect-free.
- [ ] Preview fetches recent Gmail data on demand and persists/logs no raw headers, bodies, snippets, prompts, or completions.
- [ ] Rules API supports list, get, create, compile, update, enable/disable, reorder, delete, preview, and template materialization/enable flows.
- [ ] Existing onboarding template selections materialize into real rule rows exactly once per tenant and do not overwrite edited materialized rules.
- [ ] Frontend authenticated Rules page supports create, edit, enable/disable, reorder, delete, preview, and template-enable flows on desktop and mobile.
- [ ] OpenAPI is regenerated and `apps/web` consumes generated typed paths for rules endpoints.
- [ ] Excluded actions (`send`, `forward`, `spam`, webhook, delayed action, arbitrary tool calls) are rejected at backend validation boundaries.
- [ ] Backend tests, frontend tests, i18n check, typecheck, lint, and Playwright rules-flow checks pass.

## Ambiguity Report

| Dimension           | Score | Min   | Status | Notes |
|---------------------|-------|-------|--------|-------|
| Goal Clarity        | 0.92  | 0.75  | met    | Full product slice locked: rules page, compile, preview, CRUD/reorder, templates. |
| Boundary Clarity    | 0.84  | 0.70  | met    | Phase 4 triage/write/audit work and unsafe Inbox Zero-style actions excluded. |
| Constraint Clarity  | 0.82  | 0.65  | met    | Preview limits, privacy storage, gateway-only LLM usage, matcher vocabulary, and credit/BYOK behavior locked. |
| Acceptance Criteria | 0.82  | 0.70  | met    | 14 pass/fail criteria spanning backend, frontend, privacy, OpenAPI, and browser verification. |
| **Ambiguity**       | 0.14  | <=0.20| met    | Gate passed after round 2. |

Status: met = dimension meets minimum, below = planner treats as assumption.

## Interview Log

| Round | Perspective | Question summary | Decision locked |
|-------|-------------|------------------|-----------------|
| 1 | Researcher | Should Phase 3 include real UI or backend/API only? | Real authenticated Rules page is required; backend/API only is not enough. |
| 1 | Researcher | How should preview get message data? | Fetch recent Gmail messages on demand; raw headers, bodies, snippets, prompts, and completions are transient only and never persisted. |
| 1 | Researcher | What matcher vocabulary should be required? | User delegated the exact v1 vocabulary to the agent. |
| 2 | Researcher + Simplifier | Use Inbox Zero as reference for rules? | Yes. Reference current Inbox Zero docs/repo, then narrow scope to Zero Mail v1 safety constraints. |
| 2 | Simplifier | What matcher/action/default preview scope is the irreducible v1? | Static deterministic matchers plus deferred `SEMANTIC_INTENT`; actions limited to `label`, `archive`, `save_draft`; preview defaults to last 25 messages, allows 10/25/50. |
| 2 | Boundary check | Which Inbox Zero capabilities are excluded? | Send, forward, spam, webhooks, delayed actions, prompt-file sync, learned patterns, embeddings, and runtime semantic LLM evaluation are out of scope. |
| 2 | Gate | Ambiguity score 0.14; proceed to write spec? | User selected "Yes - write SPEC.md". |

Reference material consulted during round 2:
- Inbox Zero AI Personal Assistant docs: https://github.com/elie222/inbox-zero/blob/main/docs/essentials/email-ai-personal-assistant.mdx
- Inbox Zero architecture notes: https://github.com/elie222/inbox-zero/blob/main/ARCHITECTURE.md

---

*Phase: 03-rules-engine*
*Spec created: 2026-05-09*
*Next step: $gsd-discuss-phase 3 - implementation decisions (schema details, AST record shapes, API route design, UI composition, evaluator internals, and test plan)*

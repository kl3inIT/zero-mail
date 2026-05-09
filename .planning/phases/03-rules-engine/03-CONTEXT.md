# Phase 03: rules-engine - Context

**Gathered:** 2026-05-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 delivers the rules engine slice: users author Gmail automation rules in natural language, compile them into a stored deterministic matcher AST plus safe action intents, preview results against recent Gmail messages without side effects, manage rule CRUD/reorder/enablement in an authenticated Rules page, and materialize onboarding template selections into real rules. Phase 3 creates and previews rule definitions only. Phase 4 owns runtime triage, Gmail write execution, audit, undo, shadow mode, and sender safety nets.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**10 requirements are locked.** See `03-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `03-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
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

**Out of scope (from SPEC.md):**
- Runtime triage orchestration on new messages. Phase 4 owns applying rules to live mail.
- Gmail write execution (`label`, `archive`, `save_draft`). Phase 4 owns side effects and audit.
- Undo, immutable triage audit log, shadow mode, and sender safety net. Phase 4 requirements.
- Runtime LLM evaluation for `SEMANTIC_INTENT`. Phase 4 handles batched semantic evaluation.
- Auto-send, Gmail send scope, or any send action. Permanently excluded from v1 safety policy.
- Forwarding, marking spam, moving folders, webhooks, delayed actions, and arbitrary tool calls.
- Prompt-file sync, learned patterns, inbox personalization memory, embeddings, vector DB, or RAG over mail.
- Full in-app mail client or long-term storage of fetched message content.
- Template design expansion beyond the common v1 starter set. This phase materializes and enables templates, not a marketplace.

</spec_lock>

<decisions>
## Implementation Decisions

### A. Rule Authoring and Compile Review

- **D-A1: Natural-language-first authoring is locked.** The primary authoring input is a natural-language text area. Users edit the source text, not the compiled AST. Compiled matcher/action details are shown for review, but they are review-only in Phase 3.
- **D-A2: English and Vietnamese rule text are both supported.** The compiler should detect the user's language from the rule text. Where possible, compiled matcher/action review details and clarification prompts should use the same language as the authored rule.
- **D-A3: Ambiguity must trigger inline clarification before save.** If compile output is ambiguous, the UI asks one focused clarification question in the editor, accepts the answer, and recompiles. Do not persist a guessed rule.
- **D-A4: The compiler remains constrained to the locked Phase 3 vocabulary.** Natural-language text can only compile into the deterministic matcher/action vocabulary from `03-SPEC.md`. `SEMANTIC_INTENT` may be stored and displayed as deferred, but Phase 3 never evaluates it at runtime.
- **D-A5: Saving creates a disabled rule; enabling requires preview.** After successful compile, the rule may be saved, but it is disabled by default. The user must preview the rule before enabling it.

### B. Preview Evidence and Privacy Boundary

- **D-B1: Preview rows show safe summaries plus evidence chips.** Each result should show sanitized sender/domain, subject excerpt, date, current Gmail labels, proposed action chips, and matched matcher-clause chips. Do not render full HTML email content.
- **D-B2: Preview is header-first.** Use headers, metadata, label IDs, dates, and subject excerpts by default. Fetch, sanitize, and truncate body-derived content only when a matcher actually needs body evidence. All fetched content remains request-scoped.
- **D-B3: `SEMANTIC_INTENT` renders as deferred evidence.** In preview, semantic nodes appear as visible deferred semantic-check chips. They do not produce true/false matches in Phase 3.
- **D-B4: Preview summary is impact-first.** Before enablement, show sample size, matched count, proposed action counts, deferred count, and explicit copy that no Gmail changes were made.

### C. Template Materialization

- **D-C1: Onboarding template selections materialize disabled.** Existing `onboarding_selections` rows become real rules exactly once per tenant, but resulting template-derived rules remain disabled until the user previews and enables each one.
- **D-C2: First `GET /api/rules` materializes templates idempotently.** The API is the source of truth. The first rules API read should initialize template-derived rules so all consumers see the same state; do not make materialization a frontend-only side effect.
- **D-C3: Template origin is provenance, not default behavior.** Template-derived rules preserve their template origin and template version for attribution and future template-management work. Once a user edits a template-derived rule, mark it customized and never overwrite it during future materialization.
- **D-C4: Use a DB-backed template catalog in Phase 3.** Add template catalog persistence now for the v1 starter templates, rather than keeping the catalog only in code. The admin UI and advanced lifecycle behavior are deferred, but Phase 3 should store enough metadata to support template versions, deprecations, and future migrations safely.

### D. Rule Ordering and Match Semantics

- **D-D1: Evaluate all matching rules in user order.** Preview and future Phase 4 triage should evaluate enabled rules in the user's configured order. Every matching enabled rule may contribute safe action intents.
- **D-D2: Deduplicate identical safe actions with provenance.** Exact duplicate actions should merge into one proposed action while preserving which rules contributed the duplicate and the ordered evidence behind it.
- **D-D3: Conflicts warn but do not block enablement in Phase 3.** Preview should flag conflicting or risky action combinations, but enabling is still allowed because Phase 3 does not execute Gmail writes. The warning is evidence for user trust, not a hard gate.
- **D-D4: Saved-rule preview includes the current disabled rule plus enabled siblings.** When previewing a disabled saved rule, include that current rule in the simulation along with other enabled rules. Ignore unrelated disabled rules.

### The Agent's Discretion

- Exact AST record names, JSON schema property names, enum ids, database constraint names, and API DTO names are left to the planner/executor, as long as they satisfy `03-SPEC.md`, the decisions above, and project conventions.
- Exact UI composition is left to the planner/executor, but it must follow the existing `apps/web` feature-folder, shadcn/raw-primitive, i18n, and Playwright verification patterns.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-specific, locked
- `.planning/phases/03-rules-engine/03-SPEC.md` - Locked requirements, boundaries, constraints, acceptance criteria, and interview log. MUST read before planning.

### Project-level
- `.planning/PROJECT.md` - Trust posture, privacy policy, safe action allow-list, OpenRouter/BYOK direction, Java/Spring/Gradle/Next.js constraints.
- `.planning/REQUIREMENTS.md` - `RULE-01..RULE-07`, plus upstream `MAIL-*`, `BILL-*`, and `LLM-*` requirements that Phase 3 consumes.
- `.planning/ROADMAP.md` - Phase 3 goal, dependencies on 2A/2B/2C, and phase boundary relative to Phase 4.

### Prior-phase context
- `.planning/phases/02C-llm-gateway/02C-CONTEXT.md` - `LlmGateway` contract, `CallSite.PREVIEW`, `ToolCallResult`, action allow-list, sanitization/privacy invariants, BYOK/credit behavior.
- `.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md` - `CreditLedger`, `CallSite` costs, insufficient-credit behavior, reserve/settle/release contract.
- `.planning/phases/02A-mail-ingestion/02A-CONTEXT.md` - `mail_message_observed` privacy floor, Gmail metadata available for preview, transient-content boundary, worker/API patterns.

### In-code anchors
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` - Phase 3 imports this interface for NL compile only; callers cannot pass arbitrary tools.
- `backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java` - Gateway output shape that Phase 3 decodes into rules-owned typed matcher/action records.
- `backend/core/src/main/java/com/zeromail/core/llm/model/Action.java` - Safe action enum: `label`, `archive`, `save_draft`; no send.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java` - `PREVIEW` cost and locked enum membership.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java` - Existing observed-message metadata: Gmail message ID, thread ID, history ID, label IDs, internal date, observed-at.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` - Idempotent observed-message insert pattern and tenant-scoped composite key.
- `backend/core/src/main/java/com/zeromail/core/onboarding/persistence/OnboardingSelectionRepository.java` - Source rows for template materialization.
- `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` - Thin-controller pattern: controller maps DTOs to core command records, service owns business logic.
- `apps/web/features/llm/api/llm-api.ts` and `apps/web/features/llm/hooks/use-byok.ts` - Feature API and TanStack Query hook patterns to mirror for `features/rules`.
- `apps/web/app/(protected)/layout.tsx` - Protected route group already wraps `QueryProvider` and `PauseBanner`; Rules page should fit protected-route conventions.
- `apps/web/app/(protected)/settings/page.tsx` - Current dense authenticated settings surface and shadcn Card composition pattern.
- `apps/web/features/onboarding/components/TemplateCard.tsx` and `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx` - Existing onboarding template keys and selectable-template UI.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `core.llm.service.LlmGateway` is already implemented and privacy-hardened. Phase 3 should call `chat(CallSite.PREVIEW, rawHtml)` for rule compilation and should not import Spring AI directly.
- `core.llm.model.Action` already locks the safe action vocabulary. Phase 3 action-intent validation should align with this enum, not create a wider action surface.
- `core.billing.model.CallSite.PREVIEW` already exists and costs 1 credit. Compile/preview platform calls must respect Phase 2B/2C insufficient-credit behavior; BYOK bypass remains gateway-owned.
- `mail_message_observed` provides stable Gmail IDs, thread IDs, label IDs, internal date, and observed-at without message content. Preview can select candidate recent messages from this table before fetching transient Gmail metadata/content on demand.
- `OnboardingSelectionRepository.findByTenantId(...)` provides existing selected template keys: `archive-receipts`, `label-newsletters`, `pin-calendar`.
- `apps/web` already has `features/llm` and `features/triage` examples for feature-owned API functions, hooks, and components.

### Established Patterns

- Backend domains use `core.<domain>/{model,service,persistence,persistence.lowlevel}` plus package-info Modulith boundaries. Create `core.rules` in this shape.
- Controllers are thin and map HTTP DTOs to core command records. Services own transactions and repository usage.
- Records are used for DTOs/value objects; JPA entities are mutable classes with protected no-arg constructors. No Lombok.
- Enums follow `IdentifiedEnum` with fail-loud `fromId`; never persist or compare enum ordinals.
- Privacy logs use `event=<name> tenantId={}` and must never include email bodies, prompts, completions, snippets, subjects, token bytes, or raw model output.
- Frontend feature modules use `features/<feature>/api`, `components`, `hooks`, optional `query-keys.ts`, generated OpenAPI types, and i18n parity.
- Protected frontend changes require real browser verification with Playwright before Phase 3 is declared complete.

### Integration Points

- `core.rules` depends on `core.llm` for compile, `core.billing` indirectly through the gateway call-site behavior, `core.gmail` for observed-message preview candidates and transient Gmail fetch services, `core.onboarding` for materialization source selections, `core.tenant` for current tenant resolution, and `core.shared.*` for persistence/lang conventions.
- `backend/api` adds rules controllers and DTO packages, then regenerates OpenAPI so `apps/web/lib/api/schema.d.ts` contains all rules paths and schemas.
- `apps/web` adds a protected Rules page and `features/rules` API/hooks/components. It should consume generated path types rather than hand-rolled response shapes.

</code_context>

<specifics>
## Specific Ideas

- Authoring should feel like "write a rule in English or Vietnamese, review what Zero Mail understood, preview safely, then enable."
- The UI should not present a full AST editor in Phase 3. Review cards/chips are acceptable; edits happen through the natural-language source and recompile loop.
- Clarification questions are part of the compile loop and should prevent saving guessed rules.
- Preview should make safety visible with "no Gmail changes were made" copy and action/evidence chips.
- Template metadata should support future admin/template-catalog management without shipping that admin surface now.

</specifics>

<deferred>
## Deferred Ideas

- Admin surface to manage the template catalog, template versions, deprecation behavior, and migration behavior. Phase 3 stores enough metadata to support this safely, but does not build the admin UI or lifecycle automation.

</deferred>

---

*Phase: 03-rules-engine*
*Context gathered: 2026-05-10*

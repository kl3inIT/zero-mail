# Phase 03: Rules Engine - Research

**Researched:** 2026-05-10
**Domain:** Safety-critical Gmail rule authoring, structured LLM extraction, deterministic matching, and protected Next.js operations UI
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- Natural-language-first authoring is locked. Users edit source text, not the compiled AST.
- English and Vietnamese rule text are both supported; clarification prompts should preserve the authored language where possible.
- Ambiguous compile output must ask one focused inline clarification question before save.
- The compiler is constrained to the Phase 3 matcher/action vocabulary. `SEMANTIC_INTENT` may be stored/displayed as deferred, but Phase 3 never evaluates it with an LLM.
- Saving creates a disabled rule; enabling requires preview.
- Preview rows show safe summaries plus evidence chips, not full email content.
- Preview is header-first. Body-derived content is fetched only when a matcher needs it, then sanitized/truncated and discarded.
- `SEMANTIC_INTENT` renders as deferred evidence.
- Preview summary is impact-first and must state that no Gmail changes were made.
- Onboarding template selections materialize into disabled real rules exactly once per tenant.
- The first `GET /api/rules` materializes templates idempotently.
- Template origin/version are provenance. Customized template rules are never overwritten.
- Use a DB-backed template catalog in Phase 3.
- Preview and future triage evaluate all matching rules in user order.
- Duplicate safe actions merge while preserving contributing-rule provenance.
- Conflicts warn but do not block enablement in Phase 3.
- Saved-rule preview includes the current disabled rule plus enabled siblings; unrelated disabled rules are ignored.

### The Agent's Discretion

- Exact AST record names, JSON property names, enum ids, database constraint names, API DTO names, and UI composition are left to implementation as long as the locked spec, context, and conventions are honored.

### Deferred Ideas (OUT OF SCOPE)

- Runtime triage orchestration, Gmail write execution, audit, undo, shadow mode, sender safety net, runtime semantic LLM evaluation, raw mail content storage, template admin UI, template lifecycle automation, prompt-file sync, learned patterns, embeddings, vector DB, and a full in-app mail client.
</user_constraints>

<phase_requirements>
## Phase Requirements

| Requirement | Implementation Obligation | Planning Coverage |
|-------------|---------------------------|-------------------|
| RULE-01 | Plain-language rule authoring in English and Vietnamese | Plans 02, 03, 07, 08 |
| RULE-02 | NL compile through gateway-owned Spring AI tool call into structured AST | Plans 02, 03 |
| RULE-03 | Deterministic evaluator with no LLM call | Plans 04, 05 |
| RULE-04 | `SEMANTIC_INTENT` stored/displayed as deferred only | Plans 01, 03, 04, 05, 08 |
| RULE-05 | Side-effect-free preview against recent messages | Plans 05, 07, 08 |
| RULE-06 | Enable, disable, reorder, edit, delete rules | Plans 01, 03, 07, 08 |
| RULE-07 | Template gallery/materialization for common starter rules | Plans 06, 07, 08 |
</phase_requirements>

<architectural_responsibility_map>
## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Rule persistence, ordering, versioning, template provenance | Database/Backend | API | PostgreSQL stores tenant-owned durable automation state; services own transactions and tenant predicates. |
| NL rule compilation | Backend | LLM gateway adapter | `core.rules` calls `LlmGateway.chat(CallSite.PREVIEW, ...)`; Spring AI schema/tool details remain in `core.llm.gateway.springai`. |
| Compile validation and ambiguity handling | Backend | Browser | Backend validates schema fail-closed; frontend renders clarification loop and blocks save. |
| Deterministic matcher evaluation | Backend | API | Java evaluator is the single source of truth for preview and Phase 4 reuse; no LLM dependency. |
| Preview Gmail data fetch | Backend | Gmail API | Backend reads recent observed IDs, fetches transient Gmail metadata/content on demand, sanitizes display summaries, and emits no writes. |
| Rules CRUD/reorder/enable/delete | Backend/API | Browser | Services own invariants and transactions; controllers map DTOs to commands. |
| Rules page UX | Browser/Client | Frontend Server | Next.js App Router page shell can stay server-side; interactive composer/list/preview components are client components. |
| i18n and accessibility | Browser/Client | Build scripts | Existing `next-intl` and i18n parity checks enforce VI/EN copy coverage. |
</architectural_responsibility_map>

<research_summary>
## Summary

Phase 3 should be implemented as a deterministic rules domain with a narrow LLM compile adapter, not as an agentic runtime. Spring AI documentation confirms current `ChatClient` tool-calling and native structured-output support, but project architecture already has a stricter boundary: `core.rules` must consume only the project-local `LlmGateway`, while any new tool schema or `toolChoice("required")` behavior remains gateway-owned.

The standard backend shape is `core.rules/{model,service,persistence}` with JSONB matcher/action columns, explicit tenant predicates, optimistic locking, and separate services for compile validation, rule state transitions, evaluation, preview data, and template materialization. The frontend should follow the existing protected-route feature pattern: typed OpenAPI functions under `features/rules/api`, TanStack Query hooks/query keys, a client rules workspace nested in a server page, i18n keys in VI/EN, and Playwright browser verification.

**Primary recommendation:** Split execution into waves: Wave 0 tests, Wave 1 schema/domain/gateway compile contract, Wave 2 compiler/evaluator/preview core, Wave 3 templates/API, Wave 4 frontend, Wave 5 closure.
</research_summary>

<standard_stack>
## Standard Stack

### Core

| Library/Tool | Version | Purpose | Why Standard |
|--------------|---------|---------|--------------|
| Java | 25 | Backend runtime | Project lock; records/sealed types fit AST/value objects. |
| Spring Boot | 4.0.6 | API/core application framework | Project lock; MVC + virtual threads, not WebFlux. |
| Spring AI | 2.0.0-M5 | Model/tool-call adapter behind `LlmGateway` | Project lock; official docs show `ChatClient` tool callbacks and structured output patterns. |
| Spring Modulith | 2.0.7-SNAPSHOT | Domain boundary verification | Existing project boundary tool. |
| PostgreSQL | 17.6 | Rule storage, JSONB matcher/action columns | Existing primary datastore; JSONB supports schema-versioned AST blobs. |
| Liquibase | 5.0.2 | YAML migrations | Project lock. |
| Gmail API Java client | v1-rev20250331-2.0.0 | On-demand preview read calls | Existing Gmail integration dependency. |
| Next.js | 16.2.4 | Protected Rules page | Project lock; App Router server/client component split. |
| TanStack Query | 5.100.9 | Rules queries, mutations, optimistic reorder | Existing frontend state tool; docs show invalidation and rollback patterns. |

### Supporting

| Library/Tool | Version | Purpose | When to Use |
|--------------|---------|---------|-------------|
| Jackson 3 (`tools.jackson.*`) | Boot-managed | JSONB serialization/validation tests | Serialize/deserialize AST/action records and DTOs. |
| Testcontainers PostgreSQL | 1.21.3 | Real migration/integration tests | Rule schema, JSONB, tenant isolation, reorder constraints. |
| ArchUnit | 1.4.2 | Boundary and privacy tests | Enforce no Spring AI imports in `core.rules`, no repository content persistence. |
| Playwright | 1.59.1 | Real browser rules workflow verification | Required for frontend changes. |
| shadcn/Base UI primitives | Existing | Rules page components | Preserve design system and avoid custom wrappers unless justified. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| JSONB matcher/action columns | Fully normalized matcher/action tables | Normalization makes arbitrary boolean AST trees cumbersome; JSONB plus schema version is better for v1. |
| Gateway-owned compile tool | Free-form model JSON parsing in `core.rules` | Free-form parsing violates RULE-02 and weakens prompt-injection guardrails. |
| Java deterministic evaluator | Runtime LLM evaluation | Phase 4 owns semantic LLM evaluation; Phase 3 must be deterministic. |
| Optimistic reorder with rollback | Pessimistic UI locks only | TanStack Query supports rollback; API still owns final order/version invariants. |
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### System Architecture Diagram

```text
Author rule text
  -> Rules page composer
  -> POST /api/rules/compile
  -> core.rules RuleCompilerService
  -> LlmGateway.chat(CallSite.PREVIEW, compiler payload)
  -> gateway-owned Spring AI tool schema
  -> validated RuleCompileDraft
  -> clarification OR disabled saved rule

Saved rule preview
  -> POST /api/rules/{id}/preview
  -> select recent observed Gmail IDs
  -> transient Gmail metadata/body read when needed
  -> deterministic evaluator
  -> action dedupe/conflict summary
  -> safe preview DTO
  -> UI evidence chips and "No Gmail changes were made"

First rules read
  -> GET /api/rules
  -> template materialization service
  -> onboarding selections + DB template catalog
  -> disabled template-derived rules
  -> ordered rules response
```

### Recommended Project Structure

```text
backend/core/src/main/java/com/zeromail/core/rules/
  package-info.java
  model/
    Rule*.java, MatcherNode.java, ActionIntent.java, Preview*.java, Template*.java
  persistence/
    RuleEntity.java, RuleRepository.java, RuleTemplateEntity.java, RuleTemplateRepository.java
  service/
    RuleCompilerService.java, RuleCompileResultValidator.java
    RuleManagementService.java, RuleEvaluator.java
    RulePreviewService.java, RulePreviewDataService.java
    RuleTemplateMaterializationService.java

backend/api/src/main/java/com/zeromail/api/controllers/rules/
backend/api/src/main/java/com/zeromail/api/dto/rules/

apps/web/features/rules/
  api/rules-api.ts
  hooks/use-rules.ts
  query-keys.ts
  components/*
  messages.ts
apps/web/app/(protected)/rules/page.tsx
```

### Pattern 1: Gateway Encapsulation for Structured Compile

**What:** `core.rules` submits a compact compiler payload through `LlmGateway.chat(CallSite.PREVIEW, ...)` and validates the returned tool arguments into rules-owned records.

**When to use:** Every natural-language compile and clarification recompile.

**Source:** Spring AI 2.0 snapshot docs show `ChatClient` tool registration through `.toolCallbacks(...)` and OpenAI-style `toolChoice("required")`; existing code already translates project-local `LlmTool` to Spring AI callbacks inside `SpringAiLlmModelClient`.

### Pattern 2: Server Shell + Client Workspace

**What:** Next.js App Router pages/layouts are Server Components by default; place the interactive rules workspace in a nested `'use client'` component.

**When to use:** `/rules`, because composer state, mutations, optimistic reorder, and preview interactions require client state.

**Source:** Next.js 16.2.2 docs show nesting Client Components inside Server Components and keeping only interactive components under the `'use client'` directive.

### Pattern 3: TanStack Query Optimistic Reorder

**What:** On reorder mutation, cancel current list query, snapshot previous ordered list, optimistically apply new order, rollback on error, and invalidate on settled.

**When to use:** Rule reorder controls. CRUD and enable/disable can use normal invalidation if the UX does not need immediate reordering.

**Source:** TanStack Query v5.90.3 docs show `onMutate` cancellation/snapshot, `setQueryData`, `onError` rollback, and `onSettled` invalidation.

### Anti-Patterns to Avoid

- Direct Spring AI imports in `core.rules`.
- Parsing model prose or accepting a no-tool-call response.
- Treating `ToolCallResult.args()` as trusted.
- Persisting preview headers, snippets, bodies, prompts, completions, model raw args, or embeddings.
- Implementing preview by calling Gmail write services and "dry-run" flags. The preview path should not depend on write clients at all.
- Rendering JSON AST as the primary UI.
- Enabling a new or edited rule without a successful preview for that rule version.
</architecture_patterns>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: Compile Tool Contract Mismatch

**What goes wrong:** Current `AllowListedTools` exposes action tools (`label`, `archive`, `save_draft`) but Phase 3 needs a complete matcher AST plus actions.
**Why it happens:** Reusing Phase 2C action tools directly cannot express nested matchers or ambiguity.
**How to avoid:** Add a gateway-owned rule-compile tool/schema while preserving `LlmGateway` as the only public dependency for `core.rules`.
**Warning signs:** `core.rules` starts parsing `ToolCallResult.action()` as the rule action instead of a compile envelope, or uses string parsing of completion text.

### Pitfall 2: JSONB Without Runtime Validation

**What goes wrong:** Unknown matcher/action nodes persist and later Phase 4 must interpret unsafe state.
**Why it happens:** JSONB accepts any object unless Java validation is strict.
**How to avoid:** Use schema version, sealed/typed model records, enum `fromId` fail-loud, bounded regex/strings, and tests that reject unknown keys before persistence.
**Warning signs:** Repository save methods accept raw `Map<String,Object>` from controller or gateway.

### Pitfall 3: Preview Leaks Content

**What goes wrong:** Headers/snippets/bodies or prompts enter DB, logs, spans, snapshots, or fixtures.
**Why it happens:** Preview DTOs and debug logs are convenient during development.
**How to avoid:** Return sanitized display summaries only, use synthetic fixtures, add log/DB assertion tests, and avoid logging DTO `toString()`.
**Warning signs:** Field names containing `body`, `snippet`, `rawHeader`, `prompt`, or `completion` appear in repositories, entities, logs, or frontend storage.

### Pitfall 4: Semantic Deferral Drift

**What goes wrong:** `SEMANTIC_INTENT` returns false or true to simplify evaluator tests.
**Why it happens:** Boolean evaluator APIs collapse tri-state results.
**How to avoid:** Make match result tri-state: `MATCHED`, `NOT_MATCHED`, `DEFERRED`; show deferred evidence and counts.
**Warning signs:** Evaluator return type is `boolean`, or tests assert semantic nodes as non-matches.

### Pitfall 5: Reorder Race and Cross-Tenant Updates

**What goes wrong:** Bulk reorder updates rows without tenant predicates or overwrites concurrent edits.
**Why it happens:** Ordered lists tempt simple `UPDATE rule SET position = ... WHERE id = ...`.
**How to avoid:** Service method resolves current tenant, validates all IDs belong to tenant, uses version checks or transaction locking, and saves a normalized contiguous order.
**Warning signs:** Repository methods update by ID only, or reorder request lacks expected versions.
</common_pitfalls>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| LLM provider integration | Raw HTTP/provider SDK calls in rules | Existing `LlmGateway` | Preserves BYOK, credits, sanitization, observations, and safety enforcement. |
| Client server-state cache | Local reducer cache for rules | TanStack Query | Handles invalidation, mutation lifecycle, optimistic rollback. |
| API types | Hand-written frontend DTOs | Springdoc + openapi-typescript | Existing typed-client pipeline prevents drift. |
| UI primitives | Bespoke card/button/toggle wrappers | Existing shadcn/Base UI primitives | Matches project design and accessibility expectations. |
| Gmail preview write safety | "Dry-run" flags on write services | Read-only preview service | Avoids accidental side effects by construction. |
</dont_hand_roll>

<code_examples>
## Code Examples

### Spring AI Tool Registration Boundary

```java
// Source: Spring AI 2.0-SNAPSHOT tools docs.
ChatClient.create(chatModel)
    .prompt("What day is tomorrow?")
    .tools(new DateTimeTools())
    .call()
    .content();
```

Use this only as adapter guidance. In Zero Mail, equivalent tool registration belongs in `core.llm.gateway.springai`, not `core.rules`.

### Next.js Server Page with Client Workspace

```tsx
// Source: Next.js 16.2.2 App Router docs.
import RulesWorkspace from './RulesWorkspace'

export default function Page() {
  return <RulesWorkspace />
}
```

`RulesWorkspace` owns `'use client'` because it uses mutations, local composer state, and browser interactions.

### TanStack Query Optimistic Reorder

```tsx
// Source: TanStack Query v5 optimistic updates docs.
const mutation = useMutation({
  mutationFn: reorderRules,
  onMutate: async (nextOrder, context) => {
    await context.client.cancelQueries({ queryKey: rulesKeys.list() })
    const previousRules = context.client.getQueryData(rulesKeys.list())
    context.client.setQueryData(rulesKeys.list(), applyOrder(previousRules, nextOrder))
    return { previousRules }
  },
  onError: (_error, _variables, result, context) => {
    context.client.setQueryData(rulesKeys.list(), result?.previousRules)
  },
  onSettled: (_data, _error, _variables, _result, context) => {
    context.client.invalidateQueries({ queryKey: rulesKeys.list() })
  },
})
```
</code_examples>

<sota_updates>
## State of the Art (2024-2026)

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Free-form completion parsing | Tool/structured-output validation | Fail-closed contracts are expected for automation state. |
| Client-only route components by default | Server pages with nested Client Components | Smaller bundles and clearer data/control boundaries in App Router. |
| Refetch-only list mutations | Optimistic update with rollback | Better operational UX for reorder while preserving server truth. |
| Prompt logging for debugging | Metadata-only observations | Mandatory for privacy-sensitive mail products. |
</sota_updates>

<open_questions>
## Open Questions

1. **Should Phase 3 introduce a dedicated `rule_compile` gateway tool or reuse the existing action tool names with richer arguments?**
   - What we know: Current action tools cannot express a whole AST cleanly.
   - What's unclear: Whether the existing `ToolCallResult(Action action, Map<String,Object> args)` should grow a neutral compile action or a new project-local result type.
   - Recommendation: Plan 02 should keep changes inside `core.llm` and expose only a stable `LlmGateway` result that `core.rules` can validate. Do not parse free text.

2. **Does preview need Gmail body fetch in Phase 3?**
   - What we know: Header-first is locked; body is fetched only if a matcher needs body evidence. The locked matcher vocabulary in SPEC does not require full body search explicitly.
   - What's unclear: Whether newsletter/list-unsubscribe and attachment evidence can be satisfied from metadata/headers alone for all starter templates.
   - Recommendation: Build preview data service with a capability flag: metadata-only by default, body fetch method present and tested as transient if/when a matcher declares `requiresBodyEvidence`.
</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)

- Context7 `/websites/spring_io_spring-ai_reference_2_0-snapshot` - Spring AI `ChatClient`, tool callbacks, structured output, OpenAI `toolChoice("required")` adapter guidance.
- Context7 `/vercel/next.js/v16.2.2` - App Router server/client component split and `'use client'` directive guidance.
- Context7 `/tanstack/query/v5.90.3` - `useMutation`, invalidation, optimistic updates, and rollback patterns.
- `.planning/phases/03-rules-engine/03-SPEC.md` - Locked Phase 3 scope and acceptance criteria.
- `.planning/phases/03-rules-engine/03-AI-SPEC.md` - AI compile/eval guardrails, failure modes, and evaluation strategy.
- `.planning/phases/03-rules-engine/03-UI-SPEC.md` - Rules page visual and interaction contract.
- Existing code anchors in `core.llm`, `core.gmail`, `core.onboarding`, `backend/api`, and `apps/web/features`.

### Secondary (MEDIUM confidence)

- Inbox Zero docs/repo cited by `03-SPEC.md` - Product reference narrowed by Zero Mail v1 safety constraints.
</sources>

<metadata>
## Metadata

**Research scope:**
- Core technology: Spring Boot 4, Spring AI behind gateway, PostgreSQL JSONB, Gmail API read preview, Next.js 16 App Router, TanStack Query.
- Ecosystem: Existing Zero Mail domain modules, i18n/test/build pipelines.
- Patterns: gateway encapsulation, JSONB typed validation, deterministic tri-state evaluator, optimistic reorder, protected operational UI.
- Pitfalls: schema bypass, privacy leakage, semantic deferral drift, cross-tenant reorder, accidental Gmail writes.

**Confidence breakdown:**
- Standard stack: HIGH - project versions and Context7 docs verified.
- Architecture: HIGH - follows existing codebase boundaries and locked phase artifacts.
- Pitfalls: HIGH - derived from prior phase patterns and AI-SPEC failure modes.
- Code examples: MEDIUM - official docs examples are adapter-level only; implementation must use project-local gateway.

**Research date:** 2026-05-10
**Valid until:** 2026-06-10 for project patterns; 2026-05-17 for Spring AI M5 adapter details.
</metadata>

---

*Phase: 03-rules-engine*
*Research completed: 2026-05-10*
*Ready for planning: yes*

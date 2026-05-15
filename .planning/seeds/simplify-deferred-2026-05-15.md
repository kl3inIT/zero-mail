# Simplify Pass Deferred Items — 2026-05-15

Two items from the original `/simplify` whole-project sweep were too big to inline:

1. **Redis cache layer for LLM hot path** — infrastructure surface (cache regions, invalidation events, Testcontainers setup) deserves its own phase.
2. **God service split (TriageOrchestratorService + LlmGatewayImpl)** — ~1800 LOC across two core-value services, design needs upfront work to identify natural seams; behavioral regression risk too high for a sweep commit.

The other 24 findings were executed in-session across 10 commits.

---

## C + D. Redis cache layer for LLM hot path

**Current pain points**:
1. **C1-E** `OpenAiByokModelClient:37-49` — rebuilds `OpenAiChatModel` + `ChatClient` (WebClient, RetryTemplate, observation hooks) per BYOK call. Project rule chốt "per-tenant ChatModel cache in Redis" — currently violated.
2. **C2-E** `LlmGatewayImpl:736` — `findByokCredentials` SELECT to Postgres on EVERY LLM call. For non-BYOK tenants (majority) it always returns empty.
3. **D / H1-E** `TriageOrchestratorService.loadEnabledCandidates` — re-fetches rules + re-parses `matcherAst` JSON via Jackson for every observed Gmail message.

**Design direction**:
- Add `spring.cache.type=redis` + a `@EnableCaching` configuration class.
- Cache regions:
  - `byokCredentials` keyed on tenantId — short TTL (60s) or invalidate on `ByokService.save/delete`
  - `byokChatModel` keyed on `(tenantId, model, baseUrl, apiKeyFingerprint)` — invalidate on BYOK row update
  - `tenantRules` keyed on tenantId — invalidate on rule create/update/delete/reorder (already partially solved by `H2-E TenantService.triageSettingsFor` merge for triage settings, but rule list still uncached)
  - Compiled matcher AST — Caffeine in-process per (ruleId, version)
- Redis serializer choice (JSON via Jackson 3 vs JDK)
- Invalidation events via Spring Modulith (the project already uses this pattern for cross-domain notifications)

**Blockers / decisions**:
- Cache TTL vs explicit invalidation policy — pick per region
- Test strategy: embedded Redis (Testcontainers) vs `@MockBean CacheManager`
- Whether to invalidate via direct call OR Spring Modulith event (per `core.tenant.event.*` pattern)
- Observability: cache hit/miss metrics via Micrometer

**Risk**: medium. Cache invalidation is the classic two-hard-problem. Bad invalidation = users see stale BYOK key / paused status. Need contract tests for "after update X, cache reflects X within one request."

**Suggested entry point**: `/gsd-discuss-phase` to lock TTLs + invalidation strategy → `/gsd-plan-phase` → `/gsd-execute-phase`. Test plan must include a Redis-backed integration test using Testcontainers since `@MockBean CacheManager` will not catch serializer issues.

## E. God service split (TriageOrchestratorService + LlmGatewayImpl)

**Current pain points**:
- `TriageOrchestratorService` — 896 LOC. Single class owns: pause/shadow gating, rule loading, deterministic + semantic matcher evaluation, action proposal merging, safety-policy gating, sender safety-net check, audit saga orchestration, draft body generation, post-draft thread classification.
- `LlmGatewayImpl` — 931 LOC. Single class owns: BYOK credential lookup, ChatModel construction, sanitization pipeline invocation, semantic intent evaluation, draft body generation, safety violation post-filter, tool JSON schema serialization.

The `#22 TriageDispatchContext` record (completed 2026-05-15) already trimmed `handleProposals` from 7 to 2 params and threaded the same context through `commandFor` / `preWriteIntent` / `classifyAfterDraftSaved`. That dropped the param-sprawl smell but did NOT shrink either god class — those still need decomposition.

**Design direction (to be locked in `/gsd-discuss-phase`)**:

Candidate seams for `TriageOrchestratorService`:
- **Orchestrator-step pattern** — turn each phase (rule load, evaluation, merge, safety, dispatch) into a single-method service; orchestrator becomes a thin sequencer.
- **Policy/Executor split** — `TriagePolicyEvaluator` (pure: produces a decision) + `TriageDispatcher` (impure: mutates Gmail + audit). Sender safety-net and shadow-mode become decisions, not branches.
- **Command bus** — `TriageDecision` events fan out via Spring Modulith to handlers; orchestrator only chooses the decision.

Candidate seams for `LlmGatewayImpl`:
- **Per-call-site facades** — `SemanticIntentService` + `DraftBodyService` + `ByokModelResolver` as separate `@Service` classes behind the existing `LlmGateway` interface.
- **Provider strategy** — BYOK vs platform routing extracted into a `ChatModelProvider` strategy; gateway only sees a resolved `ChatModel`.

**Blockers / decisions**:
- Which seam is the right one? Depends on whether the next product slice (auto-send? auto-followup? rules v2?) wants to inject new behavior at the orchestrator level or the executor level.
- Test coverage today is mostly integration-shaped (full orchestrator path with mocked dependencies). After the split, each sub-service needs targeted unit tests to prevent regressions — that test backfill is part of the phase scope, not free.
- `TriageDispatchContext` will likely flow through any new dispatcher service; reuse it rather than re-deriving.

**Risk**: high. These are the AI-trust hot paths (CLAUDE.md core value: "AI auto-triage that users trust with their real inbox"). A regression in safety gating or audit-saga sequencing is user-visible. Split must be backed by 100% green test suite at each atomic commit.

**Suggested entry point**: `/gsd-discuss-phase E god-service-split` → lock seam direction → `/gsd-plan-phase` with explicit test-backfill tasks → `/gsd-execute-phase` with atomic per-seam commits. Do NOT attempt this inside another `/simplify` sweep.

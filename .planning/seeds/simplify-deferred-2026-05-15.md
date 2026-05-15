# Simplify Pass Deferred Items — 2026-05-15

Only the Redis cache layer was deemed too infrastructure-heavy to inline into the simplify pass. Everything else from the original `/simplify` whole-project sweep is being executed in-session.

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

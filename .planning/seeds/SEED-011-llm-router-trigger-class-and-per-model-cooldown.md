---
id: SEED-011
status: dormant
planted: 2026-05-22
planted_during: Phase B Platform Keys v2 — A1 multi-model-per-tier design
trigger_when: "when production LLM traffic shows correlated failures across same-provider tiers OR when 429/cooldown thrash burns budget noticeably"
scope: medium
---

# SEED-011: LLM Router — Trigger-Class Differentiation + Per-Model Cooldown

## Why This Matters

Phase B Platform Keys v2 (commit chain landing 2026-05-22) implements the **A1 multi-model
per tier** failover design: each `(feature, tier)` anchors one provider plus an ordered
list of models; the router iterates models inside a tier, then escalates to the next tier.

This matches LiteLLM's two-level pattern (model_group + cross-group fallbacks) and the
2026 industry consensus on tier cascades. **Two polish items from that consensus were
deliberately deferred** so we ship A1 lean. This seed records them.

## Polish 2 — Trigger-Class Differentiation

**The pattern (LiteLLM)**: don't treat every failure the same. Classify into three buckets
and route fallback accordingly:

- `fallbacks` — rate-limit (429) and server errors (5xx). Default escalation.
- `context_window_fallbacks` — request exceeded model's context window. Skip same-family
  models in the next tier; jump to a wider-context model.
- `content_policy_fallbacks` — content moderation refusal. Same-provider models will hit
  the SAME moderation pipeline → skip entire provider in the next tier.

### What's missing today

`MasterKeyTestResult` is a flat enum (`OK / RATE_LIMITED / INVALID_KEY / NETWORK_ERROR /
UNKNOWN`). The router treats every non-OK the same: try next model → next tier.

### What changes

1. Enrich error surface in `LlmGatewayImpl.invoke()` to emit a richer `LlmCallFailure`
   with `{ type: TriggerClass, httpStatus, retryable }`.
2. Add `TriggerClass` enum: `TRANSIENT_429_5XX`, `CONTEXT_WINDOW`, `CONTENT_POLICY`,
   `AUTH_FAILURE`, `OTHER`.
3. `LlmRouter` consumes the class:
   - `TRANSIENT_429_5XX` → normal escalation (next model → next tier).
   - `CONTEXT_WINDOW` → skip remaining models in current tier (they share the same
     context window roughly); jump to next tier.
   - `CONTENT_POLICY` → skip all models on the SAME PROVIDER in subsequent tiers; jump
     to first tier with a different provider.
   - `AUTH_FAILURE` → flag the key (PENDING/REVOKED) automatically; emit an admin alert.
4. Add `feature_default_provider.fallback_policy` column or a per-tier policy struct if
   we want to make this admin-overridable.

### Trigger condition

Production telemetry shows a measurable cohort of failures that *would have* fallen back
to a same-provider tier when content moderation refused — i.e. wasted retries on
duplicate moderation. Until that's a real cost, classifier overhead isn't worth it.

## Polish 3 — Per-Model Cooldown Inside a Tier

**The pattern (LiteLLM `cooldown_time` + `enable_weighted_failover`)**: if `model[0]` just
returned 429, don't try it again for the next N seconds. Skip directly to `model[1]`.
After cooldown expires, `model[0]` is eligible again.

### What's missing today

The A1 router naïvely retries `model[0]` on every request even if it 429'd the previous
call seconds ago. Burns request budget on a key that's guaranteed to fail.

### What changes

1. Introduce a Redis-backed cooldown state per `(provider, model)`:
   - Key: `llm:cooldown:{provider}:{model_id}`
   - Value: epoch second when cooldown expires.
   - TTL: same as value (auto-expiring entry).
2. `LlmRouter.resolve()` filters the tier's model list: skip any model whose cooldown
   is still in effect.
3. `LlmGateway` on 429 / 503 writes the cooldown:
   - 429 → respect `Retry-After` header if present, else default 60s.
   - 503 / 502 → default 30s.
4. Admin UI surface: show a small "cooldown until HH:MM" badge on the affected model in
   the routing matrix.

### Trigger condition

Production logs / metrics show ≥5% of LLM calls being wasted on models we just 429'd. Or:
solo operator anecdotally sees thrash that makes routing decisions feel "stuck."

## What NOT to bundle with Polish 2/3

- **Multi-provider per tier** (Option A2). Solved by adding more tiers. Don't reintroduce.
- **Per-key model activation** (the dropped `key_model_binding` design). The semantic is
  per-MODEL, not per-key. A1 already encodes this correctly.
- **Cost-aware routing** (cheapest-first cascade). Different concept — orthogonal to
  availability fallback. Belongs in a separate seed if it ever matters.

## Implementation Estimate

- Polish 2: ~3-4 days. Touches `LlmGatewayImpl`, `LlmRouter`, and adds 1 enum + 1 column
  if admin override is wanted. Mostly straight-line work; failure-class classification
  is the only non-trivial bit (vendor SDKs report errors inconsistently).
- Polish 3: ~2 days. Redis state, router filter, gateway writer. The harder part is the
  admin UI "cooldown until …" badge — needs polling or SSE to stay live.

## Related

- Commit `7a6af6d7` — HTTP/1.1 builder for cleartext base URLs (Phase B foundation).
- Commit chain ending `<A1 batch landing 2026-05-22>` — schema 082, router refactor, FE
  multi-model picker.
- See `.planning/research/STACK.md` for the LLM stack overview.

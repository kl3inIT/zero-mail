---
id: SEED-016
status: dormant
planted: 2026-05-21
planted_during: Phase 8 admin console research detour into rate-limit / scheduler library evaluation
trigger_when: "when reviewing current Redis rate-limiter code, OR when adding a new rate-limit surface (LLM cost cap, outgoing send cap, BYOK provider quota, webhook delivery rate)"
scope: small-to-medium
---

# SEED-016: Evaluate Bucket4j-Redis for Rate Limiting

## Why This Matters

Zero Mail's stack pins Redis 7.2 for "rate limiting, idempotency, session store, per-tenant ChatModel cache" (project CLAUDE.md). The actual rate-limiter implementation is currently bespoke (Spring Data Redis + Lettuce, hand-rolled).

Bespoke Redis limiters are easy to get **subtly wrong**:
- `INCR + EXPIRE` race: if the process dies between `INCR` and `EXPIRE`, the counter never expires → key sticks at max forever for that window.
- Fixed-window has burst problem at boundary (2x rate possible in 1s spanning two windows).
- Concurrent token consume on a token-bucket without Lua atomicity → over-issue.
- No standardized refill semantics (greedy vs interval) when policy needs tuning.

**Bucket4j** is a battle-tested Java token-bucket library with:
- Lua-script atomic operations on Redis (`bucket4j-redis` via Lettuce — matches Zero Mail's transport).
- Multiple refill strategies (greedy / interval / fixed) — needed when tuning LLM cost cap policy.
- Algorithm-correct by construction; no race conditions to audit.

This is a **low-risk, high-confidence migration** if the current limiter is ad-hoc. If it's already Lua-script-based and tested, the ROI drops sharply — don't migrate just for aesthetics.

## When to Surface

**Trigger options (any one):**
1. When reviewing or refactoring current Redis rate-limit code path.
2. When adding a new rate-limit surface: per-tenant LLM cost cap, per-tenant outgoing send cap, BYOK provider quota guard, webhook delivery rate, login rate (if Spring Security's default isn't enough).
3. When a privacy / safety incident traces back to a limiter race.

**Do NOT surface** as a standalone refactor phase — pair it with a feature that needs a new limit, so the migration cost is amortized.

## Scope Estimate

**Small-to-medium**. Concrete steps:

1. **Audit current limiter** (1-2h): read code, classify as one of:
   - Naive `INCR + EXPIRE` → high-value migration
   - Lua-script atomic → low-value migration
   - Bucket4j already → done, skip
2. **Verify Boot 4 + JDK 25 compat** via Context7 + Maven Central probe (memory `feedback-spring-boot-4-breaking-changes` says verify before pinning). Bucket4j 8.x supports JDK 21+ as of last check; **8.x on Boot 4 is unverified — recheck at trigger time.**
3. **Pick algorithm per surface:**
   - Token bucket for cost caps (allows bursts up to bucket size, smooth refill)
   - Sliding window for auth/login (fairness over fairness-at-boundary)
   - Fixed window only for non-critical surfaces (analytics, metrics)
4. **Migrate one surface at a time**, behind a feature flag if production traffic is hitting the limiter.
5. **Delete** old hand-rolled limiter after last surface migrated.

## Candidate Product Shape

- `RateLimitService` interface in `backend/core` with method-level surfaces (`checkLlmCost`, `checkSend`, `checkLogin`, etc.).
- Bucket4j Redis backend wired in `backend/core/.../ratelimit/` with per-tenant bucket key naming convention (`ratelimit:<surface>:<tenantId>`).
- Bucket policy as config (`application.yml` per-environment overrides), not hard-coded.
- Metrics: bucket consumed / available / refused via Micrometer → OpenTelemetry → Grafana Cloud.

## Safety Rules

- Bucket keys MUST include `tenantId` — no cross-tenant bucket sharing ever.
- Rate-limit decision logs: `event=ratelimit.refused tenantId={} surface={} reason={}` — no user identifier, no request body.
- Limiter failure (Redis down) → **fail closed** for cost-sensitive surfaces (LLM cost cap, send cap), **fail open** for non-critical (analytics).
- Bucket state in Redis is allowed to be evicted on cache eviction — must not be the system of record. Persistent policy lives in DB.

## Open Questions

- Is current Redis limiter Lua-based or naive? Answer determines whether this seed is worth promoting.
- Bucket4j 8.x compat with Spring Boot 4.0.6 + Lettuce shipped with Spring Data Redis 4.0 — unverified.
- Per-tenant LLM cost limiting: is "cost" measured in tokens, dollars, or model-calls? Affects bucket capacity semantics.
- Webhook delivery rate-limit needs back-pressure into outbox worker, not just rejection — Bucket4j supports `tryConsume + scheduleRefill` semantics; verify pattern.

## What This Seed Is NOT

- **NOT** a swap of `processing_job` queue for any job library (Quartz/JobRunr). That decision is closed: keep Postgres + `SKIP LOCKED`. See conversation context — privacy leak via job payload serialization + duplicate source of truth + Modulith boundary bypass are all blockers.
- **NOT** a swap of `@Scheduled` for a scheduler library. If multi-instance becomes a concern, add **ShedLock** (5-line config) instead.

## References

- `bucket4j/bucket4j` GitHub
- Project CLAUDE.md "Redis 7.2 self-hosted" stack lock — Bucket4j-Redis matches the locked transport
- Memory: `reference-ai-research-repos` (related libs), `feedback-spring-boot-4-breaking-changes` (compat-verify discipline)

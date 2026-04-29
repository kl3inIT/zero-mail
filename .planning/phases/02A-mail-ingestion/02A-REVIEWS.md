---
phase: 02A
review_cycle: 5
reviewers: [codex]
reviewed_at: 2026-04-29T11:17:29.3409733+07:00
follow_up_to_cycle: 4
fix_commit: 6a3e914
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 1
---

# Cross-AI Plan Review - Phase 02A (Cycle 5)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `6a3e914` fully resolved Cycle 4's two HIGH concerns at the plan-text level. This Cycle 5 review found one new HIGH reliability concern.

## Codex Review

**Summary**

I reviewed the current repo state and pasted Phase 02A plans without modifying files. The two Cycle 4 HIGHs are now addressed in the plan text. Overall the plan set is much stronger, but I found one remaining HIGH reliability gap in the worker queue claim semantics: a crash after claiming `pubsub_delivery` rows can strand rows in `PROCESSING` forever.

**Prior HIGH Resolution**

1. Missing Google/Gmail Gradle dependency plan changes: **FULLY RESOLVED.**
   Plan 01 now modifies `gradle/libs.versions.toml`, `backend/core/build.gradle.kts`, and `backend/api/build.gradle.kts`, adds aliases for `google-auth-library-oauth2-http` and `google-api-services-gmail`, wires core/API dependencies, and adds OpenAPI dummy Pub/Sub args. Evidence: `02A-01-PLAN.md:466`, `02A-01-PLAN.md:472`, `02A-01-PLAN.md:479`.

2. Settings-page `ReconnectPrompt` parent gate for `CONNECTED` plus unhealthy `ingestionHealth`: **FULLY RESOLVED.**
   Plan 04 now correctly targets the real parent mount site and requires `ReconnectPrompt` for `DISCONNECTED` or `CONNECTED && ingestionHealth !== 'HEALTHY'`, with tests enabled. Evidence: `02A-04-PLAN.md:257`, `02A-04-PLAN.md:357`, current parent gate at `apps/web/app/(protected)/settings/page.tsx:93`.

**Strengths**

- Dependency ordering is now correct: Gradle/library wiring lands in Plan 01 before API/worker code.
- OIDC filter scoping is well specified: no `@Component`, explicit `PubSubSecurityConfig @Order(1)`, disabled servlet registration, and active test-profile coverage.
- Controller persistence moved into `PubSubIngestionService`, preserving thin-controller boundaries.
- Reconnect prompt fix is now at the actual settings-page boundary, not just the presentational component.
- Worker transaction ownership improved by extracting public `GmailDeliveryProcessingService.processDelivery`.

**Concerns**

- **HIGH - Claimed `pubsub_delivery` rows can be stranded forever after a worker crash.** Plan 01 changes rows to `PROCESSING` during `claimPendingBatch`, but the claim query only selects `status = 'PENDING'`. If the worker process dies after claim and before `processDelivery`, the row remains `PROCESSING`; even after `locked_until` expires, future claims ignore it. This contradicts the plan's crash-recovery claim and threatens MAIL-01/MAIL-04 reliability. Evidence: claim sets `PROCESSING` at `02A-01-PLAN.md:538`, filters only `PENDING` at `02A-01-PLAN.md:546`, while the research explicitly calls for stale `PROCESSING` handling at `02A-RESEARCH.md:753`.

- **MEDIUM - Pub/Sub test properties are still underspecified.** Plan 03 makes `PubSubSecurityConfig` active under `test` and requires Pub/Sub properties, but the current `ApiPostgresTestBase` only supplies datasource/OAuth/crypto properties. Controller tests using `MockGoogleOidcServer` need a clear `DynamicPropertySource` seam for audience, SA email, and local cert URL. Evidence: `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java:26`, `02A-03-PLAN.md:415`.

- **MEDIUM - Cross-tenant worker scans use tenant-owned JPA repositories before `TenantContext` is bound.** The tenant resolver falls back to `BOOTSTRAP_TENANT`, while Plan 02 calls `findConnectionsNeedingWatchRenewal` and `claimPendingBatch` before per-row `ScopedValue.where(...)`. If this is intentional system-level access, the plan should make it explicit and prefer `JdbcTemplate` projections. Evidence: `backend/core/src/main/java/com/zeromail/core/tenant/ScopedValueTenantResolver.java:26`, `02A-02-PLAN.md:525`, `02A-02-PLAN.md:597`.

- **LOW - Missing `messageId` is still not explicitly dropped.** The controller validates decoded Gmail fields but passes `envelope.message().messageId()` through without a null/blank guard, risking DB constraint failure instead of ack-fast 200-drop.

**Suggestions**

- Change `claimPendingBatch` to reclaim stale `PROCESSING` rows whose `locked_until < NOW()`, and add a test for expired `PROCESSING` recovery.
- Add explicit test property wiring for Pub/Sub OIDC in `ApiPostgresTestBase` or per Pub/Sub integration test.
- Make cross-tenant worker claim scans raw `JdbcTemplate` system queries returning small row DTOs, then bind `TenantContext` before tenant-owned JPA work.
- Add a null/blank `messageId` controller branch returning 200 with an opaque warning event.

**Risk Assessment**

Overall risk: **HIGH**. The prior HIGHs are closed, but the `PROCESSING`-row recovery bug can silently stop processing a delivery after a crash, which cuts directly against Phase 02A's reliability goal.

CURRENT_HIGH_COUNT: 1

### Current HIGH Concerns

- Stale `pubsub_delivery` rows in `PROCESSING` are not reclaimable after `locked_until` expires, so a crash after claim can permanently strand Gmail deliveries.

---

## Consensus Summary

Only Codex was invoked in Cycle 5, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 4's Google/Gmail dependency planning gap is fully resolved in Plan 01.
- Cycle 4's `ReconnectPrompt` parent gate gap is fully resolved in Plan 04 and now targets the actual settings-page mount condition.
- Security filter scoping, controller/service boundaries, and worker transaction ownership are materially stronger than Cycle 4.

### Agreed Concerns

- HIGH: `claimPendingBatch` marks deliveries as `PROCESSING` but only reclaims `PENDING` rows, so expired `PROCESSING` rows can be stranded after a worker crash.
- MEDIUM: Pub/Sub OIDC test properties still need an explicit `DynamicPropertySource` or base-test wiring plan.
- MEDIUM: Cross-tenant worker scans should clarify system-level access and preferably use raw `JdbcTemplate` projections before binding `TenantContext`.
- LOW: Missing Pub/Sub `messageId` should be explicitly ack-dropped rather than allowed to fall into a database constraint failure.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 4 HIGH concerns: 2
- Fully resolved prior HIGH concerns: 2
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 5 HIGH concerns: 1
- Current unresolved HIGH concerns: 1

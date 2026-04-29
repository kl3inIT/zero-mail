---
phase: 02A
review_cycle: 6
reviewers: [codex]
reviewed_at: 2026-04-29T11:28:37.3505479+07:00
follow_up_to_cycle: 5
fix_commit: b2ae18b
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 1
---

# Cross-AI Plan Review - Phase 02A (Cycle 6)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `b2ae18b` fully resolved Cycle 5's HIGH concern at the plan-text level. This Cycle 6 review found one new HIGH reliability concern.

## Codex Review

**Summary**

The prior stale `PROCESSING` reclaim HIGH is fully resolved in the plan text. However, I found one new HIGH reliability issue: the watch-renewal path can advance `last_synced_history_id` to the fresh `users.watch` baseline, which can skip unprocessed Gmail history deliveries.

**Prior HIGH Resolution**

FULLY RESOLVED. `claimPendingBatch` now reclaims both due `PENDING` rows and expired `PROCESSING` rows atomically in the same `UPDATE ... RETURNING *` query: `02A-01-PLAN.md:538`, `02A-01-PLAN.md:546`. Wave 0 now explicitly tests expired `PROCESSING` reclaim: `02A-00-PLAN.md:262`. Plan 02 also documents the crash-recovery invariant: `02A-02-PLAN.md:660`.

**Strengths**

- Delivery claim semantics are now correct for crash recovery: claim changes row state before returning and reclaims expired `PROCESSING`.
- OIDC filter scoping is much stronger: no `@Component`, disabled servlet filter registration, dedicated `@Order(1)` chain, and `shouldNotFilter` guard: `02A-03-PLAN.md:402`, `02A-03-PLAN.md:422`.
- Controller persistence is correctly moved into `PubSubIngestionService` with unscoped `JdbcTemplate` lookup followed by tenant-bound insert: `02A-03-PLAN.md:250`.
- The frontend reconnect gate now targets the real settings-page parent boundary and covers `CONNECTED && ingestionHealth !== 'HEALTHY'`: `02A-04-PLAN.md:357`.

**Concerns**

- **HIGH - Watch renewal can skip unprocessed Gmail history.** Plan 02 requires `recordWatchSuccess` to "initializes/advances" `last_synced_history_id` from returned `watch_history_id`: `02A-02-PLAN.md:29`. The method does exactly that when the returned watch baseline is higher: `02A-02-PLAN.md:244`. But watch renewal runs through the same path after `gmail.users().watch(...)`: `02A-02-PLAN.md:545`. On renewal, advancing the sync pointer to "now" can jump past already queued but unprocessed `pubsub_delivery` rows. The context only says renewal should persist `watch_history_id`/expiry fields, while `last_synced_history_id` is advanced by history processing: `02A-CONTEXT.md:40`, `02A-CONTEXT.md:117`.
- **MEDIUM - Watch scheduler `FOR UPDATE SKIP LOCKED` is not a durable claim.** `findConnectionsNeedingWatchRenewal` selects with `FOR UPDATE SKIP LOCKED`, but the repository transaction ends before the Gmail API call: `02A-02-PLAN.md:293`, `02A-02-PLAN.md:525`. Multiple workers can renew the same row concurrently, causing duplicate `users.watch` calls and worsening the pointer-skip issue above.
- **MEDIUM - Test Pub/Sub properties are still not globally wired.** `PubSubSecurityConfig` is active under test and requires `pubsub.push-audience-url` / service-account email: `02A-03-PLAN.md:406`, `02A-03-PLAN.md:415`. The plan adds fail-fast application properties, but does not explicitly update the shared API test base; current `ApiPostgresTestBase` only supplies datasource/OAuth/crypto props: `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java:26`.
- **LOW - Missing or blank Pub/Sub `messageId` is still not explicitly ack-dropped.** The controller validates `data`, `emailAddress`, and `historyId`, but passes `envelope.message().messageId()` directly into ingestion: `02A-03-PLAN.md:540`, `02A-03-PLAN.md:570`.

**Suggestions**

- Split watch success handling into initial registration/reconnect vs renewal. Only initialize `last_synced_history_id` when it is `NULL`; regular renewals should update `watch_history_id`, expiry, renewal timestamp, failures, and health only.
- Add a Wave 0/worker test: existing `last_synced_history_id=100`, pending delivery `history_id=110`, watch renewal returns `watchHistoryId=200`; assert `last_synced_history_id` remains `100` and the pending delivery can still process.
- Consider a durable watch-renewal claim column such as `watch_locked_until`, or an atomic update-returning claim, to avoid duplicate renewals across worker instances.
- Add Pub/Sub default test properties to `ApiPostgresTestBase` or a test profile config, with per-test override for the mock JWKS/certs URL.
- Add a controller guard for null/blank `messageId` returning `200 OK` with an opaque warning event.

**Risk Assessment**

Overall risk: **HIGH**. The original crash-recovery gap is closed, but the new watch-renewal pointer advancement can silently drop real Gmail history during normal renewal, which cuts directly against MAIL-01 and MAIL-04 reliability.

CURRENT_HIGH_COUNT: 1

### Current HIGH Concerns

- Watch renewal can advance `last_synced_history_id` to the new `users.watch` baseline and skip queued or otherwise unprocessed Gmail history deliveries.

---

## Consensus Summary

Only Codex was invoked in Cycle 6, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 5's stale `PROCESSING` reclaim gap is fully resolved by atomically reclaiming due `PENDING` rows and expired `PROCESSING` rows.
- Wave 0 now has explicit coverage for expired `PROCESSING` reclaim.
- Pub/Sub OIDC scoping, controller/service boundaries, and settings-page reconnect gating remain materially strong.

### Agreed Concerns

- HIGH: Watch renewal can call the same `recordWatchSuccess` path used for initial watch registration, advancing `last_synced_history_id` to the fresh `users.watch` baseline and skipping queued or otherwise unprocessed Gmail history.
- MEDIUM: Watch renewal's `FOR UPDATE SKIP LOCKED` select is not a durable multi-worker claim once the repository transaction ends before the Gmail API call.
- MEDIUM: Pub/Sub test properties still need explicit shared API test-base or test-profile wiring.
- LOW: Missing Pub/Sub `messageId` should be explicitly ack-dropped rather than allowed to flow into ingestion.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 5 HIGH concerns: 1
- Fully resolved prior HIGH concerns: 1
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 6 HIGH concerns: 1
- Current unresolved HIGH concerns: 1

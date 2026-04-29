---
phase: 02A
review_cycle: 8
reviewers: [codex]
reviewed_at: 2026-04-29T11:51:07.9144901+07:00
follow_up_to_cycle: 7
fix_commit: f9ecae3
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 1
---

# Cross-AI Plan Review - Phase 02A (Cycle 8)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `f9ecae3` fully resolves both Cycle 7 HIGH concerns at the plan-text and test-contract level. Gmail history processing no longer trusts `history.list` message metadata, and Pub/Sub security chain ordering is now specified on `SecurityFilterChain` bean methods. The review found one new current HIGH concern around `HISTORY_LOST` being cleared by routine watch renewal.

## Codex Review

### Summary

The Cycle 8 plan materially fixes both Cycle 7 HIGHs at the plan and test-contract level. Gmail history processing no longer trusts `history.list` message metadata, and Pub/Sub security chain ordering is now specified on `SecurityFilterChain` bean methods. Overall the plans are strong, but one new HIGH remains: `HISTORY_LOST` can be accidentally cleared by routine watch renewal, undermining MAIL-05's visible reconnect-prompt guarantee.

### Prior HIGH Resolution

- **Cycle 7 HIGH: Gmail `history.list` message metadata assumptions** - **Resolved.** Plan 02 now uses `history.list(...).setLabelId("INBOX")`, then fetches each candidate via `messages.get(format=metadata, fields=id,threadId,labelIds,internalDate)` before checking `INBOX` or storing `internalDate`. Plan 00 also adds coverage for candidates containing only `id/threadId`.
- **Cycle 7 HIGH: `SecurityFilterChain` ordering on config classes** - **Resolved.** Plan 03 explicitly puts `@Order(1)` on `pubSubFilterChain(...)` and `@Order(2)` on the user-session `SecurityFilterChain` bean method. Pub/Sub security remains active under the test profile.
- **Cycle 6 HIGH: watch-renewal cursor preservation** - Still resolved. `recordWatchSuccess` preserves a non-null `last_synced_history_id` so regular renewal does not skip queued or unprocessed Gmail history.
- **Cycle 5 HIGH: stale `PROCESSING` delivery reclaim** - Still resolved. `claimPendingBatch` still atomically reclaims eligible `PENDING` and expired `PROCESSING` deliveries.

### Strengths

- History fan-out now follows the right Gmail API shape: label-filtered history candidates plus metadata fetch before label checks.
- Pub/Sub OIDC is well-covered: valid token, wrong audience, wrong email, wrong issuer, expired token, bad signature, and non-Pub/Sub path guard.
- `GmailHistoryProcessor` delegates to a public `@Transactional` service, avoiding the private transaction interception bug.
- Pub/Sub controller stays thin and ack-fast; persistence is moved into `PubSubIngestionService`.
- Test-profile security is much better specified for `/me` and `/tenant/triage-pause`.
- Idempotency remains DB-native with `ON CONFLICT DO NOTHING`, not exception-driven.
- Frontend reconnect gating is correctly placed at the settings page parent boundary.

### Concerns

- **HIGH - Routine watch renewal can clear `HISTORY_LOST`.** Plan 02's `recordWatchSuccess` always sets `ingestionHealth = HEALTHY`. That is correct for `WATCH_UNHEALTHY`, initial registration, and explicit reconnect, but not for `HISTORY_LOST`. After a history-404, MAIL-05 requires a visible reconnect prompt. If the watch scheduler renews while the row is still `HISTORY_LOST`, this plan can hide the reconnect prompt without user reconnect/re-consent.
- **MEDIUM - Pub/Sub fail-fast properties may break unrelated API tests.** `PubSubSecurityConfig` is active under the test profile and requires `PUBSUB_PUSH_AUDIENCE_URL` / `PUBSUB_SA_PRINCIPAL_EMAIL`. The plan covers targeted Pub/Sub tests and OpenAPI dummy args, but should also ensure test-profile defaults or per-test properties for existing unrelated `@SpringBootTest` contexts.
- **MEDIUM - Worker global claim still depends on unbound tenant-owned JPA/native entity hydration.** `claimPendingBatch` runs before per-row `TenantContext` binding and returns `PubSubDeliveryEntity`, which extends a tenant-owned base. This may be fine with native SQL in this codebase, but the plan should explicitly validate that the tenant resolver does not reject or filter the unscoped claim transaction.
- **LOW - `recordWatchSuccess` reconnect wording is slightly ambiguous.** The action says "existing reconnect path", which is good, but some must-have wording says reconnect/upsert. Keep `clearForReconnect` scoped only to explicit reconnect/re-consent, not ordinary login/upsert.

### Suggestions

- Change `recordWatchSuccess` so it only flips health to `HEALTHY` when current health is `WATCH_UNHEALTHY`, or when this is initial registration/reconnect where `lastSyncedHistoryId` was null because `clearForReconnect` intentionally reset it. Preserve `HISTORY_LOST` until `clearForReconnect` runs.
- Add `watchRenewal_historyLost_doesNotClearIngestionHealth()` to `GmailWatchSchedulerTest` or `GmailConnectionServiceTest`.
- Add test-profile Pub/Sub property defaults, or require `@TestPropertySource` on every API context that loads `PubSubSecurityConfig`.
- Add a worker claim integration test that runs `claimPendingBatch` with no `TenantContext` bound and proves it returns rows safely without tenant leakage.
- Tighten wording so `clearForReconnect` is only called after explicit reconnect/re-consent.

### Risk Assessment

Overall risk: **HIGH**. The two Cycle 7 blockers are resolved, but MAIL-05 can still be violated if a routine watch renewal hides the `HISTORY_LOST` reconnect state after a history-404.

CURRENT_HIGH_COUNT: 1

### Current HIGH Concerns

- Routine watch renewal can clear `HISTORY_LOST` by setting `ingestionHealth = HEALTHY` in `recordWatchSuccess`, hiding the reconnect prompt required after a history-404.

---

## Consensus Summary

Only Codex was invoked in Cycle 8, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 7's Gmail metadata HIGH is fully resolved by `history.list(...).setLabelId("INBOX")`, followed by a metadata-only `messages.get` before checking labels or storing `internalDate`.
- Cycle 7's security-chain ordering HIGH is fully resolved by putting `@Order` on the actual `SecurityFilterChain` bean methods.
- Earlier Cycle 5 and Cycle 6 HIGH fixes remain intact: stale `PROCESSING` reclaim is still present, and watch renewal still preserves a non-null sync cursor.

### Agreed Concerns

- HIGH: `recordWatchSuccess` unconditionally sets `ingestionHealth = HEALTHY`, so routine watch renewal can erase `HISTORY_LOST` and hide the reconnect prompt required by MAIL-05.
- MEDIUM: Active test-profile Pub/Sub security may need stable dummy properties for unrelated API test contexts.
- MEDIUM: The unscoped worker claim path should explicitly prove tenant-owned entity hydration is safe without `TenantContext`.
- LOW: Reconnect cleanup wording should make ordinary login/upsert distinct from explicit reconnect/re-consent.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 7 HIGH concerns: 2
- Fully resolved prior Cycle 7 HIGH concerns: 2
- Prior Cycle 6 HIGH concerns still resolved: 1
- Prior Cycle 5 HIGH concerns still resolved: 1
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 8 HIGH concerns: 1
- Current unresolved HIGH concerns: 1

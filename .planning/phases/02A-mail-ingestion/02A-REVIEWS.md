---
phase: 02A
review_cycle: 9
reviewers: [codex]
reviewed_at: 2026-04-29T12:00:03.6104285+07:00
follow_up_to_cycle: 8
fix_commit: ddc5e8c
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 0
---

# Cross-AI Plan Review - Phase 02A (Cycle 9)

Only the Codex reviewer was requested and invoked for this follow-up convergence cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

Manual fix commit `ddc5e8c` resolves the Cycle 8 HIGH concern at the plan-text and test-contract level. `recordWatchSuccess` now clears only `WATCH_UNHEALTHY` to `HEALTHY`, preserves `HISTORY_LOST` until explicit reconnect/re-consent, adds watch-renewal coverage for the `HISTORY_LOST` state, and tightens ordinary login/upsert wording.

The review found no current unresolved HIGH-severity concerns. Remaining feedback is MEDIUM/LOW implementation-risk hardening.

## Codex Review

### Summary

The current plans resolve the Cycle 8 HIGH. `recordWatchSuccess` now preserves `HISTORY_LOST`, only self-heals `WATCH_UNHEALTHY`, and `clearForReconnect` is scoped to explicit reconnect/re-consent. I do not see any current unresolved HIGH-severity concerns in the plan text.

### Prior HIGH Resolution

- `HISTORY_LOST` cleared by routine watch renewal: **Resolved.** Plan 02 now preserves `HISTORY_LOST` and adds `watchRenewal_historyLost_doesNotClearIngestionHealth()` coverage.
- Stale `PROCESSING` delivery reclaim: **Still resolved.** `claimPendingBatch` reclaims expired `PROCESSING` rows atomically.
- Watch-renewal cursor preservation: **Still resolved.** Non-null `last_synced_history_id` is not advanced on renewal.
- Gmail metadata fetch: **Still resolved.** Plan fetches `messages.get(format=metadata, fields=id,threadId,labelIds,internalDate)` before checking labels.
- `SecurityFilterChain` bean ordering: **Still resolved.** Pub/Sub chain has `@Order(1)`, user chain has `@Order(2)`.

### Strengths

- The `HISTORY_LOST` state model is now explicit and test-backed.
- Reconnect cleanup is no longer conflated with ordinary login/upsert.
- Pub/Sub OIDC remains active under test profile with good negative coverage.
- Controller persistence is moved into `PubSubIngestionService`, keeping the push controller thin.
- Worker idempotency uses native `ON CONFLICT DO NOTHING` and atomic `PROCESSING` claim.

### Concerns

- **MEDIUM:** Worker/watch global scans still depend on unbound tenant-owned entity hydration before `TenantContext` is bound. Add explicit no-tenant-context tests for both `claimPendingBatch` and `findConnectionsNeedingWatchRenewal`.
- **MEDIUM:** `findConnectionsNeedingWatchRenewal` uses `SELECT ... FOR UPDATE SKIP LOCKED`, but the repository transaction may end before Gmail watch renewal runs. On multiple worker instances, duplicate renewals are possible. Acceptable for single-VPS v1, but worth documenting or claiming rows atomically.
- **LOW:** The ordinary login/upsert distinction is now clear in text, but a focused regression test would make it harder to re-break.

### Suggestions

- Add a test proving ordinary OAuth login/upsert does not call `clearForReconnect` when the connection is `HISTORY_LOST`.
- Add a no-tenant-context integration test for global worker claim paths.
- Consider making watch renewal claim stateful, or document that v1 runs a single worker instance.

### Risk Assessment

Overall risk: **MEDIUM**. The current HIGH blocker is resolved, and core safety guarantees are well covered. Remaining risks are implementation/runtime edge cases around tenant-scoped global scans and multi-worker renewal behavior, not current HIGH plan failures.

### Current HIGH Concerns

None.

CURRENT_HIGH_COUNT: 0

---

## Consensus Summary

Only Codex was invoked in Cycle 9, so the consensus summary reflects a single external review.

### Agreed Strengths

- Cycle 8's `HISTORY_LOST` renewal HIGH is fully resolved: `recordWatchSuccess` preserves `HISTORY_LOST`, only clears `WATCH_UNHEALTHY`, and `clearForReconnect` is scoped to explicit reconnect/re-consent.
- Earlier HIGH fixes remain intact: stale `PROCESSING` reclaim, watch-renewal cursor preservation, Gmail metadata fetch, and `SecurityFilterChain` bean ordering are still resolved.
- The plans now carry a focused `GmailWatchSchedulerTest` contract for `watchRenewal_historyLost_doesNotClearIngestionHealth()`.

### Agreed Concerns

- MEDIUM: Add explicit no-tenant-context tests for global worker/watch scan paths that hydrate tenant-owned entities before per-row `TenantContext` binding.
- MEDIUM: Watch renewal may duplicate work under multiple worker instances because `SELECT ... FOR UPDATE SKIP LOCKED` does not claim renewal rows beyond the repository transaction. This is acceptable for the single-VPS v1 baseline but should be documented or made stateful if worker concurrency increases.
- LOW: Add a focused regression test proving ordinary OAuth login/upsert does not call `clearForReconnect` for `HISTORY_LOST` connections.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle 8 HIGH concerns: 1
- Fully resolved prior Cycle 8 HIGH concerns: 1
- Prior Cycle 7 HIGH concerns still resolved: 2
- Prior Cycle 6 HIGH concerns still resolved: 1
- Prior Cycle 5 HIGH concerns still resolved: 1
- Partially resolved prior HIGH concerns: 0
- Previously raised HIGH concerns still unresolved: 0
- New Cycle 9 HIGH concerns: 0
- Current unresolved HIGH concerns: 0

---
phase: 02B-billing-prepaid-credits
fixed_at: 2026-05-07T01:08:00+07:00
review_path: .planning/phases/02B-billing-prepaid-credits/02B-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 02B: Code Review Fix Report

**Fixed at:** 2026-05-07T01:08:00+07:00
**Source review:** .planning/phases/02B-billing-prepaid-credits/02B-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

## Fixed Issues

### IN-01: Several billing logs omit the project-standard tenant field

**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java`, `backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java`, `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java`
**Commit:** bf0e09b
**Applied fix:** Added `tenantId={}` (or `tenantId=unresolved` for pre-lookup events) to every billing/auth log line that was missing it: webhook receive, missing/invalid auth, non-inbound, unknown_code, intent_not_pending, intent_expired, amount_mismatch, intent_vanished_post_lookup, replay_ignored, rounding_loss, below_min_credits, and the inner duplicate-topup branch. Bank memo text and account numbers remain excluded — the `BillingPrivacyLogScrubTest` happy-path still passes.

### WR-01: SePay memo extraction stops at the first code-shaped token

**Files modified:** `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java`, `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayMemoExtractionTest.java`
**Commit:** 3887f41
**Applied fix:** Replaced single-candidate `extractIntentCode(code, content)` with `extractCandidateIntentCodes(referenceCode, code, content)` returning a `LinkedHashSet<String>` of every plausible 8-character Crockford token in priority order (referenceCode → code → all matches in content). `applyWebhook(...)` now walks the full candidate set, picks the first that resolves to a `PENDING`, unexpired intent with the exact transferred amount, and falls back to the most specific known-intent diagnostic (status / expiry / amount mismatch) only if no candidate is fully valid. Added integration test `SepayMemoExtractionTest` covering: (a) memo with leading 8-digit transaction-date token before the real code, (b) syntactically valid but unknown `code` field with the real code in content, and (c) referenceCode priority.

### WR-02: Stale-reservation row locks released before watchdog releases reservations

**Files modified:** `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java`, `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdogBatch.java`
**Commits:** ea3a9d6 (initial structural extraction), e94ce06 (atomic SQL claim correction)
**Applied fix:** Extracted the scan-and-release loop into a separate `CreditReserveWatchdogBatch` bean so Spring's `@Transactional` proxy fires (no self-invocation). The batch method opens one transaction, performs an atomic CTE-based `UPDATE credit_reservation ... FROM (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING id, tenant_id, amount_credits`, and inserts the corresponding RELEASE ledger entries via `INSERT ... ON CONFLICT ON CONSTRAINT uq_credit_ledger_entry_ref_kind DO NOTHING`. The CTE atomic claim sidesteps Hibernate entirely on the watchdog write path, which was required because `ScopedValueTenantResolver.validateExistingCurrentSessions=true` rejects mid-transaction tenant rebinds when one batch processes reservations across multiple tenants. Idempotency is preserved by the unique constraint; concurrent watchdog invocations or a re-run after a partial commit are both safe. All 3 existing `CreditReserveWatchdogTest` tests still pass.

### WR-03: Concurrent duplicate webhooks can escape the replay-as-200 contract

**Files modified:** `backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java`, `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java`, `backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayConcurrentDeliveryTest.java`
**Commits:** d21950d (atomic UPDATE + idempotency), 5284a32 (test fixture: valid Crockford code)
**Applied fix:** Added `BillingTopupIntentRepository.markPaidIfPending(intentId, sepayTransactionId)` — a native `@Modifying` query that atomically transitions `status='PENDING' → 'PAID'` only when the row is still pending, bumping `version` and stamping `paid_at`/`updated_at`/`sepay_transaction_id` in one statement. `applyTopupCreditTransactional` no longer mutates the loaded entity; instead it calls the conditional UPDATE and treats `rowsUpdated == 0` as the replay ack (logs `sepay_topup_replay_ignored`, returns 200). The `DataIntegrityViolationException` catch on the ledger insert is kept as defense-in-depth. Added integration test `SepayConcurrentDeliveryTest.two_concurrent_identical_webhooks_both_return_200_and_credit_once` that fires two identical webhook posts from virtual threads and asserts both responses are 200 and exactly one TOPUP ledger row exists.

## Verification

- Phase-scoped test command from REVIEW.md verification section ran clean:
  `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.*" :backend:api:test --tests "com.zeromail.api.controllers.billing.*" :backend:worker:test --tests "com.zeromail.worker.billing.*"` — BUILD SUCCESSFUL.
- New tests added: `SepayMemoExtractionTest` (3 cases for WR-01), `SepayConcurrentDeliveryTest` (1 dual-thread case for WR-03).
- All pre-existing billing tests still pass, including `BillingPrivacyLogScrubTest` (the IN-01 log additions never include payload bytes, so the scrub assertions hold).

## Follow-ups for Human Review

- **WR-02 design tradeoff (worth reviewing):** The fix bypasses Hibernate on the watchdog write path because Hibernate's `validateExistingCurrentSessions=true` setting forbids mid-transaction tenant rebinds, and the watchdog's batch crosses tenant boundaries. The CTE atomic-claim path duplicates a small piece of `CreditLedgerService.release()` logic (state transition + RELEASE ledger insert). If the team prefers a single source of truth, the alternative is a per-row separate-transaction model (each release in its own ScopedValue + tx) where `FOR UPDATE SKIP LOCKED` provides no real protection — that is the original design the review flagged. The current fix preserves both `SKIP LOCKED` semantics and ledger idempotency at the cost of one extra place where the kind-RELEASE constants live.
- **WR-03 logic-bug surface area (worth a manual logic check):** The conditional `UPDATE ... WHERE id=? AND status='PENDING'` is the central correctness lever; if any future code path inserts a TOPUP ledger entry without going through `applyTopupCreditTransactional`, the unique constraint on `(ref_type, ref_id, kind)` is the only remaining guard. Worth a quick second pair of eyes on whether any admin / replay tooling could bypass it.

---

_Fixed: 2026-05-07T01:08:00+07:00_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_

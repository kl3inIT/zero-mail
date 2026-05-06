# Phase 2B: Billing (Prepaid Credits) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 2B-billing-prepaid-credits
**Areas discussed:** Atomic-reserve locking, Reservation tracking shape, SePay referenceCode + webhook protocol, 2B↔2C lifecycle contract, Intent expiry TTL, Worker fail-fast todo fold, SePay webhook test strategy

---

## A. Atomic-reserve locking strategy

| Option | Description | Selected |
|--------|-------------|----------|
| `pg_advisory_xact_lock(hashtext(tenant_id))` | Acquire advisory lock per-tenant at start of reserve txn; auto-release at commit. Cheap, scoped to tenant, no cross-tenant contention. Postgres-native, no library. Wraps SUM-balance + INSERT atomically. | ✓ |
| `SELECT ... FOR UPDATE` on tenants row | Row-lock on tenants(id) before balance check. Visible in pg_locks. Trade-off: shares lock with any other op updating tenants row. | |
| CHECK constraint + optimistic retry | UPDATE balance row with WHERE balance>=N + check rowcount=1. Trade-off: dual-source-of-truth, reconcile job needed. | |
| SERIALIZABLE txn isolation | Set isolation = serializable for reserve. Trade-off: high abort rate, retry boilerplate, impacts unrelated queries. | |

**User's choice:** Advisory lock (Recommended).
**Notes:** Selection aligns with Phase 2A's `SKIP LOCKED` precedent — Postgres-native primitives over library bolt-ons. Trade-off acknowledged: advisory locks not visible in standard `pg_locks` views without `WHERE locktype='advisory'`.

---

## B. Reservation tracking shape

| Option | Description | Selected |
|--------|-------------|----------|
| Sidecar `credit_reservation` table with status enum | id PK, tenant_id, amount_credits, status {PENDING/SETTLED/RELEASED}, created_at. Journal entries reference reservation_id. Watchdog: index-only scan WHERE status='PENDING' AND created_at < now()-5m. | ✓ |
| Journal-only + NOT EXISTS subquery | Watchdog query scans `kind='RESERVE'` AND `NOT EXISTS (SETTLE/RELEASE)` — heavier than partial-indexed sidecar. | |
| Journal-only + materialized open-flag column | Add `is_open BOOLEAN` on RESERVE rows; UPDATE on settle/release. Violates append-only invariant. | |

**User's choice:** Sidecar table (Recommended).
**Notes:** Append-only journal preserved per SPEC; sidecar mutates within the same txn for clean watchdog scan. Optimistic-lock `version` column prevents double-finalize; partial index covers steady-state empty scan.

---

## C. SePay referenceCode + webhook protocol

| Option | Description | Selected |
|--------|-------------|----------|
| Top-up intent table + 8-char Crockford base32 code | POST /api/billing/topup/intent creates billing_topup_intent(id, tenant_id, amount_vnd, code, status, expires_at). User pastes code into bank-transfer memo; webhook resolves → intent → tenant + validates amount. | ✓ |
| Stateless: ZM-{base32(tenantId 8 bytes)} | Reference code derived from tenant_id. Stateless, no intent table. Trade-off: leaks tenant-id structure; can't validate amount. | |
| Per-tenant rolling sequence: ZM{tenant-short}-{nonce} | Tenant short-id + auto-increment nonce. Human-readable but adds sequence + collision logic. | |

**User's choice:** Top-up intent table (Recommended).
**Notes:** Intent table also enables amount validation (webhook compares actual transfer amount to expected); 24-hour expiry TTL chosen below; intent expiry sweeper runs hourly in `backend/worker`.

---

## D. 2B ↔ 2C lifecycle contract

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit `settle(rid)` / `release(rid)` from gateway | Phase 2C: try { rid=reserve(); chat(); settle(rid); } catch { release(rid); throw; }. Simple, traceable, test-friendly. | ✓ |
| Auto via `TransactionSynchronization` | afterCommit→settle, afterRollback→release. Hidden side-effect; LLM I/O doesn't fit DB txn boundary cleanly. | |
| Settle = no-op; release on fail only | No SETTLE entry; reserve directly debits balance. Loses in-flight state; SPEC requires SETTLE for audit. | |

**User's choice:** Explicit calls (Recommended).
**Notes:** Locks the contract Phase 2C plan-phase will consume. `IllegalLedgerStateException` on disallowed transitions (settle-after-release, etc.); maps to HTTP 500 since it's a programming-error class.

---

## E. Top-up intent expiry TTL

| Option | Description | Selected |
|--------|-------------|----------|
| 24 hours | User can leave tab open, transfer later, account for SePay bank-settlement delay. Hourly sweep marks expired intents. | ✓ |
| 1 hour | Tighter window; risk of expired intent during user lunch break. | |
| No expiry | Intent persists until paid. No cleanup; stale-intent table bloat over time. | |

**User's choice:** 24 hours (Recommended).
**Notes:** Locked at `now() + INTERVAL '24 hours'` in Liquibase changeset; `BillingIntentExpirySweeper` `@Scheduled(fixedRate = 3_600_000)` runs hourly.

---

## F. Fold pending todo `2026-04-28-worker-application-yml-fail-fast-parity`

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, fold | 2B already touches `backend/worker/application.yml` for SEPAY_WEBHOOK_SECRET; closes CR-04 parity gap atomically. | ✓ |
| No, keep separate | Defer worker `:?` fail-fast to dedicated cleanup phase. | |

**User's choice:** Fold (Recommended).
**Notes:** CR-04 parity for `REFRESH_TOKEN_KEY_BASE64` in worker module folded into 2B. Plan-phase task: same `:?` fail-fast pattern + `@DynamicPropertySource` test-profile supply for both env vars.

---

## G. Test strategy for SePay webhook

| Option | Description | Selected |
|--------|-------------|----------|
| WireMock + recorded fixture | Direct POST signed fixture payloads to `/api/billing/sepay/webhook`. WireMock unnecessary for inbound webhook (no outbound SePay calls in 2B). Two test layers: pure unit HMAC verifier + `@SpringBootTest` integration. | ✓ |
| Live SePay sandbox + Testcontainers proxy | Setup proxy to SePay sandbox. Trade-off: fragile, requires sandbox account. | |
| Pure unit signature verifier + integration test fixture | Equivalent to selected — separates HMAC algorithm test from full flow test. | |

**User's choice:** WireMock + recorded fixture (Recommended).
**Notes:** Selected option encompasses two test layers (pure-unit HMAC + `@SpringBootTest` integration). Synthetic JSON fixture verified by `gsd-research-phase` against `https://docs.sepay.vn/` before plan-phase. Plan-phase pins exact field names; if SePay docs sparse, plan documents synthetic schema explicitly.

---

## Claude's Discretion

User delegated to Claude on:
- Out-of-scope items in spec phase (refunds, PDF receipts, credit bundles, per-action UI, multi-currency) — all deferred per Claude's recommendation.
- `vnd-per-credit` default value (recommend 1000 VND/credit ≈ $0.04 USD).
- Crockford base32 alphabet implementation source (commons-codec vs hand-rolled — pick at plan-phase).
- Watchdog batch size cap (proposed 100).
- `BillingController` sub-folder grouping under `backend/api/controllers/billing/` (parity with Phase 1.2.1 DTO group-by-domain).
- i18n key spelling for `error.billing.insufficient` (vi/en draft strings provided in CONTEXT.md).
- Whether to split `SepayWebhookController` from `BillingController` based on `@Order(1)` security chain isolation needs.

## Deferred Ideas

- Refunds / chargeback automation
- PDF receipts / invoice email
- Credit bundles / package pricing UI
- Per-action cost preview UI
- Multi-currency support
- `/settings/billing` page (Phase 5 user-surface)
- Soft-warn at low-balance threshold
- Rate limiting on SePay webhook beyond signature
- `SEPAY_WEBHOOK_SECRET` rotation drill
- Admin-facing billing dashboard
- LLM USD spend tracking + per-tenant daily spend cap (Phase 2C territory)
- `tenant_byok_credentials` table (Phase 2C SPEC owns)
- Reviewed-not-folded todo: `2026-04-28-wr-06-test-profile-securityconfig-slice.md` (different security concern; defer to test-infrastructure phase)

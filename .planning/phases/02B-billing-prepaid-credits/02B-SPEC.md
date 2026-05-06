# Phase 2B: Billing (Prepaid Credits) — Specification

**Created:** 2026-05-05
**Ambiguity score:** 0.12
**Requirements:** 9 locked

## Goal

Stand up a `core.billing` package with a single journal-table credit ledger that exposes a `CreditLedger` interface for Phase 2C to consume (`reserve` / `settle` / `release` / `balance`), drives top-ups via a SePay API-key-authenticated webhook with idempotent crediting, runs a 60-second watchdog that releases reserves older than 5 minutes, and rejects insufficient-balance traffic with HTTP 402 — backend-only, no UI pages.

## Background

The codebase has zero billing code today. `backend/core/src/main/java/com/zeromail/core/` contains only `account/`, `gmail/`, `onboarding/`, `tenant/`, `shared/{lang, persistence, privacy}`, and `config/`. No `core.billing` package, no `CreditLedger`, no payment-provider integration, and no SePay/VietQR webhook or top-up intent code. Liquibase changeset numbering reaches `013-tenants-triage-paused.yaml`.

Phase 2C SPEC has already locked the consumer-side contract: `gateway pre-call: if no BYOK for tenant → call Phase2B.CreditLedger.reserve(tenant, callSite.cost())` and reserves changeset `014-tenant-byok-credentials.yaml` — therefore Phase 2B claims `014-credit-ledger-entry.yaml` + `015-billing-sepay-payment.yaml` and Phase 2C must re-number to 016+ in its plan-phase. The user opted to ship 2B before 2C in Phase 2C's interview log, so 2C consumes 2B's interface directly without a stub.

`backend/worker` already runs `@Scheduled` jobs (Phase 2A's `GmailWatchScheduler` + `GmailHistoryProcessor`) — the credit watchdog joins this module. Spring Modulith is enforced (`ApplicationModulesTest` + `DomainBoundaryArchTests`); `core.billing` must declare `allowedDependencies` cleanly.

Constraints carried in: shared base entity hierarchy from Phase 1.2.1 (`AbstractTenantOwnedEntity`); `IdentifiedEnum` / `OrderedEnum` standard from `core.shared.lang`; Logback scrub filter from Phase 1; `:?` fail-fast pattern for deployment secrets from Phase 1.5.

## Requirements

1. **CreditLedger interface in `core.billing` (BILL-02, cross-phase contract)**: Exposes the surface Phase 2C imports.
   - Current: No `core.billing` package; no `CreditLedger` symbol anywhere in repo.
   - Target: `com.zeromail.core.billing.CreditLedger` interface with methods `ReservationId reserve(UUID tenantId, CallSite callSite)`, `void settle(ReservationId reservationId)`, `void release(ReservationId reservationId)`, `CreditBalance balance(UUID tenantId)`. Implementation `CreditLedgerService` in `core.billing.service`. `CallSite` enum implements `IdentifiedEnum` (`TRIAGE=1`, `DRAFT=2`, `PREVIEW=1`); `ReservationId` is a UUID-wrapping record; `CreditBalance` is a record `(int availableCredits, int heldCredits)`. Javadoc explicitly documents the BYOK exemption: "Phase 2C MUST NOT invoke `reserve` when a BYOK credential row exists for the tenant — BYOK traffic bypasses the ledger entirely."
   - Acceptance: Java interface compiles in `backend/core`; `core.billing` Modulith package-info declares `allowedDependencies = {tenant, shared.persistence, shared.lang}`; `ApplicationModulesTest` passes; an ArchUnit test in `backend/core` denies any package outside `core.billing` from instantiating `CreditLedgerService` directly (callers depend on the interface).

2. **Single journal-table ledger schema (BILL-02)**: Append-only Postgres table; balance derived by `SUM`.
   - Current: No billing tables exist.
   - Target: Liquibase changeset `014-credit-ledger-entry.yaml` creates `credit_ledger_entry`:
     - `id UUID PRIMARY KEY`
     - `tenant_id UUID NOT NULL` (FK → `tenants(id)` ON DELETE CASCADE)
     - `kind VARCHAR(16) NOT NULL` — one of `TOPUP`, `RESERVE`, `SETTLE`, `RELEASE`
     - `amount_credits INTEGER NOT NULL` — signed; `TOPUP` and `RELEASE` are positive, `RESERVE` is negative, `SETTLE` is `0`
     - `ref_type VARCHAR(32) NOT NULL` — `PAYMENT_SEPAY`, `RESERVATION`
     - `ref_id VARCHAR(128) NOT NULL`
     - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` (audit columns from `AbstractAuditableEntity` per Phase 1.2.1)
     - `version BIGINT NOT NULL DEFAULT 0`
     - UNIQUE constraint `(ref_type, ref_id, kind)` so `TOPUP` for the same SePay transaction id is idempotent and `SETTLE`/`RELEASE` for the same reservation is idempotent.
     - BRIN index on `created_at`; B-tree on `(tenant_id, created_at)`; B-tree on `(tenant_id, ref_type, ref_id)`.
   - Acceptance: `./gradlew :backend:core:liquibaseUpdate` (or equivalent) applies changeset cleanly on a Testcontainers Postgres; a round-trip Hibernate persist + repository-find on a `CreditLedgerEntryEntity` extending `AbstractTenantOwnedEntity` succeeds; the UNIQUE constraint trips a `DataIntegrityViolationException` on duplicate `(ref_type, ref_id, kind)` insert.

3. **Reserve / settle / release semantics, concurrency-safe (BILL-03)**: Reservations are atomic against balance; concurrent contention never double-charges and never loses credits.
   - Current: No ledger logic.
   - Target: `CreditLedgerService.reserve(tenantId, callSite)` runs in `Propagation.REQUIRES_NEW` against a tenant-scoped balance check. Implementation pattern locked at the SPEC level only as: "balance check + insert RESERVE entry must be atomic per tenant such that two concurrent `reserve(tenantId, cost=1)` calls on `available=1` cannot both succeed." Specific locking strategy (advisory lock vs row lock vs constraint-based) is owned by `discuss-phase`. `settle(reservationId)` inserts a `SETTLE` entry (`amount_credits=0`) referencing the reservation id; idempotent via the UNIQUE constraint. `release(reservationId)` inserts a `RELEASE` entry with `amount_credits = +N` (mirror of the original `RESERVE`); idempotent via the UNIQUE constraint. Calling `settle` after `release` (or vice versa) on the same reservation throws `IllegalLedgerStateException` (no double-finalize). Available balance = `SUM(amount_credits) WHERE tenant_id = ?`. Held balance = `SUM(-amount_credits) WHERE tenant_id = ? AND kind = 'RESERVE' AND ref_id NOT IN (SELECT ref_id FROM credit_ledger_entry WHERE kind IN ('SETTLE','RELEASE'))`.
   - Acceptance: A concurrent integration test starts 10 virtual threads each calling `reserve(tenantId, CallSite.TRIAGE)` (cost=1) on a tenant with `available=5`; after the test, exactly 5 reservations exist, exactly 5 calls threw `InsufficientCreditsException`, `availableCredits == 0`, no entry has `amount_credits > 0` except the original `TOPUP`, and `SUM(amount_credits)` matches `availableCredits + heldCredits`. A second test calls `settle` on the same reservation twice → second call is a no-op (UNIQUE wins, no exception thrown to caller). A third test calls `release` after `settle` on the same reservation → `IllegalLedgerStateException`.

4. **Watchdog sweep for stale reserves (BILL-04)**: Scheduled `@Scheduled` job in `backend/worker` releases reservations older than 5 minutes that have no `SETTLE` or `RELEASE` entry.
   - Current: No watchdog.
   - Target: `CreditReserveWatchdog` class in `backend/worker`, package `com.zeromail.worker.billing`, runs at `fixedRate = 60_000` ms (1 minute). Each tick: select up to N stale reservations (`RESERVE` entries where `created_at < now() - INTERVAL '5 minutes'` AND `ref_id` NOT IN open `SETTLE`/`RELEASE` rows), then for each: invoke `CreditLedger.release(reservationId)`. Logging: `event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}` — no amount, no PII. Metric: counter `zero_mail.billing.watchdog.released_total` (Micrometer).
   - Acceptance: Integration test inserts a `RESERVE` entry with `created_at = now() - 6 minutes`, runs the watchdog tick once, asserts a corresponding `RELEASE` entry exists with matching `ref_id`, and the tenant's `availableCredits` is restored. A second tick on the same already-released reservation is a no-op (UNIQUE prevents duplicate `RELEASE`). The watchdog runs under `@SchedulerLock` or equivalent so two worker pods cannot double-release.

5. **SePay/VietQR top-up intent + API-key-authenticated webhook with idempotent crediting (BILL-01)**: Payment-notification → atomic ledger TOPUP for the Vietnam beta.
   - Current: No payment integration.
   - Target: `POST /api/billing/topup/intent` creates a `billing_topup_intent` row for the current tenant with a short unique bank-memo code, expected VND amount, `PENDING` status, and 24-hour expiry. `POST /api/billing/sepay/webhook` has no Spring Security session auth — it is protected by SePay's API Key webhook auth. The webhook endpoint:
     - Reads `Authorization: Apikey ...` from the SePay-documented header and compares the API key in constant time against `SEPAY_WEBHOOK_API_KEY` (resolved from VPS deployment secret with `:?` fail-fast — boot fails if env missing, parity with `REFRESH_TOKEN_KEY_BASE64`). Wrong/missing auth → HTTP 401 + `event=sepay_webhook_auth_invalid` (no header bytes logged).
     - Parses the SePay JSON payload: extracts `transactionId`, `amountVnd` (integer), and `referenceCode` (the top-up intent code the user pasted into the bank-transfer memo).
     - Normalizes `referenceCode`, resolves the `billing_topup_intent` row by code, and validates that it is `PENDING`, unexpired, and the amount exactly matches the intent. Unknown, expired, or amount-mismatched codes are acknowledged with HTTP 200 but do not credit the ledger; they emit privacy-safe operational events for manual reconciliation.
     - Converts VND to credits: `credits = floor(amountVnd / zero-mail.billing.vnd-per-credit)` where the rate is a `@ConfigurationProperties` value (default `1000` VND per credit, configurable per environment). Rounding-down loss is logged at `event=sepay_topup_rounding_loss` with `vndLost` only.
     - In the same transaction, marks the intent `PAID` and inserts a `TOPUP` ledger entry with `ref_type='PAYMENT_SEPAY'`, `ref_id=transactionId`, `amount_credits=credits`. Replay protection: the UNIQUE `(ref_type, ref_id, kind)` constraint and unique `sepay_transaction_id` on the intent make the second delivery a no-op (caught at repository layer, returned as HTTP 200 to ack so SePay stops retrying).
     - Returns HTTP 200 on success and on duplicate (replay-safe ack).
   - Acceptance: WireMock-style test posts a valid API-key-authenticated payload → ledger has one `TOPUP` entry, balance increments. Same payload re-posted → second call returns HTTP 200, ledger still has exactly one `TOPUP` entry. Bad/missing API key payload → HTTP 401, no ledger entry. Test app boot fails fast when `SEPAY_WEBHOOK_API_KEY` is unset (matches Phase 1.5 `REFRESH_TOKEN_KEY_BASE64` fail-fast pattern). No log line during any of these flows contains the raw payload bytes, the Authorization header value, or the user's bank-account info.

6. **Insufficient-balance reject with HTTP 402 (BILL-06)**: `reserve` failure surfaces as a typed exception → HTTP 402 + `ApiError`.
   - Current: No reject logic.
   - Target: `CreditLedgerService.reserve` throws `InsufficientCreditsException` (extends `RuntimeException`, in `core.billing`) when `availableCredits < callSite.cost()`. `GlobalExceptionHandler` in `backend/api` maps it to HTTP 402 with body `ApiError` carrying `code = "BILLING_INSUFFICIENT_CREDITS"`, no `params` containing balance numbers (avoid leaking exact balance via error). Frontend localizes the code (`vi/en` keys `error.billing.insufficient`) without the backend constructing localized strings (Phase 1.1 contract).
   - Acceptance: Integration test with `availableCredits = 0` calls `reserve` directly → throws `InsufficientCreditsException`. A controller endpoint that wraps `reserve` (added as a thin test-only `@RestController` in Phase 2B, OR Phase 2C's first call site) returns HTTP 402 with `ApiError` shape; response body has `code = "BILLING_INSUFFICIENT_CREDITS"` and no balance number. `vi.json` and `en.json` both have `error.billing.insufficient` keys; `pnpm i18n:check` STRICT passes.

7. **Real-time balance API endpoint (BILL-05)**: `GET /api/billing/balance` for the current tenant.
   - Current: No endpoint.
   - Target: `BillingController.balance()` in `backend/api`, mapped to `GET /api/billing/balance`, returns `BillingBalanceResponse` record `(int availableCredits, int heldCredits, String currency)` where `currency = "credits"`. Uses `TenantContext.currentOrThrow()` to resolve tenant; service call is `CreditLedger.balance(tenantId)`. Endpoint is part of the `springdoc-openapi` contract; frontend regenerates `apps/web/lib/api/schema.d.ts` in the same plan that lands the controller.
   - Acceptance: Integration test authenticated as tenant A calls `GET /api/billing/balance` → 200 with shape `{availableCredits, heldCredits, currency: "credits"}`. Tenant-isolation test (mirror of `MultiTenantLeakIntegrationTest` from Phase 1) asserts tenant A's balance never appears in tenant B's response. `apps/web/lib/api/schema.d.ts` has `/api/billing/balance` types after `pnpm generate:api`.

8. **BYOK exemption documented at the contract boundary (BILL-07)**: Phase 2B does not detect BYOK; Phase 2C is responsible for skipping `reserve` when a BYOK row exists.
   - Current: No BYOK awareness in 2B (Phase 2C owns the BYOK table per its SPEC).
   - Target: `CreditLedger` interface Javadoc explicitly states: "BYOK traffic bypasses the ledger entirely. Phase 2C's `LlmGateway` MUST check the `tenant_byok_credentials` table before calling `reserve` and skip this method when a BYOK row exists for the tenant. The `CallSite` enum has no BYOK member because BYOK traffic does not enter this interface." `CallSite` enum stays at three members `{TRIAGE, DRAFT, PREVIEW}`; no `BYOK=0` member.
   - Acceptance: Javadoc on `CreditLedger` interface includes the BYOK boundary clause verbatim. ArchUnit test in `backend/core` asserts the `CallSite` enum has exactly `{TRIAGE, DRAFT, PREVIEW}` members (locked enum membership prevents accidental BYOK addition by Phase 2C). Phase 2C's plan-phase will reference this clause when wiring the gateway.

9. **`REQUIREMENTS.md` status flip**: `BILL-01` … `BILL-07` rows updated.
   - Current: All seven rows are `Pending`.
   - Target: Tracking-table rows for `BILL-01..BILL-07` flipped from `Pending` to `Phase 2B` once the closing plan ships and verification passes.
   - Acceptance: After Phase 2B closure plan, `grep -E 'BILL-0[1-7]' .planning/REQUIREMENTS.md` shows all seven rows with the completion marker; commit ties to the closing plan.

## Boundaries

**In scope:**
- `core.billing` package with `CreditLedger` interface + `CreditLedgerService` impl
- `CallSite` enum (`TRIAGE=1`, `DRAFT=2`, `PREVIEW=1`) implementing `IdentifiedEnum`
- `ReservationId`, `CreditBalance`, `BillingBalanceResponse` records
- Liquibase changesets `014-credit-ledger-entry.yaml` (and `015-billing-sepay-payment.yaml` if needed for SePay payload audit, owned by `discuss-phase`)
- `BillingController` with `GET /api/billing/balance`, `POST /api/billing/topup/intent`, and `POST /api/billing/sepay/webhook`
- SePay API-key webhook verification via `Authorization: Apikey ...` with `SEPAY_WEBHOOK_API_KEY` `:?` fail-fast env wiring
- VND → credits conversion via `@ConfigurationProperties("zero-mail.billing")` with `vnd-per-credit` rate
- `CreditReserveWatchdog` `@Scheduled` job in `backend/worker` (60s fixedRate, 5 min TTL)
- `InsufficientCreditsException` + global `ApiError` mapping to HTTP 402 + `error.billing.insufficient` i18n keys (vi/en)
- `IllegalLedgerStateException` for double-finalize
- BYOK exemption documented in `CreditLedger` Javadoc (no code in 2B; 2C enforces)
- `springdoc-openapi` contract update; `apps/web/lib/api/schema.d.ts` regenerated
- Spring Modulith `allowedDependencies` declaration for `core.billing`
- Concurrency invariant: 10-thread reserve test + watchdog idempotency test + SePay replay test
- `REQUIREMENTS.md` status flip for `BILL-01..BILL-07`

**Out of scope:**
- **Refunds / chargeback automation** — admin SQL/manual reconciliation or dedicated phase later. SePay is a payment gateway/bank-transfer aggregator, not Merchant of Record; Zero Mail remains responsible for beta refund/dispute policy.
- **Receipt / invoice PDF generation + email** — bank/SePay confirmations may exist, but Zero Mail receipt/invoice generation is deferred until Phase 5+ launch hardening.
- **Credit bundles / package pricing UI** — `vnd-per-credit` is a single fixed rate v1; "buy 100 credits for 50k VND" packaging is Phase 5 marketing surface.
- **Per-action cost preview UI / multi-currency** — VND-only; cost constants exposed only via `CallSite.id()` + future `/api/billing/costs` constants endpoint (not in 2B scope). Rendering belongs to Phase 5.
- **Full billing UI page (`/settings/billing`)** — backend-only this phase; Phase 5 builds the page. `WEB-04` persistent-credit-balance UI region is also Phase 5.
- **LLM USD spend tracking + per-tenant daily spend cap** — orthogonal concern owned by Phase 2C (it's the platform-side cost guard, not user-facing billing). 2B's `CreditLedger` is integer-credits-only.
- **Anything related to `tenant_byok_credentials` table** — Phase 2C SPEC owns it. Phase 2B only references the table name in BYOK exemption Javadoc.
- **Refresh-token-style key rotation drill for `SEPAY_WEBHOOK_API_KEY`** — captured in STATE.md Blockers, follows the same deferral as `REFRESH_TOKEN_KEY_BASE64`.
- **Multi-tenant team / shared wallet** — single-tenant individual prosumer model only (PROJECT.md Out-of-Scope).
- **Soft-warn at low balance threshold / configurable warnings** — hard reject only in v1.
- **Rate limiting on the SePay webhook beyond API-key auth** — SePay-side retry policy + UNIQUE constraint suffice; abuse-rate limit is a future concern.

## Constraints

- **Spring Modulith boundary**: `core.billing` package-info declares `@ApplicationModule(displayName="Billing", allowedDependencies={"tenant", "shared.persistence", "shared.lang"})`. No edges to `account`, `gmail`, `onboarding`, or `shared.privacy`. `ApplicationModulesTest` enforces.
- **Tenant isolation**: All ledger operations bind via `TenantContext` (Scoped Value, Phase 1 FND-02). Hibernate `@TenantId` filter applies to `CreditLedgerEntryEntity`. No native SQL bypasses this. ArchUnit ban on raw `JdbcTemplate` usage in `core.billing` (parity with Phase 1.2 ban).
- **Logback scrub coverage**: Webhook handler MUST NOT log the raw SePay payload, Authorization header value, or any bank-account / phone-number bytes. The Phase 1 `LogScrubFilter` should already cover `prompt=`, `body=`, etc.; extend coverage in this phase only if a new pattern is needed (`authorization=`, `apikey=`, `payload=`).
- **Deployment secret**: `SEPAY_WEBHOOK_API_KEY` resolved from VPS deployment secret only (Docker secret / systemd credential / locked-down env file) with `:?` fail-fast — no plain-env fallback in prod profile, parity with `REFRESH_TOKEN_KEY_BASE64`.
- **Watchdog runs in `backend/worker`, not `backend/api`** — same module split as Phase 2A's Gmail watch/history processors; `backend/worker` already has scheduling enabled and Modulith-aware entity scanning.
- **Integer credits only** — no fractional credits. Rounding loss on TOPUP (e.g., 999 VND with 1000 VND/credit = 0 credits) is logged with `vndLost` only and not auto-refunded in v1; manual/admin reconciliation handles edge cases during beta.
- **`CreditLedger.reserve` is `Propagation.REQUIRES_NEW`** to avoid being rolled back by an outer transaction that fails after the reserve. Settle/release run in the caller's transaction.
- **Spring AI 2.0.0-M4 milestone caveat does not apply here** — Phase 2B has no Spring AI dependency.
- **`@SchedulerLock` (or equivalent) on the watchdog** to prevent two worker pods double-releasing during horizontal scale (single-VPS today, but the lock is cheap and forward-compatible).
- **No content / PII / payment payload in logs** — only metadata (`tenantId`, `reservationId`, `transactionId`, `kind`, `amount_credits`).

## Acceptance Criteria

- [ ] `core.billing.CreditLedger` interface exists with `reserve`, `settle`, `release`, `balance` methods and BYOK-exemption Javadoc clause.
- [ ] `core.billing.CallSite` enum implements `IdentifiedEnum` with members `{TRIAGE, DRAFT, PREVIEW}` (no BYOK member); ArchUnit test enforces enum membership.
- [ ] `core.billing` package-info declares Spring Modulith `allowedDependencies = {"tenant", "shared.persistence", "shared.lang"}`; `ApplicationModulesTest` passes.
- [ ] Liquibase changeset `014-credit-ledger-entry.yaml` creates `credit_ledger_entry` with the locked column set + UNIQUE `(ref_type, ref_id, kind)` + BRIN(created_at) + B-tree(tenant_id, created_at).
- [ ] Concurrent reserve test: 10 virtual threads × `reserve(tenant, TRIAGE)` on `available=5` → exactly 5 succeed, 5 throw `InsufficientCreditsException`; `availableCredits == 0`; no negative-balance row.
- [ ] `settle` on the same reservation twice is a no-op (UNIQUE wins); `release` after `settle` (or vice versa) throws `IllegalLedgerStateException`.
- [ ] `CreditReserveWatchdog` runs in `backend/worker` at 60s fixedRate, releases reserves older than 5 minutes, emits `RELEASE` entries idempotently under `@SchedulerLock`.
- [ ] `POST /api/billing/sepay/webhook` verifies `Authorization: Apikey ...` with `SEPAY_WEBHOOK_API_KEY`; bad or missing API key → HTTP 401; missing env at boot → app fails fast.
- [ ] Replaying the same SePay `transactionId` returns HTTP 200 both times; ledger has exactly one `TOPUP` entry.
- [ ] `GET /api/billing/balance` returns `{availableCredits, heldCredits, currency: "credits"}`; tenant-isolation integration test asserts tenant A cannot read tenant B's balance.
- [ ] Insufficient balance returns HTTP 402 with `ApiError` `code = "BILLING_INSUFFICIENT_CREDITS"`; `params` does NOT leak the actual balance value.
- [ ] `vi.json` and `en.json` both have `error.billing.insufficient` keys; `pnpm i18n:check` STRICT passes.
- [ ] No log line in any flow contains the raw SePay payload, Authorization header value, or bank-account bytes.
- [ ] `apps/web/lib/api/schema.d.ts` includes `/api/billing/balance` and `/api/billing/sepay/webhook` types after `pnpm generate:api`.
- [ ] `REQUIREMENTS.md` `BILL-01..BILL-07` rows flipped from `Pending` to `Phase 2B` in the closing plan.
- [ ] `./gradlew clean check` BUILD SUCCESSFUL across `backend/core`, `backend/api`, `backend/worker`.

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                                                  |
|--------------------|-------|------|--------|--------------------------------------------------------------------------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | ✓      | Provider (SePay), unit (flat int), UI cut (backend-only), interface owner (2B), TTL (5/1 min) all locked |
| Boundary Clarity   | 0.92  | 0.70 | ✓      | Explicit out-of-scope list; refunds/PDF/bundles/per-action UI/multi-currency/LLM USD all deferred       |
| Constraint Clarity | 0.85  | 0.65 | ✓      | Schema columns, UNIQUE constraint, REQUIRES_NEW, SchedulerLock, fail-fast env all pinned                |
| Acceptance Criteria| 0.80  | 0.70 | ✓      | 16 pass/fail checkboxes; concurrent test + replay test + tenant-isolation test specified                |
| **Ambiguity**      | 0.12  | ≤0.20| ✓      |                                                                                                        |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

**No dimensions below minimum.** All requirements have current state, target state, and acceptance criteria.

**Cross-phase coupling:** Phase 2C SPEC reserves changeset `014-tenant-byok-credentials.yaml`. Phase 2B claims `014-credit-ledger-entry.yaml` first (this phase ships before 2C per the user's order). Phase 2C plan-phase MUST renumber its BYOK changeset to `015-tenant-byok-credentials.yaml` (or 016 if 2B uses 015 for SePay payload audit). The Phase 2C SPEC quote that locks our contract — `gateway pre-call: if no BYOK for tenant → call Phase2B.CreditLedger.reserve(tenant, callSite.cost())` — is satisfied by Requirement 1 verbatim.

**SePay-specific risk note:** SePay is a Vietnam-domestic bank-transfer aggregator (QR / virtual-account model) and does not publish a stable global SDK on Maven Central. The webhook integration is HTTP-only (no SDK), which is fine for v1. Current SePay docs describe API Key webhook authentication (`Authorization: Apikey ...`) plus JSON/form payload options; `gsd-research-phase` is still recommended before planning to re-check the live SePay docs. The SPEC locks the *shape* of the integration (API-key-authenticated webhook + idempotency on `transactionId` + VND→credits conversion) while letting plan-phase pin exact payload-field handling.

## Interview Log

| Round | Perspective       | Question summary                                              | Decision locked                                                                                                              |
|-------|-------------------|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| 1     | Researcher        | Payment provider for credit top-up?                          | **SePay** (Vietnam-domestic payment gateway / bank-transfer aggregator; not Stripe / not LemonSqueezy / not deferred)        |
| 1     | Researcher        | Credit unit semantics — 1 credit = ?                          | Flat integer per call site: TRIAGE=1, DRAFT=2, PREVIEW=1, BYOK=0 (BYOK exempt at gateway, not via cost). LLM USD spend tracked separately under Phase 2C |
| 1     | Researcher        | UI scope inside Phase 2B?                                     | Backend-only — `GET /api/billing/balance` + `POST /api/billing/sepay/webhook`. No `/settings/billing` page; Phase 5 owns UI. |
| 2     | Researcher (cont) | SePay integration depth?                                      | Full API-key-authenticated webhook + idempotent credit (not scaffold-only, not admin-grant)                                  |
| 2     | Researcher (cont) | Reserve TTL + watchdog sweep interval?                        | Reserve TTL = 5 minutes; watchdog sweep `fixedRate = 60_000` ms (1 minute)                                                   |
| 2     | Researcher (cont) | `CreditLedger` interface ownership?                           | Phase 2B ships interface + impl in `core.billing`; Phase 2C imports directly (cleanest, matches roadmap order)               |
| 3     | Boundary Keeper   | What's explicitly out of scope?                               | Refund automation, PDF receipts, credit bundles, per-action cost UI, multi-currency — all deferred to Phase 5+               |
| 3     | Boundary Keeper   | Double-entry ledger schema shape?                             | Single journal table `credit_ledger_entry` with signed `amount_credits`; balance = `SUM(amount_credits)` (append-only, audit-friendly) |

---

*Phase: 02B-billing-prepaid-credits*
*Spec created: 2026-05-05*
*Next step: `/gsd-discuss-phase 2B` — implementation decisions (concrete locking strategy for atomic reserve, SePay wire protocol details from `gsd-research-phase`, SchedulerLock library choice, exact top-up intent `referenceCode` parsing, ApiError code-name registration, ledger state machine for `IllegalLedgerStateException` paths)*

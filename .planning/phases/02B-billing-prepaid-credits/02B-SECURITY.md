---
phase: 02B
slug: billing-prepaid-credits
status: verified
threats_total: 34
threats_closed: 34
threats_open: 0
asvs_level: 1
audited: 2026-05-07T00:00:00+07:00
---

# Phase 02B — Security

> Per-phase security contract: threat register, accepted risks, and audit trail for the prepaid credit billing surface.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Internet -> SePay webhook | Public HTTPS POST from SePay/VietQR provider to `/api/billing/sepay/webhook` | Untrusted bank-memo payload, transaction id, transfer amount |
| Authenticated browser -> Billing API | Same-origin Next.js + signed session cookie -> `/api/billing/balance`, `/api/billing/topup/intent` | Tenant identity from `TenantContext` (ScopedValue), top-up amount |
| API/Worker -> PostgreSQL | App-layer SQL through Hibernate + JdbcTemplate, advisory locks, `SKIP LOCKED` claims | Tenant-scoped ledger / reservation / intent rows |
| Worker scheduler -> Ledger | `@Scheduled` watchdog + sweeper holding ShedLock against shared DB clock | Stale reservation tenant_id, stale intent ids |
| Cross-phase contract | Phase 2C `LlmGateway` imports `CreditLedger` interface | `tenantId`, `CallSite`, `ReservationId` |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02B-W0-01 | Tampering | Wave 0 test scaffolds | accept | Tests committed as immutable contracts; ArchUnit re-validates in Plan 06; no `@Disabled` remains (`02B-VERIFICATION.md` automated checks). | closed |
| T-02B-W0-02 | Information disclosure | `BillingPrivacyLogScrubTest` | mitigate | Test asserts log shape, not raw inputs (`backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java`). | closed |
| T-02B-W0-03 | Information disclosure | `SepayWebhookMismatchAuditEventTest` | mitigate | Privacy carve-out for VND numbers in mismatch event line; verifier confirmed via Wave 0 harness (`SepayWebhookMismatchAuditEventTest.java`). | closed |
| T-02B-01-01 | Tampering | Liquibase changesets | mitigate | Explicit rollback per changeset; CHECK constraint `ck_credit_ledger_entry_kind` (`014-credit-ledger-entry.yaml:67`); status checks at `015-credit-reservation.yaml:61` and `016-billing-topup-intent.yaml:71`. | closed |
| T-02B-01-02 | DoS | `credit_ledger_entry` growth | accept | BRIN index `idx_credit_ledger_entry_created_brin` (`014-credit-ledger-entry.yaml:88`); partial PENDING-only index `idx_credit_reservation_pending_created` (`015-credit-reservation.yaml:75`). | closed |
| T-02B-01-03 | Tampering | Multi-tenant FK | mitigate | `deleteCascade: true` to `tenants(id)` on all three billing tables (`014-credit-ledger-entry.yaml:22`, `015-credit-reservation.yaml:22`, `016-billing-topup-intent.yaml:22`). | closed |
| T-02B-01-04 | Information disclosure | `shedlock.locked_by` | accept | Operator-visible host/thread only; no PII (`017-shedlock-table.yaml:26`). | closed |
| T-02B-01-05 | Repudiation | `credit_ledger_entry` append-only | mitigate | `addUniqueConstraint columnNames: ref_type, ref_id, kind` -> `uq_credit_ledger_entry_ref_kind` (`014-credit-ledger-entry.yaml:61-64`); `version` column for `@Version` (`014-credit-ledger-entry.yaml:56-60`). | closed |
| T-02B-02-01 | Tampering | `CallSite` enum membership | mitigate | `CallSiteEnumMembershipArchTest` locks members + costs (`backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java:14-29`); DB CHECK on `kind` (`014-credit-ledger-entry.yaml:67`). | closed |
| T-02B-02-02 | Information disclosure | `InsufficientCreditsException` | mitigate | No-args ctor only, no balance message (`InsufficientCreditsException.java:11-15`); `params=Map.of()` at `GlobalExceptionHandler.java:130-138`. | closed |
| T-02B-02-03 | Repudiation | Cross-phase contract drift | mitigate | `CreditLedger` Javadoc preserves SPEC R8 BYOK exemption (`CreditLedger.java:57-62`). | closed |
| T-02B-02-04 | Elevation of privilege | Modulith boundary | mitigate | `package-info.java` `allowedDependencies = {tenant, shared.persistence, shared.lang}` (`backend/core/src/main/java/com/zeromail/core/billing/package-info.java:24-26`); `ZeroMailApiApplicationModulesTest.verify()` (`backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java:9-10`). | closed |
| T-02B-03-01 | Tampering | Reserve race / double-spend | mitigate | `@Transactional(REQUIRES_NEW)` + `pg_advisory_xact_lock(hashtext(?))` inside the same tx (`CreditLedgerService.java:43-46`, `AdvisoryLockJdbcHelper.java:23-30`); `CreditLedgerConcurrentReserveTest` exercises the path. | closed |
| T-02B-03-02 | Tampering | SePay replay | mitigate | UNIQUE on `(ref_type=PAYMENT_SEPAY, ref_id=sepayTransactionId, kind=TOPUP)` via `uq_credit_ledger_entry_ref_kind` + `CreditLedgerEntryEntity.topup` ref-type assignment (`CreditLedgerEntryEntity.java:53`); partial UNIQUE `uq_billing_topup_intent_sepay_tx` (`016-billing-topup-intent.yaml:85`); UAT Test 6 PASS. | closed |
| T-02B-03-03 | Tampering | Watchdog double-release | mitigate | `IllegalLedgerStateException` on `SETTLED -> release` (`CreditLedgerService.java:108-110`); idempotent on already-RELEASED (`CreditLedgerService.java:111-113`); `ON CONFLICT DO NOTHING` against the unique journal index in the watchdog batch (`CreditReserveWatchdogBatch.java:99`); UAT Test 10 PASS. | closed |
| T-02B-03-04 | Information disclosure | InsufficientCredits balance leak | mitigate | Service throws no-message exception (`CreditLedgerService.java:50`); handler maps to 402 with `params=Map.of()` (`GlobalExceptionHandler.java:130-138`). | closed |
| T-02B-03-05 | Information disclosure | Privacy log scrub | mitigate | All ledger logs use `event=<name> tenantId={}` shape only (`CreditLedgerService.java:62, 94, 126`); `BillingPrivacyLogScrubTest` verifies. | closed |
| T-02B-03-06 | Spoofing | API key timing attack | mitigate | `MessageDigest.isEqual(expectedKeyBytes, providedKeyBytes)`; bytes cached at construction (`SepayApiKeyVerifier.java:17, 25, 36`); UAT Test 5 PASS. | closed |
| T-02B-03-07 | Tampering | Negative-amount top-up | mitigate | DB CHECK `ck_billing_topup_intent_amount_positive` (`016-billing-topup-intent.yaml:74`); service guard against `< vndPerCredit` (`BillingTopupService.java:58-61`). | closed |
| T-02B-03-08 | Tampering | Credit conversion truncation race | accept | Fixed `vndPerCredit`; rounding loss logged (`BillingTopupService.java:193-199`); future hardening tracked (see Accepted Risks Log). | closed |
| T-02B-04-01 | Spoofing | SePay webhook forgery | mitigate | `@Order(2) SecurityFilterChain` for `/api/billing/sepay/**` (`BillingWebhookSecurityConfig.java:33-45`) + `SepayApiKeyAuthFilter` invokes `verifier.verify` -> `MessageDigest.isEqual` (`SepayApiKeyAuthFilter.java:40`); bare `${SEPAY_WEBHOOK_API_KEY}` placeholder rejected at startup if literal (`ZeroMailCoreProperties.java:50-63`); UAT Test 5 PASS. | closed |
| T-02B-04-02 | Information disclosure | Webhook key in logs | mitigate | Filter logs only `event=sepay_webhook_auth_invalid/missing` with no header bytes (`SepayApiKeyAuthFilter.java:42-44`); controller logs `event=sepay_webhook_received tenantId=unresolved` only (`SepayWebhookController.java:35`); UAT Test 5 PASS. | closed |
| T-02B-04-03 | Information disclosure | Cross-tenant balance read | mitigate | `BillingController.balance()` reads `TenantContext.currentOrThrow()` (`BillingController.java:36-39`); no `@PathVariable tenantId` exists in billing controllers (grep returned 0 matches); `BillingBalanceMultiTenantLeakTest` covers. | closed |
| T-02B-04-04 | Information disclosure | InsufficientCredits balance leak (HTTP shape) | mitigate | `GlobalExceptionHandler.onInsufficientCredits` -> `params=Map.of()` (`GlobalExceptionHandler.java:130-138`); UAT confirms 402 ApiError shape. | closed |
| T-02B-04-05 | DoS | Intent-table flood | mitigate | `createIntent` enforces `max-pending-intents-per-tenant=5` cap by expiring oldest PENDING when reached (`BillingTopupService.java:63-74`; `application.yml:85`); cap effectively bounds total PENDING per tenant. | closed |
| T-02B-04-06 | Tampering | i18n bundle drift | mitigate | `pnpm i18n:check` script enforces vi/en parity in CI (`apps/web/scripts/check-i18n.ts:377-384`); verification report records PASS. | closed |
| T-02B-05-01 | Tampering | Watchdog double-release race | mitigate | CTE-based `FOR UPDATE SKIP LOCKED` row claim + atomic `UPDATE ... RETURNING` in single tx (`CreditReserveWatchdogBatch.java:60-85`); `@SchedulerLock(name="creditReserveWatchdog", lockAtMostFor="PT2M")` (`CreditReserveWatchdog.java:44`); UNIQUE journal index plus `ON CONFLICT DO NOTHING` for idempotency (`CreditReserveWatchdogBatch.java:99`). | closed |
| T-02B-05-02 | Information disclosure | Watchdog log payload | mitigate | Single info line `event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}` only (`CreditReserveWatchdogBatch.java:108-112`). | closed |
| T-02B-05-03 | Tampering | Worker boot env-var missing | mitigate | `:?` fail-fast on `REFRESH_TOKEN_KEY_BASE64` (`backend/worker/src/main/resources/application.yml:29`); bare `${SEPAY_WEBHOOK_API_KEY}` (`backend/worker/src/main/resources/application.yml:36`) plus property-record sentinel rejection (`ZeroMailCoreProperties.java:50-63`). | closed |
| T-02B-05-04 | DoS | Watchdog stuck job | mitigate | `@SchedulerLock lockAtMostFor="PT2M"` on watchdog (`CreditReserveWatchdog.java:44`); `defaultLockAtMostFor="PT5M"` global net (`ShedLockConfig.java:22`). | closed |
| T-02B-05-05 | Privilege escalation | Cross-tenant release via watchdog | mitigate | Each release uses tenant_id sourced from the CTE-claimed reservation row itself (`CreditReserveWatchdogBatch.java:81, 102`); ledger insert hard-codes `tenant_id` from the same row, eliminating any cross-tenant reuse. **Implementation deviates from plan text (which described per-iteration ScopedValue binding) — current CTE approach achieves the same isolation invariant via row-derived tenant_id; rationale documented in `CreditReserveWatchdogBatch.java:14-34`.** | closed |
| T-02B-06-01 | Tampering | ArchUnit boundary drift | mitigate | `DomainBoundaryArchTests` includes a billing-specific rule (`backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java:84-93`); `BillingDomainBoundaryArchTest` enforces JDBC-only-in-lowlevel + service implementation hiding (`BillingDomainBoundaryArchTest.java:14-63`); `CallSiteEnumMembershipArchTest` locks BILL-07 invariant. | closed |
| T-02B-06-02 | Repudiation | REQUIREMENTS.md status drift | mitigate | `02B-VERIFICATION.md` records BILL-01..BILL-07 SATISFIED with concrete evidence; `requirements-completed` frontmatter is set in each plan summary. | closed |
| T-02B-06-03 | Tampering | Modulith allowedDependencies drift | mitigate | `ZeroMailApiApplicationModulesTest.verify()` runs against `ApplicationModules.of(ZeroMailApiApplication.class)` (`backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java:9-10`). | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Unregistered Threat Flags

None. Every plan summary in `02B-00..02B-06` reports `## Threat Flags: None - covered by plan threat model.` No new attack surface escaped the register.

---

## Implementation Deviations Worth Logging

| Threat | Planned Pattern | Implemented Pattern | Rationale | Net Effect |
|--------|-----------------|---------------------|-----------|------------|
| T-02B-05-05 | Per-iteration `ScopedValue.where(TenantContext.TENANT, ...)` before `CreditLedger.release(...)` | Atomic CTE in `CreditReserveWatchdogBatch.releaseStaleBatch` that claims rows `FOR UPDATE SKIP LOCKED`, flips status, and inserts the RELEASE ledger entry using the row's own `tenant_id` | Hibernate's tenant resolver (`ScopedValueTenantResolver.validateExistingCurrentSessions=true`) forbids mid-tx tenant rebinds, blocking a multi-tenant batch from reusing one Hibernate session. SQL CTE preserves the same anti-cross-tenant invariant. | Cross-tenant release impossible: the inserted RELEASE row's `tenant_id` is read from the claimed reservation row, never from request input or shared mutable state. Idempotency preserved by the existing `uq_credit_ledger_entry_ref_kind` unique constraint with `ON CONFLICT DO NOTHING`. |
| T-02B-04-01 | `@Order(1)` SecurityFilterChain | `@Order(2)` SecurityFilterChain | Pub/Sub ingress already owns `@Order(1)` (documented in `02B-04-SUMMARY.md`). Filter chain still wins over the user-session chain (`@Order(3)`) for `/api/billing/sepay/**`. | Webhook auth still enforced before any business logic; filter precedence preserved. |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02B-01 | T-02B-W0-01 | Wave 0 RED-by-design tests are committed as immutable contracts. Later-plan executors flip `@Disabled` only; ArchUnit Plan 06 re-validates shape. Re-audit only if `@Disabled` regressions appear. | Phase 02B planning | 2026-05-06 |
| AR-02B-02 | T-02B-01-02 | Unbounded historical growth of `credit_ledger_entry` is acceptable for v1; BRIN(created_at) plus the partial PENDING-only index keeps hot paths fast. Future hardening (TimescaleDB / time-bucketed partitioning) tracked in backlog. | Phase 02B planning | 2026-05-06 |
| AR-02B-03 | T-02B-01-04 | `shedlock.locked_by` exposes operator host/thread name only; no PII. Default ShedLock format retained. | Phase 02B planning | 2026-05-06 |
| AR-02B-04 | T-02B-03-08 | Fixed `vndPerCredit` rounding loss is logged at ledger time (`event=sepay_topup_rounding_loss`) but not auto-credited. Operator review required before changing the conversion rate. Future hardening (sub-cent ledger precision) tracked in backlog. | Phase 02B planning | 2026-05-06 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-07 | 34 | 34 | 0 | gsd-security-auditor (Claude Opus 4.7) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-07

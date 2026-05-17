---
phase: 02B-billing-prepaid-credits
verified: 2026-05-06T15:55:33+07:00
status: passed
score: "7/7 requirements verified"
overrides_applied: 0
human_verification: []
deferred:
  - truth: "In-product billing UI rendering"
    addressed_in: "Phase 5 User Surface"
    evidence: "Phase 02B delivered the authenticated balance/top-up API, OpenAPI schema, generated frontend types, and i18n keys; Phase 5 owns the complete product UI surface."
  - truth: "Live SePay/VietQR banking-provider smoke test"
    addressed_in: "Staging/payment-provider readiness before launch"
    evidence: "Local verification covers signed webhook auth, idempotency, replay, amount mismatch audit, and ledger crediting with simulated provider payloads."
---

# Phase 02B: Billing (Prepaid Credits) Verification Report

**Phase Goal:** Stand up a double-entry Postgres credit ledger with reserve/settle/release semantics and a watchdog, so that every billable action in later phases can charge credits safely under concurrency.
**Verified:** 2026-05-06T15:55:33+07:00
**Status:** passed

## Goal Achievement

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Billing schema exists for ledger entries, reservations, top-up intents, and ShedLock. | VERIFIED | Changesets `014` through `017` are wired through `db.changelog-master.yaml`; final check constraints use raw SQL and schema drift gate returned `drift_detected=false`. |
| 2 | Credit reserve/settle/release is atomic, idempotent, and safe under concurrency. | VERIFIED | `CreditLedgerService` implements `CreditLedger`, uses a per-tenant Postgres advisory transaction lock for reserve, `REQUIRES_NEW` on reserve, idempotent settle/release, and privacy-safe insufficient-credit errors. Core billing tests passed. |
| 3 | SePay/VietQR top-up flow can create intents and credit the ledger through signed webhooks. | VERIFIED | `BillingTopupService`, `BillingController`, `SepayWebhookController`, and `SepayApiKeyAuthFilter` are wired; API billing tests cover valid webhook, replay, bad auth, tenant isolation, amount mismatch audit, and privacy log scrub. |
| 4 | Stale credit holds and stale top-up intents are swept by worker jobs. | VERIFIED | `CreditReserveWatchdog` binds `TenantContext` per stale reservation before `CreditLedger.release`; `BillingIntentExpirySweeper` runs transactional expiry; ShedLock is configured with database time. Worker billing tests passed. |
| 5 | Billing settings follow the project properties convention. | VERIFIED | There is no separate `BillingProperties`, `BillingConfiguration`, `BillingApiConfiguration`, or `BillingWorkerConfiguration` class. Billing settings live under `ZeroMailCoreProperties.BillingProperties` and use the existing `zeromail.billing.*` namespace. |
| 6 | Billing API contract is available to frontend code. | VERIFIED | `GET /api/billing/balance`, `POST /api/billing/topup/intent`, and `POST /api/billing/sepay/webhook` are present in generated OpenAPI; `pnpm generate:api` regenerated `apps/web/lib/api/schema.d.ts`; i18n parity passed. |
| 7 | Billing boundaries are enforced. | VERIFIED | `BillingDomainBoundaryArchTest`, `CallSiteEnumMembershipArchTest`, `DomainBoundaryArchTests`, and `ZeroMailApiApplicationModulesTest` passed; no billing Wave 0 `@Disabled` annotations remain. |

**Score:** 7/7 requirements verified

## Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| BILL-01 | SATISFIED | Top-up intent API and SePay webhook path exist; signed webhook credits the ledger idempotently in integration tests. |
| BILL-02 | SATISFIED | Double-entry-style append-only ledger entries cover TOPUP, RESERVE, SETTLE, and RELEASE journal kinds. |
| BILL-03 | SATISFIED | Reserve/settle/release flow uses advisory locks and idempotency constraints; concurrent reserve and finalization tests passed. |
| BILL-04 | SATISFIED | Worker watchdog releases stale pending reservations and is protected by ShedLock. |
| BILL-05 | SATISFIED FOR PHASE SCOPE | Balance endpoint, generated frontend type contract, and call-site cost map are present; full visible product UI remains Phase 5 scope. |
| BILL-06 | SATISFIED | `InsufficientCreditsException` maps to HTTP 402 with no balance leak; API tests cover insufficient-credit response shape. |
| BILL-07 | SATISFIED FOR CROSS-PHASE CONTRACT | `CreditLedger` and `CallSite` explicitly exclude BYOK traffic; Phase 2C must skip ledger reservation when BYOK credentials exist. |

No orphaned Phase 02B requirements were found in `.planning/REQUIREMENTS.md`; all BILL rows are marked complete.

## Automated Checks

| Check | Result |
|-------|--------|
| `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.*"` | PASS |
| `.\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.billing.*"` | PASS |
| `.\gradlew.bat :backend:worker:test --tests "com.zeromail.worker.billing.*"` | PASS |
| `.\gradlew.bat :backend:api:generateOpenApiDocs` | PASS |
| `pnpm generate:api` | PASS |
| `pnpm --filter web i18n:check` | PASS |
| `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.arch.DomainBoundaryArchTests" --tests "com.zeromail.core.billing.BillingDomainBoundaryArchTest" --tests "com.zeromail.core.billing.CallSiteEnumMembershipArchTest" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` | PASS |
| `.\gradlew.bat clean check` | PASS |
| `pnpm --filter web generate:api` | PASS |
| `rg "@Disabled" backend/core/src/test/java/com/zeromail/core/billing backend/api/src/test/java/com/zeromail/api/controllers/billing backend/worker/src/test/java/com/zeromail/worker/billing -n` | PASS, no matches |
| `gsd-sdk query verify.schema-drift 02B` | PASS, no drift detected |

## Key Deviations Resolved

- Billing configuration was consolidated into `ZeroMailCoreProperties` after user review. The final implementation uses `zeromail.billing.*` and removes the separate billing configuration/property classes.
- Liquibase billing check constraints use explicit SQL because the installed runtime rejected the planned check-constraint change type.
- Worker scheduled methods delegate to direct `tick()` / `sweep()` methods so tests can exercise behavior without being intercepted by ShedLock startup locks.
- The API module disables Open Session in View so tenant-bound service transactions own billing JPA access.

## Human Verification Required

None for local phase completion. A live SePay/VietQR provider smoke test and final visible billing UI inspection remain launch/user-surface activities, not blockers for the Phase 02B ledger/API/worker contract.

## Gaps Summary

No automated goal-achievement gaps remain. Phase 02B is ready for Phase 02C and later billable-action integrations.

---

_Verified: 2026-05-06T15:55:33+07:00_
_Verifier: Codex (inline gsd-verifier fallback)_

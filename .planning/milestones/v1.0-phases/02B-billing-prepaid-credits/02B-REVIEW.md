---
phase: 02B-billing-prepaid-credits
reviewed: 2026-05-07T00:32:37+07:00
depth: standard
files_reviewed: 72
files_reviewed_list:
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/lib/api/schema.d.ts
  - apps/web/openapi/openapi.json
  - backend/api/build.gradle.kts
  - backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java
  - backend/api/src/main/java/com/zeromail/api/dto/billing/package-info.java
  - backend/api/src/main/java/com/zeromail/api/security/billing/BillingWebhookSecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/main/resources/application.yml
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java
  - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java
  - backend/core/src/main/java/com/zeromail/core/billing/package-info.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookup.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookupFragment.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/BillingTopupIntentRepositoryImpl.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java
  - backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml
  - backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml
  - backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml
  - backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
  - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java
  - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
  - backend/worker/build.gradle.kts
  - backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
  - backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java
  - backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
  - backend/worker/src/main/java/com/zeromail/worker/config/WorkerJpaAuditingConfig.java
  - backend/worker/src/main/resources/application.yml
  - backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java
  - backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java
  - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  - gradle/libs.versions.toml
findings:
  critical: 0
  warning: 3
  info: 1
  total: 4
status: issues_found
---

# Phase 02B: Code Review Report

**Reviewed:** 2026-05-07T00:32:37+07:00
**Depth:** standard
**Files Reviewed:** 72
**Status:** issues_found

## Summary

The core billing model is directionally sound: reserve uses a per-tenant transaction-scoped advisory lock around the balance check, ledger rows are tenant-owned and append-only, API-key webhook auth avoids logging header bytes, and the phase-scoped billing tests pass across core, API, worker, i18n, and frontend type checking.

The main risks are around edge cases that matter for real-money top-ups and background cleanup: SePay memo parsing can acknowledge a valid payment without crediting it when the first code-shaped token is not the pending intent code; the watchdog's `FOR UPDATE SKIP LOCKED` scan does not hold row locks across release processing; and concurrent duplicate webhooks can still surface non-200 responses despite the replay contract.

## Warnings

### WR-01: SePay memo extraction stops at the first code-shaped token

**File:** `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java:109`

**Issue:** `applyWebhook(...)` asks `extractIntentCode(code, content)` for exactly one candidate, then performs one lookup. `extractIntentCode(...)` accepts the `code` field if it is syntactically Crockford-valid, even when it is not a known pending top-up intent, and the content fallback returns only the first `[0-9A-HJKMNPQRSTVWXYZ]{8}` match from the memo (`BillingTopupService.java:191-201`). Real bank memo text can contain other 8-character digit/reference fragments before the Zero Mail code, and SePay's detected `code` field can be present but not match an active intent. In either case the controller still returns HTTP 200, but the user is not credited and the later correct code in `content` or `referenceCode` is never considered.

**Why it matters:** This is a real-money reconciliation failure mode. It is not a security leak, but it can silently convert a paid transfer into a manual support case.

**Fix:** Extract all plausible candidates from `referenceCode`, `code`, and `content`, normalize/deduplicate them, and choose the first candidate that resolves to a `PENDING`, unexpired intent with the exact amount. Add integration coverage for content such as `"20260505 ABC12345 nap tien"` and for a syntactically valid but unknown `code` field with the correct intent code later in content.

### WR-02: Stale-reservation row locks are released before the watchdog releases reservations

**File:** `backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java:34`

**Issue:** The stale scan uses `FOR UPDATE SKIP LOCKED`, but `CreditReserveWatchdog.tick()` is not transactional (`backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java:56-59`). With JDBC autocommit, the row locks acquired by the select are released as soon as the query returns, before the loop calls `creditLedger.release(...)`. ShedLock reduces the production risk, but if a lock expires, a node is slow, or `tick()` is invoked concurrently outside the scheduled path, another worker can select the same rows. The ledger uniqueness and optimistic lock will probably prevent double credit, but the intended `SKIP LOCKED` protection is not actually active across processing and can create avoidable rollback/noise paths.

**Fix:** Move the scan-and-release batch into a transactional service method so the selected rows remain locked until all corresponding release calls finish, or replace the scan with an atomic claim/update pattern. If `tick()` remains callable for tests, avoid relying on self-invoked `@Transactional`; put the transactional method on a separate bean or annotate the scheduled entrypoint that Spring actually invokes.

### WR-03: Concurrent duplicate webhooks can escape the replay-as-200 contract

**File:** `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java:156`

**Issue:** Sequential replay is covered: once the intent is `PAID`, the second delivery exits as a no-op. The concurrent case is weaker. Two identical webhook requests can both pass the pre-transaction lookup while the intent is still `PENDING`, then both enter `applyTopupCreditTransactional(...)`. The code catches `DataIntegrityViolationException` around the paid update and ledger insert (`BillingTopupService.java:175-187`), but it does not handle optimistic-lock conflicts from the versioned `BillingTopupIntentEntity`. Those conflicts are translated by the API layer as 409, not as the required webhook acknowledgement. SePay will retry, but the endpoint contract says duplicate delivery should return HTTP 200 and stop retries.

**Fix:** Make webhook application explicitly idempotent under concurrency. A common shape is an atomic `UPDATE billing_topup_intent SET status='PAID', ... WHERE id=? AND status='PENDING'` and then insert the ledger row, treating zero updated rows plus an existing transaction id as a successful replay ack. Add an integration test that posts the same payload from two virtual threads and asserts both responses are 200 and exactly one `TOPUP` ledger row exists.

## Info

### IN-01: Several billing logs omit the project-standard tenant field

**Files:**
- `backend/api/src/main/java/com/zeromail/api/controllers/billing/SepayWebhookController.java:35`
- `backend/api/src/main/java/com/zeromail/api/security/billing/SepayApiKeyAuthFilter.java:42`
- `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java:105`

**Issue:** The project logging convention is `event=<name> tenantId={}` plus structured fields. Some webhook/auth events happen before tenant resolution, so a concrete tenant id is not always available, but the current log shape omits `tenantId` entirely across missing-auth, invalid-auth, unknown-code, non-inbound, replay, and amount-mismatch paths. In the amount-mismatch and not-pending paths the tenant is already known, so the omission also weakens reconciliation auditability.

**Fix:** Use `tenantId=unresolved` for pre-lookup events and include `tenantId={}` once `BillingTopupIntentTenantLookup` is available. Keep payload fields, auth bytes, account numbers, and bank memo text out of logs.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.*" :backend:api:test --tests "com.zeromail.api.controllers.billing.*" :backend:worker:test --tests "com.zeromail.worker.billing.*"` passed.
- `pnpm --filter web i18n:check` passed.
- `pnpm --filter web typecheck` passed.
- Broader backend run `.\gradlew.bat :backend:core:test :backend:api:test :backend:worker:test` failed in `com.zeromail.api.security.CorsIntegrationTest.actual_response_for_frontend_origin_includes_cors_headers` with `/actuator/health` returning 503 DOWN. That test is outside the Phase 02B review scope; the phase-scoped billing tests passed.

---

_Reviewed: 2026-05-07T00:32:37+07:00_
_Reviewer: Codex inline gsd-code-review fallback_
_Depth: standard_

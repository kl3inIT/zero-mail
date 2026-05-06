---
phase: 02B-billing-prepaid-credits
plan: 03
subsystem: backend-billing-core
tags: [billing, prepaid-credits, spring-data-jpa, postgres, sepay, advisory-lock]

requires:
  - phase: 02B-01
    provides: Credit ledger, reservation, top-up intent, and ShedLock schema.
  - phase: 02B-02
    provides: Billing model contracts, enums, records, and exceptions.
provides:
  - Tenant-owned billing persistence entities and repositories.
  - Lowlevel JDBC advisory-lock and tenant-lookup fragments.
  - Billing services consume billing settings from ZeroMailCoreProperties, plus SePay API key verifier and Crockford code generator.
  - CreditLedgerService reserve, settle, release, and balance implementation.
  - BillingTopupService top-up intent and SePay webhook ledger credit flow.
affects: [02B-04-api-surface, 02B-05-worker-schedulers, 02B-06-verification-closure, 02C-llm-gateway]

tech-stack:
  added: []
  patterns:
    - Spring Data JPA repositories with lowlevel JdbcTemplate fragments for tenant-filter bypass reads.
    - TransactionTemplate inside TenantContext ScopedValue for unauthenticated webhook writes.
    - Postgres advisory transaction lock around reserve critical section.

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryRepository.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentEntity.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookup.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookupFragment.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/BillingTopupIntentRepositoryImpl.java
    - backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java
    - backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
    - backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java
  modified:
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java

key-decisions:
  - "Top-up code uniqueness checks use the tenant-bypassing lookup fragment because code is globally unique while standard JPA reads are tenant-filtered."
  - "Billing settings are nested under ZeroMailCoreProperties and mask their toString output because the record carries the SePay webhook API key."

patterns-established:
  - "Webhook tenant resolution reads by raw JDBC projection, then binds TenantContext before TransactionTemplate opens the JPA transaction."
  - "CreditLedgerService remains package-private; callers depend on the public CreditLedger interface."

requirements-completed: [BILL-02, BILL-03, BILL-04, BILL-05, BILL-06]

duration: 14min
completed: 2026-05-06
---

# Phase 02B Plan 03: Credit Ledger Service Summary

**Postgres-backed prepaid credit ledger services with advisory-lock reservations, SePay top-up idempotency, and core billing tests enabled.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-05-06T05:43:42Z
- **Completed:** 2026-05-06T05:57:41Z
- **Tasks:** 3/3
- **Files modified:** 25

## Accomplishments

- Added tenant-owned ledger, reservation, and top-up intent persistence with lowlevel JDBC fragments for stale reservation scans and webhook tenant lookup.
- Added billing settings under `ZeroMailCoreProperties`, constant-time `SepayApiKeyVerifier`, and Crockford `TopupCodeGenerator`; enabled their Wave 0 unit tests.
- Implemented `CreditLedgerService` and `BillingTopupService` with advisory-lock reserve, idempotent finalization, tenant-bound webhook writes, replay handling, and minimum top-up safeguards.

## Task Commits

1. **Task 1: JPA entities, repositories, fragments, advisory lock helper** - `9dcdc82` (`feat`)
2. **Task 2: Billing configuration and utility services** - `d5151da` (`feat`)
3. **Task 3: Credit ledger and top-up services** - `b445768` (`feat`)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/billing/persistence/*` - Ledger, reservation, top-up intent entities, repositories, and tenant-aware projection contracts.
- `backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/*` - Raw JDBC advisory lock and tenant-filter-bypassing lookup implementations.
- `backend/core/src/main/java/com/zeromail/core/billing/service/*` - Billing utilities and service implementations.
- `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java` - Billing settings nested under the existing core properties root.
- `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java` - Core billing property test overrides.
- `backend/core/src/test/java/com/zeromail/core/billing/**` - Plan 03 runtime-disabled annotations removed from utility and ledger tests.

## Decisions Made

- Used `Math.toIntExact(...)` in `CreditLedgerService` around JPQL `SUM(...)` results so widened aggregate values are converted deliberately at the service boundary.
- Used `BillingTopupIntentRepository.findTenantLookupByCode(...)` for code collision checks, not tenant-filtered `findByCode(...)`, because `billing_topup_intent.code` is globally unique.
- Masked nested billing properties in `ZeroMailCoreProperties.toString()` to avoid accidental logging of the SePay webhook API key.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Masked billing secret in nested core configuration record**
- **Found during:** Task 2 (ZeroMailCoreProperties billing settings)
- **Issue:** The planned record carried `sepay.webhookApiKey` but did not override record `toString()`, which would expose the secret if the bean were logged.
- **Fix:** Added a masked `toString()` returning `sepay=****`.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java`
- **Verification:** `./gradlew.bat :backend:core:compileJava` and utility tests passed.
- **Committed in:** `d5151da`

**2. [Rule 1 - Bug] Avoided tenant-filtered global code collision check**
- **Found during:** Task 3 (BillingTopupService)
- **Issue:** The plan predicate used `findByCode`, but that JPA read is tenant-filtered while `billing_topup_intent.code` is globally unique. Cross-tenant collisions could skip retry and fail on the database constraint.
- **Fix:** Used `findTenantLookupByCode(candidateCode).isEmpty()` through the lowlevel lookup fragment.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java`
- **Verification:** `./gradlew.bat :backend:core:compileJava`, `:backend:core:compileTestJava`, and static lookup greps passed.
- **Committed in:** `b445768`

**Total deviations:** 2 auto-fixed (1 missing critical security guard, 1 bug fix)  
**Impact on plan:** Both are local correctness/security fixes inside declared files. No architecture or schema expansion.

## Verification

- `.\gradlew.bat :backend:core:compileJava` - PASS.
- `.\gradlew.bat :backend:core:compileTestJava` - PASS.
- `.\gradlew.bat :backend:core:test --tests "*SepayApiKeyVerifierTest*" --tests "*TopupCodeGeneratorTest*"` - PASS.
- Static plan greps for advisory lock, `Propagation.REQUIRES_NEW`, `MessageDigest.isEqual`, Crockford alphabet, `ScopedValue.where(TenantContext.TENANT, ...)`, `transactionTemplate.executeWithoutResult`, and tenant lookup files - PASS.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.service.*"` - BLOCKED by environment before billing code execution: Testcontainers cannot find a valid Docker environment.
- JetBrains full-project build - BLOCKED by out-of-scope Plan 04 API Wave 0 test symbols (`com.zeromail.api.dto.billing.*`) that are not owned by this plan.

## Known Stubs

None. Stub scan found only false positives: placeholder-sentinel validation text, null checks, and SLF4J `{}` placeholders.

## Threat Flags

None - new raw JDBC, tenant binding, replay idempotency, API-key comparison, and privacy logging surfaces are covered by the plan threat model.

## Issues Encountered

- Testcontainers-backed ledger integration tests could not execute because Docker is unavailable to Testcontainers in this environment. The tests compile and should be rerun when Docker is available.
- Full IDE build still sees expected API Wave 0 RED compile errors for Plan 04-owned billing DTO/controller classes.

## User Setup Required

None - no external service configuration required by this core implementation plan.

## Next Phase Readiness

Ready for Plan 04 API wiring and Plan 05 worker schedulers. The core `CreditLedger` interface has a real injectable implementation, top-up services are available for controllers, and worker code can consume `StaleReservation` projections.

## Authentication Gates

None.

## Self-Check: PASSED

- All declared summary and key implementation files exist on disk.
- Task commits found in git history: `9dcdc82`, `d5151da`, `b445768`.
- No tracked file deletions were introduced by the task commits.

---
*Phase: 02B-billing-prepaid-credits*
*Completed: 2026-05-06*

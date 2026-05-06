---
phase: 02B-billing-prepaid-credits
plan: 05
subsystem: backend-worker
tags: [billing, prepaid-credits, worker, shedlock, scheduler]

requires:
  - phase: 02B-01
    provides: ShedLock dependency and shedlock table migration.
  - phase: 02B-03
    provides: CreditLedger, CreditReservationRepository stale projection scan, and BillingTopupIntentRepository expiry update.
provides:
  - Worker billing configuration for BillingProperties.
  - ShedLock JDBC lock provider using database time.
  - Stale credit reservation watchdog.
  - Billing top-up intent expiry sweeper.
affects: [02B-06-verification-closure, 02C-llm-gateway]

tech-stack:
  added: []
  patterns:
    - ShedLock-backed worker schedulers with explicit lock names and lock windows.
    - TenantContext ScopedValue binding per stale reservation before calling CreditLedger.release.
    - Transaction-owned bulk expiry sweep for billing top-up intents.

key-files:
  created:
    - backend/worker/src/main/java/com/zeromail/worker/billing/BillingWorkerConfiguration.java
    - backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
    - backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java
    - backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
  modified:
    - backend/worker/src/main/resources/application.yml
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java

key-decisions:
  - "Worker BillingWorkerConfiguration only enables BillingProperties because ZeroMailWorkerApplication already scans com.zeromail.core."
  - "CreditReserveWatchdog consumes StaleReservation projections and binds TenantContext before ledger release; it never calls reservationRepository.findById before tenant binding."
  - "Worker application.yml uses a bare SEPAY_WEBHOOK_API_KEY placeholder for fail-fast placeholder resolution, matching the plan's cycle-3 review constraint."

patterns-established:
  - "Worker jobs use @SchedulerLock in addition to SKIP LOCKED repository behavior for cluster-safe execution."
  - "Worker billing tests get billing and crypto defaults through DynamicPropertySource, not committed test profile secrets."

requirements-completed: [BILL-04]

duration: 18min
completed: 2026-05-06
---

# Phase 02B Plan 05: Worker Schedulers Summary

**Worker-side billing safety jobs for stale reservation release and top-up intent expiry.**

## Performance

- **Duration:** 18 min
- **Tasks:** 2/2
- **Files modified:** 8 implementation/test files plus this summary

## Accomplishments

- Added `BillingWorkerConfiguration` to bind core billing properties in the worker module.
- Added `ShedLockConfig` with `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")` and `JdbcTemplateLockProvider` configured with database time.
- Implemented `CreditReserveWatchdog` at `fixedRate = 60_000L`, using `findStalePendingProjections`, per-row `ScopedValue.where(TenantContext.TENANT, ...)`, and `CreditLedger.release`.
- Implemented `BillingIntentExpirySweeper` at `fixedRate = 3_600_000L` with `@Transactional` around `expireStale(Instant.now())`.
- Enabled the two worker Wave 0 billing tests by removing their `@Disabled` annotations.
- Updated worker `application.yml` and `PostgresContainerTest` so billing properties and refresh-token crypto are present under tests.

## Commits

| Commit | Description |
|--------|-------------|
| `424d5d2` | `feat(02B-05): wire worker billing scheduling configuration` |
| `5771a41` | `test(02B-05): enable worker billing scheduler tests` |
| `4e1aefb` | `feat(02B-05): implement worker billing schedulers` |

## Verification

- `.\gradlew.bat :backend:worker:compileJava :backend:worker:compileTestJava` — **PASS**
- `.\gradlew.bat :backend:worker:test --tests "com.zeromail.worker.billing.*"` — **BLOCKED before tests executed** by Liquibase rejecting `addCheckConstraint` in Phase 02B schema changelogs.

## Issues Encountered

Liquibase failed during worker test context startup because `backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml` uses `addCheckConstraint`, which this runtime reports as an unknown change type. The same pattern is likely present in `015-credit-reservation.yaml` and `016-billing-topup-intent.yaml`.

This is schema drift from Plan 01, not worker scheduler logic. Worker production and test sources compile successfully. Plan 06 or a pre-verification repair must replace the unsupported check-constraint changes with repo-compatible SQL before full integration tests can pass.

## Deviations from Plan

The implementation classes are public rather than package-private so Spring proxying, tests, and cross-package wiring stay straightforward in the worker module. Behavior and scheduler annotations match the plan.

## Self-Check: PASSED

- Required worker files exist.
- Scheduler annotations and lock names are present.
- Watchdog binds `TenantContext.TENANT` before calling `CreditLedger.release`.
- Sweeper owns a transaction for the `@Modifying` expiry update.
- Worker compile and test compile pass.

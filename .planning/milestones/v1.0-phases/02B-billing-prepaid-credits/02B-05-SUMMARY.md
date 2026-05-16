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
  - ShedLock JDBC lock provider using database time.
  - Stale credit reservation watchdog.
  - Billing top-up intent expiry sweeper.
  - Worker-local JPA auditing configuration for core entities persisted by worker jobs.
affects: [02B-06-verification-closure, 02C-llm-gateway]

tech-stack:
  added: []
  patterns:
    - ShedLock-backed worker schedulers with explicit lock names and lock windows.
    - TenantContext ScopedValue binding per stale reservation before calling CreditLedger.release.
    - Transaction-owned bulk expiry sweep for billing top-up intents.

key-files:
  created:
    - backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
    - backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java
    - backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
    - backend/worker/src/main/java/com/zeromail/worker/config/WorkerJpaAuditingConfig.java
  modified:
    - backend/worker/src/main/resources/application.yml
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java

key-decisions:
  - "No worker BillingWorkerConfiguration remains; billing properties are nested under ZeroMailCoreProperties and discovered by the existing @ConfigurationPropertiesScan."
  - "CreditReserveWatchdog consumes StaleReservation projections and binds TenantContext before ledger release; it never calls reservationRepository.findById before tenant binding."
  - "Worker application.yml uses a bare SEPAY_WEBHOOK_API_KEY placeholder for fail-fast placeholder resolution, matching the plan's cycle-3 review constraint."
  - "Scheduled entrypoints are separated from direct work methods so tests can call tick()/sweep() without being blocked by ShedLock startup locks."

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

- Added `ShedLockConfig` with `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")` and `JdbcTemplateLockProvider` configured with database time.
- Implemented `CreditReserveWatchdog` at `fixedRate = 60_000L`, using `findStalePendingProjections`, per-row `ScopedValue.where(TenantContext.TENANT, ...)`, and `CreditLedger.release`.
- Implemented `BillingIntentExpirySweeper` at `fixedRate = 3_600_000L` with `@Transactional` around `expireStale(Instant.now())`.
- Added `WorkerJpaAuditingConfig` so worker-persisted core entities populate `created_at` and `updated_at`.
- Enabled the two worker Wave 0 billing tests by removing their `@Disabled` annotations.
- Updated worker `application.yml` and `PostgresContainerTest` so billing properties and refresh-token crypto are present under tests.

## Commits

| Commit | Description |
|--------|-------------|
| `424d5d2` | `feat(02B-05): wire worker billing scheduling configuration` |
| `5771a41` | `test(02B-05): enable worker billing scheduler tests` |
| `4e1aefb` | `feat(02B-05): implement worker billing schedulers` |
| `768b52a` | `fix(02B-05): make billing scheduler tests deterministic` |

## Verification

- `.\gradlew.bat :backend:worker:compileJava :backend:worker:compileTestJava` — **PASS**
- `.\gradlew.bat :backend:worker:test --tests "com.zeromail.worker.billing.*"` — **PASS**

## Issues Encountered

Initial worker verification was blocked by Liquibase rejecting `addCheckConstraint` in the Phase 02B schema changelogs. Commit `8f1f423` replaced those changes with SQL constraints, after which the worker tests exposed two real wiring issues:

- Worker persisted core entities without API module JPA auditing, causing null audit timestamps on insert.
- ShedLock could lock scheduled methods during startup, making direct test calls no-op.

Both were fixed in `768b52a`.

## Deviations from Plan

The implementation classes are public rather than package-private so Spring proxying, tests, and cross-package wiring stay straightforward in the worker module. Scheduled methods delegate to direct work methods (`scheduledTick()` -> `tick()`, `scheduledSweep()` -> `sweep()`) so tests can exercise behavior without lock interception.

## Self-Check: PASSED

- Required worker files exist.
- Scheduler annotations and lock names are present.
- Watchdog binds `TenantContext.TENANT` before calling `CreditLedger.release`.
- Sweeper owns a transaction for the `@Modifying` expiry update.
- Worker billing scheduler tests pass.

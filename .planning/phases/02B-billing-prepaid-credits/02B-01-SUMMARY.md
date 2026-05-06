---
phase: 02B-billing-prepaid-credits
plan: 01
subsystem: database
tags: [postgres, liquibase, shedlock, gradle, billing]

requires:
  - phase: 01.2.1
    provides: AbstractTenantOwnedEntity audit/version column contract
  - phase: 02B
    provides: billing schema and ShedLock research decisions
provides:
  - Credit ledger, reservation, top-up intent, and ShedLock database schema
  - Ordered changelog master wiring through changeset 017
  - ShedLock 7.7.0 version catalog and worker runtime dependencies
affects: [02B-02-domain-model, 02B-03-credit-ledger-service, 02B-05-worker-schedulers, 02C-llm-gateway]

tech-stack:
  added: [net.javacrumbs.shedlock:shedlock-spring:7.7.0, net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0]
  patterns:
    - Liquibase raw SQL for BRIN and partial indexes
    - Explicit ordered changelog includes

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml
    - backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml
    - backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml
    - backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - gradle/libs.versions.toml
    - backend/worker/build.gradle.kts

key-decisions:
  - "Used raw SQL check constraints for billing enums and positive amounts because the installed Liquibase runtime rejected addCheckConstraint in these changelogs."
  - "Replaced includeAll with explicit ordered includes because appending explicit 014-017 includes to the existing includeAll master would duplicate changeset execution."

patterns-established:
  - "Billing migrations declare audit columns explicitly, with version as int to match AbstractAuditableEntity.version."
  - "ShedLock uses the standard name, lock_until, locked_at, locked_by table shape."

requirements-completed: [BILL-01, BILL-02, BILL-03, BILL-04]

duration: 7min
completed: 2026-05-06
---

# Phase 02B Plan 01: Schema and Dependencies Summary

**Postgres billing schema with append-only credit ledger tables plus ShedLock 7.7.0 worker dependency wiring**

## Performance

- **Duration:** 7 min
- **Started:** 2026-05-06T05:19:34Z
- **Completed:** 2026-05-06T05:25:49Z
- **Tasks:** 2 completed
- **Files modified:** 7 implementation files + this summary

## Accomplishments

- Added `credit_ledger_entry`, `credit_reservation`, and `billing_topup_intent` Liquibase changesets with tenant FKs, audit columns, idempotency constraints, and performance indexes.
- Added the `shedlock` table migration and wired ShedLock 7.7.0 into `gradle/libs.versions.toml` and `backend/worker`.
- Changed the master changelog to explicit ordered includes through `017-shedlock-table.yaml` so Phase 2B migration order is deterministic and grep-verifiable.

## Task Commits

1. **Task 1: Liquibase changesets 014-016 (billing tables)** - `fb73738` (`feat`)
2. **Task 2: ShedLock changeset + master include + version catalog + worker build wiring** - `8a1b866` (`chore`)

**Plan metadata:** committed separately after this summary.

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml` - Credit ledger journal with `UNIQUE(ref_type, ref_id, kind)`, tenant/time indexes, and BRIN `created_at` index.
- `backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml` - Reservation sidecar with status/amount checks and `PENDING` partial stale-scan index.
- `backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml` - Top-up intent table with unique memo code and partial unique SePay transaction replay guard.
- `backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml` - Standard ShedLock JDBC table.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - Explicit ordered includes for changesets `001` through `017`.
- `gradle/libs.versions.toml` - ShedLock version and library aliases.
- `backend/worker/build.gradle.kts` - Worker runtime dependencies for ShedLock Spring and JDBC provider.

## Decisions Made

- Used `constraintBody` for Liquibase `addCheckConstraint` because current Liquibase docs expose that YAML field; Testcontainers verified the changesets apply.
- Converted the master changelog from `includeAll` to explicit ordered includes. This preserves the plan's master-wiring requirement without duplicating changesets.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Avoided duplicate changelog execution**
- **Found during:** Task 2
- **Issue:** The plan expected an existing explicit-include master file, but the repo used `includeAll`; appending explicit `014`-`017` includes would execute those changesets twice.
- **Fix:** Replaced `includeAll` with explicit ordered includes for all existing changesets plus `014`-`017`.
- **Files modified:** `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`
- **Verification:** `./gradlew.bat :backend:core:test --tests "*PostgresContainerTest*"` passed.
- **Committed in:** `8a1b866`

**2. [Rule 3 - Blocking] Used explicit SQL for billing check constraints**
- **Found during:** Task 1
- **Issue:** The initial plan used Liquibase's check-constraint change type, but the installed runtime rejected it during integration verification.
- **Fix:** Wrote check constraints as explicit `ALTER TABLE ... ADD CONSTRAINT ... CHECK (...)` SQL changes.
- **Files modified:** `014-credit-ledger-entry.yaml`, `015-credit-reservation.yaml`, `016-billing-topup-intent.yaml`
- **Verification:** `./gradlew.bat :backend:core:test --tests "*PostgresContainerTest*"` passed.
- **Committed in:** `fb73738`

**Total deviations:** 2 auto-fixed (2 blocking integration issues)  
**Impact on plan:** No scope expansion; both changes preserve the intended schema and migration wiring.

## Issues Encountered

- Another executor's Phase 02B model files were staged in the shared index during Task 2. Commits were made with path-limited `git commit --only` so this plan did not include unrelated files.

## Verification

- `./gradlew.bat :backend:core:test --tests "*PostgresContainerTest*"` - PASS
- `./gradlew.bat :backend:worker:dependencies --configuration runtimeClasspath` - PASS; output includes both `net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0` and `net.javacrumbs.shedlock:shedlock-spring:7.7.0`.
- Static checks for required files, changelog entries, ShedLock catalog aliases, worker dependency lines, and no `version` column using `bigint` in billing changesets - PASS.

## Known Stubs

None.

## Threat Flags

None - new schema/dependency surfaces are covered by the plan threat model.

## User Setup Required

None - no external service configuration required by this schema/dependency plan.

## Next Phase Readiness

Ready for `02B-02-domain-model` and later billing service/API/worker plans. Phase 2C should treat `018` as the next free billing-adjacent changeset floor because Phase 2B now owns `014` through `017`.

## Self-Check: PASSED

- Found all 7 implementation files on disk.
- Found task commits `fb73738` and `8a1b866` in git history.
- Stub scan over this plan's implementation files returned no matches.

---
*Phase: 02B-billing-prepaid-credits*
*Completed: 2026-05-06*

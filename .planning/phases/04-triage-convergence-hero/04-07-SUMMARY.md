---
phase: 04-triage-convergence-hero
plan: 07
subsystem: worker
tags: [triage, spring-modulith, shedlock, postgres, retention]

requires:
  - phase: 04-triage-convergence-hero
    provides: Modulith event registry schema, triage audit persistence, Gmail writer, and orchestrator audit transitions
provides:
  - ShedLock-coordinated triage event retry and completed-publication cleanup jobs
  - Bounded decided_at-based triage audit retention purge with transactional batch collaborator
  - 30-minute abandoned-threshold PENDING reaper using whitelisted markFailed transition
affects: [phase-04-triage, worker-scheduling, audit-retention, modulith-events]

tech-stack:
  added: []
  patterns:
    - Scheduler delegates transactional scan/mutate work to a separate batch component
    - Spring Modulith event maintenance uses local 2.0.7-SNAPSHOT publication APIs
    - Global scheduled-job logs use tenantId=system and count/id-only fields

key-files:
  created:
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeBatch.java
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeJob.java
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperBatch.java
  modified:
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventRetryJob.java
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventCleanupJob.java
    - backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageAuditPurgeJobContractTest.java

key-decisions:
  - "Spring Modulith 2.0.7-SNAPSHOT exposes a distinct FailedEventPublications bean; TriageEventRetryJob resubmits incomplete publications older than 5 minutes and failed publications with ResubmissionOptions.withMinAge(PT5M)."
  - "TriageEventCleanupJob counts outstanding publications with JdbcTemplate against the Liquibase-owned event_publication table because the public incomplete/failed publication views do not expose a count method on this classpath."
  - "TriagePendingReaperBatch ships the conservative minimal variant: abandoned PENDING rows become FAILED after the PT30M threshold; metadata verification to APPLIED is deferred."

patterns-established:
  - "Use tenantId=system for global worker maintenance logs that are not tenant-scoped."
  - "Guard configurable abandoned thresholds so they remain strictly greater than the saga retry lease."

requirements-completed: [TRG-04, TRG-06]

duration: 23min
completed: 2026-05-11
---

# Phase 04 Plan 07: Triage Scheduled Jobs Summary

**ShedLock-coordinated triage maintenance jobs for event retry, event cleanup, audit retention, and abandoned PENDING audit recovery**

## Performance

- **Duration:** 23 min
- **Started:** 2026-05-11T13:30:01Z
- **Completed:** 2026-05-11T13:52:43Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Added `TriageEventRetryJob` to resubmit both incomplete and failed Modulith publications older than 5 minutes.
- Added `TriageEventCleanupJob` to delete completed publications older than 7 days and log outstanding publication count.
- Added `TriageAuditPurgeJob` plus `TriageAuditPurgeBatch` to purge terminal audit rows by `decided_at` in bounded `LIMIT 1000` batches.
- Added `TriagePendingReaperJob` plus `TriagePendingReaperBatch` to flip abandoned `PENDING` audit rows to `FAILED` after the default `PT30M` threshold.
- Replaced the disabled purge reflection contract with an enabled Postgres-backed test proving aged `APPLIED` and aged `SHADOW_LOGGED` rows purge while fresh and `PENDING` rows remain.

## Task Commits

1. **Task 1: Triage event retry and cleanup jobs** - `75c133c` (feat)
2. **Task 2: Triage audit retention purge** - `f690f9f` (feat)
3. **Task 3: Triage pending reaper** - `1045d81` (feat)

## Files Created/Modified

- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventRetryJob.java` - Scheduled incomplete and failed event-publication retry.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventCleanupJob.java` - Scheduled completed-publication cleanup with outstanding-count observability.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeBatch.java` - Transactional `JdbcTemplate` bounded delete on `triage_audit.decided_at`.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeJob.java` - Daily ShedLock purge scheduler.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperBatch.java` - Transactional abandoned-PENDING reaper using `findStuckPendingForReaping` and `markFailed`.
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java` - Five-minute ShedLock reaper scheduler.
- `backend/worker/src/test/java/com/zeromail/worker/triage/TriageAuditPurgeJobContractTest.java` - Enabled Testcontainers purge contract.

## Verification

- `.\gradlew.bat :backend:worker:compileJava :backend:core:compileJava` - BUILD SUCCESSFUL.
- `.\gradlew.bat :backend:core:test --tests "*TriageAuditRepositoryBoundaryArchTest" --tests "*ApplicationModulesTest"` - BUILD SUCCESSFUL.
- `.\gradlew.bat :backend:worker:test --tests "*TriageAuditPurgeJobContractTest"` - BUILD SUCCESSFUL; worker context booted with scheduled jobs registered.
- `.\gradlew.bat :backend:api:test --tests "*ApplicationModulesTest"` - BUILD SUCCESSFUL; run because the actual Modulith verification test lives in `backend/api`.

## Decisions Made

- Used the local Spring Modulith API shape verified from Gradle cache: `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration)`, `FailedEventPublications.resubmit(ResubmissionOptions.defaults().withMinAge(...))`, and `CompletedEventPublications.deletePublicationsOlderThan(Duration)`.
- Counted outstanding event publications with SQL against `event_publication` instead of importing the internal `EventPublicationRepository`, keeping worker compile classpath clean.
- Shipped the minimal PENDING reaper (`FAILED`) and documented metadata verification to `APPLIED` as deferred because the plan marks it optional.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced internal Modulith repository import with JdbcTemplate count**
- **Found during:** Task 1
- **Issue:** `org.springframework.modulith.events.core.EventPublicationRepository` exists in the local jars but was not resolvable from worker source through the IDE/compile classpath as a clean public dependency.
- **Fix:** Counted outstanding rows directly from the Liquibase-owned `event_publication` table using `JdbcTemplate`.
- **Files modified:** `TriageEventCleanupJob.java`
- **Verification:** `:backend:worker:compileJava` passed; cleanup grep gate passed.
- **Committed in:** `75c133c`

**2. [Rule 2 - Missing Critical] Added threshold guard for pending reaper configuration**
- **Found during:** Task 3
- **Issue:** The plan requires the abandoned threshold to stay strictly greater than the saga retry lease; a future config typo could collapse the safety window.
- **Fix:** `TriagePendingReaperBatch` rejects thresholds less than or equal to `PT2M`.
- **Files modified:** `TriagePendingReaperBatch.java`
- **Verification:** Worker/core compile passed; Task 3 grep gate confirmed no `ofMinutes(2)` cutoff logic and default `PT30M` remains.
- **Committed in:** `1045d81`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both changes preserve the planned behavior and reduce runtime risk without expanding user-facing scope.

## Issues Encountered

- JetBrains MCP timed out during one reformat/problem-check pass; Gradle compile and targeted tests were used as the verification authority.
- The first purge contract run exposed a test fixture binding issue: PostgreSQL/JdbcTemplate needs `Timestamp` values instead of raw `Instant` parameters. Fixed before committing Task 2.

## Known Stubs

None. Stub-pattern scan only matched SLF4J `{}` placeholders and defensive null checks.

## Authentication Gates

None.

## Next Phase Readiness

Phase 04 Plan 08 can build on a worker where triage event retry, event cleanup, audit retention, and abandoned-PENDING recovery are scheduled and ShedLock-coordinated. Metadata verification to convert abandoned rows to `APPLIED` remains an optional future enhancement; the invariant that `PENDING` rows do not live forever is now enforced.

## Self-Check: PASSED

- Created/modified files listed above exist on disk.
- Task commits `75c133c`, `f690f9f`, and `1045d81` exist in git history.
- Plan-level verification commands passed.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*

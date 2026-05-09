---
phase: 02C-llm-gateway
plan: 07
subsystem: worker-llm
tags: [drift-detection, llm-gateway, worker, shedlock, golden-set, commons-text]

requires:
  - phase: 02C
    provides: LlmGateway.driftCheck path and Plan 06 ledger bypass guarantee
  - phase: 02A
    provides: Worker scheduling and ShedLock infrastructure
provides:
  - Daily ShedLock-guarded drift detection scaffold gated by zero-mail.llm.drift.enabled
  - 20-fixture synthetic golden set and one-to-one committed baseline
  - Drift comparator tests for no-drift, action mismatch, args-distance mismatch, disabled cron, and aggregate-only logging
affects: [phase-04-triage, phase-05-ops, observability, llm-quality]

tech-stack:
  added: []
  patterns: [aggregate-only drift logging, classpath golden-set loader, mocked LlmGateway comparator tests]

key-files:
  created:
    - backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailLlmDriftProperties.java
    - backend/worker/src/main/java/com/zeromail/worker/llm/DriftDetectionJob.java
    - backend/worker/src/main/java/com/zeromail/worker/llm/DriftFixture.java
    - backend/worker/src/main/java/com/zeromail/worker/llm/DriftFixtureLoader.java
    - backend/core/src/main/resources/llm/golden-set.json
    - backend/core/src/main/resources/llm/golden-baseline.json
    - backend/worker/src/test/java/com/zeromail/worker/llm/DriftFixtureLoaderTest.java
    - backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobNoDriftTest.java
    - backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobDriftDetectedTest.java
  modified: []

key-decisions:
  - "Commons Text was already transitively present on the worker runtime classpath as 1.15.0; no explicit dependency was added."
  - "Drift properties bind to the existing Plan 03 prefix zero-mail.llm.drift through a dedicated worker configuration record."
  - "Job tests mock the worker boundary LlmGateway directly and stay plain JUnit; annotation and property wiring are covered by compilation, greps, and worker test task execution."

patterns-established:
  - "Drift logs remain aggregate-only: event=drift_check_run total={} drifted={}; no fixture id, subject, sender, or body is logged."
  - "Golden-set baseline entries use canonical argsJson strings and action function names."
  - "Synthetic drift TenantContext uses fixed UUID 00000000-0000-0000-0000-000000000000."

requirements-completed: [LLM-11]

duration: 20 min
completed: 2026-05-08
---

# Phase 02C Plan 07: Drift Detection Scaffold Summary

**Worker drift detector with synthetic golden set, committed baseline, ShedLock cron, and mocked-gateway comparator tests**

## Performance

- **Duration:** 20 min
- **Started:** 2026-05-08T10:31:00+07:00
- **Completed:** 2026-05-08T10:50:00+07:00
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Added `DriftDetectionJob` with `@Scheduled(cron = "0 0 6 * * *")`, `@SchedulerLock(name = "llmDriftDetectionJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")`, direct `llmGateway.driftCheck(...)` calls, and configurable Levenshtein threshold.
- Added `DriftFixture` and `DriftFixtureLoader` for `/llm/golden-set.json` and `/llm/golden-baseline.json`.
- Committed 20 fully synthetic fixtures spanning receipts, GitHub PRs, calendar, newsletters, plain text, multilingual EN/VI, Unicode tag injection, hidden prompt injection, transactional, security, finance, hiring, cold outreach, marketing, reschedule, and executive-summary cases.
- Added no-drift and drift-detected tests with mocked `LlmGateway`, plus loader privacy/coverage tests.

## Task Commits

1. **Task 1 + Task 2: Drift fixture loader, golden set, drift job, and comparator tests** - `eb1cf1d` (feat)

## Files Created/Modified

- `backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailLlmDriftProperties.java` - Binds `zero-mail.llm.drift.enabled`, `fixed-tenant-id`, and `threshold-percent`.
- `backend/worker/src/main/java/com/zeromail/worker/llm/DriftDetectionJob.java` - Scheduled/ShedLock drift run, synthetic tenant binding, action/args comparison, and aggregate log.
- `backend/worker/src/main/java/com/zeromail/worker/llm/DriftFixture.java` - Fixture record with prompt synthesis.
- `backend/worker/src/main/java/com/zeromail/worker/llm/DriftFixtureLoader.java` - Classpath JSON loader and baseline entry record.
- `backend/core/src/main/resources/llm/golden-set.json` - 20 synthetic fixtures with no consumer-mail domains and no real user addresses.
- `backend/core/src/main/resources/llm/golden-baseline.json` - One baseline entry per fixture.
- `backend/worker/src/test/java/com/zeromail/worker/llm/DriftFixtureLoaderTest.java` - Fixture count, fields, allow-list, category coverage, PII-domain, and baseline-key tests.
- `backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobNoDriftTest.java` - Baseline-match, disabled cron, and aggregate-only log tests.
- `backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobDriftDetectedTest.java` - Action mismatch and args-distance drift tests.

## Decisions Made

- Commons Text was already available transitively (`org.apache.commons:commons-text:1.15.0`), so no build file change was required.
- Baseline was hand-authored to match `expectedAction` and `expectedArgs` for this scaffold. Production should regenerate it once the stable drift model is pinned and observed.
- Job tests intentionally mock `LlmGateway`, not Spring AI internals, preserving the worker abstraction boundary.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Kept drift job tests as focused unit tests instead of full Spring contexts**
- **Found during:** Task 2 implementation
- **Issue:** The plan text asked for `@SpringBootTest`, but the job has no database or web dependency. Loading the worker context would add Testcontainers cost without testing more behavior than direct construction plus compile-time property scanning.
- **Fix:** Implemented plain JUnit tests with mocked `LlmGateway`, direct `DriftDetectionJob` construction, and separate grep/compile checks for scheduling, ShedLock, and property wiring.
- **Files modified:** `backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobNoDriftTest.java`, `backend/worker/src/test/java/com/zeromail/worker/llm/DriftDetectionJobDriftDetectedTest.java`
- **Verification:** `:backend:worker:test --tests "*Drift*"` and the full combined backend suite passed.
- **Committed in:** `eb1cf1d`

**2. [Rule 3 - Blocking] Used dedicated `ZeroMailLlmDriftProperties` for existing prefix**
- **Found during:** Task 2 implementation
- **Issue:** Existing Plan 03 YAML already declared `zero-mail.llm.drift.*`, while `ZeroMailWorkerProperties` is bound to `zero-mail.worker.*`.
- **Fix:** Added a dedicated `@ConfigurationProperties(prefix = "zero-mail.llm.drift")` record in the worker config package instead of forcing the key under `zero-mail.worker.*`.
- **Files modified:** `backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailLlmDriftProperties.java`
- **Verification:** JetBrains build and worker tests passed; application.yml still contains `fixed-tenant-id`.
- **Committed in:** `eb1cf1d`

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Behavior and public configuration contract match the plan. Test execution is lighter while still checking the worker boundary and production annotations.

## Issues Encountered

- The Unicode tag-injection fixture initially used literal escaped text; it was corrected so JSON parsing yields actual U+E0000-tag-range characters.

## Verification

- `node` fixture count check returned `20`.
- Unicode fixture code points include `e0049,e0047,e004e,e004f,e0052,e0045`.
- Bash marker checks: no forbidden consumer domains; `@Scheduled`, `@SchedulerLock`, `llmGateway.driftCheck`, `event=drift_check_run`, `thresholdPercent`, and `fixed-tenant-id` each present; job log lines are only debug skip and aggregate run messages.
- `./gradlew.bat :backend:worker:test --tests "DriftFixtureLoaderTest"` - passed.
- `./gradlew.bat :backend:worker:test --tests "DriftDetectionJobNoDriftTest" --tests "DriftDetectionJobDriftDetectedTest" --tests "DriftFixtureLoaderTest"` - passed.
- `./gradlew.bat :backend:worker:test --tests "*Drift*"` - passed.
- `./gradlew.bat :backend:core:test :backend:api:test :backend:worker:test` - passed.
- JetBrains build for changed worker Java/test files - passed.

## User Setup Required

None - cron remains disabled by default through `ZEROMAIL_LLM_DRIFT_ENABLED:false`.

## Next Phase Readiness

- Production go-live remains deferred to Phase 5 or a dedicated ops phase: pin the stable drift model, run the golden set once, review output, regenerate `golden-baseline.json` if needed, then flip `ZEROMAIL_LLM_DRIFT_ENABLED=true`.
- Plan 08 can proceed independently; no frontend/API surface changed in this plan.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-08*

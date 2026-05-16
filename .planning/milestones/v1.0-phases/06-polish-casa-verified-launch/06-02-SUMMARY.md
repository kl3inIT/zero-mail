---
phase: 06-polish-casa-verified-launch
plan: 02
subsystem: launch load testing
tags: [k6, docker-compose, gradle, loadtest, postgres, redis, invariants]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: loadtest and e2e-stub launch profiles from 06-01
provides:
  - 50-tenant k6 Pub/Sub load script using UUID tenant ids
  - docker-compose loadtest stack for Postgres 17.6, Redis 7.2, API, and worker
  - deterministic loadtest tenant, allow-list, and Gmail connection SQL seeding
  - psql-based invariant verifier wired as :backend:api:loadtestVerify
  - operator runbook for pre-tag committed load-test evidence
affects: [06-04-release-ci, 06-05-rc-tag, launch-go-nogo, casa-verified-launch]

tech-stack:
  added: []
  patterns:
    - k6 constant-arrival-rate traffic against real Pub/Sub ingress
    - compose down-v cleanup before launch load stack startup
    - psql shell-out verifier for post-load invariant assertions

key-files:
  created:
    - loadtest/compose.loadtest.yml
    - loadtest/scripts/golden-path.js
    - loadtest/scripts/seed-tenants.sql
    - loadtest/scripts/wait-for-worker-drain.sh
    - loadtest/scripts/loadtest-verify.sh
    - loadtest/README.md
    - loadtest/.gitignore
  modified:
    - backend/api/build.gradle.kts
    - backend/worker/build.gradle.kts
    - .gitignore

key-decisions:
  - "Use hard-coded deterministic UUIDs 00000000-0000-4000-8000-1de57e570001 through ...0050 for reproducible tenant seeding."
  - "Seed gmail_connections in SQL as critical loadtest setup so PubSubIngestionService can resolve emailAddress to tenant_id."
  - "Configure BootBuildImage through the local Spring Boot 4.0.6 runtime getImageName Property because the generated Kotlin DSL imageName.set accessor is stale in this checkout."

patterns-established:
  - "Loadtest evidence is generated locally before the rc tag, then re-run by release CI for record."
  - "Invariant verification waits for pubsub_delivery PENDING rows to drain before querying triage_audit and credit_ledger_entry."

requirements-completed: [SPEC-06-R2]

duration: 21 min
completed: 2026-05-15
---

# Phase 06 Plan 02: Docker Compose Load Harness Summary

**50-tenant k6 load harness with UUID seeding, compose runtime, queue-drain wait, and psql invariant checks.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-05-14T19:57:33Z
- **Completed:** 2026-05-14T20:19:12Z
- **Tasks:** 5 completed
- **Files modified:** 10

## Accomplishments

- Added `loadtest/scripts/golden-path.js`, a k6 constant-arrival-rate script for 500 Pub/Sub pushes per minute across 50 UUID tenants.
- Added `loadtest/compose.loadtest.yml` with Postgres, Redis, API, and worker services; both API and worker receive `REFRESH_TOKEN_KEY_BASE64`.
- Added deterministic SQL seeding for `tenants`, `loadtest_tenant`, and `gmail_connections`.
- Added `wait-for-worker-drain.sh` and `loadtest-verify.sh`, then wired `:backend:api:loadtestVerify` as a Gradle `Exec` task.
- Documented the pre-tag local run and committed evidence flow in `loadtest/README.md`.

## Task Commits

1. **Task 1: Author k6 golden-path script** - `87b3a60` (feat)
2. **Task 2: Compose stack and tenant seed SQL** - `ad3fb96` (feat)
3. **Task 3: Wire bootBuildImage OCI image names** - `674ca49` (feat)
4. **Task 4: Add drain and invariant verifier scripts** - `40010de` (feat)
5. **Task 5: Document loadtest runbook and ignores** - `dd0a6db` (docs)

Plan metadata is recorded in the final docs commit for this plan.

## Files Created/Modified

- `loadtest/scripts/golden-path.js` - k6 script reading `LOADTEST_TENANT_UUIDS`, posting Pub/Sub envelopes to `/internal/pubsub/gmail`, and writing `loadtest/run/summary.json`.
- `loadtest/compose.loadtest.yml` - ephemeral load stack using `zeromail-api:loadtest` and `zeromail-worker:loadtest`.
- `loadtest/scripts/seed-tenants.sql` - idempotent 50-UUID seed for `tenants`, `loadtest_tenant`, and `gmail_connections`.
- `loadtest/scripts/wait-for-worker-drain.sh` - polls `pubsub_delivery` for claimable `PENDING` rows until zero or 120s timeout.
- `loadtest/scripts/loadtest-verify.sh` - psql shell-out verifier for cross-tenant audit, ledger finalization/balance, and log-bleed invariants.
- `loadtest/README.md` - operator runbook with MED-4 cleanup, seeding, k6, drain, log capture, verifier, and pre-tag evidence commit.
- `loadtest/.gitignore` and `.gitignore` - ignore `loadtest/run/` runtime artifacts.
- `backend/api/build.gradle.kts` - adds `loadtestVerify` and API image-name configuration.
- `backend/worker/build.gradle.kts` - adds worker image-name configuration.

## Decisions Made

- **OQ-1:** resolved with Spring Boot `bootBuildImage`, no Dockerfile. Context7 Spring Boot docs confirm `bootBuildImage` creates OCI images and supports `--imageName`; the local runtime task property is configured for default loadtest tags.
- **Codex HIGH-6:** used a hard-coded deterministic UUID list from `00000000-0000-4000-8000-1de57e570001` through `00000000-0000-4000-8000-1de57e570050`.
- **Codex HIGH-7:** implemented ledger invariant as two checks: no reservation `ref_id` has more than one SETTLE, more than one RELEASE, or both; every loadtest tenant has non-negative `SUM(amount_credits)`.
- **Codex HIGH-8:** implemented psql shell-out rather than Gradle buildscript JDBC.
- **Codex HIGH-9:** documented the local-run-before-tag flow; the actual `06-LOAD-TEST-RESULT.md` evidence file was not produced in this environment because `k6` and `psql` are missing on PATH.

## Required Environment Variables

Exact fail-fast API variables from `backend/api/src/main/resources/application.yml`:
`PUBSUB_PUSH_AUDIENCE_URL`, `PUBSUB_SA_PRINCIPAL_EMAIL`, `REFRESH_TOKEN_KEY_BASE64`, `ZEROMAIL_BILLING_BANK_CODE`, `ZEROMAIL_BILLING_BANK_NAME`, `ZEROMAIL_BILLING_ACCOUNT_NUMBER`, `ZEROMAIL_BILLING_ACCOUNT_NAME`, `ZEROMAIL_LLM_PLATFORM_API_KEY`.

API also has a bare required placeholder without a `:?` message: `SEPAY_WEBHOOK_API_KEY`.

Exact fail-fast worker variables from `backend/worker/src/main/resources/application.yml`:
`GOOGLE_PUBSUB_TOPIC_NAME`, `REFRESH_TOKEN_KEY_BASE64`, `ZEROMAIL_BILLING_BANK_CODE`, `ZEROMAIL_BILLING_BANK_NAME`, `ZEROMAIL_BILLING_ACCOUNT_NUMBER`, `ZEROMAIL_BILLING_ACCOUNT_NAME`, `ZEROMAIL_LLM_PLATFORM_API_KEY`, `RESEND_API_KEY`.

Worker also has a bare required placeholder without a `:?` message: `SEPAY_WEBHOOK_API_KEY`.

## Verification Results

- `node -e "...golden-path validator..."` - PASS, all locked k6 constants and the no-slug guard passed.
- `docker compose -f loadtest/compose.loadtest.yml config > $null` - PASS. Docker Compose warned that `REFRESH_TOKEN_KEY_BASE64` was unset outside a real run, which is expected.
- `.\gradlew.bat --no-daemon :backend:api:tasks --group="verification"` - PASS, `loadtestVerify` listed.
- `.\gradlew.bat --no-daemon :backend:api:tasks --group="build"` - PASS, `bootBuildImage` listed.
- `.\gradlew.bat --no-daemon :backend:worker:tasks --group="build"` - PASS, `bootBuildImage` listed.
- Git Bash executable/static script checks - PASS: both shell scripts are executable; `wait-for-worker-drain.sh` references `pubsub_delivery` and not `processing_job`; `loadtest-verify.sh` references `loadtest_tenant` and not `LIKE 'loadtest-tenant-%'`.
- `.\gradlew.bat --no-daemon :backend:api:check :backend:worker:check` - PASS, `BUILD SUCCESSFUL in 2m 14s`.

## Functional Run Status

Full functional run was not performed locally.

- Docker daemon: available (`docker version` server `29.4.3`).
- k6: blocked - `k6` command missing on PATH.
- psql: blocked - `psql` command missing on PATH.

Because the verifier depends on psql and the workload depends on k6, no local 30s smoke run or 10-minute run was possible. No SETTLE/RELEASE counts were observed from a live workload, no 120s drain duration was observed, and no `06-LOAD-TEST-RESULT.md` was generated or committed in this plan. Plan 06-04/06-05 own the CI and pre-tag evidence run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Seeded `gmail_connections` for Pub/Sub tenant lookup**
- **Found during:** Task 2 (compose stack and tenant seed SQL)
- **Issue:** The plan made Gmail connection seeding optional, but `PubSubIngestionService` drops pushes when `gmail_connections.google_email` does not resolve to a CONNECTED tenant.
- **Fix:** Added idempotent SQL that maps every seeded tenant UUID to `{tenantUuid}@loadtest.invalid` with `status='CONNECTED'`.
- **Files modified:** `loadtest/scripts/seed-tenants.sql`
- **Verification:** Read `PubSubIngestionService`; static seed checks passed; `seed-tenants.sql` now inserts `gmail_connections`.
- **Committed in:** `ad3fb96`

**2. [Rule 3 - Blocking] Worked around stale BootBuildImage Kotlin DSL accessor**
- **Found during:** Task 3 (bootBuildImage wiring)
- **Issue:** `imageName.set(...)`, `getImageName().set(...)`, and `getImageName().value(...)` did not compile in this checkout. The generated Kotlin DSL accessor attempted the removed `setImageName(String)` method, while the local Spring Boot 4.0.6 runtime class exposes `getImageName(): Property<String>`.
- **Fix:** Configured the runtime `getImageName` `Property` through reflection inside the typed `BootBuildImage` task block.
- **Files modified:** `backend/api/build.gradle.kts`, `backend/worker/build.gradle.kts`
- **Verification:** API and worker `bootBuildImage` tasks are discoverable; `:backend:api:check :backend:worker:check` passed.
- **Committed in:** `674ca49`

---

**Total deviations:** 2 auto-fixed (1 missing-critical, 1 blocking).
**Impact on plan:** Both changes were required for the load harness to be executable against the current codebase. No product runtime behavior was added.

## Issues Encountered

- Full load execution is blocked locally by missing `k6` and `psql` commands. Static, compose, and Gradle checks were run instead.
- The plan's future-reference to `release.yml` could not be verified in this plan because release CI is owned by Plan 06-04.

## Known Stubs

None. The `loadtest` literal environment values are synthetic load harness values, not UI or production data-source stubs.

## Threat Flags

None. The new surfaces are covered by the plan threat model: compose ports are local, run logs are ignored, and synthetic tenant rows are allow-listed.

## User Setup Required

Install `k6` and `psql` on PATH before running the local pre-tag load-test evidence flow.

## Next Phase Readiness

Ready for Plan 06-03 and Plan 06-04. Plan 06-04 should install k6 and psql in release CI before invoking the same runbook sequence, and Plan 06-05 should produce and commit the real `06-LOAD-TEST-RESULT.md` before cutting `v1.0.0-rc1`.

## Self-Check: PASSED

- Summary exists at `.planning/phases/06-polish-casa-verified-launch/06-02-SUMMARY.md`.
- Key created and modified files exist on disk.
- Task commits resolve in git: `87b3a60`, `ad3fb96`, `674ca49`, `40010de`, `dd0a6db`.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*

---
phase: 05C-user-surface-analytics-daily-digest
plan: 02
subsystem: analytics-api
tags: [spring-mvc, jdbc, postgres, modulith, analytics]

requires:
  - phase: 05C-01
    provides: sender_email, tenant time zone, notification schema, and analytics indexes
provides:
  - core.analytics Modulith module with AnalyticsSummaryQueryService
  - GET /api/analytics/summary endpoint with 7d/30d/90d windows
  - analytics summary response DTOs for frontend OpenAPI generation
  - tenant-scoped analytics fixture tests, content-ban guard, and log privacy sweep
affects: [05C-03, 05C-04, analytics, digest, frontend-api-codegen]

tech-stack:
  added: []
  patterns:
    - explicit tenantId read-side service API
    - closed-open TimeWindow for shared web and worker analytics
    - nested api dto package exposed via Modulith NamedInterface

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java
    - backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeWindow.java
    - backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java
    - backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java
    - backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java
  modified: []

key-decisions:
  - "core.analytics is a new Modulith module, not an inline triage projection."
  - "AnalyticsSummaryQueryService takes UUID tenantId and TimeWindow; it never reads TenantContext."
  - "Q1 and Q3 filter INBOX observed messages; Q2 and Q4 stay on triage_audit only."
  - "Analytics window validation is controller-local: unknown nonblank ids map to 400."

patterns-established:
  - "Use TimeWindow.between/endingAt for all analytics consumers so digest can pass a fixed send-moment window."
  - "Expose any new nested api.dto.<domain> package with @NamedInterface before controllers import it."

requirements-completed:
  - ANL-01
  - ANL-02

duration: 35min
completed: 2026-05-13
---

# Phase 05C Plan 02: Analytics Summary API Summary

**Tenant-scoped analytics read model and `/api/analytics/summary` endpoint with closed-window aggregation**

## Performance

- **Duration:** 35 min
- **Started:** 2026-05-13T22:38:00+07:00
- **Completed:** 2026-05-13T23:13:16+07:00
- **Tasks:** 2
- **Files modified:** 16

## Accomplishments

- Added `core.analytics` with `TimeWindow`, `TimeSavedWeights`, projection records, and `AnalyticsSummaryQueryService`.
- Implemented 4 parameterized JDBC aggregations over `mail_message_observed` and `triage_audit`.
- Added `GET /api/analytics/summary?window=7d|30d|90d`, including default/blank handling and 400s for unknown values.
- Added tests for tenant isolation, INBOX filtering, closed-open time windows, top-sender/rule-hit ordering, log privacy, and OpenAPI registration.

## Task Commits

1. **Task 1: AnalyticsSummaryQueryService + tests** - `761a2a4` (`feat`)
2. **Task 2: AnalyticsController + DTOs + API contract test** - `10f14ec` (`feat`)

**Plan metadata:** this summary commit.

## Final SQL

Q1 observed volume:

```sql
SELECT count(*)
FROM mail_message_observed
WHERE tenant_id = ?
  AND observed_at >= ?
  AND observed_at < ?
  AND 'INBOX' = ANY(label_ids)
```

Q1 applied volume:

```sql
SELECT count(*)
FROM triage_audit
WHERE tenant_id = ?
  AND applied_at >= ?
  AND applied_at < ?
  AND reverted_at IS NULL
```

Q2 time saved:

```sql
SELECT action_type, count(*)
FROM triage_audit
WHERE tenant_id = ?
  AND applied_at >= ?
  AND applied_at < ?
  AND reverted_at IS NULL
GROUP BY action_type
```

Q3 top senders:

```sql
SELECT sender_email, count(*) AS c
FROM mail_message_observed
WHERE tenant_id = ?
  AND observed_at >= ?
  AND observed_at < ?
  AND sender_email IS NOT NULL
  AND 'INBOX' = ANY(label_ids)
GROUP BY sender_email
ORDER BY c DESC, sender_email ASC
LIMIT 3
```

Q4 rule hits:

```sql
SELECT rule_name_snapshot,
       count(*) AS decisions,
       count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL) AS applied,
       count(*) FILTER (WHERE reverted_at IS NOT NULL) AS reverted
FROM triage_audit
WHERE tenant_id = ?
  AND decided_at >= ?
  AND decided_at < ?
GROUP BY rule_name_snapshot
ORDER BY decisions DESC, rule_name_snapshot ASC
```

## Files Created

- `backend/core/src/main/java/com/zeromail/core/analytics/package-info.java` - Modulith module marker.
- `backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeWindow.java` - closed-open analytics interval value.
- `backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeSavedWeights.java` - label/archive/save-draft second constants.
- `backend/core/src/main/java/com/zeromail/core/analytics/projection/*` - summary projection records and JDBC query service.
- `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java` - thin HTTP endpoint.
- `backend/api/src/main/java/com/zeromail/api/dto/analytics/*` - API window enum and response records.
- `backend/core/src/test/java/com/zeromail/core/analytics/*` - core query, weight, and privacy tests.
- `backend/core/src/test/java/com/zeromail/core/arch/AnalyticsRepositoryContentBanTest.java` - content-surface guard.
- `backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java` - API contract and OpenAPI path test.

## Decisions Made

- `core.analytics` is a separate module so Plan 03 can reuse the same service from digest composition.
- `AnalyticsSummaryQueryService.summarize(UUID tenantId, TimeWindow window)` is explicit and worker-safe; only the controller reads `TenantContext`.
- `?window=` and whitespace-only window values default to `7d`; unknown nonblank values return 400 via a controller-local `NoSuchElementException` handler.
- OpenAPI registration is tested through `/v3/api-docs` in `AnalyticsControllerContractTest` rather than by generating and mutating `apps/web/openapi/openapi.json` during Plan 02.

## Index Verification

- Liquibase changeset `036-analytics-supporting-indexes.yaml` provides the planned btree indexes for `(tenant_id, observed_at)`, `(tenant_id, sender_email, observed_at) WHERE sender_email IS NOT NULL`, and `(tenant_id, rule_name_snapshot, decided_at)`.
- `./gradlew.bat :backend:core:check :backend:api:check` validated Liquibase schema application and Hibernate validation against the new columns.
- No standalone `EXPLAIN ANALYZE` output was committed; the test fixture is intentionally tiny and would not reliably prove production planner choices without forcing planner settings.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Exposed nested analytics DTO package to Modulith**
- **Found during:** Task 2 (`AnalyticsController`)
- **Issue:** IntelliJ Modulith inspection reported `controllers` importing non-exposed `api.dto.analytics` types.
- **Fix:** Added `backend/api/src/main/java/com/zeromail/api/dto/analytics/package-info.java` with `@NamedInterface("analytics")`, matching existing DTO domain packages.
- **Verification:** `AnalyticsController.java` file problems returned 0 errors; `:backend:api:test --tests "AnalyticsControllerContractTest"` passed.
- **Committed in:** `10f14ec`

---

**Total deviations:** 1 auto-fixed (blocking module exposure).  
**Impact:** Required for existing Modulith boundaries; no behavior change or scope creep.

## Issues Encountered

- IntelliJ still reports unresolved `sender_email` inside core analytics SQL strings when inspecting `AnalyticsSummaryQueryService`; this is the same stale IDE DB metadata issue recorded in 05C-01. Gradle tests, Liquibase, and Hibernate validation pass against the actual schema.
- The whitespace-window API test initially double-encoded `%20%20`; the test now uses `UriBuilder.queryParam("window", "  ")` so Spring receives actual whitespace and defaults it to `7d`.

## Verification

- `./gradlew.bat :backend:core:test --tests "TimeSavedWeightsTest" --tests "AnalyticsSummaryQueryServiceTest" --tests "AnalyticsRepositoryContentBanTest" --tests "AnalyticsPrivacySweepTest"` - PASS
- `./gradlew.bat :backend:api:test --tests "AnalyticsControllerContractTest"` - PASS
- `./gradlew.bat :backend:core:check :backend:api:check` - PASS
- JetBrains file problems on new API files - 0 errors
- JetBrains file problems on core test files - 0 errors
- JetBrains file problems on `AnalyticsSummaryQueryService` - only stale SQL-column metadata for `sender_email`

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 03 can consume `AnalyticsSummaryQueryService.summarize(tenantId, TimeWindow.between(...))` directly for daily digest composition. Plan 04 can generate a typed client from the API docs and render the analytics panels from the stable response shape.

---
*Phase: 05C-user-surface-analytics-daily-digest*
*Completed: 2026-05-13*

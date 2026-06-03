---
phase: 08-admin-console-operator-tooling
plan: 8C
subsystem: admin-tenant-operations
tags: [spring-boot, spring-modulith, liquibase, postgres, openapi-fetch, tanstack-router, tanstack-query, playwright]
requires:
  - phase: 08-8A
    provides: admin auth chain, AdminContext, admin audit/read events, apps/admin shell, admin OpenAPI group
provides:
  - Metadata-only admin tenant list and five-tab tenant inspection API
  - AdminTenantAccess audit-before-read bridge for tenant-scoped inspection
  - Pause, disconnect, and delete tenant operations with reason capture
  - Admin response body-ban failsafe and shared regex parity with ArchUnit
  - Generated admin OpenAPI schema consumed by apps/admin without axios
  - apps/admin tenant list/detail routes with confirm-twice destructive actions and Playwright coverage
affects: [08D-curated-catalog, 08E-queue-health, 08F-spend-dashboard, 09-user-settings-ui]
tech-stack:
  added: [AdminResponseBodyBanFilter, TenantDeletionRegistry, apps/admin tenant feature hooks]
  patterns:
    - Admin tenant APIs derive frontend types from admin-schema.d.ts and use openapi-fetch typed GET/POST calls
    - Tenant detail tabs use TanStack Router validated ?tab= search state plus lazy TanStack Query fetches
    - Body-ban runtime filter and ArchUnit scan share AdminBodyBanRegex.FORBIDDEN_FIELD_NAME
key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java
    - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantInspectionService.java
    - backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionRegistry.java
    - backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java
    - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java
    - apps/admin/src/features/tenants/tenants-api.ts
    - apps/admin/src/routes/_authenticated/tenants.tsx
    - apps/admin/src/routes/_authenticated/tenants.$tenantId.tsx
    - apps/admin/e2e/tenants.spec.ts
  modified:
    - apps/admin/src/lib/api/admin-schema.d.ts
    - apps/admin/src/components/AdminLayout.tsx
    - backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java
key-decisions:
  - "No axios added: apps/admin tenant calls use the existing openapi-fetch client generated from /v3/api-docs/admin."
  - "Tenant route files follow D-24 TanStack Router convention: _authenticated/tenants.tsx plus nested _authenticated/tenants.$tenantId.tsx."
  - "Postgres Docker/Testcontainers references were moved to postgres:18.4 after the user upgraded the project baseline."
patterns-established:
  - "Admin DTO packages that controllers consume must declare @NamedInterface; 8C adds admin.tenant and backfills admin.mkey."
  - "Tenant destructive UI maps 204 backend responses to ConfirmTwiceDialog auditId='recorded' until action endpoints return concrete audit IDs."
  - "Parent list route disables its TanStack Query fetch when rendering the nested tenant detail route to avoid extra TENANT_LIST read events."
requirements-completed:
  - OPS-TENANT-01
  - OPS-TENANT-02
  - OPS-TENANT-03
  - OPS-TENANT-04
  - OPS-TENANT-05
duration: "multi-session continuation completed 2026-05-20T11:25:00+07:00"
completed: 2026-05-20
---

# Phase 08 Plan 8C: Tenant Inspection Summary

**Metadata-only tenant inspection with audited tenant reads, body-leak failsafes, destructive admin actions, generated OpenAPI types, and apps/admin tenant routes.**

## Performance

- **Duration:** Multi-session execution; inline continuation completed at 2026-05-20T11:25:00+07:00.
- **Tasks:** 3/3 plan tasks covered.
- **Files modified:** 71 source, test, migration, generated schema, and admin UI files.

## Accomplishments

- Added `core.admin.tenant` projections/services for tenant list, overview, health, billing, spend, activity, deletion preview, pause, disconnect, and delete.
- Added `/api/admin/tenants/**` controller/DTOs and regenerated `apps/admin/src/lib/api/admin-schema.d.ts` from a live Spring API backed by Docker Postgres `18.4`.
- Added `AdminResponseBodyBanFilter`, shared `AdminBodyBanRegex`, system actor migration, and ArchUnit/test coverage for forbidden response body-shaped admin output.
- Added apps/admin tenant API hooks, `/tenants` list route, `/tenants/:tenantId?tab=...` detail route, Tenants sidebar nav, confirm-twice destructive flows, deletion preview display, disabled activity detail tooltip, and Playwright coverage.
- Updated Postgres Docker/Testcontainers references from `17.6` to `18.4` after the baseline upgrade.

## Task Commits

1. **Task 8C production:** `986fa62e` - `feat(08-8C): add admin tenant inspection`

## Verification

- Context7 checked `openapi-typescript/openapi-fetch` typed `GET`/`POST` usage and TanStack Router validated search parameter patterns.
- `pnpm --filter @zeromail/admin build` passed.
- `pnpm --filter @zeromail/admin test:unit` passed.
- `pnpm --filter @zeromail/admin e2e -- e2e/tenants.spec.ts --reporter=list` passed.
- `./gradlew :backend:core:test :backend:api:test --tests "*AdminTenant*" --tests "*AdminResponseBodyBan*" --tests "*AdminPathBodyBan*"` passed.
- `./gradlew :backend:api:test --tests "*ZeroMailApiApplicationModulesTest*"` passed after exposing admin DTO named interfaces and moving the confirm-email mismatch exception out of the controller.
- JetBrains file problem checks returned no errors for the new tenant routes/API file, `AdminTenantController`, `AdminResponseBodyBanFilter`, `AdminErrorAdvice`, and `TenantInspectionService`.
- Playwright MCP opened `http://localhost:5174/tenants`, navigated to tenant detail with mocked admin APIs, and verified the clean `/tenants/{tenantId}?tab=overview` URL with no console errors.
- `rg` scans found no remaining `postgres:17.6`, `PostgreSQL 17`, or `Postgres 17` references in the updated docs/testcontainer/loadtest paths.
- `rg` scans found no forbidden body/prompt/content field names in tenant projection or DTO packages, and no `axios` usage in apps/admin.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Correctness] Used generated OpenAPI schema instead of adding axios**
- **Found during:** Frontend continuation after schema generation.
- **Issue:** The user asked whether axios was needed; project convention requires generated OpenAPI types and the existing `openapi-fetch` client.
- **Fix:** Implemented `features/tenants/tenants-api.ts` with `api.GET` / `api.POST` and generated schema-derived types.
- **Files modified:** `apps/admin/src/features/tenants/*`, `apps/admin/src/routes/_authenticated/tenants*.tsx`
- **Verification:** Admin build, unit test, tenants Playwright spec, and Context7 docs check passed.
- **Committed in:** `986fa62e`

**2. [Rule 1 - Correctness] Reconciled TanStack nested route behavior**
- **Found during:** Playwright e2e.
- **Issue:** `tenants.$tenantId.tsx` is a nested child of `tenants.tsx`; without an outlet the detail route URL changed but the list remained rendered.
- **Fix:** Parent route renders `<Outlet />` for non-list paths and disables the list query there to avoid extra list read events.
- **Files modified:** `apps/admin/src/routes/_authenticated/tenants.tsx`
- **Verification:** Tenants Playwright spec and MCP browser check passed.
- **Committed in:** `986fa62e`

**3. [Rule 1 - Architecture] Exposed admin DTO packages and moved controller-owned exception**
- **Found during:** `ZeroMailApiApplicationModulesTest`.
- **Issue:** New admin tenant DTOs were non-exposed Modulith internals, and `AdminErrorAdvice` depended on `AdminTenantController.TenantConfirmEmailMismatchException`.
- **Fix:** Added `@NamedInterface("admin.tenant")`, backfilled `@NamedInterface("admin.mkey")`, and moved the mismatch exception into `com.zeromail.api.error`.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/dto/admin/{tenant,mkey}/package-info.java`, `AdminTenantController.java`, `AdminErrorAdvice.java`, `TenantConfirmEmailMismatchException.java`
- **Verification:** `./gradlew :backend:api:test --tests "*ZeroMailApiApplicationModulesTest*"` passed.
- **Committed in:** `986fa62e`

**Total deviations:** 3 auto-fixed issues. **Impact:** All fixes were required to keep 8C aligned with project frontend/API and Modulith conventions; no new dependencies were added.

## Issues Encountered

- `TenantInspectionService` uses `NamedParameterJdbcTemplate` rather than the plan addendum's Spring Data JDBC repository projections. The current project does not include a Spring Data JDBC starter, and existing read-side code already uses JDBC templates, so this kept the change dependency-free while preserving metadata-only SQL projection behavior.
- The cycle-3 `Phase8E2ESmokeTest` harness referenced by the plan is not present in the 8A codebase. 8C coverage is instead provided by admin-specific backend tests, `ZeroMailApiApplicationModulesTest`, generated OpenAPI schema verification, and apps/admin Playwright tenant flows.

## User Setup Required

None for local code execution. The admin dev server is running at `http://localhost:5174` for local inspection.

## Next Phase Readiness

8D/8E/8F can reuse the typed admin client pattern, `@NamedInterface` DTO exposure rule, and nested-route query suppression pattern. The remaining Phase 8 plans should continue using `postgres:18.4` for Docker/Testcontainers and regenerate `apps/admin/src/lib/api/admin-schema.d.ts` after adding their admin endpoints.

---
*Phase: 08-admin-console-operator-tooling*
*Completed: 2026-05-20*

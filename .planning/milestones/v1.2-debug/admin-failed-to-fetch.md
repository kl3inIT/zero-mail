---
status: resolved
trigger: "Admin app cannot be accessed; UI shows Failed to fetch."
created: "2026-05-22"
updated: "2026-05-22"
---

# Debug Session: admin-failed-to-fetch

## Symptoms

- Expected behavior: admin app loads and can call the backend API.
- Actual behavior: admin UI shows "Failed to fetch".
- Error messages: "Failed to fetch".
- Timeline: after merging main into the current branch.
- Reproduction: open/admin app locally or deployed, observe fetch failure.

## Current Focus

- hypothesis: admin frontend API base, backend availability, CORS, or auth endpoint changed during merge.
- test: inspect admin API client/env config and reproduce request from local browser/runtime.
- expecting: identify the exact URL/request failing.
- next_action: inspect admin package, API client, env, and current local ports.

## Evidence

- 2026-05-22: admin Vite server was listening on `http://localhost:5174`; backend API was not listening on `8080`, so `/api/admin/me` initially failed with `net::ERR_CONNECTION_REFUSED`.
- 2026-05-22: after starting API, Playwright showed CORS failure for `http://localhost:5174` because API default CORS allowed only `http://localhost:3000`.
- 2026-05-22: `.env.example` already included `http://localhost:5174`; `backend/api/src/main/resources/application.yml` and `ZeroMailApiProperties` fallback did not.
- 2026-05-22: after fixing defaults and restarting API, `GET /api/admin/me` from origin `http://localhost:5174` returned `401` with `Access-Control-Allow-Origin: http://localhost:5174`; admin rendered the login page.
- 2026-05-22: `CorsIntegrationTest` was initially blocked before assertions by an unrelated schema validation failure: `processing_job.version` missing in the test DB schema.
- 2026-05-22: merge resolution had kept the `main` changelog tail but dropped Phase 8 changelog includes (`081-processing-job-tenant-scope` through `086-triage-audit-source`) from `db.changelog-master.yaml`; re-added them after the `main` tail so schema validation matches the current branch code.

## Eliminated

- Admin frontend bundle load failure: app assets load and page title is `Zero Mail Admin`.
- API route absence: `/api/admin/me` exists and returns expected unauthenticated `401` when backend is running.

## Resolution

- root_cause: admin dev origin `http://localhost:5174` was missing from API default CORS origins; additionally, the backend API was not running when the first reproduction was captured.
- fix: added `http://localhost:5174` to API CORS defaults in `application.yml` and `ZeroMailApiProperties`, with a pure unit test for the fallback.
- verification: JetBrains build/diagnostics passed; `ZeroMailApiPropertiesTest` and `CorsIntegrationTest` passed; Playwright verified admin renders `/login` and browser-context fetch to `/api/admin/me` returns CORS-visible `401` instead of throwing `TypeError: Failed to fetch`.
- files_changed: `backend/api/src/main/resources/application.yml`, `backend/api/src/main/java/com/zeromail/api/config/ZeroMailApiProperties.java`, `backend/api/src/test/java/com/zeromail/api/config/ZeroMailApiPropertiesTest.java`, `backend/api/src/test/java/com/zeromail/api/security/CorsIntegrationTest.java`, `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`.

---
phase: 08-admin-console-operator-tooling
plan: 8A
subsystem: admin-auth-ui-infra
tags: [spring-security, webauthn, spring-modulith, liquibase, vite, react, tanstack-router, docker-compose, nginx-proxy-manager, 9router]
requires: []
provides:
  - WebAuthn-backed admin auth foundation with isolated admin/user security chains
  - Admin user, audit event, and read event persistence with append-only audit chain
  - Admin audit and role-grant API scaffolding plus OpenAPI group split
  - Standalone apps/admin Vite React SPA with enrollment, login, dashboard, audit, and role-grant routes
  - Docker Compose operator infrastructure for 9Router and Nginx Proxy Manager
  - v1.2 deploy, backup, rollback, lost-passkey, bootstrap, and load-probe runbooks
affects: [08B-master-keys, 08C-tenant-inspection, 08D-curated-catalog, 08E-queue-health, 08F-spend-dashboard, 09-user-settings-ui]
tech-stack:
  added: [Spring Security WebAuthn, SimpleWebAuthn browser, TanStack Router, TanStack Query, TanStack Form, Vite, nginx-proxy-manager, 9router]
  patterns:
    - AdminContext/TenantContext mutex via ScopedValue
    - HMAC chained admin audit rows ordered by chain_index
    - apps/admin separated from apps/web with import guards
    - NPM admin UI bound to localhost for SSH-tunneled access
key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml
    - backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml
    - backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml
    - apps/admin/package.json
    - apps/admin/src/components/AdminModeBanner.tsx
    - apps/admin/src/components/ConfirmTwiceDialog.tsx
    - docs/ops/admin-interface-freeze.md
    - docs/ops/v1.2-deploy.md
    - docs/ops/admin-load-probe.md
    - docs/ops/admin-shared-file-ownership.md
  modified:
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - docker-compose.yml
    - apps/web/eslint.config.mjs
    - turbo.json
    - pnpm-lock.yaml
key-decisions:
  - "/enroll remains SPA-only; backend enrollment token validation lives at POST /api/admin/enrollment/session."
  - "NPM port 81 is bound to 127.0.0.1 for SSH-tunneled admin access rather than public 81 exposure."
  - "Task 8A-08 human-verify checkpoint was auto-approved because workflow.auto_advance=true and it was not a package-legitimacy gate."
patterns-established:
  - "Admin API DTO packages that cross Modulith boundaries must expose named interfaces."
  - "Admin SPA shadcn primitives must be Vite-safe; copied Next-specific primitives need adaptation before use."
  - "Phase 8 shared files require explicit ownership rows before downstream plans edit them."
requirements-completed:
  - OPS-INFRA-01
  - OPS-INFRA-02
  - OPS-INFRA-03
  - ADMIN-01
  - ADMIN-02
  - ADMIN-03
  - ADMIN-04
  - ADMIN-05
  - ADMIN-06
  - ADMIN-07
  - ADMIN-08
  - ADMIN-09
  - ADMIN-10
  - ARCH-08
  - ARCH-09
  - ARCH-10
  - ARCH-12
duration: "multi-session; continuation completed 2026-05-19T20:53:15Z"
completed: 2026-05-19
---

# Phase 08 Plan 8A: Admin Console Foundation Summary

**WebAuthn admin auth, tamper-evident audit chain, standalone Vite admin console, and VPS operator routing/runbooks for Phase 8.**

## Performance

- **Duration:** Multi-session execution; continuation completed at 2026-05-19T20:53:15Z
- **Tasks:** 8/8 complete; Task 8A-08 auto-approved by `workflow.auto_advance=true`
- **Files modified:** 100+ source, config, test, and ops files

## Accomplishments

- Added admin persistence and audit infrastructure: `admin_users`, `admin_audit_event`, `admin_read_event`, append-only trigger, HMAC chain, purge job, and chain verifier.
- Added admin auth foundation: admin/user chain split, WebAuthn relying-party wiring, bootstrap enrollment, enrollment session endpoint, AdminContext mutex, and admin API contracts.
- Added `apps/admin` as a standalone Vite React SPA with passkey enrollment/login routes, ADMIN MODE shell, audit viewer, role grants, ConfirmTwiceDialog, Vitest, and Playwright coverage.
- Added deploy/operator infrastructure: `9router`, NPM service, v1.2 deploy runbook, load-probe scaffold, and shared-file ownership table.

## Task Commits

1. **Task 8A-01 RED:** `2d3bfb84` test admin foundation regression tests
2. **Task 8A-01 GREEN:** `260d9b16` add admin schema and audit chain foundation
3. **Task 8A-02 RED:** `b89caa36` add admin auth module regression tests
4. **Task 8A-02 GREEN:** `3ab83c3b` implement core admin auth module
5. **Task 8A-03 RED:** `1177e9f4` add admin audit module regression tests
6. **Task 8A-03 GREEN:** `70c4a5f3` implement admin audit module
7. **Task 8A-04 RED:** `949c8747` add failing admin chain wiring tests
8. **Task 8A-04 GREEN:** `0575d92e` wire admin security chain
9. **Task 8A-05 RED:** `16f60026` add admin controller contract tests
10. **Task 8A-05 GREEN:** `ceffa40d` add admin audit and grant controllers
11. **Task 8A-06:** `37dda12f` scaffold admin console SPA
12. **Task 8A-07:** `836f3493` add operator deploy compose runbook
13. **Rule 3 fix:** `9f35e0b2` expose admin validation interface
14. **Rule 2 fix:** `dc89054e` document admin shared file ownership

## Verification

- `pnpm install` passed before the 8A-06 commit.
- `pnpm --filter @zeromail/admin build` passed.
- `pnpm --filter @zeromail/admin test:unit` passed.
- `pnpm --filter @zeromail/admin exec playwright test --reporter=list` passed: 3 chromium specs.
- Playwright MCP browser pass on `http://127.0.0.1:5174/login` passed with no console errors after adding the inline favicon.
- `pnpm --filter web lint` passed. The plan's `@zeromail/web` filter does not match this workspace package name.
- `docker compose config` passed; required services `postgres`, `redis`, `9router`, `nginx-proxy-manager`, `api`, `worker`, and `frontend` are present.
- `./gradlew :backend:core:test --tests "*Admin*" :backend:api:test --tests "*Admin*"` passed.
- `./gradlew :backend:worker:test` passed.
- `./gradlew :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` passed after `security.validation` named interface fix.
- Production Gmail send grep passed: `PROD_GMAIL_SEND_COUNT=1` at `AssistantSendExecutor`.
- Full `./gradlew :backend:core:test :backend:api:test :backend:worker:test` was run and failed on pre-existing public API route test drift from `db38a7be`; see `deferred-items.md`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Orchestrator Deviation] Phase branch source**
- **Found during:** Orchestrator setup
- **Issue:** `origin/main` did not contain approved Phase 8 planning files.
- **Fix:** Phase branch was created from current local HEAD.
- **Files modified:** None by executor.
- **Committed in:** N/A

**2. [Rule 3 - Blocking] Exposed admin DTO named interfaces**
- **Found during:** Task 8A-05
- **Issue:** Spring Modulith rejected admin DTO types crossing module boundaries.
- **Fix:** Added `@NamedInterface("admin.audit")` and `@NamedInterface("admin.grants")` package markers.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/dto/admin/**/package-info.java`
- **Verification:** Admin API tests and Modulith verification passed for admin scope.
- **Committed in:** `ceffa40d`

**3. [Rule 3 - Blocking] Made copied Sonner primitive Vite-safe**
- **Found during:** Task 8A-06
- **Issue:** Copied `sonner.tsx` imported `next-themes`, which is not present in the Vite admin app.
- **Fix:** Removed the Next-specific dependency and passed Sonner's `theme` prop directly.
- **Files modified:** `apps/admin/src/components/ui/sonner.tsx`
- **Verification:** Admin build and tests passed.
- **Committed in:** `37dda12f`

**4. [Rule 1 - Bug] Removed admin login favicon console error**
- **Found during:** Task 8A-06 browser verification
- **Issue:** `/login` produced a browser console 404 for `/favicon.ico`.
- **Fix:** Added an inline SVG favicon link to `apps/admin/index.html`.
- **Verification:** Playwright MCP browser console had zero errors after reload.
- **Committed in:** `37dda12f`

**5. [Rule 2 - Security] Bound NPM admin UI to loopback**
- **Found during:** Task 8A-07
- **Issue:** Plan shorthand listed `81:81`, but action text required port 81 only through SSH tunnel.
- **Fix:** Compose binds `127.0.0.1:81:81`; runbook documents SSH tunneling.
- **Verification:** `docker compose config` passed and rendered loopback binding.
- **Committed in:** `836f3493`

**6. [Rule 3 - Blocking] Exposed shared validation annotations to DTO module**
- **Found during:** Final verification
- **Issue:** `RevokeAdminRequest` used `@NoSentinelLeak` from `api.security.validation`, but that package was not exposed as a Modulith named interface.
- **Fix:** Added `backend/api/src/main/java/com/zeromail/api/security/validation/package-info.java`.
- **Verification:** JetBrains file problems clean; `ZeroMailApiApplicationModulesTest` passed.
- **Committed in:** `9f35e0b2`

**7. [Rule 2 - Missing Critical] Added shared-file ownership table**
- **Found during:** Final success-criteria scan
- **Issue:** Required `docs/ops/admin-shared-file-ownership.md` artifact was missing.
- **Fix:** Added six ownership rows covering SecurityConfig, ChatModelCacheEvictionListener, db.changelog-master.yaml, apps/admin `__root.tsx`, OpenApiConfig, and application.yml.
- **Verification:** Required row grep passed.
- **Committed in:** `dc89054e`

**Total deviations:** 7 documented items; all in-scope fixes were committed.

## Known Stubs

- `apps/admin/src/lib/api/admin-schema.d.ts` is a hand-authored admin OpenAPI type stub used until a running backend can generate `/v3/api-docs/admin` through `apps/admin/scripts/generate-api.ts`. It does not block the scaffold, but future API changes must regenerate this file.
- `apps/admin/src/components/ConfirmTwiceDialog.tsx` includes a UI placeholder example for the reason textarea. This is instructional form placeholder text, not rendered data.

## Deferred Issues

- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` still fails public API tests with `/me` and `/tenant` 404s plus unprefixed OpenAPI path assertions. This traces to pre-8A commit `db38a7be deploy: production Docker setup and API routing fixes`, which changed public controller mappings to `/api/**` without aligning legacy tests. Logged in `deferred-items.md`.

## User Setup Required

- Manual WebAuthn verification still requires a real passkey-capable authenticator (Touch ID, Windows Hello, or YubiKey). Task 8A-08 was auto-approved by workflow policy, not physically performed in this executor session.
- Operators must provide `ZEROMAIL_9ROUTER_JWT_SECRET`, `ZEROMAIL_9ROUTER_INITIAL_PASSWORD`, and `ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64` before production boot.

## Next Phase Readiness

8B-8F can build on the admin auth, audit writer, role grant scaffolding, OpenAPI split, Vite admin shell, and compose/runbook foundation. The main caution for downstream phases is the deferred public API route test drift; admin-specific backend gates are green.

## Self-Check: PASSED

- Required summary/deferred files exist.
- Key admin app, ops docs, and validation interface files exist.
- All 14 task/fix commits are reachable in git.

---
*Phase: 08-admin-console-operator-tooling*
*Completed: 2026-05-19*

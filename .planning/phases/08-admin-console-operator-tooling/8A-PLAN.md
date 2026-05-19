---
phase: 08-admin-console-operator-tooling
plan: 8A
type: execute
wave: 1
depends_on: []
files_modified:
  - docker-compose.yml
  - docs/ops/v1.2-deploy.md
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/AdminBindingFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/EnrollmentSessionController.java
  - backend/api/src/main/java/com/zeromail/api/admin/AdminBootstrapRunner.java
  - docs/ops/admin-interface-freeze.md
  - backend/api/src/test/java/com/zeromail/api/admin/AdminChainIntegrationTest.java
  - backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminAuditController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminRoleGrantsController.java
  - backend/api/src/main/java/com/zeromail/api/dto/admin/audit/
  - backend/api/src/main/java/com/zeromail/api/dto/admin/grants/
  - backend/core/src/main/java/com/zeromail/core/admin/auth/AdminContext.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/AdminUser.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/domain/AdminStatus.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/AdminUserDetailsService.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/WebAuthnCredentialStore.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/EnrollmentTokenService.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventEntity.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventRepository.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AdminAuditWriter.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/HmacChainHasher.java
  - backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AuditCsvExporter.java
  - backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/AdminRoleGrantService.java
  - backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
  - backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml
  - backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml
  - backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/main/resources/application.yml
  - backend/worker/src/main/java/com/zeromail/worker/admin/AdminReadEventPurgeJob.java
  - backend/worker/src/main/java/com/zeromail/worker/admin/AdminAuditChainVerifyJob.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminContextMutexTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSendBanTest.java
  - backend/api/src/test/java/com/zeromail/api/arch/AdminControllerPreAuthorizeTest.java
  - backend/api/src/test/java/com/zeromail/api/arch/AdminChainNoOauth2LoginTest.java
  - backend/api/src/test/java/com/zeromail/api/admin/AdminChainCookieIsolationTest.java
  - backend/core/src/test/java/com/zeromail/core/admin/audit/AuditChainIntegrityTest.java
  - apps/admin/package.json
  - apps/admin/vite.config.ts
  - apps/admin/tsconfig.json
  - apps/admin/index.html
  - apps/admin/src/main.tsx
  - apps/admin/src/App.tsx
  - apps/admin/src/lib/api/admin-client.ts
  - apps/admin/scripts/generate-api.ts
  - apps/admin/src/lib/webauthn.ts
  - apps/admin/src/components/ui/
  - apps/admin/src/components/AdminModeBanner.tsx
  - apps/admin/src/components/ConfirmTwiceDialog.tsx
  - apps/admin/src/components/JsonDiffViewer.tsx
  - apps/admin/src/routes/enroll.tsx
  - apps/admin/src/routes/login.tsx
  - apps/admin/src/routes/dashboard.tsx
  - apps/admin/src/routes/audit.tsx
  - apps/admin/src/routes/role-grants.tsx
  - apps/admin/src/features/audit/
  - apps/admin/src/features/role-grants/
  - apps/admin/src/styles/globals.css
  - apps/admin/playwright.config.ts
  - pnpm-workspace.yaml
  - turbo.json
  - apps/web/eslint.config.mjs
  - docs/ops/admin-shared-file-ownership.md
  - backend/api/src/test/java/com/zeromail/api/admin/Phase8E2ESmokeTest.java

autonomous: false
requirements:
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

user_setup:
  - service: webauthn-test-authenticator
    why: "WebAuthn ceremony cannot be fully verified without a real platform authenticator (Touch ID / Windows Hello / YubiKey)."
    dashboard_config:
      - task: "Have a hardware passkey (Touch ID / Windows Hello / YubiKey) registered on the test machine for the enrollment + login smoke tests."
        location: "OS settings / browser passkey manager"

must_haves:
  truths:
    - "Operator can run `docker compose config` against the updated `docker-compose.yml` and validation succeeds (9Router sidecar + NPM proxy services defined)."
    - "`docs/ops/v1.2-deploy.md` runbook documents (a) nginx→NPM migration, (b) 9Router sidecar first-run, (c) rollback path, (d) NPM `/data` + 9Router SQLite backup procedure, (e) lost-passkey shell recovery."
    - "Bootstrap admin can sign in at `admin.zeromail.com` via WebAuthn passkey ceremony with `userVerificationRequirement=REQUIRED` and reach `/api/admin/*` routes."
    - "Request to `/api/admin/*` without a valid admin WebAuthn session returns HTTP 401 at chain level (chain isolation enforced)."
    - "User logging into `zeromail.com` via Google OAuth carries NO `ROLE_ADMIN`; `/api/admin/*` is unreachable from user session."
    - "Every admin state mutation writes one `admin_audit_event` row in the same transaction with HMAC-SHA256 chained hash."
    - "App DB user cannot UPDATE or DELETE `admin_audit_event`; Postgres trigger raises EXCEPTION on attempt regardless of role."
    - "`apps/admin` Vite + React 19 SPA builds standalone via `pnpm --filter @zeromail/admin build` with no Next.js or SSR runtime."
    - "`apps/web` Next.js bundle ships zero references to `admin-schema.d.ts` or admin route code (ESLint guard + workspace separation)."
    - "Persistent ADMIN MODE banner renders at top of every authenticated `apps/admin` page (40px, `#FDE8BA` background, copy locked per UI-SPEC §Color)."
    - "Inside any admin request, `AdminContext.currentOrThrow()` resolves and `TenantContext.currentOrThrow()` throws (mutex enforced)."
    - "Cross-tenant admin reads can ONLY happen via `AdminTenantAccess.readOnly(tenantId, supplier)` which writes `admin_read_event` row first (skeleton present for 8C to extend)."
    - "ArchUnit gates green: `AdminContextMutexTest`, `AdminPathBodyBanTest`, `AdminSendBanTest`, `AdminControllerPreAuthorizeTest`, `AdminChainNoOauth2LoginTest`."
    - "Repo-wide grep gate still asserts exactly 1 Gmail send call site; admin packages additionally banned from referencing Gmail send methods."
    - "Audit log viewer at `/audit` paginates `admin_audit_event` rows, filters by actor/action/target/date, exports max-10k CSV stream."
    - "`<ConfirmTwiceDialog>` primitive renders red header strip + reason textarea (8-500 chars, sentinel-leak regex reject) + step-5 token confirm + final destructive button."
    - "`GroupedOpenApi` split: `/v3/api-docs/public` excludes `/api/admin/**`; `/v3/api-docs/admin` includes only `/api/admin/**`."
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml"
      provides: "`admin_users` table with WebAuthn credential columns (user_handle, credential_id, public_key_cose, signature_counter, aaguid, attestation_format, status) + CHECK constraint + UNIQUE on email/user_handle/credential_id + REVOKE DELETE."
    - path: "backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml"
      provides: "`admin_audit_event` table + `BEFORE UPDATE OR DELETE` trigger raising `EXCEPTION 'admin_audit_event is append-only'` + REVOKE UPDATE/DELETE from zeromail_app + GRANT INSERT/SELECT."
    - path: "backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml"
      provides: "`admin_read_event` table without append-only trigger (30-day retention via worker)."
    - path: "backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java"
      provides: "`@Order(1) adminChain` with `securityMatcher(\"/api/admin/**\", \"/webauthn/**\", \"/login/webauthn/**\", \"/enroll\")` + `.webAuthn(rpId=admin.zeromail.com, userVerificationRequirement=REQUIRED)`; existing chain demoted to `@Order(2)`."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/auth/AdminContext.java"
      provides: "`ScopedValue<AdminUser>` with `currentOrThrow()` that throws when `TenantContext.TENANT.isBound()` (mutex)."
    - path: "backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AdminAuditWriter.java"
      provides: "Same-transaction audit insert + HMAC-SHA256 chain hash computation."
    - path: "apps/admin/package.json"
      provides: "Vite 7 + React 19 + React Router 6 + TanStack Query 5 + Tailwind 4 + openapi-fetch 0.17 + @simplewebauthn/browser workspace."
    - path: "apps/admin/src/components/AdminModeBanner.tsx"
      provides: "40px sticky banner: bg `#FDE8BA`, border-bottom `#E3A023`, copy `ADMIN MODE • actions affect real tenants • signed in as {admin.email} • {env}`."
    - path: "apps/admin/src/components/ConfirmTwiceDialog.tsx"
      provides: "Shared destructive-action shell (red header + reason 8-500 chars w/ sentinel-leak guard + step-5 typed-token confirm + final button)."
    - path: "docs/ops/v1.2-deploy.md"
      provides: "Runbook: nginx→NPM migration + 9Router first-run + rollback + backup + lost-passkey shell recovery + bootstrap STDOUT capture."
  key_links:
    - from: "backend/api/SecurityConfig#adminChain"
      to: "backend/core/.../AdminUserDetailsService"
      via: ".userDetailsService(adminUserDetailsService)"
      pattern: "AdminUserDetailsService"
    - from: "backend/api/security/AdminBindingFilter"
      to: "backend/core/.../AdminContext#run"
      via: "ScopedValue.where(AdminContext.ADMIN, adminUser).run(...)"
      pattern: "AdminContext\\.run|ScopedValue\\.where\\(AdminContext\\.ADMIN"
    - from: "backend/core/.../AdminAuditWriter"
      to: "admin_audit_event INSERT"
      via: "same-transaction native query"
      pattern: "INSERT INTO admin_audit_event"
    - from: "apps/admin/src/lib/api/admin-client.ts"
      to: "/v3/api-docs/admin"
      via: "openapi-fetch + codegenned admin-schema.d.ts"
      pattern: "createClient<paths>"
---

<objective>
Lay the foundation for Phase 8: docker-compose VPS deploy artifacts (9Router sidecar + NPM proxy + runbook); admin SecurityFilterChain with Spring Security 7 `.webAuthn(...)` DSL; admin identity store (`admin_users`) + first-admin bootstrap (Liquibase seed + `CommandLineRunner` printing one-time enrollment URL to STDOUT); append-only audit infrastructure (`admin_audit_event` + `admin_read_event` + Postgres trigger + HMAC chain + worker retention + nightly chain verify); `AdminContext` ScopedValue mutex with `TenantContext`; `GroupedOpenApi` split for public vs admin OpenAPI; admin REST controller scaffolding (Audit viewer + Role Grants); shared ArchUnit gates; NEW `apps/admin` Vite + React 19 SPA workspace with login/enroll/dashboard/audit/role-grants screens, shadcn primitives, ADMIN MODE banner, `<ConfirmTwiceDialog>`, codegenned admin client.

Purpose: 8A is the hard gate. Until admin sign-in works end-to-end, audit rows write, ArchUnit fences exist, and the Vite SPA scaffolds against the GroupedOpenApi admin spec, the parallel plans (8B/8C/8D/8E/8F) have nothing to build against.

Output: A bootstrapped operator can run `docker compose config` successfully, complete a WebAuthn registration ceremony at `admin.zeromail.com/enroll?token=...`, sign in at `admin.zeromail.com/login`, browse `/audit` and `/role-grants`, and grant a second admin — every action emits a tamper-evident `admin_audit_event` row.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@CONVENTIONS.md
@TESTING.md
@.planning/ROADMAP.md
@.planning/phases/08-admin-console-operator-tooling/08-CONTEXT.md
@.planning/phases/08-admin-console-operator-tooling/08-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md
@.planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md
@.planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md
@.planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html
@.planning/phases/08-admin-console-operator-tooling/08-VALIDATION.md
@backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
@backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java
@backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java
@backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java
@backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
@backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml
@backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
@backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java
@backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java
@apps/web/scripts/generate-api.ts
@apps/web/lib/api/client.ts
@apps/web/app/globals.css
</context>

<documentation_lookup>
Before any auth code, planner-time research (Context7) is mandatory and supplied. Executor MUST verify current Spring Security 7 `.webAuthn(...)` DSL via Context7 `/websites/spring_io_spring-security_reference_7_0` (fallback: `/spring-projects/spring-security`) before writing `SecurityConfig.adminChain(...)` — surface `WebAuthnRelyingPartyOperations`, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository` interfaces. For STDOUT runner: Context7 `/spring-projects/spring-boot` `CommandLineRunner`. For frontend ceremony: Context7 `/MasterKale/SimpleWebAuthn` (`startRegistration`, `startAuthentication`, `optionsJSON` wrapper v10 shape). For OpenAPI split: Context7 `/springdoc/springdoc-openapi` `GroupedOpenApi`.
</documentation_lookup>

<reviews_addendum_8A>
## Reviews-pass replan addendum — 2026-05-19 (Codex + OpenCode HIGHs incorporated)

Tasks below have been amended to resolve the following HIGH-severity concerns surfaced in `08-REVIEWS.md`. Where a task's `<behavior>` / `<action>` / `<acceptance_criteria>` sections still reference legacy shapes, this addendum is authoritative — executor MUST apply these corrections.

### R-8A-H1 — WebAuthn interface freeze (Codex HIGH, OpenCode HIGH)
**Decision:** Before any of 8A-04 / 8A-06 coding starts, produce `docs/ops/admin-interface-freeze.md` (new file, added to Task 8A-07 deliverables) containing the verified Spring Security 7.0.5 `.webAuthn(...)` DSL surface fetched from Context7 `/websites/spring_io_spring-security_reference_7_0`. The freeze pins: (a) exact bean names and signatures for `WebAuthnRelyingPartyOperations`, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`; (b) exact stock endpoint paths emitted by `.webAuthn(...)` (e.g. `/webauthn/register/options`, `/webauthn/authenticate/options`, `/login/webauthn` — confirm exact paths against Context7 docs, do NOT assume); (c) the `securityMatcher(...)` glob list MUST be a superset of those stock paths; (d) `apps/admin` route `/enroll` is RESERVED for SPA only — backend token validation lives at `/api/admin/enrollment/session` (new path, see R-8A-H3), never at `/enroll`. The freeze doc is the contract; SecurityConfig + apps/admin routing MUST cite it inline as `// see docs/ops/admin-interface-freeze.md §{section}`.

### R-8A-H2 — Audit chain ordering deterministic under concurrency (Codex HIGH)
**Decision:** Add column `chain_index BIGSERIAL NOT NULL UNIQUE` to `admin_audit_event` (Liquibase 049). HMAC chain ordering is now by `chain_index`, NOT `created_at`. `HmacChainHasher` hashes `(previous_hash || canonical(chain_index || actor_user_id || action || target_kind || target_id || before_json || after_json || reason || request_ip || request_id || canonical_timestamp_ms))` where `canonical_timestamp_ms` is set by the application (`Instant.now().toEpochMilli()`) at insert time and stored alongside `created_at` (DB default) in a new `canonical_timestamp_ms BIGINT NOT NULL` column. `AdminAuditEventRepository.findLatestHmac()` becomes `SELECT hmac_chain_hash FROM admin_audit_event ORDER BY chain_index DESC LIMIT 1 FOR UPDATE`. `AdminAuditChainVerifyJob.verifyOnce()` re-derives from `chain_index=1` ascending. AcceptanceCriteria for Task 8A-01 and 8A-03 extended: insert 1000 rows concurrently across 4 threads → chain re-derives green; mutating any row's `chain_index=500` reason then re-verifying detects mismatch at exactly chain_index=500.

### R-8A-H3 — Enrollment routing split: SPA vs backend (Codex HIGH)
**Decision:** `/enroll` is exclusively an `apps/admin` SPA route. Backend token validation moves to a NEW REST path `POST /api/admin/enrollment/session` (request body `{token, email}` → response `{enrollmentSessionCookie, expiresAt}`); NPM proxy routes `/enroll` to the SPA bundle and `/api/admin/**` to backend. `EnrollmentTokenGate` is REMOVED (no filter on `/enroll`). Replace with `EnrollmentSessionController` (added to Task 8A-04 files): handles `POST /api/admin/enrollment/session` (validates one-time token via `EnrollmentTokenService.consume`, mints short-lived enrollment session cookie scoped to admin domain), `POST /api/admin/enrollment/register` (consumes enrollment cookie, drives the WebAuthn registration ceremony through the stock Spring Security endpoint chain). SPA `/enroll?token=` page first POSTs the token+email to `/api/admin/enrollment/session`, then on 200 invokes `startRegistration({optionsJSON})` which hits the stock Spring Security registration-options endpoint (path pinned in R-8A-H1 freeze). Task 8A-04 acceptance criteria extended: NPM routing manifest in runbook documents path split; unauthenticated GET `/api/admin/enrollment/session` returns 401; valid token POST returns 200 + Set-Cookie; expired/used token returns HTTP 410 Gone.

### R-8A-H4 — Per-chain Spring Session cookie isolation (Codex HIGH)
**Decision:** Two `SpringSessionRepositoryFilter` registrations bound to separate `RedisIndexedSessionRepository` beans (`adminSessionRepository`, `userSessionRepository`) backed by different Redis key namespaces (`spring:session:admin:*` vs `spring:session:user:*`). Two `CookieSerializer` beans: `adminCookieSerializer` with `cookieName="SESSION_ADMIN"` and `cookieDomain="admin.zeromail.com"` (or `cookiePath="/api/admin"` for dev/localhost where subdomain is unavailable); `userCookieSerializer` with `cookieName="SESSION_USER"` and `cookieDomain="zeromail.com"`. Admin chain installs `addFilterBefore(springSessionRepositoryFilter("admin"), SecurityContextPersistenceFilter.class)` referencing the admin repository bean explicitly; user chain references the user repository bean. Executor MUST verify the multi-`SessionRepositoryFilter` registration shape via Context7 `/spring-projects/spring-session` before coding — if Spring Session 4 (Boot 4) forbids two repository beans on the same dispatcher, fall back to single repository + cookie path scoping (`/api/admin/**` vs `/api/**`) and document the deviation in `admin-interface-freeze.md`. Task 8A-04 acceptance criteria extended: cookie isolation test `AdminChainCookieIsolationTest` asserts request to `/api/admin/audit/events` with only `SESSION_USER=...` returns 401 and that the admin chain never reads `SESSION_USER`.

### R-8A-H5 — Bootstrap moves from Liquibase seed to startup runner (Codex MEDIUM→HIGH per net effect)
**Decision:** REMOVE `zeromail.admin.bootstrap-emails` Liquibase `<insert>` seed if any was implied. Bootstrap is ENTIRELY runtime: `AdminBootstrapRunner` (Task 8A-04) is `CommandLineRunner` reading `zeromail.admin.bootstrap-emails` from `application.yml`/env and upserting `admin_users(status='PENDING_ENROLLMENT')` rows at startup. Idempotency rules clarified: (a) row with `status='ACTIVE'` → skip silently (no token, no audit); (b) row with `status='PENDING_ENROLLMENT'` and an outstanding non-expired token in `EnrollmentTokenService` cache → reprint same token (re-emit STDOUT line for ops convenience); (c) row with `status='PENDING_ENROLLMENT'` and NO valid token → mint a fresh token, print URL. This matches the spec intent that `PENDING` rows get a fresh enrollment URL on second boot when needed, not silent skip.

### R-8A-H6 — Replace H2 verification with Postgres Testcontainers (Codex MEDIUM)
**Decision:** Task 8A-01 verify command now uses `:liquibaseUpdate -Pdb=testcontainer-postgres` (Liquibase against a Postgres Testcontainer) instead of `-Pdb=h2`. H2 cannot validate Postgres-specific features: BEFORE-trigger SQLSTATE 23514, REVOKE UPDATE/DELETE grants, INET column type, BYTEA, JSONB CHECK constraints, BIGSERIAL. Add Gradle task `:backend:core:liquibaseUpdateTestcontainer` that boots a `postgres:17.6-alpine` container, applies all changelogs (048–050), then runs four direct-SQL assertions: (1) `UPDATE admin_audit_event SET reason='x' WHERE id=...` raises SQLSTATE 23514; (2) `DELETE FROM admin_users WHERE id=...` as `zeromail_app` role returns permission denied; (3) `INSERT INTO admin_audit_event` works for the `zeromail_app` role; (4) `chain_index` column exists and is BIGSERIAL UNIQUE. Acceptance criteria for Task 8A-01 amended accordingly.

### R-8A-H7 — `AdminChainNoOauth2LoginTest` source-parsing is fragile; add MockMvc integration test
**Decision:** Keep `AdminChainNoOauth2LoginTest` as a lightweight ArchUnit complement but add `AdminChainIntegrationTest` (new file added to Task 8A-04 files list): `@SpringBootTest(webEnvironment=RANDOM_PORT)` MockMvc test that (a) presents a valid user OAuth session cookie to `/api/admin/audit/events` → expect 401; (b) presents a valid admin WebAuthn session cookie to `/api/inbox` (user-side route) → expect 401; (c) admin chain has zero `OAuth2LoginAuthenticationFilter` instances in its filter list (introspect via `FilterChainProxy.getFilters("/api/admin/x")`). The integration test is the load-bearing guarantee; ArchUnit stays as a fast lint.

### R-8A-H8 — Turborepo + apps-web ESLint hardening (OpenCode MEDIUM)
**Decision:** Task 8A-06 `turbo.json` MUST set `outputs: ["dist/**"]` for `@zeromail/admin#build` and `outputs: ["coverage/**"]` for `@zeromail/admin#test`. Add an additional ESLint rule on `apps/web/eslint.config.mjs`: `no-restricted-imports` patterns `**/apps/admin/**` AND `**/admin-schema*`. Update Task 8A-06 acceptance criteria to assert: `grep -c '"@zeromail/admin#build"' turbo.json` ≥1 and `grep -c '"outputs"' turbo.json` ≥1; `pnpm --filter @zeromail/web lint` flags an injected `import './admin-schema'` as violation.

### R-8A-H9 — Runbook backup default + detached-Docker warning (OpenCode LOW + cross-cutting MEDIUM)
**Decision:** Task 8A-07 runbook adds (a) §Backup defaults to `tar | gpg | rsync` to `/opt/zeromail/backups/` with optional AWS S3 variant noted; (b) §Bootstrap interactive-mode warning: explicit block "Run `docker compose up api` in foreground for first bootstrap; capture the enrollment URL; Ctrl-C then restart in detached mode (`docker compose up -d`)" with a callout that `docker compose run -d` writes STDOUT to Docker logging driver, which violates the "never in log file" invariant; (c) §Security Considerations: disable `/actuator/heapdump`, restrict JMX to loopback, `--memory-swap` limit on api container, document `ProviderMasterKeyResolver` heap-residence threat from 8B (cross-reference). Section heading count in acceptance criteria bumps from ≥5 to ≥7.

### R-8A-H10 — Liquibase numbering offset to avoid 8A/8B/8D collisions (OpenCode LOW)
**Decision:** Keep 8A at 048–050. 8B Liquibase changeset renumbered from `051-llm-provider-master-key.yaml` → `058-llm-provider-master-key.yaml`. 8D renumbered from `052/053` → `068-catalog-tables.yaml` / `069-anthropic-catalog-seed.yaml`. 8E (if it adds `054-processing-job-extend.yaml`) → `055-processing-job-extend.yaml`. Update db.changelog-master.yaml include ordering accordingly. This addendum reserves contiguous ranges per plan (048–057 = 8A, 058–067 = 8B, 068–077 = 8D, 078+ = 8E/8C as needed) so parallel-after-8A waves cannot collide on master changelog merges. Each plan's Liquibase task acceptance criterion now references the offset number.

---

## Cycle 3 reviews-pass addendum — 2026-05-19 (7 remaining HIGHs from cycle 2)

The cycle 2 review (`08-REVIEWS.md`) reconciled 7 unresolved HIGHs across 8A/8B/8D/8E/8F. The 8A-owned items below are authoritative over the cycle 2 R-8A-H* decisions where they conflict. Cross-plan items (HIGH-2, HIGH-4, HIGH-18, NEW-HIGH-1, NEW-HIGH-2) are addressed in the receiving plans' own cycle-3 addenda.

### R-8A-H11 — Ownership matrix for shared files (closes cycle-2 HIGH-3)
**Decision:** Task 8A-07 deliverables extend with a NEW file `docs/ops/admin-shared-file-ownership.md` (added to `<files_modified>`) that declares, for every file touched by more than one plan in Phase 8, a single owning plan and a contribution protocol. Minimum mandatory entries:

| Shared artifact | Owning plan | Contributors (append-only) | Contribution protocol |
|---|---|---|---|
| `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` | 8A | 8B (no contributions expected) | All chains + filters defined in 8A; later plans MUST NOT add new chains. New filters appended via `addFilterAfter(...)` with explicit cite to `docs/ops/admin-interface-freeze.md §{section}`. |
| `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java` | 8B | 8D (CatalogChangedEvent listener method) | Each contributor adds ONE additional `@TransactionalEventListener` method; the class stays single-responsibility "evict ChatModel cache"; signature `void on(<EventType>)` only. |
| `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` | 8A | 8B, 8C, 8D, 8E, 8F | Append-only `<include file="changes/0NN-...yaml"/>` entries in the numeric ranges reserved by R-8A-H10 (8A=048–057, 8B=058–067, 8D=068–077, 8E=078+, 8F=079+). Last writer rebases on numeric order; no in-place edits to existing `<include>` lines. |
| `apps/admin/src/routes/__root.tsx` (admin nav module) | 8A | 8B (`/master-keys`), 8C (`/tenants`), 8D (`/catalog`), 8E (`/queue`), 8F (`/spend`) | Each contributor registers ONE TanStack Router route via the file-based-routing convention (drop a new `routes/<feature>.tsx` file); they MUST NOT edit `__root.tsx` itself. The root layout iterates a static `NAV_ENTRIES` array — extending the array is the only allowed root edit and lives in 8A. |
| `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` | 8A | none expected | 8A defines `GroupedOpenApi publicApi()` + `GroupedOpenApi adminApi()`; later plans MUST NOT add additional groups; new admin paths under `/api/admin/**` are matched by the existing `adminApi()` glob automatically. |
| `backend/core/src/main/resources/application.yml` (api module) | 8A | 8B (`zeromail.mkey.*`), 8D (`zeromail.catalog.*`), 8F (`zeromail.spend.*`) | Property namespaces are non-overlapping; conflict resolution is "owning plan namespace wins". |

The ownership doc is a CODE-OWNERS-style contract — pre-merge any PR touching a row above MUST cite the owning plan and the contribution protocol; reviewers reject silent edits to `__root.tsx`, in-place changelog reorders, or new chains in `SecurityConfig`. Acceptance for Task 8A-07: `docs/ops/admin-shared-file-ownership.md` exists with all 6 rows above; `wc -l` ≥ 60; CI grep gate `grep -c 'apps/admin/src/routes/__root.tsx' docs/ops/admin-shared-file-ownership.md` ≥ 1.

### R-8A-H12 — Extend `admin-interface-freeze.md` to pin Spring Session API (closes cycle-2 HIGH-8)
**Decision:** The same `docs/ops/admin-interface-freeze.md` artifact produced under R-8A-H1 gains a NEW Section §Spring Session API surface fetched from Context7 `/spring-projects/spring-session` (fallback `/websites/docs_spring_io_spring-session`). The freeze pins:
1. Whether Spring Session 4 (Boot 4) permits two `SessionRepository` beans on the same dispatcher (`RedisIndexedSessionRepository` x2) — if NO, the chosen strategy is single-repository + cookie-path scoping (`/api/admin/**` vs `/api/**`) and the R-8A-H4 implementation collapses to that variant.
2. Exact class names + qualifier annotations for the chosen variant (e.g. `RedisIndexedSessionRepository`, `SpringSessionRepositoryFilter`, `CookieSerializer`, `DefaultCookieSerializer`).
3. Cookie/Redis-key namespace shape locked: cookie names `SESSION_ADMIN`/`SESSION_USER`, Redis key prefixes `spring:session:admin:` and `spring:session:user:` (or `spring:session:` shared root if single-repo variant).
4. `Last verified: 2026-05-19 against spring-session-refdoc {version}` timestamp line — re-verify required if Spring Session is upgraded.

Outcome: HIGH-8 is closed by the SAME artifact that closed HIGH-1/HIGH-6 (WebAuthn endpoints), removing the "conditional on unproven Spring Session shape" residual. Task 8A-04 acceptance amended: SecurityConfig.adminChain and the Spring Session bean wiring MUST cite `docs/ops/admin-interface-freeze.md §Spring Session API` inline; `AdminChainCookieIsolationTest` covers BOTH variant outcomes (two-repo OR single-repo + path scoping) — the test that runs depends on the freeze-doc decision. CI gate `grep -c '## Spring Session API' docs/ops/admin-interface-freeze.md` ≥ 1.

### R-8A-H13 — `Phase8E2ESmokeTest` capstone test (closes cycle-2 HIGH-4)
**Decision:** Add a single cross-plan end-to-end smoke test owned by 8A: `backend/api/src/test/java/com/zeromail/api/admin/Phase8E2ESmokeTest.java` (NEW, added to Task 8A-04 `<files>`). Shape: `@SpringBootTest(webEnvironment=RANDOM_PORT)` with mocked external dependencies (Gmail, provider `/v1/models`, OpenRouter). The test walks the full admin lifecycle in one method:

1. Bootstrap: STDOUT runner mints enrollment URL → consume token via `POST /api/admin/enrollment/session`.
2. Register: drive WebAuthn registration ceremony via `WebAuthnTestHarness` (mock authenticator) → assert `admin_users.status='ACTIVE'`.
3. Login: assert authenticated admin session cookie returned + `GET /api/admin/audit/events` returns 200.
4. Master key: `POST /api/admin/master-keys/OPENAI/edit-session` → `PUT /api/admin/master-keys/OPENAI` with mocked-OK provider → assert masked key visible + `MASTER_KEY_SET` audit row written.
5. Catalog Sync: `POST /api/admin/catalog/OPENAI/sync` (Fetch) → poll job until DIFF_READY → `POST /api/admin/catalog/sync/{jobId}/confirm` → assert `CATALOG_SYNC_CONFIRMED` audit row + ≥1 `model_catalog` row inserted.
6. Tenant inspect: seed 1 tenant fixture → `GET /api/admin/tenants/{tenantId}` → assert 200 + no body-ban-tripped response.
7. Queue requeue: seed 1 dead-letter `processing_job` row → `POST /api/admin/queue/dead-letters/{jobId}/requeue` → assert `admin_requeue_count=1` + `attempts=0`.
8. Spend view: `GET /api/admin/spend?range=7d` → assert HTTP 200, body contains `platformCost` + `byokCost` keys.

The test is the load-bearing integration gate that closes HIGH-4 (autonomous execution residual). Each contributor plan (8B/8C/8D/8E/8F) adds the relevant fixture seed via a `@TestComponent` that the smoke test wires in. If any contributor's slice is incomplete, the smoke test fails at that step — that is the integration checkpoint the cycle-1 reviewers asked for. Acceptance for Task 8A-04 amended: `./gradlew :backend:api:test --tests "*Phase8E2ESmokeTest*"` exits 0 after 8F merges; intermediate fails (during 8B-only / 8D-only execution) are expected and tolerated until the dependency chain completes. The test is tagged `@Tag("phase8-e2e")` so it runs in a dedicated CI job after the wave-3 plans land.

### R-8A-H14 — Scrub stale acceptance text contradicting cycle-1/cycle-2 addendums (Codex executor-drift warning)
**Decision:** Before Task 8A-04 / 8A-07 declare done, executor MUST `grep -RnE` the phase-8 plans + this PLAN.md for the following stale tokens and rewrite each hit to match the authoritative addendum decisions:

- `EnrollmentTokenGate` (legacy `/enroll` filter — R-8A-H3 removed it; replace any `EnrollmentTokenGate` mention with `EnrollmentSessionController @ POST /api/admin/enrollment/session`).
- `-Pdb=h2` in verify commands (R-8A-H6 replaced with `-Pdb=testcontainer-postgres`).
- Liquibase numbers `051` (8B), `052/053` (8D), `054` (8E) — replace with the R-8A-H10 offsets `058`, `068/069/070`, `055`.
- "second boot bootstrap" silent-skip wording — replace with R-8A-H5 three-case behavior (ACTIVE skip / PENDING-with-valid-token reprint / PENDING-no-token mint fresh).

This is a non-coding cleanup; success is `grep -c` ≤ expected occurrence count per token (zero for `EnrollmentTokenGate`, zero for `-Pdb=h2`, ranges per renumbering).

</reviews_addendum_8A>

<tasks>

<task type="auto" tdd="true">
  <name>Task 8A-01 (Wave 0): Liquibase changesets 048/049/050 + Wave 0 ArchUnit + audit-chain integrity test scaffolds</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml,
    backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml,
    backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml,
    backend/core/src/main/resources/db/changelog/db.changelog-master.yaml,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminContextMutexTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSendBanTest.java,
    backend/api/src/test/java/com/zeromail/api/arch/AdminControllerPreAuthorizeTest.java,
    backend/api/src/test/java/com/zeromail/api/arch/AdminChainNoOauth2LoginTest.java,
    backend/api/src/test/java/com/zeromail/api/admin/AdminChainCookieIsolationTest.java,
    backend/core/src/test/java/com/zeromail/core/admin/audit/AuditChainIntegrityTest.java
  </files>
  <read_first>
    backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml (lines 14-178 — table+trigger DDL inside `<sql splitStatements: false>`),
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml (lines 1-60 — column-list YAML idiom + check constraint),
    backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (full — append pattern for 048/049/050),
    backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java (lines 31-110 — body-ban + send-ban condition shape),
    backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java (lines 1-60 — repo-confinement pattern),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C5, §C6, §C17,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §ADMIN-04/05/09 + §ARCH-08/09/10/12
  </read_first>
  <behavior>
    - 048-admin-users.yaml deploys `admin_users` with columns per SPEC ADMIN-09 (id UUID PK, email VARCHAR(320) UNIQUE NOT NULL, display_name VARCHAR(200), user_handle BYTEA NOT NULL UNIQUE, status VARCHAR(20) NOT NULL CHECK IN ('PENDING_ENROLLMENT','ACTIVE','REVOKED'), credential_id BYTEA UNIQUE, public_key_cose BYTEA, signature_counter BIGINT DEFAULT 0, aaguid UUID, attestation_format VARCHAR(50), last_used_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), revoked_at TIMESTAMPTZ, revoked_reason VARCHAR(500)); REVOKE DELETE ON admin_users FROM zeromail_app. Invalid status value rejected. DELETE attempt by app role returns permission error.
    - 049-admin-audit-event.yaml deploys `admin_audit_event` (id UUID PK, actor_user_id UUID NOT NULL FK admin_users.id, actor_email VARCHAR(320) NOT NULL, action VARCHAR(64) NOT NULL, target_kind VARCHAR(32), target_id UUID, before_state_json JSONB, after_state_json JSONB, reason VARCHAR(500), request_ip INET, request_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), hmac_chain_hash BYTEA NOT NULL) + `BEFORE UPDATE OR DELETE` trigger raising `EXCEPTION 'admin_audit_event is append-only' USING ERRCODE='23514'` + REVOKE UPDATE,DELETE FROM zeromail_app + GRANT INSERT,SELECT TO zeromail_app. UPDATE attempt by superuser raises trigger exception.
    - 050-admin-read-event.yaml deploys `admin_read_event` (id UUID PK, actor_user_id UUID NOT NULL, actor_email VARCHAR(320), action VARCHAR(64) NOT NULL, target_kind VARCHAR(32), target_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()) — NO append-only trigger (30d retention requires DELETE), index on (created_at) for purge job.
    - db.changelog-master.yaml appends includes for 048/049/050 in order.
    - AdminContextMutexTest: ArchUnit + unit-style. Asserts (a) `AdminContext.run(admin, () -> TenantContext.currentOrThrow())` throws IllegalStateException; (b) admin packages do not depend on `com.zeromail.core.tenant.TenantContext` except whitelisted `AdminTenantAccess` class.
    - AdminPathBodyBanTest: classes in `..controllers.admin..`, `..core.admin..projection..`, `..api.dto.admin..` cannot have fields matching regex `(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*` and cannot call methods matching `(?i)get(Body|BodyHtml|Snippet|Payload|Prompt|Completion|Content).*`. (Trivially green in 8A; 8C tightens.)
    - AdminSendBanTest: classes in `..controllers.admin..` and `..core.admin..` cannot call `Gmail.Users.Messages.send`, `Gmail.Users.Drafts.send`, or `Gmail.Users.Drafts.update` (extends DraftPathArchUnitTest pattern).
    - AdminControllerPreAuthorizeTest: every `@RestController` class in `..controllers.admin..` must have class-level `@PreAuthorize` annotation with value containing `hasRole('ADMIN')`. Uses `JavaClass.isAnnotatedWith(...)` per RESEARCH Pitfall 8.
    - AdminChainNoOauth2LoginTest: parse `SecurityConfig` source; assert `adminChain(...)` method body contains no `.oauth2Login` call and `chain(...)` (user) method body contains no `.webAuthn` call.
    - AdminChainCookieIsolationTest (integration scaffold, marked `@Tag("integration")` + initially `@Disabled` until Task 8A-04 wires `adminChain`): when implemented in 8A-04, requests `/api/admin/audit/events` without admin session return 401; user OAuth session does not pass admin chain.
    - AuditChainIntegrityTest: HmacChainHasher computes HMAC-SHA256(secret, prev_hash || canonical(this_row)); chain verification with one row mutated detects mismatch.
  </behavior>
  <action>
    Create Liquibase YAML changelogs 048/049/050 per ADMIN-09 + ADMIN-04/05 + ARCH-12 SPEC columns. Use `<sql splitStatements: false>` block (mirror 042) for 049 to atomically create table + trigger function `reject_admin_audit_event_mutation()` + trigger `admin_audit_event_append_only BEFORE UPDATE OR DELETE` + REVOKE/GRANT (per D-13). Use `<createTable>` + `<addCheckConstraint>` for 048. Add Liquibase rollback sections mirroring 042 lines 169-178. Append `<include file="changes/048-admin-users.yaml"/>` etc. to db.changelog-master.yaml in numeric order. Create Wave 0 ArchUnit test scaffolds per PATTERNS §C17 — each test runs `importProductionClasses()` from existing project arch helper and uses `.allowEmptyShould(true)` since admin packages do not exist yet at Wave 0; tests must still compile and run green so Wave 1 inserts admin classes incrementally. `AuditChainIntegrityTest` uses a tiny in-memory fixture (no Spring context) to assert HMAC chain compute + tamper detection.
  </action>
  <verify>
    <automated>./gradlew :backend:core:liquibaseUpdate -Pdb=h2 && ./gradlew :backend:core:test --tests "com.zeromail.core.admin.arch.*" --tests "com.zeromail.core.admin.audit.AuditChainIntegrityTest" && ./gradlew :backend:api:test --tests "com.zeromail.api.arch.AdminControllerPreAuthorizeTest" --tests "com.zeromail.api.arch.AdminChainNoOauth2LoginTest"</automated>
  </verify>
  <done>
    3 Liquibase changesets deploy clean on H2; trigger blocks UPDATE/DELETE against Postgres-flavored integration; all 7 ArchUnit / chain tests green (allowEmptyShould tolerates empty admin packages at this stage); db.changelog-master.yaml includes all 3 new files in order.
  </done>
  <acceptance_criteria>
    - `./gradlew :backend:core:liquibaseUpdate` exits 0; `admin_users`, `admin_audit_event`, `admin_read_event` tables visible via `mcp__postgres__list_objects` against the test DB.
    - Direct `psql` `UPDATE admin_audit_event SET reason='x'` raises `admin_audit_event is append-only` exception (SQLSTATE 23514).
    - Direct `psql` `DELETE FROM admin_users WHERE id='...'` returns permission denied (REVOKE applied).
    - `./gradlew :backend:core:test --tests "*Admin*Arch*"` exits 0 with 6 green tests.
    - `grep -c 'changes/048-admin-users.yaml' backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` == 1 (and similarly for 049/050).
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-02: Core admin auth module — AdminContext/AdminUser/AdminStatus + admin_users entity + repository + AdminUserDetailsService + WebAuthnCredentialStore + EnrollmentTokenService</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/auth/AdminContext.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/AdminUser.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/domain/AdminStatus.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/AdminUserDetailsService.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/WebAuthnCredentialStore.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/EnrollmentTokenService.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/exception/AdminAuthException.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/package-info.java,
    backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java,
    backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java (entire — mirror shape + add `requireUnbound()` static helper),
    backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java (lines 1-70 — byte[] columns + AbstractEntity superclass + explicit getters/setters no Lombok),
    backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java (lines 97-134 — ScopedValue.where.run pattern for AdminContext.run),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C2, §C3, §C4,
    .planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md §Spring Security 7 WebAuthn DSL (PublicKeyCredentialUserEntityRepository + UserCredentialRepository),
    CONVENTIONS.md §Backend domain package layout + §Enum state machines (IdentifiedEnum + fromId)
  </read_first>
  <behavior>
    - `AdminContext.run(adminUser, runnable)` throws IllegalStateException when `TenantContext.TENANT.isBound()` (D-10 mutex per ARCH-08).
    - `AdminContext.currentOrThrow()` returns bound AdminUser; throws when unbound; throws if TenantContext.TENANT is bound.
    - `AdminContext.isBound()` returns boolean without throwing.
    - `TenantContext.requireUnbound()` new static helper throws if bound; used by AdminContext.run.
    - `AdminStatus` IdentifiedEnum with PENDING_ENROLLMENT(1), ACTIVE(2), REVOKED(3); static `fromId(int)` throws NoSuchElementException on unknown.
    - `AdminUser` record (id UUID, email String, status AdminStatus, displayName Optional<String>).
    - `AdminUserEntity` JPA `@Entity` with explicit getters/setters; status uses converter to AdminStatus.id(); user_handle/credential_id/public_key_cose are byte[]; signature_counter long; aaguid UUID; revocation via UPDATE status, never DELETE.
    - `AdminUserRepository.findByEmail(String)`, `findByCredentialId(byte[])`, `findByUserHandle(byte[])`, `upsertPending(String email)`, `markActive(UUID id, byte[] credentialId, byte[] publicKeyCose, long signCounter, UUID aaguid, String attestationFormat)`, `incrementSignCounter(UUID id, long newCounter, Instant lastUsed)`.
    - `AdminUserDetailsService implements UserDetailsService`: `loadUserByUsername(email)` returns Spring Security `User` with `ROLE_ADMIN` authority sourced from `admin_users` row where status='ACTIVE'; REVOKED returns disabled UserDetails; PENDING_ENROLLMENT throws UsernameNotFoundException ("complete enrollment first").
    - `WebAuthnCredentialStore` implements Spring Security 7 `PublicKeyCredentialUserEntityRepository` + `UserCredentialRepository`: stores credential_id/public_key_cose/signature_counter/aaguid in `admin_users`; counter-replay rejected when reported signCount ≤ stored signature_counter (audit row WEBAUTHN_REPLAY_SUSPECTED written by AdminAuditWriter in task 8A-03).
    - `EnrollmentTokenService`: in-memory `ConcurrentHashMap<String,EnrollmentTokenEntry>` with 10-min TTL; `mintToken(UUID adminUserId, String email)` returns 32-byte hex token; `consume(String token, String email)` returns Optional<UUID> consuming the token atomically; `@Scheduled(fixedDelay=60000)` sweep purges expired. Token bytes NEVER logged.
    - `ZeroMailCoreProperties` extended: new `admin.bootstrapEmails: List<String>` (default empty) + `admin.audit.hmacKekBase64: String` (KEK for HMAC chain).
  </behavior>
  <action>
    Implement files per shapes in PATTERNS §C2/C3 + SPEC ADMIN-09. AdminContext mirrors TenantContext.java with mutex guard inside `currentOrThrow()` and `run(...)` per excerpt. AdminUserEntity extends an `@MappedSuperclass AbstractEntity` (look up at `core.shared.persistence.AbstractEntity` — analog used by non-tenant-owned entities) since `admin_users` is platform-scope (per D-04, no AbstractTenantOwnedEntity). Use `BYOKProviderAttributeConverter` style converter for `status` enum column. WebAuthnCredentialStore signatures must match Spring Security 7 7.0.5 interfaces — executor MUST verify via Context7 `/websites/spring_io_spring-security_reference_7_0` (RP `PublicKeyCredentialUserEntityRepository` + `UserCredentialRepository` shapes) before coding method signatures. EnrollmentTokenService uses `SecureRandom.nextBytes(new byte[32])` + `HexFormat.of().formatHex(...)`; print path is in task 8A-04. Per CONVENTIONS Privacy logging: log `event=admin_enrollment_token_minted adminUserId={}` — never token bytes, never email. Add ZeroMailCoreProperties nested record `Admin(List<String> bootstrapEmails, Audit audit)` per existing properties shape; wire to application.yml in task 8A-04. Throw `AdminAuthException` from REVOKED status; package-info documents the module surface.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.auth.*" && ./gradlew :backend:core:test --tests "com.zeromail.core.admin.arch.AdminContextMutexTest"</automated>
  </verify>
  <done>
    AdminContext mutex enforced (unit test green); admin_users entity persists and round-trips status; AdminUserDetailsService returns ROLE_ADMIN for ACTIVE row; EnrollmentTokenService mints 32-byte hex token + consumes one-time + sweeps TTL; WebAuthnCredentialStore round-trips credential_id/public_key_cose/signature_counter; ZeroMailCoreProperties parses `zeromail.admin.bootstrap-emails` from application.yml.
  </done>
  <acceptance_criteria>
    - `AdminContext.run(adminUser, () -> TenantContext.currentOrThrow())` throws IllegalStateException with message containing "mutex" or "admin scope".
    - `EnrollmentTokenService.consume(token, email)` returns Optional.empty() on second call (one-time consumption) and after 10-min TTL.
    - `AdminUserDetailsService.loadUserByUsername(activeEmail).getAuthorities()` contains `ROLE_ADMIN`.
    - `admin_users` row insert with status='PENDING_ENROLLMENT' + null credential_id succeeds; subsequent `markActive(...)` flips status + populates credential bytes.
    - No log line in test output contains `bootstrapEmail` value or token hex (grep test output: `grep -E '[0-9a-f]{32,}' build/reports/tests/test/index.html` returns no matches inside admin auth test class output).
    - `AdminContextMutexTest` ArchUnit green: no admin class imports `TenantContext` except `AdminTenantAccess` (created in 8C, whitelisted now).
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-03: Admin audit module — AdminAuditEvent/AdminReadEvent entities + repositories + AdminAuditWriter (same-transaction HMAC chain) + HmacChainHasher + AuditCsvExporter + worker purge + chain-verify jobs</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventEntity.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventRepository.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/domain/AdminAuditAction.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AdminAuditWriter.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/HmacChainHasher.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AuditCsvExporter.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/projection/AdminAuditRow.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/projection/AdminAuditPageQuery.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AdminAuditQueryService.java,
    backend/core/src/main/java/com/zeromail/core/admin/audit/package-info.java,
    backend/worker/src/main/java/com/zeromail/worker/admin/AdminReadEventPurgeJob.java,
    backend/worker/src/main/java/com/zeromail/worker/admin/AdminAuditChainVerifyJob.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java (lines 1-283 — JSONB columns + bytea hash + immutable audit row),
    backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java (lines 16-48 — INSERT-only @Query nativeQuery=true pattern),
    backend/core/src/main/java/com/zeromail/core/triage/usecases/AuditLogQueryService.java (read-side page query),
    backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java + AuditLogRow.java,
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C5,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §ADMIN-04/05/07 + §ARCH-12,
    CONVENTIONS.md §Privacy logging
  </read_first>
  <behavior>
    - `AdminAuditAction` IdentifiedEnum: ADMIN_LOGIN, ADMIN_PASSKEY_REGISTERED, ADMIN_GRANTED, ADMIN_REVOKED, MASTER_KEY_SET, MASTER_KEY_TESTED, MASTER_KEY_ROTATED, MASTER_KEY_ROTATION_FAILED, CATALOG_SYNC_FETCHED, CATALOG_SYNC_CONFIRMED, MODEL_DISABLED, TENANT_PAUSED, TENANT_DISCONNECTED, TENANT_DELETED, ADMIN_RESPONSE_BODY_BAN_TRIPPED, WEBAUTHN_REPLAY_SUSPECTED, DEAD_LETTER_REQUEUED.
    - `AdminAuditEventEntity`: JPA entity mapping `admin_audit_event`; immutable row (no setters once persisted except via dedicated `AdminAuditWriter.append(...)`); JSONB columns via `@JdbcTypeCode(SqlTypes.JSON)` matching TriageAuditEntity pattern.
    - `AdminAuditEventRepository`: native-query INSERT-only (`@Modifying @Query(nativeQuery=true) INSERT INTO admin_audit_event ... RETURNING id`); `findLatestHmac()`: SELECT hmac_chain_hash FROM admin_audit_event ORDER BY created_at DESC LIMIT 1 FOR UPDATE; paged read for query service.
    - `HmacChainHasher.hash(byte[] previousHash, byte[] rowCanonical)`: HMAC-SHA256 keyed by `zeromail.admin.audit.hmacKekBase64`; deterministic ordering of fields when canonicalizing row (id, actor_user_id, action, target_kind, target_id, before_json, after_json, reason, request_ip, request_id, created_at_epoch_millis).
    - `AdminAuditWriter.append(action, targetKind, targetId, beforeJson, afterJson, reason, requestIp, requestId)`: runs inside caller's @Transactional; calls findLatestHmac() FOR UPDATE; computes new HMAC; inserts row. Reads `AdminContext.currentOrThrow()` for actor_user_id + actor_email. Throws if not in admin scope.
    - `AdminAuditWriter.writeReadEvent(admin, action, tenantId)`: inserts row into `admin_read_event` (no HMAC chain; separate concern); used by AdminTenantAccess (task 8C-01).
    - `AdminAuditQueryService.page(AdminAuditPageQuery)`: paginates admin_audit_event with filters (actor email exact match, action enum, target_kind, target_id UUID prefix, date range from/to); returns AdminAuditPage(rows, nextCursor, totalEstimate).
    - `AuditCsvExporter.streamCsv(query, outputStream)`: streams up to 10,000 rows as CSV with columns (audit_id, actor_email, action, target_kind, target_id, reason, request_ip, created_at_iso); rejects if estimate >10,000 with explicit error pointing to date-range narrowing.
    - Worker `AdminReadEventPurgeJob`: `@Scheduled(cron="0 30 3 * * *")` (3:30 AM daily) deletes rows where created_at < NOW() - INTERVAL '30 days'.
    - Worker `AdminAuditChainVerifyJob`: `@Scheduled(cron="0 0 4 * * *")` (4 AM daily) re-derives chain from row 1; on mismatch logs `event=admin_audit_chain_mismatch atRowId={}` and emits Micrometer counter `admin.audit.chain.mismatch`.
  </behavior>
  <action>
    Implement audit module per PATTERNS §C5. AdminAuditEventEntity uses `@JdbcTypeCode(SqlTypes.JSON)` JSONB columns for before_state_json/after_state_json (mirror TriageAuditEntity); hmac_chain_hash is `byte[]` like args_hash in TriageAuditEntity line 26. Repository INSERT must be native query `RETURNING id, created_at, hmac_chain_hash` so the writer can return a row reference; FOR UPDATE on prior hash row ensures chain ordering under concurrency. HmacChainHasher key reads from ZeroMailCoreProperties.admin.audit.hmacKekBase64 (Base64-decoded; constant-time fail-loud if blank in non-test profile). Privacy logging: `log.info("event=admin_audit_appended action={} actorAdminId={} targetKind={} targetId={}", action.id(), admin.id(), targetKind, targetId)` — NEVER log actor_email or reason or JSON payloads. CSV exporter uses `OpenCSV CSVWriter` (already on classpath via spring-boot dependencies; if not, executor adds `com.opencsv:opencsv` to libs.versions.toml and confirms via Context7 it has Java 25 support) and writes via streaming `StreamingResponseBody` in the controller. Worker jobs in `backend/worker` use `@Component @Profile("!test")` + `@Scheduled`. Chain-verify emits Micrometer counter `admin.audit.chain.mismatch` for OPS dashboards.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "com.zeromail.core.admin.audit.*"</automated>
  </verify>
  <done>
    Audit writer inserts row inside caller transaction; HMAC chain verified across 100 rows (integrity test); rollback of caller transaction rolls back audit row (transactional join); CSV exporter streams ≤10k row file with correct columns; rejecting JSON containing `sk-test123` is NOT done here (sentinel-leak test lives in 8B per MKEY-08, but the audit writer never logs payloads); chain-verify worker re-derives chain successfully on clean data.
  </done>
  <acceptance_criteria>
    - Integration test inserts 100 rows via AdminAuditWriter; `AdminAuditChainVerifyJob.verifyOnce()` returns no mismatch; mutating row #50's reason then re-verifying detects mismatch and emits the Micrometer counter.
    - Calling AdminAuditWriter.append outside AdminContext (no scope) throws IllegalStateException.
    - Rolling back the surrounding @Transactional block also removes the audit row (verified by count before/after rollback in test).
    - `admin_read_event` rows inserted by `writeReadEvent` accumulate without HMAC chain; purge job deletes rows older than 30 days against test fixture with `INTERVAL '31 days'` shift.
    - `AuditCsvExporter.streamCsv` with a fixture of 5 rows produces a 6-line CSV (1 header + 5 rows) with no JSON payload content >200 chars exposed.
    - No log line in test output contains `actor_email` value, JSON `before_state` value, or `reason` text content.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-04: Backend wiring — SecurityConfig admin chain (Spring Security 7 .webAuthn DSL), AdminBindingFilter, EnrollmentTokenGate, AdminBootstrapRunner, GroupedOpenApi split, application.yml admin properties</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java,
    backend/api/src/main/java/com/zeromail/api/security/AdminBindingFilter.java,
    backend/api/src/main/java/com/zeromail/api/security/EnrollmentTokenGate.java,
    backend/api/src/main/java/com/zeromail/api/admin/AdminBootstrapRunner.java,
    backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java,
    backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/AdminRoleGrantService.java,
    backend/api/src/main/resources/application.yml,
    backend/api/src/test/java/com/zeromail/api/admin/AdminChainCookieIsolationTest.java
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (lines 1-67 — existing chain + matcher),
    backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java (lines 1-60 — OncePerRequestFilter + ScopedValue.where wiring),
    backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java (lines 51-253 — existing customizers; extend with GroupedOpenApi),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C1, §C2, §C4, §C15,
    .planning/phases/08-admin-console-operator-tooling/08-RESEARCH.md §Spring Security 7 WebAuthn DSL + §Pitfall 4 (session cookie domain),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §ADMIN-01/02/03/06/10
  </read_first>
  <behavior>
    - `@Order(1) adminChain` bean with `securityMatcher("/api/admin/**", "/webauthn/**", "/login/webauthn/**", "/enroll")`, `.authorizeHttpRequests(a -> a.requestMatchers("/webauthn/**","/login/webauthn/**","/enroll").permitAll().anyRequest().hasRole("ADMIN"))`, `.webAuthn(w -> w.rpName("Zero Mail Admin").rpId("admin.zeromail.com").allowedOrigins("https://admin.zeromail.com"))`, `.userDetailsService(adminUserDetailsService)`, CSRF SPA mode, `HttpStatusEntryPoint(UNAUTHORIZED)` for unauthenticated admin requests, addFilterAfter(adminBindingFilter, AuthorizationFilter.class), addFilterBefore(enrollmentTokenGate, ...) for `/enroll`.
    - Existing chain demoted from `@Order(3)` to `@Order(2)`; no `securityMatcher` (catch-all). User chain receives NO `.webAuthn` config.
    - Spring Session cookie scoped to `Domain=admin.zeromail.com` for admin chain (Pitfall 4) — verified via Context7 `/spring-projects/spring-session` before coding.
    - `AdminBindingFilter extends OncePerRequestFilter`: resolves principal from `SecurityContextHolder.getContext().getAuthentication()`; loads AdminUser via `AdminUserRepository.findByEmail(principal.getName())`; ScopedValue.where(AdminContext.ADMIN, adminUser).run(chain::doFilter). Rejects requests if status != ACTIVE.
    - `EnrollmentTokenGate extends OncePerRequestFilter`: intercepts `/enroll`; reads `?token=` param; calls `EnrollmentTokenService.consume(token, email_query_param)`; on success opens short-lived enrollment session (server-side state via HttpSession attribute `enrollment.adminUserId`, never logged); on failure returns HTTP 410 Gone with body `{"code":"error.admin.enrollment_token_expired"}`.
    - `AdminBootstrapRunner implements CommandLineRunner`, `@Profile("!test")`: for each email in `zeromail.admin.bootstrap-emails`, upsert PENDING_ENROLLMENT row; mint token; print `System.out.println("[ZeroMail Admin Bootstrap] " + email + " enrollment URL: https://admin.zeromail.com/enroll?token=" + tokenHex + " (valid 10 minutes)")` directly, NOT via SLF4J. Skip if row already ACTIVE (idempotent).
    - `AdminRoleGrantService.grant(email)`: writes new PENDING_ENROLLMENT row + mints enrollment token + writes `ADMIN_GRANTED` audit row + returns one-time enrollment URL in the response body of POST /api/admin/grant-admin.
    - `AdminRoleGrantService.revoke(adminUserId, reason)`: updates status='REVOKED' + writes `ADMIN_REVOKED` audit row.
    - `OpenApiConfig` adds `@Bean GroupedOpenApi publicApi()` (pathsToMatch /api/**, pathsToExclude /api/admin/**, group="public") + `@Bean GroupedOpenApi adminApi()` (pathsToMatch /api/admin/**, group="admin").
    - `application.yml` adds `zeromail.admin.bootstrap-emails: []` placeholder, `zeromail.admin.audit.hmac-kek-base64: ${ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64:}` (fail-loud at startup in non-test profile if blank).
    - `AdminChainCookieIsolationTest` enabled (no longer @Disabled): request to `/api/admin/audit/events` without admin session returns 401; user-OAuth-authenticated cookie applied to admin path returns 401 (chain isolation); admin session does not satisfy user paths.
  </behavior>
  <action>
    Modify SecurityConfig.java to add adminChain bean per PATTERNS §C1 excerpt. Spring Security 7 `.webAuthn(...)` DSL signatures MUST be verified via Context7 `/websites/spring_io_spring-security_reference_7_0` before coding — fetch the passkeys reference page (RP config builder methods may differ from training data). Configure Spring Session cookie via `CookieSerializer` bean with `cookieDomain="admin.zeromail.com"` for admin chain (Pitfall 4) — if same JVM serves both subdomains, use distinct cookie names (`SESSION_ADMIN` vs `SESSION`) via separate `SpringSessionBackedSessionRepository` beans OR scope by `cookieDomain`/`cookiePath` — Context7 lookup mandatory before final shape. Demote existing chain to @Order(2). AdminBindingFilter copies TenantBindingFilter shape verbatim but binds AdminUser via AdminContext.run inside a try/catch unwrapping IOException/ServletException from RuntimeException (mirror lines 168-184 of PATTERNS §C2). EnrollmentTokenGate is registered via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` only on the admin chain. AdminBootstrapRunner uses `System.out.println(...)` direct call (NOT via SLF4J — RESEARCH §STDOUT). Audit row for ADMIN_GRANTED includes only `{email, status:'PENDING_ENROLLMENT'}` in after_state_json — NEVER the token. Enable AdminChainCookieIsolationTest via `@SpringBootTest(webEnvironment=RANDOM_PORT) @ActiveProfiles("integration")` slice with `MockMvc` (not real WebAuthn — that is manual per VALIDATION.md). OpenApiConfig: per PATTERNS §C15 excerpt; preserve existing customizers.
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.admin.AdminChainCookieIsolationTest" --tests "com.zeromail.api.arch.AdminChainNoOauth2LoginTest" --tests "com.zeromail.api.arch.AdminControllerPreAuthorizeTest"</automated>
  </verify>
  <done>
    Admin chain bean registers at @Order(1) with securityMatcher and .webAuthn; existing chain demoted to @Order(2); user OAuth flow on zeromail.com unchanged; cookie isolation test green; bootstrap runner prints enrollment URL to STDOUT (captured by test launcher, NOT to log file — verified by absence in `build/test-results/*/std.out` text capture); GroupedOpenApi exposes /v3/api-docs/public and /v3/api-docs/admin separately.
  </done>
  <acceptance_criteria>
    - `curl http://localhost:8080/api/admin/audit/events` without session returns 401; with valid admin session returns 200 (mocked in integration test).
    - `curl http://localhost:8080/v3/api-docs/public` JSON contains no paths starting `/api/admin/`; `/v3/api-docs/admin` JSON contains ONLY paths starting `/api/admin/`.
    - Boot startup with `zeromail.admin.bootstrap-emails=[op@example.com]` prints exactly one line to STDOUT containing `https://admin.zeromail.com/enroll?token=<32-hex>` and NO line containing the token hex appears in `application.log` / SLF4J output.
    - Boot startup with empty `bootstrap-emails` creates zero admin_users rows and prints zero enrollment URLs.
    - Second boot with same config + the PENDING_ENROLLMENT row still present prints NO new URL (token already in memory or row already ACTIVE — idempotent).
    - `AdminChainNoOauth2LoginTest` green: SecurityConfig source has zero `.oauth2Login` calls within adminChain method body and zero `.webAuthn` calls within user chain method body.
    - `AdminChainCookieIsolationTest`: admin session cookie name and admin path return 200 on /api/admin/*; same cookie used against /api/inbox returns 401; user OAuth cookie used against /api/admin/* returns 401.
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-05: Admin REST controllers — AdminAuditController (/api/admin/audit/events + /csv) + AdminRoleGrantsController (/api/admin/grant-admin + admins list + revoke) + DTOs</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminAuditController.java,
    backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminRoleGrantsController.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/audit/AdminAuditEventResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/audit/AdminAuditPageResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/audit/AdminAuditPageRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/grants/GrantAdminRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/grants/GrantAdminResponse.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/grants/RevokeAdminRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/admin/grants/AdminUserSummaryResponse.java,
    backend/api/src/main/java/com/zeromail/api/error/AdminErrorAdvice.java
  </files>
  <read_first>
    backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java (entire — controller + Tag + @PreAuthorize + DTO mapping pattern),
    backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java (page DTO + filter request shape),
    backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java (record + @Schema requiredProperties + static from(...)),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C14,
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §ADMIN-02/03/07/08
  </read_first>
  <behavior>
    - `AdminAuditController @RestController @Tag("admin-audit") @RequestMapping("/api/admin/audit") @PreAuthorize("hasRole('ADMIN')")`:
      - GET `/events?actorEmail=&action=&targetKind=&targetId=&from=&to=&cursor=&limit=` → AdminAuditPageResponse (rows + nextCursor + totalEstimate). Calls AdminAuditQueryService.page(...). Writes one admin_read_event row per distinct filter combo per session (debounced server-side; same filter within 60s = no additional row).
      - GET `/events/csv?actorEmail=&action=&targetKind=&targetId=&from=&to=` → StreamingResponseBody with Content-Disposition `attachment; filename="audit-{from}-{to}.csv"`. Rejects with HTTP 400 + body `{"code":"error.admin.audit_export_too_large"}` if estimate >10,000 rows.
    - `AdminRoleGrantsController @RestController @Tag("admin-grants") @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")`:
      - GET `/admins` → List<AdminUserSummaryResponse> (id, email, status, last_used_at, has_credential boolean). NEVER returns credential_id/public_key_cose bytes.
      - POST `/grant-admin` body `{email: String}` → GrantAdminResponse `{adminUserId, enrollmentUrl, expiresAt}`. Calls AdminRoleGrantService.grant(email). Writes ADMIN_GRANTED audit row.
      - POST `/admins/{id}/revoke` body `{reason: String}` → 204 No Content. Calls AdminRoleGrantService.revoke(id, reason). Writes ADMIN_REVOKED audit row.
    - Bean Validation: email is `@Email`; reason min 8 max 500 chars + custom `@NoSentinelLeak` validator rejecting `sk-`, `sk-ant-`, `AIza`, `sk-or-` substrings (returns HTTP 400 with `{"code":"error.admin.reason_sentinel_leak"}`).
    - AdminErrorAdvice maps AdminAuthException, IllegalStateException (mutex breach), audit-export-too-large into structured JSON error responses without leaking server stack frames.
    - DTOs use `@Schema(requiredProperties = {...})` per CONVENTIONS §3; `@JsonInclude(NON_NULL)` for variant fields.
  </behavior>
  <action>
    Implement controllers per PATTERNS §C14 excerpts. All admin controllers MUST carry class-level `@PreAuthorize("hasRole('ADMIN')")` annotation (enforced by AdminControllerPreAuthorizeTest from Task 8A-01). Controllers resolve actor via `AdminContext.currentOrThrow()` — NEVER `TenantContext` (ARCH-08 mutex; enforced by AdminContextMutexTest). Custom `@NoSentinelLeak` constraint annotation lives in `backend/api/src/main/java/com/zeromail/api/security/validation/NoSentinelLeakValidator.java` (scoped to admin reason fields; case-sensitive on the 4 prefixes). CSV streaming uses `StreamingResponseBody` returning from controller method directly (not `ResponseEntity<Resource>`) so server flushes per row. DTO records under `api/dto/admin/{audit,grants}/`. GrantAdminResponse contains the one-time enrollment URL value (per SPEC ADMIN-03(f)) — this is communicated out-of-band by admin; document carve-out in DTO Javadoc + ensure DTO field name `enrollmentUrl` is NOT in the body-ban regex (it's a URL with token, not a "content" field — but the URL value carries a fresh token, never a master key). Validate that the URL value never appears in audit row's after_state_json (audit row contains only `{email, status:'PENDING_ENROLLMENT'}`, never the URL/token).
  </action>
  <verify>
    <automated>./gradlew :backend:api:test --tests "com.zeromail.api.controllers.admin.*" --tests "com.zeromail.api.arch.AdminControllerPreAuthorizeTest"</automated>
  </verify>
  <done>
    Controllers respond per spec under mocked admin session; CSV export streams correct rows; sentinel-leak rejection on reason works; @PreAuthorize present on every admin controller class (ArchUnit green); GET /admins never returns credential bytes.
  </done>
  <acceptance_criteria>
    - `POST /api/admin/grant-admin {email:"new@example.com"}` returns 200 with body `{adminUserId, enrollmentUrl, expiresAt}`; writes ADMIN_GRANTED audit row with after_state_json={email,status} and reason=null; URL value NOT present in any admin_audit_event row.
    - `POST /api/admin/grant-admin {email:"sk-test@example.com"}` returns 400 with `error.admin.email_invalid` (or sentinel-leak — depending on @Email/regex order; either is acceptable as long as not 500).
    - `POST /api/admin/admins/{id}/revoke {reason:"compromised"}` returns 400 (reason < 8 chars). With `{reason:"compromised hardware key"}` returns 204 + writes ADMIN_REVOKED row.
    - `POST /api/admin/admins/{id}/revoke {reason:"key leaked sk-test123"}` returns 400 with `error.admin.reason_sentinel_leak`.
    - `GET /api/admin/audit/events?from=2026-05-01T00:00:00Z&to=2026-05-20T00:00:00Z` returns paged AdminAuditPageResponse; writes 1 admin_read_event row; same request within 60s writes 0 additional rows.
    - `GET /api/admin/audit/events/csv` with fixture of 3 audit rows returns CSV `Content-Type: text/csv` with 4 lines.
    - `GET /api/admin/admins` response JSON contains no `credentialId` / `publicKeyCose` / `userHandle` field.
    - AdminControllerPreAuthorizeTest green; AdminPathBodyBanTest green (DTO record fields scanned).
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-06: apps/admin Vite + React 19 SPA scaffold — workspace, codegen, admin client, WebAuthn lib, shadcn primitives copy, ADMIN MODE banner, ConfirmTwiceDialog, JsonDiffViewer, /enroll + /login + /dashboard + /audit + /role-grants routes, Playwright + Vitest setup, apps/web ESLint cross-workspace import block</name>
  <files>
    apps/admin/package.json,
    apps/admin/vite.config.ts,
    apps/admin/tsconfig.json,
    apps/admin/index.html,
    apps/admin/src/main.tsx,
    apps/admin/src/App.tsx,
    apps/admin/src/lib/api/admin-client.ts,
    apps/admin/scripts/generate-api.ts,
    apps/admin/src/lib/webauthn.ts,
    apps/admin/src/components/ui/,
    apps/admin/src/components/AdminModeBanner.tsx,
    apps/admin/src/components/ConfirmTwiceDialog.tsx,
    apps/admin/src/components/JsonDiffViewer.tsx,
    apps/admin/src/components/AdminLayout.tsx,
    apps/admin/src/routes/enroll.tsx,
    apps/admin/src/routes/login.tsx,
    apps/admin/src/routes/dashboard.tsx,
    apps/admin/src/routes/audit.tsx,
    apps/admin/src/routes/role-grants.tsx,
    apps/admin/src/features/audit/audit-api.ts,
    apps/admin/src/features/audit/query-keys.ts,
    apps/admin/src/features/audit/use-audit-page.ts,
    apps/admin/src/features/role-grants/role-grants-api.ts,
    apps/admin/src/features/role-grants/query-keys.ts,
    apps/admin/src/features/role-grants/use-admins.ts,
    apps/admin/src/features/role-grants/use-grant-admin.ts,
    apps/admin/src/features/role-grants/use-revoke-admin.ts,
    apps/admin/src/styles/globals.css,
    apps/admin/src/test-setup.ts,
    apps/admin/playwright.config.ts,
    apps/admin/e2e/enroll-and-login.spec.ts,
    apps/admin/e2e/audit-and-grant.spec.ts,
    pnpm-workspace.yaml,
    turbo.json,
    apps/web/eslint.config.mjs
  </files>
  <read_first>
    apps/web/scripts/generate-api.ts (lines 1-43 — codegen idiom),
    apps/web/lib/api/client.ts (lines 1-15 — openapi-fetch client),
    apps/web/components/ui/button.tsx + alert-dialog.tsx + tabs.tsx + table.tsx + dialog.tsx + input.tsx + textarea.tsx + sonner.tsx + sidebar.tsx + tooltip.tsx + badge.tsx + skeleton.tsx + card.tsx (all primitives listed in UI-SPEC Component Inventory — copy byte-identical),
    apps/web/app/globals.css (`.zm-proto` palette block — copy + extend with `--warning-soft`, `--ink-2`, `--amber`),
    apps/web/components.json (preset `base-nova` for `pnpm dlx shadcn@latest init` matching),
    .planning/phases/08-admin-console-operator-tooling/08-PATTERNS.md §C16,
    .planning/phases/08-admin-console-operator-tooling/08-UI-SPEC.md (entire),
    .planning/phases/08-admin-console-operator-tooling/08-PROTOTYPE.html (visual reference),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §ADMIN-06/07/08/10,
    CONVENTIONS.md §Frontend feature API/hooks/query keys
  </read_first>
  <behavior>
    - `apps/admin/package.json` declares `@zeromail/admin` workspace; deps: vite@^7, @vitejs/plugin-react@^5, react@19, react-dom@19, react-router-dom@^6.27, @tanstack/react-query@^5.100, tailwindcss@^4.2, @tailwindcss/postcss@^4.2, openapi-fetch@^0.17, openapi-typescript@^7.13, @simplewebauthn/browser@^11, lucide-react@latest, sonner@^1, class-variance-authority, clsx, tailwind-merge, recharts (for queue/spend later); devDeps: vitest@^3, @testing-library/react@^16, @playwright/test@^1.51, typescript@^5.8.
    - `pnpm --filter @zeromail/admin build` produces `apps/admin/dist/` <500KB gzipped (per ADMIN-06 acceptance). `pnpm --filter @zeromail/admin dev` runs Vite dev server on port 5174 (apps/web uses 3000).
    - `apps/admin/scripts/generate-api.ts` fetches `http://localhost:8080/v3/api-docs/admin` → writes `apps/admin/src/lib/api/admin-schema.d.ts` via openapi-typescript.
    - `apps/admin/src/lib/api/admin-client.ts` exports `api = createClient<paths>({baseUrl: 'https://api.zeromail.com', credentials:'include'})` (per PATTERNS §C16 fork of apps/web/lib/api/client.ts).
    - `apps/admin/src/lib/webauthn.ts` wraps `@simplewebauthn/browser` `startRegistration({optionsJSON})` and `startAuthentication({optionsJSON})` (v10+ wrapper shape per Context7); typed inputs from admin-schema.d.ts WebAuthn endpoint responses.
    - shadcn primitives copied byte-identical from apps/web/components/ui/ to apps/admin/src/components/ui/ — first run `pnpm dlx shadcn@latest init` in apps/admin with preset `base-nova` so components.json matches apps/web before copying.
    - `<AdminModeBanner>`: 40px sticky top, bg `var(--warning-soft)` #FDE8BA, border-bottom 1px `var(--amber)` #E3A023, text `var(--ink-2)` #4F496B, copy `ADMIN MODE  •  actions affect real tenants  •  signed in as {admin.email}  •  {env}`. Not dismissible. z-index 60. Renders inside AdminLayout for all authenticated routes; NOT rendered on `/login` or `/enroll`.
    - `<ConfirmTwiceDialog>` (UI-SPEC §Destructive action confirmations): step1 = reason textarea 8-500 chars with client-side sentinel-leak regex reject + `Continue` button (secondary); step2 = "Type {confirmationToken} to confirm" input + final destructive button. On submit calls onConfirm(reason); on success surfaces toast with audit row link `/audit?id={auditId}`.
    - `<JsonDiffViewer>`: before/after JSON tree with collapsible nodes; pure component; no external diff lib.
    - `/enroll?token={hex}` route: reads token from URL; renders centered card "Register passkey for {email}" with email input + `Register passkey` button → POSTs `/login/webauthn/options` via webauthn.ts startRegistration → on success transitions to /login.
    - `/login` route: centered card with email input + `Sign in with passkey` → startAuthentication → on success navigates to `/`.
    - `/` (dashboard): KpiCard stubs + recent-audit list (queries `/api/admin/audit/events?limit=10`).
    - `/audit`: filter bar (actor/action/target/date) + paginated table with inline `<JsonDiffViewer>` expand on row click + CSV export button (streams from `/api/admin/audit/events/csv`).
    - `/role-grants`: admin list table + `Grant admin` dialog (email input + result modal showing one-time enrollment URL with `Copy URL` button) + `Revoke admin` button per row using `<ConfirmTwiceDialog>` with `reason` + admin email as step-2 token.
    - Feature folders per CONVENTIONS §8: `features/audit/{api,query-keys,use-audit-page}`, `features/role-grants/{api,query-keys,use-admins,use-grant-admin,use-revoke-admin}`. Use generated types from `admin-schema.d.ts`, NEVER hand-write mirror DTOs. Use `api.GET`/`api.POST` typed client, NOT raw fetch.
    - `apps/admin/playwright.config.ts` + `apps/admin/e2e/enroll-and-login.spec.ts` (uses Playwright virtual authenticator) + `apps/admin/e2e/audit-and-grant.spec.ts` (login as seeded admin via cookie injection + verify grant flow returns one-time URL + revoke flow opens ConfirmTwiceDialog with email-typed token).
    - `apps/admin/vitest.config.ts` + `src/test-setup.ts` (Vitest + Testing Library jsdom).
    - `pnpm-workspace.yaml` adds `apps/admin`; `turbo.json` adds `@zeromail/admin#build`, `@zeromail/admin#test`, `@zeromail/admin#e2e` pipelines.
    - `apps/web/eslint.config.mjs` adds `no-restricted-imports` rule blocking imports from `apps/admin/**` (per PATTERNS §C16 acceptance).
  </behavior>
  <action>
    Run `pnpm dlx shadcn@latest init` inside `apps/admin` with `base-nova` preset to seed components.json + globals.css base, then copy each primitive from apps/web/components/ui/ byte-identical (per UI-SPEC §Component Inventory list — 24 primitives). Extend globals.css with `.zm-proto` palette block + `--warning-soft: #FDE8BA`, `--amber: #E3A023`, `--ink-2: #4F496B`, `--ring: #867AEB` tokens to match UI-SPEC §Color. AdminModeBanner copy is locked verbatim per UI-SPEC §Copywriting line 124. ConfirmTwiceDialog props: `{open, onOpenChange, actionLabel, targetLabel, consequences: string[], confirmationToken: string, finalButtonLabel, variant?: 'destructive'|'warning', onConfirm(reason): Promise<{auditId:string}>}`. WebAuthn ceremonies via Context7-confirmed `@simplewebauthn/browser` v11 `startRegistration({optionsJSON})` shape. Generate admin-schema.d.ts via `pnpm --filter @zeromail/admin generate-api` (run after backend boots with /v3/api-docs/admin available). React Router 6 wraps AdminLayout around authenticated routes; Layout renders `<AdminModeBanner>` + top bar + left sidebar (collapsible per UI-SPEC §Spacing); auth guard redirects unauthed users to /login. Playwright virtual authenticator setup per Context7 `/microsoft/playwright` "WebAuthn" guide. ESLint cross-workspace block: pattern `**/apps/admin/**` with message "apps/web cannot import from apps/admin — admin schema types stay out of public bundle (ADMIN-06)".
  </action>
  <verify>
    <automated>pnpm install && pnpm --filter @zeromail/admin build && pnpm --filter @zeromail/admin test:unit && pnpm --filter @zeromail/web lint && (cd apps/admin && pnpm exec playwright test --grep "enroll-and-login|audit-and-grant")</automated>
  </verify>
  <done>
    apps/admin builds standalone <500KB gzipped; ESLint blocks any apps/web import from apps/admin; admin-schema.d.ts codegens; ConfirmTwiceDialog + JsonDiffViewer + AdminModeBanner render; /enroll + /login complete WebAuthn ceremony with Playwright virtual authenticator; /audit lists events + exports CSV; /role-grants creates + revokes admins with confirm-twice + reason.
  </done>
  <acceptance_criteria>
    - `pnpm --filter @zeromail/admin build` exits 0; `du -k apps/admin/dist/assets/*.js | awk '{sum+=$1}END{print sum}'` <500 (KB) gzipped equivalent.
    - `grep -c "from ['\\"]@zeromail/admin" apps/web/{app,components,lib,features}/**/*.{ts,tsx} 2>/dev/null | grep -v ':0$'` returns empty (zero imports of admin workspace from apps/web).
    - `grep -c "admin-schema" apps/web/lib/api/*.ts` returns 0 (no admin schema reference in public client).
    - `grep -n "ADMIN MODE" apps/admin/src/components/AdminModeBanner.tsx` returns the locked banner copy.
    - Playwright spec `enroll-and-login.spec.ts` green via virtual authenticator (registration → assertion round-trip).
    - Playwright spec `audit-and-grant.spec.ts`: grant admin returns dialog with one-time URL + Copy button; revoke admin opens ConfirmTwiceDialog with `Type "admin@example.com" to confirm` step.
    - Vitest unit test on `<ConfirmTwiceDialog>`: typing reason `key:sk-test123` shows inline error; reason `compromised hardware` enables `Continue`; second-step token mismatch keeps final button disabled.
    - `pnpm --filter @zeromail/web lint` green (ESLint no-restricted-imports rule applies but no violations exist).
  </acceptance_criteria>
</task>

<task type="auto" tdd="true">
  <name>Task 8A-07: docker-compose 9Router sidecar + NPM proxy + v1.2 deploy runbook (OPS-INFRA-01/02/03)</name>
  <files>
    docker-compose.yml,
    docs/ops/v1.2-deploy.md,
    docs/ops/admin-load-probe.md
  </files>
  <read_first>
    docker-compose.yml (existing — current postgres + redis services),
    docs/ops/ (any existing runbook for naming conventions),
    .planning/phases/08-admin-console-operator-tooling/08-SPEC.md §OPS-INFRA-01/02/03 (full Current/Target/Acceptance),
    .planning/phases/08-admin-console-operator-tooling/08-CONTEXT.md §D-22 (merge gate scope: compose + runbook only, live migration is deploy step)
  </read_first>
  <behavior>
    - `docker-compose.yml` adds service `9router` using image `decolua/9router:latest`, bound `127.0.0.1:20128:8080`, env `REQUIRE_API_KEY=true`, `JWT_SECRET=${ZEROMAIL_9ROUTER_JWT_SECRET}`, `INITIAL_PASSWORD=${ZEROMAIL_9ROUTER_INITIAL_PASSWORD}`, `AUTH_COOKIE_SECURE=true`, volume `/opt/zeromail/9router-data:/data` for SQLite.
    - `docker-compose.yml` adds service `nginx-proxy-manager` using `jc21/nginx-proxy-manager:latest`, ports `80:80`, `443:443`, `81:81` (admin UI), volumes `/opt/zeromail/npm-data:/data`, `/opt/zeromail/npm-letsencrypt:/etc/letsencrypt`.
    - `docker compose config` validates without error.
    - `docs/ops/v1.2-deploy.md` covers 5 sections per OPS-INFRA-02/03:
      1. Pre-migration backup of hand-managed nginx config.
      2. NPM container boot + parallel routing test on staging port.
      3. DNS cutover for zeromail.com + admin.zeromail.com.
      4. Rollback path (stop NPM, restore nginx).
      5. Post-migration backup procedure for NPM `/data` + 9Router SQLite volume.
    - Runbook documents OAuth callback URL preservation (bit-for-bit identical: `https://zeromail.com/login/oauth2/code/google`).
    - Runbook documents 9Router first-run: default password reset, API-key generation, provider account connection.
    - Runbook documents lost-passkey shell recovery: `ssh root@vps` → `docker compose exec api psql -U zeromail_app zeromail` → `UPDATE admin_users SET status='PENDING_ENROLLMENT', credential_id=NULL, public_key_cose=NULL, signature_counter=0 WHERE email='lost@example.com'` → `docker compose restart api` → capture STDOUT enrollment URL.
    - Runbook documents bootstrap STDOUT capture: `docker compose up api 2>&1 | tee /tmp/admin-bootstrap.log` then `grep enrollment /tmp/admin-bootstrap.log` (operator deletes /tmp file after URL captured).
    - Optional IP allowlist for admin.zeromail.com via NPM Access List documented in §IP allowlist (per D-23).
    - `docs/ops/admin-load-probe.md` scaffold: k6 script outline for concurrent body-ban filter probe (referenced by VALIDATION §Manual-Only).
  </behavior>
  <action>
    Edit docker-compose.yml: append services per OPS-INFRA-01/02 SPEC. Use Docker Compose v3 schema matching existing services. Bind 9Router to loopback only (Acceptance: not reachable from public internet). NPM exposes 80/443 publicly + 81 only when admin SSH-tunneled (document in runbook). Write `docs/ops/v1.2-deploy.md` as a numbered runbook with command blocks (`docker compose ...`, `psql ...`) — use POSIX shell syntax; document Windows VPS variants only if user uses Windows VPS (CLAUDE.md indicates single VPS, OS unspecified — default to Linux). Per D-22 merge gate scope: this task ships compose + runbook only; live migration is a separate deploy step. `docs/ops/admin-load-probe.md` is a scaffold (~30 lines) with k6 script + invocation command for VALIDATION manual-only check.
  </action>
  <verify>
    <automated>docker compose config && test -s docs/ops/v1.2-deploy.md && grep -q "9router-data" docker-compose.yml && grep -q "nginx-proxy-manager" docker-compose.yml && grep -Eqi "(rollback|backup|first-run|lost.?passkey|oauth callback)" docs/ops/v1.2-deploy.md</automated>
  </verify>
  <done>
    docker compose config validates; runbook covers all 5 sections + first-run + rollback + backup + lost-passkey + bootstrap STDOUT capture + IP allowlist; admin-load-probe.md scaffold present.
  </done>
  <acceptance_criteria>
    - `docker compose config | grep '^services:' -A 1000 | grep -E 'postgres|redis|9router|nginx-proxy-manager|api|worker' | wc -l` ≥ 4 (all required services present).
    - `docker compose config` exit code 0 (no YAML or env interpolation errors).
    - 9router service env contains `REQUIRE_API_KEY=true` and `AUTH_COOKIE_SECURE=true` (grep both literals in docker-compose.yml).
    - `docs/ops/v1.2-deploy.md` line count ≥ 100 and contains the 5 numbered sections (verified by `grep -E '^(## |### )' docs/ops/v1.2-deploy.md | wc -l` ≥ 5).
    - Runbook contains the literal `lost-passkey` (or similar heading) recovery psql command using `UPDATE admin_users SET status='PENDING_ENROLLMENT'`.
    - Runbook documents NPM Access List as the IP allowlist mechanism (per D-23).
    - Live VPS cutover is explicitly noted as out-of-scope-for-merge in the runbook intro (per D-22).
  </acceptance_criteria>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 8A-08 (CHECKPOINT): Manual WebAuthn ceremony smoke test + bootstrap STDOUT capture</name>
  <what-built>
    8A backend foundation + apps/admin SPA + docker-compose deploy artifacts. WebAuthn enrollment + login ceremonies, append-only audit infrastructure, admin role grants, ADMIN MODE banner, ConfirmTwiceDialog, GroupedOpenApi split, ArchUnit gates, db trigger + grants, runbook.
  </what-built>
  <how-to-verify>
    1. Start backend with bootstrap email configured:
       `ZEROMAIL_ADMIN_BOOTSTRAP_EMAILS=you@example.com ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64=$(openssl rand -base64 32) ./gradlew :backend:api:bootRun 2>&1 | tee /tmp/zm-boot.log`
    2. Confirm STDOUT contains exactly one line: `[ZeroMail Admin Bootstrap] you@example.com enrollment URL: https://admin.zeromail.com/enroll?token=<32hex> (valid 10 minutes)`.
    3. Confirm `grep -i 'enrollment' application.log` returns ZERO matches (token never in log file).
    4. In another shell: `pnpm --filter @zeromail/admin dev` and open `http://localhost:5174/enroll?token=<32hex>&email=you@example.com`.
    5. Complete WebAuthn registration ceremony on physical authenticator (Touch ID / Windows Hello / YubiKey).
    6. Navigate to `http://localhost:5174/login`, sign in with passkey, confirm ADMIN MODE banner renders 40px amber strip with locked copy.
    7. Open `/audit` — confirm at least one row exists (ADMIN_PASSKEY_REGISTERED + ADMIN_LOGIN).
    8. Open `/role-grants`, click `Grant admin`, enter a second email, confirm one-time URL dialog appears with Copy button; confirm second admin can complete enrollment.
    9. Click `Revoke admin` on the second admin row, type the email in the step-2 token field, submit with reason `decommissioning test admin`, verify toast with audit-row link.
    10. `psql -c "UPDATE admin_audit_event SET reason='tampered' WHERE id=(SELECT id FROM admin_audit_event LIMIT 1)"` — confirm Postgres raises `admin_audit_event is append-only` exception.
    11. Confirm `docker compose config` exits 0 against the merged docker-compose.yml.
  </how-to-verify>
  <resume-signal>Type "approved" if all 11 checks pass; otherwise describe which step failed and the error output</resume-signal>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Internet → NPM (443) | Public TLS termination; admin.zeromail.com vs zeromail.com routed to same backend/api JVM |
| NPM → backend/api (8080) | Trusted internal hop; relies on `X-Forwarded-*` headers (Pitfall 5) |
| Browser → /api/admin/** | WebAuthn-bound admin session; chain-isolated from /api/** user session |
| Browser → /api/** (user) | Google OAuth bundled session; chain-isolated from /api/admin/** |
| backend/api → admin_users / admin_audit_event | Same-JVM JPA; append-only invariants enforced at DB layer |
| AdminBootstrapRunner → STDOUT | One-time enrollment token leaves process via STDOUT only; never SLF4J |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-08-01 | Spoofing | admin session cookie | mitigate | Spring Session cookie scoped to `Domain=admin.zeromail.com` only; separate cookie name from user session (D-02 subdomain); `Secure; HttpOnly; SameSite=Lax`; verified via Context7 `/spring-projects/spring-session` before coding |
| T-08-02 | Tampering | admin_audit_event row | mitigate | DB-level REVOKE UPDATE,DELETE + Postgres BEFORE UPDATE OR DELETE trigger raising EXCEPTION regardless of role; HMAC-SHA256 chain re-derived nightly with Micrometer alert metric |
| T-08-03 | Tampering | bootstrap enrollment token | mitigate | Token printed to STDOUT only via System.out.println (bypassing SLF4J); held in-memory ConcurrentHashMap with 10-min TTL; one-time consumption; never persisted to disk/log/DB |
| T-08-04 | Repudiation | admin actions | mitigate | Every admin state mutation writes admin_audit_event row in same transaction (same-tx rollback on state mutation failure); HMAC chain prevents silent insertion/removal |
| T-08-05 | Information Disclosure | admin response body leak (placeholder until 8C) | mitigate | AdminPathBodyBanTest ArchUnit on `..controllers.admin..`, `..core.admin..projection..`, `..api.dto.admin..` (trivially green in 8A; tightens in 8C); response body never includes JSONB payload contents beyond audited fields |
| T-08-06 | Information Disclosure | WebAuthn credential bytes via /admins endpoint | mitigate | AdminUserSummaryResponse DTO excludes credential_id, public_key_cose, user_handle; returns only `{id, email, status, last_used_at, has_credential}` |
| T-08-07 | Denial of Service | enrollment token brute-force | mitigate | 32-byte hex token (256-bit entropy); 10-min TTL; one-time consumption; rate-limit on /enroll endpoint (10 req/IP/min via Redis bucket, planned in 8B alongside MKEY rate limit) |
| T-08-08 | Denial of Service | admin login brute-force | accept | WebAuthn protocol inherently resists brute force (signed challenge); userVerificationRequirement=REQUIRED blocks silent passkeys; no rate-limit needed in 8A |
| T-08-09 | Elevation of Privilege | user → admin via cookie replay | mitigate | Two separate SecurityFilterChain beans with non-overlapping securityMatcher; admin chain uses .webAuthn(), user chain uses .oauth2Login(); ArchUnit AdminChainNoOauth2LoginTest enforces split; AdminChainCookieIsolationTest covers replay scenario |
| T-08-10 | Elevation of Privilege | TenantContext leak into admin scope | mitigate | AdminContext.run() throws if TenantContext.TENANT.isBound() and vice versa; AdminContextMutexTest ArchUnit forbids admin packages from importing TenantContext (whitelist only AdminTenantAccess in 8C) |
| T-08-11 | Information Disclosure | NPM forwards Host header crafted for cookie scope confusion | mitigate | NPM proxy config in runbook pins Host header to `$server_name`; admin chain validates Origin matches `https://admin.zeromail.com` via WebAuthn allowedOrigins; runbook documents `X-Forwarded-Host` trust boundary |
| T-08-12 | Tampering | WebAuthn signCount replay (cloned authenticator) | mitigate | WebAuthnCredentialStore rejects assertions where reported signCount ≤ stored signature_counter; writes WEBAUTHN_REPLAY_SUSPECTED audit row; admin_users.signature_counter updated only on monotonic increase |
| T-08-13 | Information Disclosure | one-time enrollment URL via grant-admin response leaks to logs | mitigate | URL value emitted in HTTP response body only; never logged at API layer (privacy logging format forbids); audit row's after_state_json contains only `{email, status}`, never URL/token |
| T-08-SC | Tampering | npm/pnpm package installs (apps/admin scaffolding) | mitigate | RESEARCH.md package legitimacy audit gate. ALL `@simplewebauthn/browser`, openapi-fetch, openapi-typescript packages have signed maintainers + >10k weekly downloads + npmjs.com verified; treated as [VERIFIED]. No new transitive dependencies of unknown provenance — if any `[ASSUMED]`/`[SUS]` markers surface during `pnpm install`, executor inserts a blocking `<task type="checkpoint:human-verify" gate="blocking-human">` before proceeding |

</threat_model>

<verification>

After all tasks complete, run end-to-end:

```bash
# Backend
./gradlew :backend:core:test :backend:api:test :backend:worker:test
./gradlew :backend:core:liquibaseUpdate -Pdb=local
mcp__postgres__execute_sql "SELECT count(*) FROM admin_users WHERE status='ACTIVE'"  # expect >= 1 after bootstrap
mcp__postgres__execute_sql "UPDATE admin_audit_event SET reason='x' WHERE id=(SELECT id FROM admin_audit_event LIMIT 1)"  # expect: ERROR 23514

# Frontend
pnpm install
pnpm --filter @zeromail/admin build
du -k apps/admin/dist/assets/*.js | awk '{s+=$1}END{print s" KB"}'  # expect < 500
pnpm --filter @zeromail/admin test:unit
pnpm --filter @zeromail/admin e2e -- --grep "enroll-and-login|audit-and-grant"
pnpm --filter @zeromail/web lint

# ArchUnit + grep gates
./gradlew :backend:core:test --tests "*Admin*Arch*"
./gradlew :backend:api:test --tests "*Admin*Arch*"

# Repo-wide Gmail send call-site invariant (must still be 1)
grep -rn --include='*.java' 'Gmail.Users.Messages.send\|gmailMessages\.send' backend/ | grep -v '^#' | wc -l
# expect: 1 (the existing AssistantSendExecutor in v1.1)

# OpenAPI split
curl -s http://localhost:8080/v3/api-docs/public | jq '.paths | keys[]' | grep -c '/api/admin' # expect 0
curl -s http://localhost:8080/v3/api-docs/admin  | jq '.paths | keys[]' | grep -c '/api/admin' # expect > 0

# Compose
docker compose config | head -1   # expect "services:" YAML root

# Runbook
test -s docs/ops/v1.2-deploy.md && wc -l docs/ops/v1.2-deploy.md  # expect ≥ 100
```

</verification>

<success_criteria>

- [ ] Liquibase 048/049/050 deploy + db.changelog-master.yaml includes 3 new files
- [ ] `admin_users` UPDATE allowed (last_used_at), DELETE forbidden (REVOKE applied)
- [ ] `admin_audit_event` BEFORE UPDATE OR DELETE trigger raises exception
- [ ] `admin_read_event` 30-day purge job exists in backend/worker
- [ ] AdminContext mutex with TenantContext enforced (unit test + ArchUnit)
- [ ] Admin chain `@Order(1)` with `.webAuthn(rpId=admin.zeromail.com, userVerificationRequirement=REQUIRED)`
- [ ] User chain demoted to `@Order(2)`, NO `.webAuthn` config (ArchUnit green)
- [ ] AdminBootstrapRunner prints enrollment URL to STDOUT only (not SLF4J)
- [ ] EnrollmentTokenGate filter consumes one-time token on `/enroll` access
- [ ] `AdminAuditWriter.append(...)` writes row in caller transaction with HMAC chain
- [ ] `AdminAuditChainVerifyJob` re-derives chain nightly + Micrometer counter on mismatch
- [ ] GroupedOpenApi split: `/v3/api-docs/public` excludes `/api/admin/**`, `/v3/api-docs/admin` includes only `/api/admin/**`
- [ ] `AdminAuditController` paginates + filters + CSV exports ≤10k rows
- [ ] `AdminRoleGrantsController` returns one-time enrollment URL on POST `/grant-admin`; revoke flow audited
- [ ] `AdminUserSummaryResponse` never exposes credential bytes
- [ ] Custom `@NoSentinelLeak` validator rejects `sk-`/`sk-ant-`/`AIza`/`sk-or-` substrings in reason fields
- [ ] `apps/admin` Vite + React 19 SPA builds <500KB gzipped
- [ ] `apps/web` ESLint blocks any import from `apps/admin/**`
- [ ] ADMIN MODE banner 40px sticky with locked copy and amber palette
- [ ] `<ConfirmTwiceDialog>` step machine with reason 8-500 chars + sentinel-leak guard + typed-token step
- [ ] `/enroll` + `/login` + `/dashboard` + `/audit` + `/role-grants` routes implemented
- [ ] Playwright virtual authenticator e2e green
- [ ] `docker compose config` exits 0 with 9router + nginx-proxy-manager services
- [ ] `docs/ops/v1.2-deploy.md` runbook covers 5 OPS-INFRA-03 sections + lost-passkey shell recovery + bootstrap STDOUT capture
- [ ] All 8A ArchUnit gates green: AdminContextMutexTest, AdminPathBodyBanTest, AdminSendBanTest, AdminControllerPreAuthorizeTest, AdminChainNoOauth2LoginTest, AdminChainCookieIsolationTest, AuditChainIntegrityTest
- [ ] Repo-wide grep gate still asserts exactly 1 Gmail send call site
- [ ] Human checkpoint 8A-08 approved with all 11 manual checks
- [ ] (reviews-pass) `docs/ops/admin-interface-freeze.md` exists and is cited by SecurityConfig + EnrollmentSessionController + apps/admin enroll route
- [ ] (reviews-pass) `admin_audit_event.chain_index BIGSERIAL UNIQUE` + `canonical_timestamp_ms BIGINT NOT NULL` columns deployed; chain re-derive uses chain_index ordering
- [ ] (reviews-pass) `/enroll` is SPA-only; backend validates token via `POST /api/admin/enrollment/session`; `EnrollmentTokenGate` filter removed
- [ ] (reviews-pass) Two `CookieSerializer` beans with distinct cookie names (`SESSION_ADMIN` vs `SESSION_USER`) + namespaced Redis session repositories; cross-cookie 401 verified
- [ ] (reviews-pass) Liquibase verification runs against Postgres Testcontainer (not H2); 4 Postgres-specific assertions green
- [ ] (reviews-pass) `AdminChainIntegrationTest` MockMvc-based green; ArchUnit source-parse test kept as lightweight complement
- [ ] (reviews-pass) `turbo.json` declares `outputs: ["dist/**"]` for `@zeromail/admin#build`; `apps/web/eslint.config.mjs` also blocks `**/admin-schema*` imports
- [ ] (reviews-pass) Runbook §Backup defaults to `tar | gpg | rsync`; §Bootstrap warns against detached mode; §Security Considerations documents heap-dump + JMX + memory-swap mitigations
- [ ] (reviews-pass) Liquibase numbering offsets: 8A=048–057, 8B=058–067, 8D=068–077, 8E=078+
- [ ] (cycle-3) `docs/ops/admin-shared-file-ownership.md` exists with ≥6 ownership rows covering SecurityConfig, ChatModelCacheEvictionListener, db.changelog-master.yaml, apps/admin __root.tsx, OpenApiConfig, application.yml
- [ ] (cycle-3) `docs/ops/admin-interface-freeze.md` §Spring Session API section pins repository/cookie/namespace API names against Spring Session 4 (Boot 4) + Last-verified timestamp line present
- [ ] (cycle-3) `Phase8E2ESmokeTest` capstone test exists in `backend/api/src/test/java/com/zeromail/api/admin/`; tagged `@Tag("phase8-e2e")`; covers bootstrap→enroll→login→set-master-key→catalog-sync→view-tenant→requeue→view-spend
- [ ] (cycle-3) Stale-token grep scrub clean: 0 `EnrollmentTokenGate` references; 0 `-Pdb=h2` in verify commands; numbering matches R-8A-H10 offsets

</success_criteria>

<output>
Create `.planning/phases/08-admin-console-operator-tooling/08-8A-SUMMARY.md` when done.
</output>

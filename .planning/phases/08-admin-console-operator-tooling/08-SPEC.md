# Phase 8: Admin Console & Operator Tooling — Specification

**Created:** 2026-05-19
**Last amended:** 2026-05-19 (WebAuthn admin auth pivot during discuss-phase)
**Ambiguity score:** 0.14 (gate: ≤ 0.20)
**Requirements:** 42 locked
**Merge note:** Consolidates original v1.2 Phase 8 (foundation, 15 reqs) and original Phase 9 (operator surface, 25 reqs) into a single phase per user directive during spec-phase 2026-05-19. Former Phase 10 renumbered → Phase 9.
**Pivot note:** During /gsd:discuss-phase 8 on 2026-05-19, admin auth shape was re-locked from "Google OAuth bundled + `users.role` column + `GrantedAuthoritiesMapper`" to "WebAuthn passkey + separate `admin_users` table + separate `apps/admin` Vite+React frontend on `admin.zeromail.com`". Driver: best-practice research (2026 industry standard = passkeys; HTTP Basic deprecated; Google-OAuth-only admin couples admin compromise surface to user IdP). ADMIN-01/02/03/06 + ARCH-08 rewritten; ADMIN-09 + ADMIN-10 added.

## Goal

An operator (admin user) can deploy v1.2 infrastructure, sign in to `admin.zeromail.com` using a hardware-bound WebAuthn passkey (no Google OAuth, no password), reach a `/admin/*` console gated at the SecurityFilterChain level with full append-only audit, configure all 6 LLM providers with master keys, curate the per-feature model catalog via 3-step Sync-from-`/models`, and inspect tenant health / worker queue / platform LLM spend — with zero tenant-content leakage (no email body, no chat content, no prompts/completions) and zero master-key byte leakage (no `sk-` / `sk-ant-` / `AIza` / `sk-or-` sentinel ever in logs, response bodies, audit rows, exceptions, or YAML). User-facing app (`zeromail.com`) carries zero RBAC concept and zero admin schema types in its bundle — admin shape is fully decoupled at frontend, auth, and identity-store layers from user shape.

## Background

**What exists today (v1.1 baseline):**

- Spring Boot 4.0.6 + Spring Security 7.0.5 + Spring Session Redis + bundled Google OAuth (Phase 01.5 — `GoogleAuthorizationRequestResolver` finalized the one-leg login + Gmail consent flow).
- Single `SecurityFilterChain` bean (`@Order(3)`) in `backend/api` matches `/api/**` with OAuth2 login + `TenantBindingFilter`.
- `users` table without a `role` column; **no admin identity store of any kind today** — admin elevation does not exist in v1.1.
- Spring Security 7 ships `.webAuthn(...)` DSL natively (Spring Security 6.4+ feature, present in 7.0.5) — **no new dependency** required to add passkey auth.
- `controllers/` package has no `admin/` sub-package; no `/api/admin/**` routes.
- `apps/web/app/` has `(auth)`, `(public)`, `(protected)/(app)`, `(protected)/onboarding` route groups for users — no admin frontend exists today; `apps/admin` does not exist as a sibling Next.js or Vite app.
- AES-GCM `RefreshTokenCipher` exists for OAuth refresh-token encryption; no `llm_provider_master_key` table; no `ProviderMasterKeyResolver`; LLM model selection is hardcoded per feature.
- No catalog tables (`provider_catalog`, `model_catalog`, `feature_binding`); model IDs live in YAML config.
- VPS reverse proxy is hand-managed nginx; no NPM container; no 9Router sidecar in `docker-compose.yml`.
- `outbox` + `processing_job` tables exist (Postgres queue from v1.0) but no `/admin/queue` view.
- `llm_call_audit` table exists (v1.1 ARCH-04 expanded for chat) but no `/admin/spend` view.
- `triage_audit` + `assistant_send_audit` exist; **no** `admin_audit_event` or `admin_read_event`.
- ArchUnit infrastructure ships (`ChatSendCallSiteTest`, `LlmAdapterConfinementTest`); **no** `AdminPathBodyBanTest` or admin-controller annotation gate yet.
- Spring AI 2.0.0-M6 OpenAI/Anthropic/Google/DeepSeek adapters confined to `core.llm.gateway.springai`; no admin-side `/models` HTTP client.
- `springdoc-openapi` emits single OpenAPI document; no `GroupedOpenApi` split.

**What triggers this work:**

- Zero Mail has no operator surface — every config change today requires SSH + Liquibase + manual restart. Solo-operator pain.
- v1.0 LLM model defaults are hardcoded; switching providers or adding new models requires a Java code change.
- BYOK users have no way for the platform operator to swap the platform-default model without affecting their key choice.
- No mechanism for the operator to inspect tenant health (token refresh failures, watch renewal gaps) without `psql` against prod.
- No append-only audit trail for operator actions — current trust posture is "trust SSH discipline".
- Pre-launch hardening: master-key bytes must never leak; tenant content (email body, chat) must never leak to operator surface.

## Requirements

### Infrastructure (OPS-INFRA)

1. **9Router sidecar in docker-compose**: docker-compose ships a 9Router sidecar service definition.
   - Current: `docker-compose.yml` has no 9Router service
   - Target: `decolua/9router:latest` service in `docker-compose.yml`, bound to loopback `127.0.0.1:20128`, with `REQUIRE_API_KEY=true`, persistent SQLite volume at `/opt/zeromail/9router-data`, `JWT_SECRET` + `INITIAL_PASSWORD` env overrides, `AUTH_COOKIE_SECURE=true`
   - Acceptance: `docker compose config` validates; `docker compose up 9router` boots; `curl 127.0.0.1:20128/health` returns 200; sidecar not reachable from public Internet
   - REQ-ID: OPS-INFRA-01

2. **NPM proxy service in docker-compose + runbook**: NPM container service definition + zero-downtime migration runbook.
   - Current: VPS reverse proxy is hand-managed nginx serving `apps/web` + `/api/*`; no NPM
   - Target: `jc21/nginx-proxy-manager` service in `docker-compose.yml`; `docs/ops/v1.2-deploy.md` documents zero-downtime migration steps from manual nginx → NPM, including route config for `web`, `api`, `9router-dashboard`, Let's Encrypt auto-renewal via NPM, and OAuth callback URL preservation
   - Acceptance: `docker compose config` validates; runbook covers (a) pre-migration backup of nginx config, (b) NPM boot + parallel routing test, (c) DNS cutover, (d) rollback path, (e) post-migration backup of NPM `/data`; OAuth callback URLs documented bit-for-bit identical
   - **Merge gate scope:** compose definitions + runbook land in this phase; live VPS migration is a separate deploy step (tracked but not blocking phase merge)
   - REQ-ID: OPS-INFRA-02

3. **Deploy runbook complete**: written runbook covers 9Router sidecar first-run + rollback + backup procedures.
   - Current: No deploy runbook for v1.2 infrastructure
   - Target: `docs/ops/v1.2-deploy.md` covers (a) zero-downtime nginx → NPM migration, (b) 9Router sidecar first-run (default password reset, API-key generation, provider account connection), (c) rollback if NPM/9Router fail, (d) backup of NPM `/data` + 9Router SQLite volumes
   - Acceptance: all 4 sections present; runbook reviewed against actual `docker-compose.yml` service definitions for consistency
   - REQ-ID: OPS-INFRA-03

### Admin Auth & RBAC (ADMIN-01..03)

4. **WebAuthn passkey admin login on `admin.zeromail.com`**: Admin authenticates via hardware-bound WebAuthn passkey, completely decoupled from user-facing Google OAuth flow.
   - Current: No admin authentication exists; user-facing app uses Google OAuth bundled flow; `users` table has no `role` column
   - Target: Admin auth implemented via Spring Security 7 `.webAuthn(...)` DSL configured in a dedicated `@Order(1) SecurityFilterChain` bean in `backend/api/SecurityConfig` with `securityMatcher("/api/admin/**")`. Relying Party config: `rpName="Zero Mail Admin"`, `rpId="admin.zeromail.com"`, `allowedOrigins("https://admin.zeromail.com")`. WebAuthn endpoints (`POST /webauthn/register/options`, `POST /webauthn/register`, `POST /login/webauthn/options`, `POST /login/webauthn`) ship via the DSL. Credential storage: `admin_users` table holds `user_handle`, `credential_id`, `public_key_cose`, `signature_counter`, `aaguid`, `attestation_format`, `created_at`. No password column. No Google OAuth integration on this chain. User-facing chain (`@Order(2)`, no `securityMatcher`) remains exactly as v1.1.
   - Acceptance: Admin opens `admin.zeromail.com` → calls `POST /login/webauthn/options` → browser invokes `navigator.credentials.get(...)` → user verifies on hardware (Touch ID / Windows Hello / YubiKey) → `POST /login/webauthn` succeeds → session cookie issued (Spring Session Redis) → subsequent `/api/admin/*` calls authenticated with `ROLE_ADMIN` authority sourced from `admin_users` row. User logging into `zeromail.com` via Google OAuth never carries `ROLE_ADMIN`. `users` table has no `role` column added by this phase. The Google OAuth chain is never invoked for `/api/admin/**` requests.
   - REQ-ID: ADMIN-01

5. **Chain-level + method-level RBAC enforcement**: Admin routes protected by a separate `SecurityFilterChain` (chain isolation) + `@PreAuthorize` per controller (defense in depth) + ArchUnit gate.
   - Current: No `/admin/*` routes exist; only one `SecurityFilterChain` covers `/api/**`
   - Target: (a) `@Order(1) adminChain` bean uses `securityMatcher("/api/admin/**")` + `.authorizeHttpRequests(a -> a.anyRequest().hasRole("ADMIN"))` + `.webAuthn(...)` — request-level isolation guarantees Google OAuth filters never run on admin paths and admin authority never produced on user paths; (b) every `@RestController` in `controllers/admin/` carries explicit `@PreAuthorize("hasRole('ADMIN')")` (no meta-annotation — rule-of-three not yet met); (c) ArchUnit rule `every_admin_controller_must_have_preauthorize` enforces (b) in CI; (d) ArchUnit rule `admin_chain_does_not_use_oauth2login` enforces that `adminChain` bean does not configure `.oauth2Login(...)` and `userChain` does not configure `.webAuthn(...)`.
   - Acceptance: HTTP request to `/api/admin/audit/events` without admin WebAuthn session returns 401 (chain-level); admin with valid session returns 200; deleting `@PreAuthorize` from any admin controller fails ArchUnit test; integration test confirming Google OAuth code path never runs on `/api/admin/**` request green; integration test confirming WebAuthn code path never runs on `/api/inbox` request green.
   - REQ-ID: ADMIN-02

6. **First-admin bootstrap via Liquibase seed + startup enrollment ceremony**: First admin row created via Liquibase changelog reading `zeromail.admin.bootstrap-emails` config; enrollment URL printed to STDOUT on startup; admin completes passkey ceremony to activate.
   - Current: No bootstrap mechanism, no `admin_users` table
   - Target: (a) `application.yml` defines `zeromail.admin.bootstrap-emails: [<list-of-operator-emails>]`; (b) Spring Boot `CommandLineRunner` on startup: for each email in config, if no `admin_users` row exists, insert one with `status='PENDING_ENROLLMENT'`, `credential_id=NULL`; (c) for each `PENDING_ENROLLMENT` row, generate a 32-byte hex one-time enrollment token (held in-memory, 10-min TTL, never persisted to disk or log file); (d) print the enrollment URL `https://admin.zeromail.com/enroll?token=<hex>` to STDOUT exactly once per startup (operator captures from terminal, never from log files); (e) admin visits URL → enters their email → server verifies token matches in-memory entry + email matches PENDING row → triggers WebAuthn registration ceremony via `.webAuthn(...)` DSL → ceremony succeeds → row updated `status='ACTIVE'`, `credential_id`, `public_key_cose`, `aaguid` populated → token consumed (one-time); (f) subsequent admin grants via `POST /api/admin/grant-admin {email}` (admin-only, audited): creates `admin_users` row PENDING_ENROLLMENT + returns a fresh 10-min enrollment URL in the response body — admin communicates URL out-of-band (Signal/paper/encrypted email) to target.
   - Acceptance: with `zeromail.admin.bootstrap-emails` unset, no admin_users row exists on startup; with one email configured, exactly one PENDING_ENROLLMENT row created; enrollment URL printed to STDOUT (verified by capturing process output, not by tailing `application.log`); URL accessed after 10 min returns HTTP 410 Gone; valid URL completes ceremony → row status = ACTIVE; second startup with same config produces no new row and no new URL; `POST /api/admin/grant-admin` writes audit row and returns one-time URL; second access to that URL returns 410.
   - REQ-ID: ADMIN-03

### Audit Infrastructure (ADMIN-04..05)

7. **Append-only `admin_audit_event` with same-transaction write**: Every admin state mutation writes one audit row in the same transaction.
   - Current: No `admin_audit_event` table
   - Target: Liquibase changelog creates `admin_audit_event` with columns `id`, `actor_user_id`, `actor_email`, `action`, `target_kind`, `target_id`, `before_state_json`, `after_state_json`, `reason VARCHAR(500)`, `request_ip`, `request_id`, `created_at`, `hmac_chain_hash`; admin services append the row in the same `@Transactional` block as the state mutation
   - Acceptance: every admin action (role grant/revoke, master-key set/rotate, catalog edit, tenant pause/disconnect/delete) writes exactly one row; the row contains a populated `hmac_chain_hash` chained to the previous row's hash; rolling back the state mutation also rolls back the audit row
   - REQ-ID: ADMIN-04

8. **Append-only `admin_read_event` for tenant data access**: Admin reads touching tenant data write to a separate `admin_read_event` table with 30-day retention.
   - Current: No `admin_read_event` table
   - Target: Liquibase changelog creates `admin_read_event` (similar columns); admin reads of tenant data (tenant detail view, audit log query by `target_id` tenant) write one row before reading; 30-day retention enforced by nightly cleanup job
   - Acceptance: opening `/admin/tenants/<tenantId>` writes one `admin_read_event` row; nightly job deletes rows older than 30 days; `admin_audit_event` retention is unaffected
   - REQ-ID: ADMIN-05

### Admin Frontend (ADMIN-06..08)

9. **Separate `apps/admin` Vite + React frontend on `admin.zeromail.com`**: Admin UI is a separate single-page app, decoupled from `apps/web` Next.js bundle, served at a dedicated subdomain via NPM proxy.
   - Current: `apps/web/app/(...)/` route groups for users only; no `apps/admin` exists; single `schema.d.ts` for public API
   - Target: (a) NEW `apps/admin` workspace (pnpm + Turborepo) containing a Vite + React 19 SPA — **no Next.js, no SSR, no SEO**. Stack: Vite 7+, React 19, Tailwind 4, shadcn/ui (copy-paste primitives — same primitives reused from `apps/web/components/ui` via shared copy or workspace re-export), TanStack Query 5, openapi-fetch 0.17, `@simplewebauthn/browser` for WebAuthn ceremony client-side. (b) Entry routes: `/enroll` (passkey registration after token verification), `/login` (passkey assertion), `/` (post-login dashboard), `/audit`, `/role-grants`, `/master-keys/<provider>`, `/catalog/<provider>`, `/tenants`, `/tenants/<tenantId>`, `/queue`, `/spend`. (c) Persistent "ADMIN MODE — actions affect tenants" banner top of every authenticated page (defense for the "alt-tab between admin tabs" failure mode within admin app itself; DNS handles user-vs-admin cognitive cue). (d) `apps/admin/scripts/generate-api.ts` codegens `admin-schema.d.ts` from `https://api.zeromail.com/v3/api-docs/admin`; `admin-client.ts` wraps `openapi-fetch` with admin schema. (e) `apps/web` (Next.js) emits zero changes for ADMIN-06 — public bundle remains free of any admin schema types or admin routes. (f) NPM proxy routes `admin.zeromail.com` → `apps/admin` static build dir; `zeromail.com` → `apps/web`; both proxy `/api/*` to same `backend/api` JVM. (g) Optional IP allowlist for `admin.zeromail.com` configurable per OPS-INFRA-03 runbook (not mandatory v1.2 but documented).
   - Acceptance: `apps/admin` builds standalone with `pnpm --filter @zeromail/admin build`; build artifact size <500KB gzipped (no SSR runtime, no Next.js framework code); `apps/web` Next.js bundle inspected via `next build` analyzer contains zero references to `admin-schema.d.ts` types or `apps/admin` source; `springdoc-openapi` exposes `/v3/api-docs/public` (excludes `/api/admin/**`) and `/v3/api-docs/admin`; NPM proxy serves both subdomains with separate Let's Encrypt certs; navigating `admin.zeromail.com` without session redirects to `/login` (passkey assertion form); navigating `zeromail.com/admin` returns 404 (no admin route exists in `apps/web`).
   - REQ-ID: ADMIN-06

10. **Audit log viewer with filter + CSV export**: Admin can browse paginated `admin_audit_event` log with filters and export.
    - Current: No audit viewer UI
    - Target: `/admin/audit` page paginates `admin_audit_event` filtered by actor email, action, target kind/id, and date range; each row shows actor email + action + target + before/after JSON diff (collapsible); CSV export endpoint streams up to 10k rows per request
    - Acceptance: filter combinations return correctly scoped rows; pagination cursor works across 1k+ rows; CSV export downloads a file with proper headers; rows beyond 10k require date-range narrowing
    - REQ-ID: ADMIN-07

11. **Confirm-twice + reason on destructive admin actions**: Destructive actions require modal confirm-twice with min-8-char free-text reason.
    - Current: No destructive admin actions exist yet
    - Target: tenant delete, master-key rotate, catalog disable-with-active-pins, role revoke require an in-modal first confirm + second confirm with a free-text `reason` (min 8 chars, max 500 chars, regex sanitizer rejecting key-prefix patterns `sk-`, `sk-ant-`, `AIza`, `sk-or-`); reason recorded in `admin_audit_event.reason`
    - Acceptance: clicking destructive action without reason or with <8-char reason rejected client-side and server-side; reason containing forbidden prefix rejected with HTTP 400; successful action writes reason to audit row
    - REQ-ID: ADMIN-08

11a. **`admin_users` table schema + Liquibase changelog**: WebAuthn credential storage table.
    - Current: No `admin_users` table exists
    - Target: Liquibase changelog creates `admin_users` with columns: `id UUID PRIMARY KEY`, `email VARCHAR(320) UNIQUE NOT NULL`, `display_name VARCHAR(200)`, `user_handle BYTEA NOT NULL UNIQUE` (random 64-byte handle for WebAuthn user identification), `status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_ENROLLMENT', 'ACTIVE', 'REVOKED'))`, `credential_id BYTEA UNIQUE`, `public_key_cose BYTEA`, `signature_counter BIGINT DEFAULT 0`, `aaguid UUID`, `attestation_format VARCHAR(50)`, `last_used_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `revoked_at TIMESTAMPTZ`, `revoked_reason VARCHAR(500)`. App DB user has INSERT + SELECT + UPDATE (for `last_used_at`, `signature_counter`, `status` transitions) on `admin_users`. DELETE forbidden — revoke via `status='REVOKED'` only (audit trail preservation).
    - Acceptance: schema deploys; `status` CHECK constraint rejects invalid values; UNIQUE constraints on `email`, `user_handle`, `credential_id` enforced; row revocation via UPDATE status works, DELETE returns Postgres permission error.
    - REQ-ID: ADMIN-09

11b. **WebAuthn enrollment + assertion ceremonies wired to Spring Security `.webAuthn(...)` DSL**: First-time passkey registration + subsequent login flows are fully implemented end-to-end.
    - Current: No WebAuthn ceremony endpoints exist
    - Target: (a) Spring Security 7 `.webAuthn(...)` DSL configured on `@Order(1) adminChain` produces the 4 stock endpoints (`POST /webauthn/register/options`, `POST /webauthn/register`, `POST /login/webauthn/options`, `POST /login/webauthn`). (b) Custom `EnrollmentTokenGate` filter intercepts `/enroll` access: validates the one-time token + matches a PENDING_ENROLLMENT row + opens a short-lived enrollment session (5-min server-side state, never written to logs/DB) → user proceeds to `/webauthn/register/options` → completes ceremony → row transitions to ACTIVE. (c) `AdminUserDetailsService` resolves `Authentication` from `admin_users` row, exposing `ROLE_ADMIN` authority. (d) `WebAuthnRelyingPartyOperations` bean configured with `rpId="admin.zeromail.com"` + `origins=["https://admin.zeromail.com"]` + `userVerificationRequirement=REQUIRED` (mandates biometric/PIN, blocks silent passkeys). (e) Counter-replay defense: assertions where reported `signCount <= admin_users.signature_counter` are rejected and audited as `WEBAUTHN_REPLAY_SUSPECTED`. (f) Lost-passkey recovery: out of scope v1.2 — operator with shell access manually inserts a fresh PENDING_ENROLLMENT row for the affected email and runs through the bootstrap ceremony again; documented in OPS-INFRA-03 runbook.
    - Acceptance: enrollment ceremony with valid token + valid platform authenticator → row ACTIVE, credential_id stored, public_key_cose stored; ceremony with expired token (>10 min) returns HTTP 410; ceremony with replayed token returns 410 (one-time consumption); login assertion with valid passkey → session issued, `signature_counter` incremented; assertion with downgraded sign count rejected + audit row `WEBAUTHN_REPLAY_SUSPECTED` written; assertion against revoked row (status=REVOKED) returns 401; `userVerificationRequirement=REQUIRED` enforced (test with `userVerified=false` flag rejected).
    - REQ-ID: ADMIN-10

### Architectural Invariants (ARCH-08..12)

12. **`AdminContext` ScopedValue mutually exclusive with `TenantContext`**: Admin scope clears tenant binding and vice versa (codepath-level defense in depth on top of SecurityFilterChain isolation).
    - Current: Only `TenantContext` ScopedValue exists
    - Target: `AdminContext` ScopedValue is a sibling of `TenantContext`; entering admin scope (`AdminContext.run(admin, () -> ...)`) makes `TenantContext.currentOrThrow()` throw; entering tenant scope makes `AdminContext.currentOrThrow()` throw; admin auth filter (in `@Order(1) adminChain`) binds `AdminContext` on successful WebAuthn assertion and never binds `TenantContext`; user `TenantBindingFilter` (in `@Order(2) userChain`) binds `TenantContext` and never binds `AdminContext`. Cross-tenant admin reads route through `AdminTenantAccess.readOnly(tenantId, supplier)` which writes one `admin_read_event` row before invoking the supplier inside a `TenantContext.run` block (after asserting `AdminContext.isBound()` is true). ArchUnit rule forbids admin packages from reading `TenantContext` directly.
    - Acceptance: unit test confirms mutex (binding both throws); ArchUnit `AdminContextMutexTest` green; integration test: WebAuthn-authenticated request reaches admin controller with `AdminContext` bound + `TenantContext` unbound; Google-OAuth-authenticated request reaches user controller with `TenantContext` bound + `AdminContext` unbound; `AdminTenantAccess.readOnly` writes audit row before invoking supplier; admin code attempting to inject `TenantContext`-aware repo fails ArchUnit.
    - REQ-ID: ARCH-08

13. **`AdminPathBodyBanTest` ArchUnit**: Admin packages cannot reference email-content / chat-content / prompt-content field accessors.
    - Current: No body-ban ArchUnit rule exists
    - Target: ArchUnit `AdminPathBodyBanTest` enforces that classes under `..controllers.admin..` and `..core.admin..projection..` cannot reference `GmailClient` body-exposing methods, `ChatMessageRepository.findContent*` accessors, `LlmCallAudit.prompt*` / `.completion*` field accessors, or any field named per the forbidden regex `body|bodyHtml|snippet|payload|prompt|completion|content`; test runs in CI
    - Acceptance: rule fires when a test fixture adds an admin projection field named `body`; rule does NOT fire on the production code Phase 8 ships (rules trivially green on AuditController + RoleController until OPS-TENANT projections land in same phase)
    - REQ-ID: ARCH-09

14. **Single Gmail send call-site invariant holds + admin packages cannot send**: Repo-wide grep gate stays at exactly 1 Gmail send call site; admin packages are additionally banned from referencing send methods.
    - Current: v1.1 ARCH-01 grep gate asserts 1 call site (the `AssistantSendExecutor`)
    - Target: Grep gate continues; ArchUnit additionally forbids classes under `..controllers.admin..` and `..core.admin..` from referencing `GmailClient` send methods; master-key test-connection uses `GET /v1/models` (or per-provider equivalent), never a send method
    - Acceptance: repo grep for Gmail send returns exactly 1 hit; ArchUnit rule fires when test fixture adds send-method reference in admin package; CI green
    - REQ-ID: ARCH-10

15. **`MasterKeySentinelLeakTest` CI gate**: No master-key sentinel ever appears in logs, responses, exceptions, YAML, or audit rows.
    - Current: No sentinel-leak test exists
    - Target: CI test scans logs/response payloads/exception messages/YAML/audit JSON for `sk-`, `sk-ant-`, `AIza`, `sk-or-` (and masked-encoded forms like base64); fails CI on any hit; uses test fixtures simulating master-key set/test/rotate flows
    - Acceptance: fixture inserting `sk-test123` into a log line fails the test; production code Phase 8 ships passes the test green
    - REQ-ID: ARCH-11

16. **`admin_audit_event` append-only at DB level**: App DB user lacks UPDATE/DELETE privilege; Postgres trigger raises EXCEPTION on any UPDATE/DELETE attempt regardless of role; HMAC chain + nightly verification.
    - Current: No `admin_audit_event` exists
    - Target: Liquibase changelog grants only INSERT + SELECT on `admin_audit_event` to the app DB user (no UPDATE, no DELETE); Postgres `BEFORE UPDATE OR DELETE` trigger raises `EXCEPTION 'admin_audit_event is append-only'` regardless of role; per-row `hmac_chain_hash` chains to previous row's hash via HMAC-SHA256; nightly verification job re-derives the chain and alerts on mismatch
    - Acceptance: `UPDATE admin_audit_event SET reason='x'` fails with the trigger exception even as `postgres` superuser; chain verification detects an injected row tampered to break the HMAC; nightly job emits a Prometheus alert metric on mismatch
    - REQ-ID: ARCH-12

### Master Keys (MKEY-01..08)

17. **Per-provider master key set + AES-GCM encryption**: Operator can set master keys for all 6 providers, encrypted at rest with the existing `RefreshTokenCipher`.
    - Current: No `llm_provider_master_key` table; no master-key UI; LLM credentials hardcoded in YAML
    - Target: Liquibase changelog creates `llm_provider_master_key` (columns: `provider`, `key_format`, `encrypted_key`, `kek_version`, `created_by_user_id`, `created_at`, `last_rotated_at`); UI at `/admin/master-keys` lists all 6 providers (OpenAI, Anthropic, Google, DeepSeek, OpenRouter, 9Router) with set/edit form; on save, the key is AES-GCM-encrypted via `RefreshTokenCipher` (same algorithm + KEK rotation as v1.0 OAuth refresh tokens); KEK lives in `ZeroMailCoreProperties.crypto.masterKeys.kekBase64`
    - Acceptance: setting a key writes one encrypted row; the decrypted value matches the input bytes; KEK rotation re-wraps with new KEK version
    - REQ-ID: MKEY-01

18. **Masked-only display + edit-session token**: Master keys are never displayed in plaintext after save.
    - Current: N/A
    - Target: After save, UI only shows `sk-****abc1` (last 4 chars only); editing a key requires a 5-minute edit-session token (issued by `POST /api/admin/master-keys/<provider>/edit-session`) + 10 req/hour/admin rate limit; on edit, plaintext input is sent over HTTPS, encrypted server-side, no plaintext echo in response
    - Acceptance: GET `/api/admin/master-keys/<provider>` returns masked-only; POST without valid edit-session returns 400; >10 edit-session requests/hour returns 429
    - REQ-ID: MKEY-02

19. **Test-connection returns enum, no provider error body**: Test-connection oracle returns only `OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT`.
    - Current: N/A
    - Target: `POST /api/admin/master-keys/<provider>/test-connection` calls provider `GET /v1/models` (per-provider equivalent); response body is exclusively the enum value; provider error response bodies are stripped server-side and replaced by enum; `/models` HTTP client is isolated from the logging proxy (no request/response body logging)
    - Acceptance: deliberate INVALID_KEY returns `{"result":"INVALID_KEY"}` with no other field; logs contain only the enum value, never the provider error body; sentinel-leak test green
    - REQ-ID: MKEY-03

20. **Rotation evicts ChatModel cache atomically + preserves old key on test failure**: Rotation is transactional with cache eviction.
    - Current: N/A
    - Target: Rotation flow: (a) admin enters new key; (b) test-connection runs against new key; (c) on OK, write new encrypted row + emit `MasterKeyRotatedEvent` Spring Modulith event; (d) `@ApplicationModuleListener` evicts every cached `ChatModel` instance for that provider across all tenants; (e) on test failure, old key preserved, no rotation written, audit row records `MASTER_KEY_ROTATION_FAILED`
    - Acceptance: rotation against valid key writes new row + evicts all cached ChatModels for that provider; rotation against invalid key leaves old row intact, no ChatModel eviction; subsequent `chat.send` uses the new key without restart
    - REQ-ID: MKEY-04

21. **9Router dual-mode key format toggle**: 9Router master key entry toggles between `OPENAI_FORMAT` and `ANTHROPIC_FORMAT`.
    - Current: N/A
    - Target: 9Router master-key form has a `key_format` enum field (`OPENAI_FORMAT` | `ANTHROPIC_FORMAT`); on save, `ProviderMasterKeyResolver` selects the Spring AI OpenAI adapter or Anthropic adapter at the configured `base_url`; other 5 providers have fixed adapter type (no toggle); admin can pick per-feature default provider for `chat`, `triage`, `draft` (default `OpenRouter` preserved at launch)
    - Acceptance: toggling 9Router to `OPENAI_FORMAT` routes `chat.send` through `OpenAiChatModel`; toggle to `ANTHROPIC_FORMAT` routes through `AnthropicChatModel`; both use the same `base_url` from 9Router master-key row
    - REQ-ID: MKEY-05

22. **Master-key dependents count + 90-day rotation reminder**: UI surfaces dependents count and rotation freshness.
    - Current: N/A
    - Target: Master-key list shows per-provider "Dependents: N tenants using" badge (computed from `byok_credential` + `assistant_settings`); rows older than 90 days since `last_rotated_at` show a "Rotation recommended" tag
    - Acceptance: badge updates after a tenant pins a new model; 90-day-old key shows tag; freshly rotated key removes tag
    - REQ-ID: MKEY-06

23. **`ProviderMasterKeyResolver` single resolution point**: All master-key lookups flow through a single class inside `core.llm.gateway.springai`.
    - Current: N/A
    - Target: `ProviderMasterKeyResolver` is the sole class reading `llm_provider_master_key`; lives inside `core.llm.gateway.springai`; ArchUnit rule forbids any other class from reading the table directly; resolver caches the decrypted key in-memory with TTL matching ChatModel cache lifetime
    - Acceptance: ArchUnit rule fires on test fixture that injects `LlmProviderMasterKeyRepository` outside the gateway package; chat send path resolves master key without DB hit after first call
    - REQ-ID: MKEY-07

24. **Master-key audit row contains no key bytes**: Audit row records masked metadata only.
    - Current: N/A
    - Target: `admin_audit_event` rows for master-key actions contain `before_state_json`/`after_state_json` with `masked_key: "sk-****abc1"`, `kek_version`, `last_rotated_at` — never the encrypted bytes, never plaintext bytes, never raw `sk-` prefix
    - Acceptance: sentinel-leak test scans `admin_audit_event` JSON and finds no `sk-` / `sk-ant-` / `AIza` / `sk-or-` substrings
    - REQ-ID: MKEY-08

### Curated Catalog (CAT-01..07)

25. **3-table normalized catalog**: `provider_catalog`, `model_catalog`, `feature_binding` tables.
    - Current: No catalog tables; model IDs in YAML config
    - Target: Liquibase changelog creates `provider_catalog` (provider enum, enabled, default-for-feature columns), `model_catalog` (model_id, provider FK, display_name, cost_per_1k_input, cost_per_1k_output, deprecated_at, is_recommended), `feature_binding` (model FK × feature enum {CHAT, TRIAGE, DRAFT}, enabled, is_default); FK + UNIQUE partial indexes prevent stale-pin failures from `assistant_settings.{chat|triage|draft}_model_id`
    - Acceptance: schema deploys; FK on `assistant_settings.chat_model_id` prevents pinning a non-existent model; UNIQUE partial index ensures one default per feature per provider
    - REQ-ID: CAT-01

26. **3-step Sync-from-`/models` flow**: Fetch → Diff → Confirm; no auto-apply.
    - Current: N/A
    - Target: `/admin/catalog/<provider>/sync` UI runs (a) Fetch (async via `processing_job` SKIP LOCKED with 60s Redis debounce lease), (b) Diff review (added/removed/changed models surfaced), (c) Confirm (atomic apply in transaction with `SELECT ... FOR UPDATE` on small lock table); auto-apply forbidden
    - Acceptance: clicking Sync within 60s of previous Sync rejected with debounce message; Diff page shows machine-readable diff; clicking Confirm applies atomically; clicking Cancel discards
    - REQ-ID: CAT-02

27. **Model-ID validation + per-provider JSON Schema**: Model IDs validated against regex + schema before catalog insert.
    - Current: N/A
    - Target: Every model ID validated against `^[a-zA-Z0-9._:/\-]{1,128}$`; per-provider JSON Schema validates `/models` response shape before diff; Schema mismatches surface as Sync errors, no partial apply
    - Acceptance: model ID `bad model id!` rejected; provider returning unexpected shape fails Sync with clear error
    - REQ-ID: CAT-03

28. **Anthropic manual-only with Liquibase seed**: Anthropic Sync button disabled; initial Claude family seeded via Liquibase.
    - Current: N/A
    - Target: Liquibase data seed inserts initial Claude family (Claude 4.7 Opus, Claude 4.6 Sonnet, Claude 4.5 Haiku) into `model_catalog`; Anthropic provider's Sync button disabled with tooltip "Anthropic has no public /models endpoint — add new models via manual entry"; manual entry form available
    - Acceptance: post-Liquibase, Anthropic catalog has 3 models; Sync button disabled state visible; manual entry adds a new model successfully
    - REQ-ID: CAT-04

29. **Disable-with-pinned-tenants requires confirm-twice**: Disabling a model that tenants have pinned requires extra confirmation.
    - Current: N/A
    - Target: Catalog disable action computes pinned-tenant count from `assistant_settings.*_model_id`; if >0, force confirm-twice + reason flow (ADMIN-08); on confirm, model is soft-deleted (`deprecated_at` set), but pinned tenants keep working until they pick a new model (no auto-migration)
    - Acceptance: disabling Claude 3 Opus with 5 pinned tenants requires 2 confirms + reason; soft-deleted model not shown in `GET /api/settings/catalog`; pinned tenants still get LLM completion via that model
    - REQ-ID: CAT-05

30. **`CuratedCatalogQueryService` + `GET /api/settings/catalog`**: User-facing catalog endpoint with different DTO shape than admin endpoints.
    - Current: N/A
    - Target: `CuratedCatalogQueryService` reads `provider_catalog` + `model_catalog` + `feature_binding` (READ COMMITTED) with short Redis ETag cache; serves `GET /api/settings/catalog` returning per-feature `[{provider, model_id, display_name, is_default, is_recommended, cost_per_1k_input, cost_per_1k_output, deprecated_at}]`; admin DTOs are different shape (include sync history, dependents count, etc.) and live in `admin-schema.d.ts` only
    - Acceptance: `GET /api/settings/catalog` returns user-facing shape with no admin fields; admin endpoint at `/api/admin/catalog/<provider>` returns admin shape; `springdoc-openapi` GroupedOpenApi places `/api/settings/catalog` in `public` group and admin endpoints in `admin` group
    - REQ-ID: CAT-06

31. **Catalog cache eviction on Sync + MasterKey rotation**: `CATALOG_CHANGED` + `MASTER_KEY_ROTATED` Modulith events evict per-tenant `ChatModel` cache.
    - Current: N/A
    - Target: Successful Sync Confirm emits `CatalogChangedEvent`; `MasterKeyRotatedEvent` already in MKEY-04; `@ApplicationModuleListener` in `core.llm.gateway.springai` evicts cached `ChatModel` instances scoped by `(tenantId, feature, provider, model_id)` tuples affected by the change
    - Acceptance: Sync removing Claude 3 Opus evicts cache for tenants pinned to it; rotation of OpenAI master key evicts ALL OpenAI ChatModels across tenants; next chat request rebuilds the cache with new state
    - REQ-ID: CAT-07

### Tenant Inspection (OPS-TENANT-01..05)

32. **Tenant list page metadata-only**: `/admin/tenants` shows paginated tenant list with metadata only.
    - Current: No tenant inspection UI
    - Target: `/admin/tenants` page paginates tenants showing `tenantId`, `creation date`, `connected Gmail account email`, `status` (active/paused/disconnected), `7d-spend-bucket` (k-anonymized — bucket label, not exact figure)
    - Acceptance: page renders without rendering email body, chat content, or prompts/completions; pagination cursor works
    - REQ-ID: OPS-TENANT-01

33. **Tenant detail 5-tab view metadata-only**: `/admin/tenants/<tenantId>` shows 5 tabs (Overview, Health, Billing, Spend, Activity) with metadata only.
    - Current: N/A
    - Target: Overview = tenant metadata + status + creation; Health = token refresh status, watch renewal status, last Pub/Sub push timestamp; Billing = credits balance, plan; Spend = LLM call count + cost rollup by feature/provider (last 7/30d); Activity = recent rule firings count, chat session count, last activity timestamp; chat-session inspection limited to metadata (count, last activity, model selection) — "Show details" disabled with tooltip referring to a future v1.3+ tenant-bound support ticket grant
    - Acceptance: opening any tab writes one `admin_read_event` row; no tab renders email body, chat content, or prompts/completions; chat-session "Show details" button is disabled
    - REQ-ID: OPS-TENANT-02

34. **Pause / Disconnect / Delete tenant actions with confirm-twice + reason**: Destructive tenant actions follow ADMIN-08 flow.
    - Current: N/A
    - Target: `POST /api/admin/tenants/<tenantId>/pause` (suspends rule firing, Pub/Sub consumption); `POST /api/admin/tenants/<tenantId>/disconnect` (revokes Gmail OAuth token, stops watch); `POST /api/admin/tenants/<tenantId>/delete` (cascades to all tenant data; "deletion preview" shows counts before final confirm); all require confirm-twice + reason; all write `admin_audit_event`
    - Acceptance: pause halts new rule firings within 5s; disconnect revokes Gmail token (verified by checking next Pub/Sub push fails with 401); delete preview shows accurate counts; final delete cascades and writes audit row
    - REQ-ID: OPS-TENANT-03

35. **`AdminResponseBodyBanFilter` failsafe**: Servlet filter rejects admin responses with forbidden field names >200 chars.
    - Current: N/A
    - Target: `AdminResponseBodyBanFilter` registered on `/api/admin/**`; scans JSON response body for string fields whose key matches regex `body|bodyHtml|snippet|payload|prompt|completion|content` AND value length >200; on match, returns HTTP 500 + writes `admin_audit_event` row (action=`ADMIN_RESPONSE_BODY_BAN_TRIPPED`); filter runs after controller serialization
    - Acceptance: test fixture returning `{"content": "<200-char-string>"}` from admin endpoint triggers HTTP 500 + audit row; legitimate short metadata fields pass through; production code Phase 8 ships does not trip the filter
    - REQ-ID: OPS-TENANT-04

36. **Tenant OAuth credentials never reachable from admin path**: ArchUnit rule forbids admin packages from resolving tenant OAuth tokens.
    - Current: N/A
    - Target: ArchUnit rule forbids `..controllers.admin..` and `..core.admin..` from injecting `GmailConnectionRepository`, `GmailOAuthTokenService`, or any class exposing decrypted OAuth tokens; admin disconnect flow uses a token-revocation service that takes a `tenantId` and acts without exposing the token bytes to admin code
    - Acceptance: ArchUnit fires on test fixture injecting `GmailOAuthTokenService` into admin controller; disconnect flow works in integration test without admin reading token bytes
    - REQ-ID: OPS-TENANT-05

### Queue Health (OPS-QUEUE-01..02)

37. **`/admin/queue` real-time aggregates over `outbox` + `processing_job`**: 10s auto-refresh dashboard.
    - Current: No queue health UI
    - Target: `/admin/queue` shows depth by job type, oldest-unleased job age, retry distribution histogram, failure rate (last 24h), dead-letter count; auto-refreshes every 10s; reads aggregates only (no per-row payload exposure)
    - Acceptance: dashboard renders correct counts against test fixtures; 10s refresh fires; no `job.payload_json` ever serialized to response
    - REQ-ID: OPS-QUEUE-01

38. **Dead-letter re-queue without payload exposure**: Admin can re-queue a dead-letter row without viewing or editing payload.
    - Current: N/A
    - Target: Dead-letter list shows job ID, type, last-failure reason, retry count; "Re-queue" action moves the row back to active queue with retry count reset to 0; admin cannot view `payload_json` or edit any field
    - Acceptance: re-queue action works against fixture; payload field not in dead-letter list DTO; modifying payload via API rejected with HTTP 403
    - REQ-ID: OPS-QUEUE-02

### Spend Dashboard (OPS-SPEND-01..02)

39. **`/admin/spend` metadata-only dashboard**: Today / 7d / 30d totals split platform-vs-BYOK, stacked bar by provider, donut by feature, top-20 tenants.
    - Current: No spend dashboard
    - Target: `/admin/spend` aggregates `llm_call_audit` rows (no prompt/completion text); shows top-line cards (today/7d/30d totals split platform-vs-BYOK), stacked bar by provider, donut by feature, top-20 tenants table; max 90-day date-range picker; k-anonymity on deleted tenants (no exact figures for fewer than 5 tenants in a bucket)
    - Acceptance: dashboard reads only aggregates; no per-prompt drill-down available; date range >90d rejected; deleted-tenant bucket with <5 entries shows aggregated rollup only
    - REQ-ID: OPS-SPEND-01

40. **Spend dashboard does not expose per-prompt text**: ArchUnit rule + integration test confirm.
    - Current: N/A
    - Target: ArchUnit rule forbids `controllers.admin.spend.*` from reading `LlmCallAudit.prompt*` / `.completion*` accessors; integration test confirms response payload contains no string fields >200 chars matching the forbidden regex
    - Acceptance: ArchUnit fires on test fixture injecting prompt accessor in spend controller; `AdminResponseBodyBanFilter` does not trip on production spend response
    - REQ-ID: OPS-SPEND-02

## Boundaries

**In scope:**

- VPS deployment artifacts: 9Router sidecar service + NPM proxy service in `docker-compose.yml`, NPM subdomain routing for `admin.zeromail.com` + separate Let's Encrypt cert, `docs/ops/v1.2-deploy.md` runbook
- Backend: `admin_users` table (WebAuthn credentials), `@Order(1) adminChain` SecurityFilterChain with Spring Security 7 `.webAuthn(...)` DSL on `admin.zeromail.com`, `AdminUserDetailsService`, `EnrollmentTokenGate` filter, `AdminContext` ScopedValue, `AdminTenantAccess.readOnly`, `admin_audit_event` + `admin_read_event` tables with append-only trigger + HMAC chain, Liquibase seed + startup-runner enrollment ceremony, `POST /api/admin/grant-admin` audited endpoint returning one-time enrollment URL
- Backend: `llm_provider_master_key` table + `ProviderMasterKeyResolver` + 6-provider set/test/rotate REST endpoints with edit-session token + rate limit
- Backend: `provider_catalog` + `model_catalog` + `feature_binding` tables + 3-step Sync flow + `CuratedCatalogQueryService` + `GET /api/settings/catalog`
- Backend: tenant list + 5-tab detail read-only endpoints + pause/disconnect/delete write endpoints + `AdminResponseBodyBanFilter`
- Backend: `/admin/queue` + `/admin/spend` read-only aggregate endpoints
- Backend: ArchUnit rules — `AdminContextMutexTest`, `AdminPathBodyBanTest`, admin send-method ban (extends ARCH-10), admin master-key resolver confinement, `every_admin_controller_must_have_preauthorize`, admin spend prompt-accessor ban
- Backend: `MasterKeySentinelLeakTest` CI gate
- Frontend: NEW `apps/admin` Vite + React 19 SPA (separate from `apps/web` Next.js) served at `admin.zeromail.com` with ADMIN MODE banner chrome
- Frontend: `admin-schema.d.ts` + `admin-client.ts` typed client generated from `springdoc-openapi` GroupedOpenApi admin spec; lives inside `apps/admin/src/lib/api/` only
- Frontend: `@simplewebauthn/browser` for WebAuthn ceremony client-side
- Frontend: admin pages — `/enroll` (passkey registration), `/login` (passkey assertion), Audit Log viewer with filter + CSV export, Role Grants (with one-time enrollment URL response), Master Keys per-provider, Catalog browser + Sync flow per provider, Tenant List + 5-tab Detail, Queue Health, Spend Dashboard
- Liquibase: 7 new YAML changelogs (`admin_users`, `admin_audit_event`, `admin_read_event`, `llm_provider_master_key`, `provider_catalog` + `model_catalog` + `feature_binding`, Anthropic catalog seed) — NO `users.role` column changelog
- Spring Modulith: new `core.admin` top-level module (sibling of `core.chat`, `core.llm`)
- HTML prototype: `08-PROTOTYPE.html` covering Audit / Role Grants / Master Keys / Catalog / Tenants / Queue / Spend screens

**Out of scope:**

- **Live VPS migration from hand-managed nginx → NPM and 9Router sidecar boot on production VPS** — Phase 8 ships compose definitions + runbook; live cutover is a deploy step tracked separately (not a phase merge gate). Reason: VPS cutover needs a downtime window and is operationally distinct from code merge.
- **Google OAuth for admin login** — explicitly NOT used; admin chain uses WebAuthn passkey exclusively. Reason: decouple admin compromise surface from Google IdP availability/incident; 2026 best practice for high-privilege auth.
- **HTTP Basic Auth, password+TOTP, or any password-based admin auth** — locked NO. Reason: OWASP ASVS deprecates HTTP Basic for admin; WebAuthn covers all requirements without password handling code.
- **`users.role` column or any RBAC concept on the user-facing side** — admin authority lives entirely in `admin_users` table + admin SecurityFilterChain; user codepath retains only `authenticated` concept.
- **Self-service "I lost my passkey" recovery UI** — operator with shell access manually inserts a fresh PENDING_ENROLLMENT row via Liquibase or psql; documented in OPS-INFRA-03. Reason: lost-passkey UI invites social-engineering attack vector; out-of-band recovery is safer for v1.2 scale.
- **Admin impersonation of a user (act-as-tenant)** — locked NO at architectural level (ARCH-08); tenant authority cannot be borrowed by admin.
- **Auto-send / auto-forward triggered by admin action** — locked NO (auto-send ban from v1.0 ARCH).
- **Free-form model-ID override in admin catalog** — only entries via Sync diff confirm or manual entry through admin form (validated against regex + JSON Schema).
- **User-triggered Sync** — only admin can trigger Sync; `GET /api/settings/catalog` is read-only for users.
- **Per-tenant master keys** — master keys are platform-wide; BYOK keys live in v1.0 `byok_credential` (per-tenant) and are not affected by master-key rotation.
- **HashiCorp Vault / GCP KMS / AWS KMS for master keys** — locked NO (single VPS, reuse `RefreshTokenCipher` AES-GCM).
- **Embedding catalog curation** — embeddings are forbidden by v1.0 privacy constraint.
- **Audit log full-text search across tenants** — filter is by actor/action/target/date range only.
- **Per-rule model override UI** — out of scope (model selection is per-feature `chat/triage/draft`, not per-rule).
- **Cron-based master-key rotation** — manual rotation only (90-day reminder tag is informational).
- **`AdminController` meta-annotation** — explicit `@PreAuthorize` per class per Decision 1.5 (rule-of-three not yet met).
- **Shared SecurityFilterChain for admin + user paths** — chain split locked per Decision 1.7 (post-pivot); admin and user chains never overlap.
- **`(admin)` route group inside `apps/web` Next.js** — replaced by separate `apps/admin` Vite + React app on `admin.zeromail.com` per ADMIN-06 (post-pivot).
- **Admin SQL console** — direct DB query access banned.
- **Admin "reveal master key once" workflow** — masked-only forever post-save.
- **Worker stop/start admin UI** — read-only queue inspection + dead-letter re-queue only.
- **Reveal/edit job `payload_json`** — never exposed via admin API.
- **Per-prompt drill-down on spend dashboard** — aggregates only.
- **v1.3+ items deferred per REQUIREMENTS.md "Deferred to v1.3+" section** — Grafana ops dashboards, CASA evidence, formal GA tag, purple brand visual refresh of user pages, fine-grained admin permissions, admin chat-content inspection via tenant-bound support ticket grant.

## Constraints

**Stack lock-ins (from project CLAUDE.md):**

- Java 25, Spring Boot 4.0.6, Spring Security 7.0.5, Spring AI 2.0.0-M6, Spring Modulith.
- PostgreSQL 17 self-hosted on VPS; Liquibase YAML changelogs; AES-GCM at app layer via existing `RefreshTokenCipher`.
- Redis 7.2 self-hosted on VPS for cache + Spring Session + rate limit (not a queue).
- Postgres-backed queue (`outbox` + `processing_job` with SKIP LOCKED) — no Kafka, no RabbitMQ.
- Bundled Google OAuth (one OAuth client, one login flow); cookie + Spring Session Redis (no stateless JWT).
- Spring AI usage confined to `core.llm.gateway.springai`; raw vendor SDK calls outside this package banned (extension: master-key `/models` HTTP client lives inside `core.llm.gateway.springai.admin`).
- Privacy: no long-term storage of email content / LLM prompts/completions; rule-builder chat is DB-persistable (already shipped). Admin path doubles down — `AdminPathBodyBanTest` ArchUnit + `AdminResponseBodyBanFilter` failsafe.
- ArchUnit + repo-wide grep gates enforced in CI; deprecation warnings from Boot 4 / Jackson 3 / Spring Security 7 must be addressed (no deprecated usage shipping).

**Phase-specific locks (post-pivot 2026-05-19):**

- Admin auth = Spring Security 7 `.webAuthn(...)` DSL (WebAuthn passkey, hardware-bound, `userVerificationRequirement=REQUIRED`) on `admin.zeromail.com`; NOT Google OAuth, NOT HTTP Basic, NOT password.
- Admin identity store = NEW `admin_users` table (separate from `users`); `users` table gains NO `role` column; no `GrantedAuthoritiesMapper` ROLE_ADMIN merge needed.
- 2 separate `SecurityFilterChain` beans: `@Order(1) adminChain` with `securityMatcher("/api/admin/**")` + WebAuthn; `@Order(2) userChain` (no `securityMatcher`) keeps Google OAuth current. Chains never share auth method.
- Explicit `@PreAuthorize("hasRole('ADMIN')")` per admin controller (defense in depth on top of chain-level enforcement; no meta-annotation until rule-of-three).
- First-admin bootstrap = Liquibase seed `admin_users` row + Spring Boot startup runner reading `zeromail.admin.bootstrap-emails` config + one-time 10-min enrollment URL printed to STDOUT (never to log file or DB).
- `admin_audit_event` indefinite retention; `admin_read_event` 30-day retention.
- Frontend admin = NEW `apps/admin` Vite + React 19 SPA on `admin.zeromail.com`; NOT a route group inside `apps/web` Next.js. Public `apps/web` bundle stays free of admin schema types and admin route code.
- NPM proxy serves both subdomains (`zeromail.com` → apps/web, `admin.zeromail.com` → apps/admin) with separate Let's Encrypt certs; both proxy `/api/*` and `/webauthn/*` and `/login/*` to same `backend/api` JVM port.
- Optional IP allowlist for `admin.zeromail.com` documented in OPS-INFRA-03 runbook (not mandatory v1.2 — solo operator accessibility trade-off).
- Tenant chat-session inspection limited to metadata (count, last activity, model selection); detail viewing deferred to v1.3+.
- Anthropic catalog: Liquibase seed for initial Claude family; manual admin entry for new models; Sync button disabled.
- Master-key test-connection uses `GET /v1/models` (or per-provider equivalent), never a send method.
- New `core.admin` top-level Spring Modulith module (sibling of `core.chat`, `core.llm`).
- Catalog: 3-table normalized (`provider_catalog`, `model_catalog`, `feature_binding`), NOT JSONB.
- Frontend `(admin)` route group: sibling of `(app)`, own typed client, public bundle ships zero admin types.

**Research mandate:**

- Plan-phase MUST pull Spring Security 7 docs via Context7 (`/spring-projects/spring-security` or `/websites/spring_io_spring-security_reference_7_0`) before any auth code: **`.webAuthn(...)` DSL (RP config, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`, `WebAuthnRelyingPartyOperations`, signature-counter replay defense, attestation policies)**, multiple `SecurityFilterChain` with `securityMatcher`, method security, `AuthorizationManager`. Boot 4 + Security 7 has multiple breaking API surfaces vs. Security 5.x/6.x training data. WebAuthn DSL is a Spring Security 6.4+ feature — training data may not cover it.
- Plan-phase MUST pull Spring AI M6 docs for `StreamingChatModel` selection / `ChatModel` cache eviction across tenants on master-key rotation.
- Plan-phase MUST pull `springdoc-openapi` 3.0.3 docs for `GroupedOpenApi` split (admin spec separate from public spec).
- Plan-phase MUST pull `@simplewebauthn/browser` docs for client-side ceremony invocation (`startRegistration`, `startAuthentication`).
- Plan-phase MUST consult [Spring Security 7 Passkey Reference Docs](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passkeys.html) for endpoint shapes, request/response payloads, and CSRF requirements on WebAuthn endpoints.

**Planning structure (inside this phase):**

- 8A foundation (RBAC + `AdminContext` + audit primitive + ArchUnit infrastructure + GroupedOpenApi split + `(admin)` route group) is a hard sequential gate
- 8B master keys, 8C tenant inspection, 8D catalog Sync, 8E queue, 8F spend can wave-parallelize after 8A
- 8B (master keys) is a soft prerequisite for 8D (catalog Sync needs `/models` calls with master key)
- Single PLAN.md acceptable, OR split into sub-plans 8A.PLAN / 8B.PLAN / ... in the same phase directory if review fatigue becomes real

## Acceptance Criteria

- [ ] OPS-INFRA: docker-compose validates (`docker compose config`), 9Router sidecar service definition present, NPM service definition present, `docs/ops/v1.2-deploy.md` covers all 4 sections (migration / 9Router first-run / rollback / backup)
- [ ] ADMIN-01: Admin logs in at `admin.zeromail.com/login` via WebAuthn passkey ceremony → session cookie issued, `GET /api/admin/me` returns `ROLE_ADMIN` authority sourced from `admin_users`; Google-OAuth-authenticated user logging at `zeromail.com` does NOT carry `ROLE_ADMIN`; `users` table has no `role` column
- [ ] ADMIN-02: Request to `/api/admin/audit/events` without WebAuthn session returns 401 at chain level; admin with valid session returns 200; deleting `@PreAuthorize` from any admin controller fails ArchUnit; ArchUnit `admin_chain_does_not_use_oauth2login` green; integration test confirms Google OAuth filters never run on admin path and vice versa
- [ ] ADMIN-03: With `zeromail.admin.bootstrap-emails` set, startup creates exactly one PENDING_ENROLLMENT row per configured email + prints one-time enrollment URL to STDOUT (not to `application.log`); URL accessed after 10 min returns HTTP 410; valid URL completes WebAuthn registration ceremony → row status ACTIVE + credential_id populated; second startup produces no new row; `POST /api/admin/grant-admin` writes audit row + returns one-time URL
- [ ] ADMIN-04: Every admin state mutation writes one `admin_audit_event` row in the same transaction with populated `hmac_chain_hash`
- [ ] ADMIN-05: Opening tenant detail writes one `admin_read_event` row; 30-day retention enforced by nightly job
- [ ] ADMIN-06: `apps/admin` builds with `pnpm --filter @zeromail/admin build`, artifact <500KB gzipped; `apps/web` Next.js bundle analyzer shows zero admin schema type references; `admin.zeromail.com/login` serves SPA login screen; `zeromail.com/admin` returns 404 (no admin code in Next.js); `springdoc-openapi` exposes `/v3/api-docs/public` + `/v3/api-docs/admin` separately
- [ ] ADMIN-07: Audit viewer paginates + filters by actor/action/target/date; CSV export streams up to 10k rows
- [ ] ADMIN-08: Destructive actions require confirm-twice + min-8-char reason; reason recorded in audit row; forbidden prefix rejected
- [ ] ADMIN-09: `admin_users` Liquibase schema deploys with all columns + CHECK + UNIQUE; DELETE attempt on the table returns Postgres permission error; UPDATE for `status`/`last_used_at`/`signature_counter` allowed
- [ ] ADMIN-10: WebAuthn enrollment ceremony succeeds with valid one-time token → row ACTIVE + credential stored; expired token returns 410; replayed token returns 410; WebAuthn assertion with downgraded `signCount` rejected + `WEBAUTHN_REPLAY_SUSPECTED` audit row written; assertion against REVOKED row returns 401; `userVerificationRequirement=REQUIRED` enforced (assertion with `userVerified=false` rejected)
- [ ] ARCH-08: `AdminContextMutexTest` green; `AdminTenantAccess.readOnly` writes `admin_read_event` before invoking supplier
- [ ] ARCH-09: `AdminPathBodyBanTest` green on production code; fires on test fixture
- [ ] ARCH-10: Repo grep for Gmail send returns exactly 1 hit; admin packages cannot reference send methods (ArchUnit)
- [ ] ARCH-11: `MasterKeySentinelLeakTest` green; fixture inserting `sk-test123` fails the test
- [ ] ARCH-12: `UPDATE admin_audit_event` fails with Postgres trigger exception; HMAC chain verification job alerts on mismatch
- [ ] MKEY-01: Setting a master key writes encrypted row; decrypted value matches input
- [ ] MKEY-02: Master keys displayed masked-only; edit requires 5-min token; >10 req/hour returns 429
- [ ] MKEY-03: Test-connection returns enum-only; provider error bodies never in response or logs
- [ ] MKEY-04: Rotation against valid key writes new row + evicts ChatModel cache; rotation against invalid key preserves old row
- [ ] MKEY-05: 9Router toggle between `OPENAI_FORMAT` / `ANTHROPIC_FORMAT` routes correctly; other 5 providers fixed
- [ ] MKEY-06: Dependents count badge updates; 90-day-old key shows tag
- [ ] MKEY-07: `ProviderMasterKeyResolver` confinement ArchUnit rule green; resolver caches decrypted key
- [ ] MKEY-08: Master-key `admin_audit_event` JSON contains no `sk-` / `sk-ant-` / `AIza` / `sk-or-` substrings
- [ ] CAT-01: 3-table catalog schema deploys; FK prevents stale pin; UNIQUE partial index works
- [ ] CAT-02: 3-step Sync (Fetch → Diff → Confirm); 60s debounce works; auto-apply forbidden
- [ ] CAT-03: Bad model ID rejected; bad provider response shape fails Sync without partial apply
- [ ] CAT-04: Anthropic Claude family seeded via Liquibase; Sync button disabled; manual entry works
- [ ] CAT-05: Disabling pinned model requires confirm-twice + reason; soft-delete; pinned tenants keep working
- [ ] CAT-06: `GET /api/settings/catalog` returns user-facing shape; admin shape lives in `admin-schema.d.ts` only
- [ ] CAT-07: Sync confirm emits `CatalogChangedEvent` evicting affected ChatModel caches
- [ ] OPS-TENANT-01: Tenant list renders metadata-only; k-anonymity on spend buckets
- [ ] OPS-TENANT-02: 5-tab detail renders metadata-only; chat-session "Show details" disabled
- [ ] OPS-TENANT-03: Pause/disconnect/delete actions require confirm-twice + reason; cascade works
- [ ] OPS-TENANT-04: `AdminResponseBodyBanFilter` trips on >200-char forbidden-key field; production endpoints don't trip
- [ ] OPS-TENANT-05: ArchUnit fires on test fixture injecting `GmailOAuthTokenService` into admin controller
- [ ] OPS-QUEUE-01: `/admin/queue` shows correct aggregates; 10s auto-refresh; no payload in responses
- [ ] OPS-QUEUE-02: Dead-letter re-queue works without exposing payload; payload edit rejected
- [ ] OPS-SPEND-01: `/admin/spend` aggregates correctly; date range >90d rejected; k-anonymity enforced
- [ ] OPS-SPEND-02: ArchUnit fires on test fixture injecting prompt accessor; production spend response passes body-ban filter
- [ ] HTML prototype: `08-PROTOTYPE.html` exists, self-contained, covers all 7 admin screen groups, consistent with locked design tokens (per UI phase prototype rule)

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                              |
|--------------------|-------|------|--------|----------------------------------------------------|
| Goal Clarity       | 0.88  | 0.75 | ✓      | 12 success criteria, 42 REQ-IDs (post-pivot adds 2) |
| Boundary Clarity   | 0.88  | 0.70 | ✓      | Explicit in/out scope; pivot adds explicit "no Google OAuth admin / no HTTP Basic / no password / no Next.js admin" |
| Constraint Clarity | 0.85  | 0.65 | ✓      | 10 planning-time decisions locked (8 pre-pivot + 2 from discuss-phase research) + expanded research mandate |
| Acceptance Criteria| 0.87  | 0.70 | ✓      | 43 pass/fail criteria                              |
| **Ambiguity**      | 0.14  | ≤0.20| ✓      | Improved from 0.16 (pre-pivot) — auth shape now sharper |

**Status:** ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

**Scope risk note:** Merged 40-requirement scope is unusually large for a single phase (1.6× Phase 7 baseline). Planner should expect to split PLAN.md into sub-plans (8A.PLAN / 8B.PLAN / ... / 8F.PLAN) within this same phase directory. Risk concentration in master keys (Pitfall 2) + catalog Sync (Pitfall 4, 7) + tenant inspection (Pitfall 3) requires 8A foundation patterns to prove out before 8B/8C/8D code lands — sequencing matters.

## Interview Log

| Round | Perspective     | Question summary                            | Decision locked                                                  |
|-------|-----------------|---------------------------------------------|------------------------------------------------------------------|
| 0     | Researcher      | What exists today; what's the delta?        | Grounded baseline from ROADMAP + REQUIREMENTS + research SUMMARY; initial ambiguity already ≤ 0.20 |
| 1     | Boundary Keeper | Method-security mechanism — meta-annotation vs explicit `@PreAuthorize`? | Explicit `@PreAuthorize` per controller + ArchUnit gate; defer `@AdminController` meta-annotation until rule-of-three (Phase 9+) |
| 1     | Boundary Keeper | Role elevation mechanism — where to wire?   | (pre-pivot) `GrantedAuthoritiesMapper` bean; (POST-PIVOT discuss-phase) entire concept removed — admin authority comes from separate `admin_users` table via `AdminUserDetailsService` on dedicated chain |
| 2     | Simplifier      | Merge Phase 8 + 9 into single phase, or keep split? | **Merged** into single Phase 8 (40 reqs, post-pivot 42 reqs); former Phase 10 renumbered → Phase 9; planning structure inside phase: 8A foundation → 8B/8C/8D/8E/8F callers wave-parallel after 8A |
| 2     | Simplifier      | OPS-INFRA gating — merge gate or deploy step? | Compose definitions + runbook in merge gate; live VPS migration is deploy step (tracked separately) |
| 2     | Simplifier      | ARCH-09 body-ban — ship Phase 8 or defer?   | Mandatory ship Phase 8 (OPS-TENANT projection now in scope, body-ban must precede) |
| 3     | Failure Analyst | Spring Security 7 API surface risk          | Plan-phase MUST pull Security 7 docs via Context7 before coding; memory note `project_phase8_spring_security_7_research` saved |
| 4 (discuss-phase pivot) | Failure Analyst + Simplifier | Admin auth method + frontend shape: bundled Google OAuth single-app vs separate frontend + Basic Auth vs WebAuthn passkey separate frontend? | **WebAuthn passkey + separate `apps/admin` Vite+React on `admin.zeromail.com` + 2 SecurityFilterChain via `securityMatcher`**. Reasoning: WebSearch + Spring Security 7 Context7 confirmed 2026 best practice = passkeys (HTTP Basic deprecated by OWASP ASVS; Google OAuth admin couples compromise surface to Google IdP). Spring Security 7 ships `.webAuthn(...)` DSL natively. Decoupling admin auth removes user-side RBAC entirely (`users.role` column not added). ADMIN-01/02/03/06 + ARCH-08 rewritten inline in SPEC.md; ADMIN-09 (admin_users schema) + ADMIN-10 (WebAuthn ceremony) added. Memory note `project_v12_admin_webauthn_pivot` saved. |

---

*Phase: 08-admin-console-operator-tooling*
*Spec created: 2026-05-19*
*Next step: /gsd:discuss-phase 8 — implementation decisions (PLAN.md structure, sub-plan split if needed, sequencing 8A → 8F, Spring Security 7 API choice points)*

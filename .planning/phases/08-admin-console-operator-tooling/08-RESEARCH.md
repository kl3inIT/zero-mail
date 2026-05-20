# Phase 8: Admin Console & Operator Tooling — Research

**Researched:** 2026-05-19
**Domain:** Operator/admin surface on Spring Boot 4 + Spring Security 7 with WebAuthn passkey auth, append-only HMAC-chained audit, dual SecurityFilterChain isolation, and read-only multi-tenant inspection — sitting on top of a shipped v1.1 Java 25 / Spring Modulith codebase
**Confidence:** HIGH on Spring Security 7 WebAuthn DSL (verified via official Spring docs + Maven Central), HIGH on existing codebase integration points (verified by reading source), MEDIUM on provider `/models` endpoint shapes (cited but not all exercised), MEDIUM on Vite + React 19 admin SPA monorepo wiring (training-data based with WebSearch confirmation), HIGH on documented pitfalls.

> Cảnh báo định hướng: phase này merge từ Phase 8 cũ + Phase 9 cũ và đổi auth shape giữa chừng (Google OAuth → WebAuthn). RESEARCH.md này coi mọi giả định pre-pivot (env-var bootstrap, `users.role`, một filter chain) là **không còn áp dụng** — planner phải sử dụng các quyết định POST-PIVOT trong CONTEXT.md (D-01..D-23) làm nguồn duy nhất.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Architectural Shape (POST-PIVOT 2026-05-19)**
- **D-01:** Admin auth uses **WebAuthn passkey** via Spring Security 7 `.webAuthn(...)` DSL with `userVerificationRequirement=REQUIRED`. Not Google OAuth, not HTTP Basic, not password.
- **D-02:** Admin frontend = **NEW `apps/admin` Vite + React 19 SPA** at `admin.zeromail.com` — no SSR, no SEO, no Next.js. Public `apps/web` bundle stays free of admin schema types and admin route code.
- **D-03:** Backend = **1 JVM** (`backend/api`) with **2 `SecurityFilterChain` beans** via `securityMatcher`: `@Order(1) adminChain` matches `/api/admin/**` + `.webAuthn(...)`; `@Order(2) userChain` (no matcher) keeps `.oauth2Login(...)` current. ArchUnit enforces non-overlap.
- **D-04:** Admin identity store = **NEW `admin_users` table** (separate from `users`). `users` table gains NO `role` column. Admin authority sourced via `AdminUserDetailsService` on admin chain only.
- **D-05:** First-admin bootstrap = **Liquibase seed of `admin_users` row(s)** from `zeromail.admin.bootstrap-emails` config + Spring Boot startup runner prints **10-min one-time enrollment URL to STDOUT** (never log file or DB).

**Spring Modulith Module Structure**
- **D-06:** Single `core.admin` top-level Modulith module with vertical sub-packages: `auth/`, `audit/`, `mkey/`, `cat/`, `tenant/`, `queue/`, `spend/`. Each sub-package follows `domain/usecases/projection/persistence/exception/`.
- **D-07:** **No `@NamedInterface` annotations** on sub-packages in Phase 8.
- **D-08:** `ProviderMasterKeyResolver` and `/models` HTTP client live in `core.llm.gateway.springai.admin`. Master-key storage + admin CRUD live in `core.admin.mkey`. `MasterKeyRotatedEvent` and `CatalogChangedEvent` are Spring Modulith events.

**Method-Security & RBAC**
- **D-09:** **Explicit `@PreAuthorize("hasRole('ADMIN')")` per `@RestController`** in `controllers/admin/`. No `@AdminController` meta-annotation until rule-of-three.
- **D-10:** `AdminContext` ScopedValue + `TenantContext` ScopedValue are **mutually exclusive**. `AdminTenantAccess.readOnly(tenantId, supplier)` is the only legitimate path for cross-tenant admin reads.

**Tenant Detail Routing**
- **D-11:** Tenant detail 5-tab page uses **single React Router route + shadcn `<Tabs>` + `?tab=` query param**. `admin_read_event` writes 1 row per tab visit.

**Audit & Append-Only Invariants**
- **D-12:** `admin_audit_event` **indefinite retention**; `admin_read_event` **30-day retention**.
- **D-13:** `admin_audit_event` grant = INSERT + SELECT only; Postgres `BEFORE UPDATE OR DELETE` trigger raises EXCEPTION; HMAC-SHA256 `hmac_chain_hash` per row.

**Catalog & Master Keys**
- **D-14:** Catalog = **3-table normalized** (`provider_catalog`, `model_catalog`, `feature_binding`) — NOT JSONB.
- **D-15:** Sync-from-`/models` = **3-step Fetch → Diff → Confirm** via `processing_job` SKIP LOCKED with 60s Redis debounce lease. Auto-apply forbidden. Model IDs validated against `^[a-zA-Z0-9._:/\-]{1,128}$` + per-provider JSON Schema.
- **D-16:** Anthropic catalog: Liquibase data seed for initial Claude family; Sync button disabled; manual entry only.
- **D-17:** Master keys reuse existing **AES-GCM `RefreshTokenCipher`**.
- **D-18:** Master-key rotation flow: enter → test against `GET /v1/models` → on OK, write new row + emit `MasterKeyRotatedEvent` → `@ApplicationModuleListener` evicts every cached `ChatModel` for that provider across all tenants.
- **D-19:** 9Router master-key entry has `key_format` toggle (`OPENAI_FORMAT` | `ANTHROPIC_FORMAT`); `ProviderMasterKeyResolver` selects Spring AI adapter accordingly.

**Tenant Inspection**
- **D-20:** Tenant chat-session inspection limited to **metadata only**. "Show details" disabled.
- **D-21:** `AdminResponseBodyBanFilter` failsafe runs on `/api/admin/**` responses; >200-char string fields keyed on `body|bodyHtml|snippet|payload|prompt|completion|content` → HTTP 500 + audit row.

**OPS-INFRA Gating**
- **D-22:** Phase 8 ships docker-compose changes + NPM subdomain config + `docs/ops/v1.2-deploy.md` runbook. Live VPS migration = separate deploy step.
- **D-23:** Optional IP allowlist for `admin.zeromail.com` at NPM proxy layer documented; not mandatory.

### Claude's Discretion

- Tenant detail tab routing (D-11) — locked by Claude during discuss-phase.
- **PLAN.md structure inside phase** (single PLAN.md covering 42 reqs across waves vs. split `8A.PLAN`..`8F.PLAN`) — deferred to plan-phase. **Research recommends split** (see § Sub-Phase Boundary Recommendations).
- **Liquibase changelog grouping** — match existing per-feature convention (per-feature 048..054).
- **Admin enrollment URL out-of-band delivery channel** — operator's choice; runbook documents options.
- **Audit log row presentation** — UI concern, defer to ui-phase.
- **Catalog Sync Diff page layout** — UI concern, defer to ui-phase.

### Deferred Ideas (OUT OF SCOPE)

- `@NamedInterface` API surfaces in `backend/core` (todo `2026-05-12-...`) — defer to Phase 11+.
- Test-profile `SecurityConfig` slice (todo `2026-04-28-wr-06-...`) — defer to security-hardening phase.
- Rules UX structured When/Then builder (todo `2026-05-15-...`) — v1.3+.
- `@AdminController` meta-annotation — defer to Phase 11+ when admin controllers ≥6.
- Two-cookie session split — defer to v1.3+ if real CSRF/impersonation vector surfaces.
- Self-service passkey recovery UI — defer to v1.3+ with proper out-of-band identity verification.
- Multiple passkey enrollment per admin (primary + backup) — v1.3+ enhancement.
- TOTP fallback for admin — WebAuthn-only locked.
- Admin-side IP allowlist UI — v1.3+ (NPM-level documented in OPS-INFRA-03).
- Audit log forensic export with cryptographic chain proof — future ADR.
- Cross-process admin events (if/when `backend/admin-api` JVM split) — v1.3+.
- Spring AI starter exclusion in admin path — currently in-scope only via discipline.
- Live VPS migration scripting — separate deploy step.
- Per-prompt drill-down in spend dashboard — forbidden by privacy invariant.
- Admin RBAC beyond ROLE_ADMIN — no granular admin roles in v1.2.

</user_constraints>

<phase_requirements>
## Phase Requirements

42 requirements locked. See `08-SPEC.md` for full Current/Target/Acceptance per entry. Mapping IDs → research support areas below.

| Cluster | IDs | Research Support |
|---------|-----|------------------|
| OPS-INFRA | OPS-INFRA-01, 02, 03 | § L (NPM + 9Router + Let's Encrypt + `admin.zeromail.com`) |
| Admin Auth | ADMIN-01, 02, 03, 09, 10 | § A (Spring Security 7 dual chain + WebAuthn DSL), § B (WebAuthn ceremony lifecycle), § F (AdminContext) |
| Admin Audit | ADMIN-04, 05, ARCH-12 | § E (HMAC chain + Postgres trigger) |
| Admin Frontend | ADMIN-06, 07, 08 | § C (Vite SPA), § D (GroupedOpenApi) |
| Architectural Invariants | ARCH-08, 09, 10, 11 | § F (ScopedValue mutex), § G (AdminResponseBodyBanFilter), § O (tests) |
| Master Keys | MKEY-01..08 | § N (AES-GCM reuse), § H (provider `/models` shapes), § M (9Router toggle) |
| Catalog | CAT-01..07 | § H (Sync flow + processing_job — see Gap C-1), § D (GroupedOpenApi split for `GET /api/settings/catalog`) |
| Tenant Inspection | OPS-TENANT-01..05 | § I (5-tab metadata + cascade design — see Gap T-1), § G (body-ban filter) |
| Queue Health | OPS-QUEUE-01, 02 | § J (read-only aggregates over `outbox`+`processing_job` — see Gap C-1) |
| Spend Dashboard | OPS-SPEND-01, 02 | § K (aggregates over `llm_call_audit`) |
</phase_requirements>

## Summary

Phase 8 stacks an operator-facing admin console on top of the v1.1 Java/Spring multi-tenant baseline without adding more than two runtime dependencies: **`com.webauthn4j:webauthn4j-core` 0.29.1.RELEASE** for the Spring Security `.webAuthn(...)` DSL backend and **`@simplewebauthn/browser` 13.3.0** for the client-side ceremony in `apps/admin`. Everything else — AES-GCM cipher, ScopedValue tenant context, Postgres SKIP LOCKED queue patterns, shadcn primitives, `openapi-fetch`, Spring Modulith events, Spring Session Redis — is already on the classpath or workspace.

The risk concentrates in five surfaces, in order of severity: (1) dual `SecurityFilterChain` configuration where chain bleed or session-cookie collision quietly grants admin authority on tenant paths; (2) WebAuthn ceremony state (challenge persistence across SPA navigation, `userVerificationRequirement=REQUIRED` enforcement, signCount replay defense); (3) Hibernate `CurrentTenantIdentifierResolver` returning `BOOTSTRAP_TENANT` (the `(0,0,0,0)` UUID) on admin paths — admin JPA reads of tenant data **silently return empty rows**, forcing all cross-tenant admin reads through `AdminTenantAccess.readOnly` or Spring Data JDBC; (4) master-key oracle leakage via test-connection (provider error bodies, log lines, audit JSON); (5) catalog + `ChatModel` cache races across tenants on Sync confirm and key rotation.

**Primary recommendation:** Hard-gate sub-phase 8A on **four** invariants landing before any caller (8B–8F) ships: (a) dual SecurityFilterChain + WebAuthn DSL + `admin_users` schema + `AdminUserDetailsService`; (b) `AdminContext` ScopedValue mutex + `AdminTenantAccess.readOnly` + ArchUnit `AdminContextMutexTest`; (c) `admin_audit_event` + `admin_read_event` schema + append-only trigger + HMAC chain helper; (d) `AdminResponseBodyBanFilter` + `AdminPathBodyBanTest` ArchUnit. After 8A merges green, 8B (master keys) and 8C/8E/8F (read-only views) can wave-parallelize; 8D (catalog) sequences after 8B.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| WebAuthn passkey ceremony (registration + assertion) | Browser (`@simplewebauthn/browser` invokes `navigator.credentials.create/get`) + Backend API (Spring Security `.webAuthn(...)` DSL endpoints) | — | WebAuthn is by spec split: hardware authenticator lives in browser; RP validation lives on server. Cannot push either side to a different tier. |
| Admin SecurityFilterChain | Backend API (`@Order(1)` bean) | — | Servlet-level concern; cannot live in worker (no request) or frontend. |
| AdminContext ScopedValue | Backend API filter (binds) + `backend/core` (consumes via `currentOrThrow`) | — | Mirrors v1.1 TenantContext shape; binding happens in API filter, consumers live in core. |
| Append-only audit trigger + HMAC chain | Database (Postgres trigger, INSERT-only grant) + Backend core (HMAC computation in service) | — | DB-level append-only is the only credible defense; computing HMAC in Java keeps the chain key out of the DB. |
| Admin SPA chrome (ADMIN MODE banner, routing) | Frontend `apps/admin` (Vite + React 19 SPA, served by NPM as static files) | CDN/Static (NPM caches static bundle) | DNS subdomain + SPA isolation is the locked design; SSR/Next.js is explicitly out of scope (D-02). |
| `apps/admin` OpenAPI client | Build-time codegen (Node script consumes `/v3/api-docs/admin`) → committed to `apps/admin/src/lib/api/admin-schema.d.ts` | — | Same shape as existing `apps/web/scripts/generate-api.ts`. |
| Master-key encryption at rest | Backend core (`RefreshTokenCipher` reuse, AAD = sentinel `"master-key"`) | Database (`encrypted_key BYTEA`) | Reuses KEK rotation infrastructure. See § N for AAD-rebinding pitfall. |
| Master-key resolution on hot path | Backend core `core.llm.gateway.springai.admin.ProviderMasterKeyResolver` | Cache (in-memory TTL matched to ChatModel cache) | Single resolution point preserves "one adapter package" boundary (D-08). |
| Catalog Sync Fetch step | Backend worker (consumes `processing_job` rows via SKIP LOCKED) | Backend API (admin POST enqueues + Redis 60s debounce lease) | Async to avoid holding admin HTTP request open; matches existing v1.0 SKIP LOCKED worker pattern. |
| Catalog Sync Diff/Confirm | Backend API (synchronous: pulls Fetch result, renders diff JSON, applies in transaction) | — | Diff review must be a synchronous admin decision; no good reason to involve worker. |
| Tenant inspection 5-tab aggregates | Backend API admin controllers + Backend core projection layer (Spring Data JDBC reads, **NOT JPA** — see Pitfall #6) | — | JPA `@TenantId` filter + `BOOTSTRAP_TENANT` sentinel makes cross-tenant admin reads via JPA silently return empty. |
| Queue health aggregates | Backend API (read-only SQL aggregates over `outbox` + `processing_job`) | — | Pure read; no domain logic. |
| Spend dashboard aggregates | Backend API (read-only SQL aggregates over `llm_call_audit`) + per-provider/feature/tenant rollups | — | Pre-existing audit table; admin layer reads metadata columns only. |
| AdminResponseBodyBanFilter | Backend API (Servlet `OncePerRequestFilter` wrapping response output stream) | — | Must run after Jackson serialization to inspect actual JSON wire shape. |

## Standard Stack

### Core (Backend)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Security 7.0.5 | 7.0.5 (already on classpath via Boot 4.0.6) | Dual SecurityFilterChain, `.webAuthn(...)` DSL, `@PreAuthorize`, method security | Locked by project; Boot 4 dependency-managed [VERIFIED: spring-security-web 7.0.5 confirmed via Maven Central search] |
| webauthn4j-core | 0.29.1.RELEASE (published 2025-05-01) | Required runtime backend for Spring Security `.webAuthn(...)` DSL (`Webauthn4JRelyingPartyOperations`) | Spring Security ships the DSL adapters but **not** the webauthn4j engine — must be added explicitly [VERIFIED: Maven Central `com.webauthn4j:webauthn4j-core` query returned 1 hit, latestVersion 0.29.1.RELEASE; spring-security-web 7.0.5 pom inspected and contains NO transitive webauthn4j] |
| Spring Modulith | (current Boot 4 BOM) | `core.admin` module + `@ApplicationModuleListener` for `MasterKeyRotatedEvent` / `CatalogChangedEvent` | Already used for v1.0/v1.1 cross-module events; matches `feedback_modulith_listener_scope` memory note |
| Liquibase | 5.0.2 | 7 new YAML changelogs (048-054) | Locked by project; per-feature numeric convention (047 priors) |
| Postgres 17.6 | self-hosted on VPS | `admin_users`, `admin_audit_event`, `admin_read_event`, `llm_provider_master_key`, catalog 3-table set, BEFORE UPDATE OR DELETE trigger | Locked datastore |
| Spring Data JDBC | already on classpath | Admin tenant-inspection projections that need to read tenant-scoped tables **without** Hibernate's `@TenantId` filter applying (see Pitfall #6) | Spring Data JDBC bypasses Hibernate multi-tenancy; required for `AdminTenantAccess.readOnly` projection reads |
| Spring Session Redis | already on classpath | Shared session store for both filter chains | Locked by v1.1 |
| `RefreshTokenCipher` | existing class | AES-GCM-256 for master keys (reuse) | Same KEK rotation as OAuth tokens (D-17) |
| springdoc-openapi | 3.0.3 (already on classpath) | `GroupedOpenApi` split: `/v3/api-docs/public` vs `/v3/api-docs/admin` | Standard split pattern; `OpenApiConfig` already uses `GlobalOpenApiCustomizer` anticipating group split [CITED: `OpenApiConfig.java` source comment "future grouping via GroupedOpenApi would silently bypass plain OpenApiCustomizer beans"] |

### Core (Frontend — `apps/admin`)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vite | 7.x (latest stable at v1.2 ship) | Bundler for admin SPA | Locked by D-02 (no Next.js for admin); industry standard for SPA |
| React | 19.2.5 | Match `apps/web` React version | Workspace consistency; locked by D-02 |
| React Router | 7.x | Client-side routing (no SSR) | Standard React 19 SPA router; community default |
| TanStack Query | 5.100.1 | Server state for admin endpoints | Already used in `apps/web`; same key-factory pattern |
| openapi-fetch | 0.17.0 | Typed admin API client | Already used in `apps/web/lib/api` |
| openapi-typescript | 7.13.0 | Codegen from `/v3/api-docs/admin` → `admin-schema.d.ts` | Already used in `apps/web/scripts/generate-api.ts` |
| Tailwind CSS | 4.2.4 | Match `apps/web` | Workspace consistency |
| shadcn/ui primitives | copy-paste | Tabs, Tables, Dialog, AlertDialog, Sheet, Sidebar, Chart, Switch, Sonner, Skeleton, Badge, Alert, etc. | Copy from `apps/web/components/ui/` per shadcn convention (do NOT workspace re-export — shadcn primitives are intentionally per-app source) |
| `@simplewebauthn/browser` | 13.3.0 | Client-side `startRegistration` / `startAuthentication` ceremony invocation | De facto standard WebAuthn browser shim; ~2.1M weekly downloads on npm; maintained by `MasterKale` (SimpleWebAuthn project) [VERIFIED: `npm view @simplewebauthn/browser` returned version 13.3.0 + repo github.com/MasterKale/SimpleWebAuthn; weekly downloads 2,100,368 verified via api.npmjs.org] |

### Supporting (Already Present — Re-Used)

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `TenantContext` ScopedValue | Existing tenant binding | `AdminTenantAccess.readOnly` uses `TenantContext.runWith(tenantId, ...)` to legitimately bind a tenant inside an admin scope |
| `RefreshTokenCipher` | AES-GCM-256 | Reuse via `MasterKeyCipher` facade (recommend new tiny class to avoid renaming a v1.0 stable type) |
| `processing_job` SKIP LOCKED pattern | Already used in `CreditReserveWatchdogBatch`, `PubSubDeliveryRepository`, `TriageAuditPurgeBatch` | **GAP C-1:** no general-purpose `processing_job` *table* exists; existing pattern is per-domain. Catalog Sync needs a new `processing_job` table (or per-domain `catalog_sync_job`). See Gaps section. |

### Alternatives Considered

| Instead of | Could Use | Why Rejected |
|------------|-----------|--------------|
| Spring Security WebAuthn DSL + webauthn4j-core | Yubico java-webauthn-server library directly | Spring Security DSL is the integration point; Yubico's library is the engine inside webauthn4j-core's underlying competitors. Bypassing the DSL forfeits filter-chain integration, default URLs, `WebAuthnAuthentication` token wiring. |
| AES-GCM via existing `RefreshTokenCipher` | HashiCorp Vault / GCP KMS / AWS KMS | Single VPS deploy locked (CLAUDE.md "Hard do not use" implicit); KMS adds external dependency for no v1.2 benefit. |
| 3-table normalized catalog | Single JSONB `model_catalog` row per provider | FK + UNIQUE partial indexes prevent stale-pin failures from `assistant_settings`; JSONB defers schema enforcement to runtime. |
| Spring Modulith `@ApplicationModuleListener` | Postgres LISTEN/NOTIFY for cache eviction | In-JVM eventing is sufficient; LISTEN/NOTIFY would only matter if we split to a second JVM (deferred per CONTEXT.md). |
| Vite + React 19 SPA for `apps/admin` | Next.js route group `(admin)` inside `apps/web` | Decoupling at bundle level keeps admin schema types out of public Next.js bundle (locked D-02). |

### Installation

Backend (`backend/api/build.gradle.kts` or `gradle/libs.versions.toml`):
```kotlin
// New dependencies for Phase 8
implementation("com.webauthn4j:webauthn4j-core:0.29.1.RELEASE")
// (spring-security-web already on classpath via spring-boot-starter-security)
```

Frontend (`apps/admin/package.json`):
```bash
pnpm --filter @zeromail/admin add react@19.2.5 react-dom@19.2.5
pnpm --filter @zeromail/admin add react-router @tanstack/react-query openapi-fetch
pnpm --filter @zeromail/admin add @simplewebauthn/browser
pnpm --filter @zeromail/admin add -D vite @vitejs/plugin-react typescript openapi-typescript tailwindcss
```

**Version verification (executed during research):**
- `com.webauthn4j:webauthn4j-core` 0.29.1.RELEASE — Maven Central confirms (timestamp 2025-05-01).
- `@simplewebauthn/browser` 13.3.0 — npm registry confirms (last week downloads 2.1M).
- `spring-security-web` 7.0.5 — Maven Central confirms; **pom does NOT pull webauthn4j transitively** (verified by reading the 7.0.5 pom). The DSL classes live in `spring-security-web` but the engine adapter (`Webauthn4JRelyingPartyOperations`) requires the explicit webauthn4j-core dependency.

## Package Legitimacy Audit

slopcheck was **not installed** in the research environment. All packages below were verified by hand against (a) Maven Central / npm registry existence, (b) source repo presence, (c) maintainer reputation. Per project policy this means external packages are tagged `[ASSUMED]` only where there is no canonical reference; verified-via-official-source packages remain `[VERIFIED]`.

| Package | Registry | Age | Downloads / Adoption | Source Repo | Disposition |
|---------|----------|-----|----------------------|-------------|-------------|
| `com.webauthn4j:webauthn4j-core` 0.29.1.RELEASE | Maven Central | 8+ yrs (95 versions) | Linked from Spring Security docs as the engine for `Webauthn4JRelyingPartyOperations` | https://github.com/webauthn4j/webauthn4j | Approved [VERIFIED: maven central + Spring Security ref docs] |
| `@simplewebauthn/browser` 13.3.0 | npm | 6+ yrs | 2.1M downloads/week | https://github.com/MasterKale/SimpleWebAuthn | Approved [VERIFIED: official Spring Security docs link to `@simplewebauthn/browser`; npm registry confirms maintainer + version] |
| `vite` 7.x | npm | 4+ yrs | Industry standard SPA bundler | https://github.com/vitejs/vite | Approved [VERIFIED: well-known] |
| `react-router` 7.x | npm | 10+ yrs | Industry standard React SPA router | https://github.com/remix-run/react-router | Approved [VERIFIED] |
| `@vitejs/plugin-react` | npm | 4+ yrs | Standard Vite React plugin | https://github.com/vitejs/vite-plugin-react | Approved [VERIFIED] |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

> Note: If a future iteration of plan-phase introduces additional npm packages (charting library, CSV export library), the planner MUST re-run the Package Legitimacy Gate. Candidate libraries the planner should evaluate include `recharts` (already implied by shadcn `chart` primitive) and a CSV streaming library — verify these against Context7 or shadcn's docs before adding.

## Architecture Patterns

### System Architecture Diagram

```
                       ┌───────────────────────────┐
                       │  Browser (admin operator) │
                       │  - apps/admin SPA static  │
                       │  - @simplewebauthn/browser│
                       │  - navigator.credentials  │
                       └────────────┬──────────────┘
                                    │ HTTPS
                                    ▼
                       ┌───────────────────────────┐
                       │ nginx-proxy-manager (NPM) │
                       │  - admin.zeromail.com →   │
                       │      apps/admin static    │
                       │  - zeromail.com →         │
                       │      apps/web (Next.js)   │
                       │  - /api/*, /webauthn/*, /login/* → backend/api │
                       │  - Let's Encrypt per host │
                       └────────────┬──────────────┘
                                    │
                                    ▼
                       ┌───────────────────────────┐
                       │ backend/api Spring Boot 4 │
                       │   │                       │
                       │   ├─ @Order(1) adminChain │
                       │   │  - securityMatcher    │
                       │   │     /api/admin/**     │
                       │   │     /webauthn/**      │
                       │   │     /login/webauthn   │
                       │   │  - .webAuthn(...)     │
                       │   │  - AdminBindingFilter │
                       │   │       → AdminContext  │
                       │   │  - AdminResponseBody  │
                       │   │      BanFilter        │
                       │   │                       │
                       │   └─ @Order(2) userChain  │
                       │      - .oauth2Login(...)  │
                       │      - TenantBindingFilter│
                       │           → TenantContext │
                       │                           │
                       │ Both chains share:        │
                       │  - Spring Session Redis   │
                       │  - SecurityContextHolder  │
                       └──┬────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┬────────────────────┐
        ▼                 ▼                 ▼                    ▼
┌──────────────┐  ┌───────────────┐  ┌──────────────┐  ┌──────────────────┐
│ core.admin   │  │ core.llm      │  │ core.tenant  │  │ Postgres 17.6    │
│  auth/audit/ │  │  .gateway     │  │  TenantCtx   │  │  - admin_users   │
│  mkey/cat/   │  │  .springai    │  │  Resolver    │  │  - admin_audit_  │
│  tenant/     │  │  .admin       │  │  (returns    │  │      event       │
│  queue/spend │  │   - Provider  │  │   BOOTSTRAP_ │  │  - admin_read_   │
│              │  │     MasterKey │  │   TENANT if  │  │      event       │
│ HMAC chain   │  │     Resolver  │  │   none bound)│  │  - llm_provider_ │
│ AdminContext │  │   - /models   │  │              │  │      master_key  │
│   ScopedValue│  │     client    │  │              │  │  - provider_cat. │
│ AdminTenant  │◀─┤   - model     │  │              │  │  - model_cat.    │
│   Access     │  │     catalog   │  │              │  │  - feature_bind. │
│   .readOnly  │  │     reader    │  │              │  │  - processing_   │
└──────┬───────┘  └───────┬───────┘  └──────────────┘  │      job (NEW)   │
       │                  │                            │  - outbox (NEW)  │
       │ Spring Modulith  │                            │  - llm_call_audit│
       │ events:          │                            │      (existing)  │
       │  MasterKey       │                            └────────┬─────────┘
       │   RotatedEvent   │                                     │
       │  CatalogChanged  │                                     │ trigger:
       │   Event          │                                     │ BEFORE UPDATE
       │                  ▼                                     │ OR DELETE on
       │       ┌──────────────────┐                             │ admin_audit_
       │       │ ChatModel cache  │                             │ event RAISE
       │       │ (per-tenant,     │                             │ EXCEPTION
       │       │  per-provider)   │                             │
       │       │ eviction on event│                             ▼
       │       └──────────────────┘                       ┌──────────────┐
       │                                                  │ Worker JVM   │
       └──────────────────────────────────────────────────│ - Catalog    │
                                                          │   Sync Fetch │
                                                          │   (SKIP      │
                                                          │   LOCKED)    │
                                                          │ - admin_read │
                                                          │   _event     │
                                                          │   cleanup    │
                                                          │ - audit chain│
                                                          │   verify     │
                                                          └──────────────┘

Component responsibilities are in the table below; see § File-to-Implementation Map.
```

### Component Responsibilities (File-to-Implementation Map)

| File / Class | Responsibility | Module |
|--------------|----------------|--------|
| `backend/api/security/SecurityConfig.java` (extend) | Add `@Bean @Order(1) adminChain` + retain existing chain renamed `@Order(2) userChain`; remove old `@Order(3)` annotation | `backend/api` |
| `backend/api/security/AdminBindingFilter.java` (NEW) | After WebAuthn assertion success, look up `admin_users` row by `WebAuthnAuthentication.getName()` and bind `AdminContext.run(adminUser, () -> chain.doFilter(...))`. Counterpart to `TenantBindingFilter`. | `backend/api` |
| `backend/api/security/EnrollmentTokenGate.java` (NEW) | Servlet filter intercepting `/enroll?token=...`; validates one-time token against in-memory `ConcurrentHashMap<String, Instant>` (10-min TTL); opens short-lived enrollment session; routes user to `/webauthn/register/options` ceremony. | `backend/api` |
| `backend/core/admin/auth/AdminContext.java` (NEW) | `ScopedValue<AdminUser>` mirror of `TenantContext`; `currentOrThrow()`, `currentOptional()`, `run(AdminUser, Runnable)`. | `core.admin.auth` |
| `backend/core/admin/auth/AdminTenantAccess.java` (NEW) | `static <T> T readOnly(UUID tenantId, Supplier<T>)` — asserts `AdminContext.isBound()`, writes `admin_read_event` row in a separate `@Transactional(propagation=REQUIRES_NEW)`, then invokes `TenantContext.runWith(tenantId, () -> supplier.get())`. | `core.admin.auth` |
| `backend/core/admin/auth/AdminUserDetailsService.java` (NEW) | Resolves `org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository` lookups → `admin_users` row → `User.withUsername(email).roles("ADMIN").build()`. | `core.admin.auth` |
| `backend/core/admin/auth/AdminWebAuthnConfig.java` (NEW) | `@Bean Webauthn4JRelyingPartyOperations` configured with `rpId="admin.zeromail.com"`, `allowedOrigins=Set.of("https://admin.zeromail.com")`, `setCustomizeCreationOptions(b → b.authenticatorSelection(...userVerification(REQUIRED)))`, `setCustomizeRequestOptions(b → b.userVerification(REQUIRED))`. JDBC-backed repos. | `core.admin.auth` |
| `backend/core/admin/auth/BootstrapAdminRunner.java` (NEW) | `CommandLineRunner` reading `zeromail.admin.bootstrap-emails`; for each email with PENDING_ENROLLMENT row, generate 32-byte hex token, store in `EnrollmentTokenStore` (10-min TTL), `System.out.println("Enrollment URL: https://admin.zeromail.com/enroll?token=" + hex)`. | `core.admin.auth` |
| `backend/core/admin/audit/AdminAuditAppender.java` (NEW) | `void appendInSameTransaction(AdminAuditEvent event)` — computes `hmac_chain_hash = HMAC-SHA256(secret, prev.hash || canonical_json(event))`, inserts row via `JdbcTemplate`. Reads HMAC chain secret from `ZeroMailAdminProperties.audit.hmacChainSecretBase64`. | `core.admin.audit` |
| `backend/core/admin/audit/AdminReadAppender.java` (NEW) | Same shape as above but for `admin_read_event`. Called by `AdminTenantAccess.readOnly`. | `core.admin.audit` |
| `backend/core/admin/audit/AdminAuditChainVerifyJob.java` (worker) | Scheduled job; re-derives HMAC chain across `admin_audit_event` rows in order of `id`; emits Micrometer `admin_audit.chain.mismatch` counter on any drift. | `backend/worker` |
| `backend/core/admin/mkey/ProviderMasterKey.java` (entity) + `ProviderMasterKeyRepository` | `llm_provider_master_key` row mapping. | `core.admin.mkey` |
| `backend/core/admin/mkey/MasterKeyCipher.java` (NEW) | Tiny facade over `RefreshTokenCipher` with AAD = `"master-key:" + provider.name()` (NOT a tenantId — see Pitfall #5). | `core.admin.mkey` |
| `backend/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java` (NEW) | Single resolution point; reads `llm_provider_master_key` via repo, decrypts via `MasterKeyCipher`, caches in-memory with TTL matching ChatModel cache (e.g. 10 min). | `core.llm.gateway.springai.admin` |
| `backend/core/llm/gateway/springai/admin/ProviderModelsClient.java` (NEW) | `RestClient`-based `/models` fetcher per provider; isolated from logging proxy. | `core.llm.gateway.springai.admin` |
| `backend/core/admin/mkey/MasterKeyRotationService.java` | Set/test/rotate use cases; on success, emits `MasterKeyRotatedEvent` (Spring Modulith). | `core.admin.mkey` |
| `backend/core/admin/cat/CatalogSyncOrchestrator.java` | Enqueues `processing_job` row of type `CATALOG_SYNC_FETCH`; holds Redis 60s debounce lease; returns sync run ID for Diff polling. | `core.admin.cat` |
| `backend/worker/cat/CatalogSyncFetchWorker.java` | Consumes `processing_job` rows via `FOR UPDATE SKIP LOCKED`; calls `ProviderModelsClient`; writes Diff result to `catalog_sync_run` row (transient, 24h TTL). | `backend/worker` |
| `backend/core/admin/cat/CatalogSyncConfirmService.java` | Applies diff atomically; on success emits `CatalogChangedEvent`. | `core.admin.cat` |
| `backend/core/admin/tenant/TenantInspectionService.java` | 5-tab projections via Spring Data JDBC (NOT JPA — see Pitfall #6); all reads gated by `AdminTenantAccess.readOnly`. | `core.admin.tenant` |
| `backend/core/admin/queue/QueueHealthService.java` | Read-only SQL aggregates over `outbox` + `processing_job`. | `core.admin.queue` |
| `backend/core/admin/spend/SpendAggregationService.java` | Read-only SQL aggregates over `llm_call_audit`; k-anonymity bucketing for deleted tenants. | `core.admin.spend` |
| `backend/api/security/AdminResponseBodyBanFilter.java` (NEW) | `OncePerRequestFilter` registered on `/api/admin/**`; wraps `HttpServletResponse` with content-capturing wrapper; on commit, parses JSON via Jackson 3 `JsonMapper`, walks for `key ~ /body\|bodyHtml\|snippet\|payload\|prompt\|completion\|content/` AND `value.length > 200`; on match, replaces response with HTTP 500 + writes `admin_audit_event` row `ADMIN_RESPONSE_BODY_BAN_TRIPPED`. | `backend/api` |
| `backend/core/admin/arch/AdminContextMutexTest.java` | ArchUnit: classes in `..core.admin..` cannot reference `TenantContext.currentOrThrow` / `TenantContext.TENANT` except `AdminTenantAccess`. | `core.admin.arch` |
| `backend/core/admin/arch/AdminPathBodyBanTest.java` | ArchUnit: classes in `..controllers.admin..` and `..core.admin..projection..` cannot reference `GmailClient` body methods or fields named via the forbidden regex. | `core.admin.arch` |
| `backend/core/admin/arch/AdminChainNoOauth2LoginTest.java` | ArchUnit: `SecurityConfig.adminChain` lambda does not call `.oauth2Login(...)`; `userChain` does not call `.webAuthn(...)`. | `core.admin.arch` |
| `backend/core/admin/arch/AdminControllerPreAuthorizeTest.java` | ArchUnit: every `@RestController` in `..controllers.admin..` has `@PreAuthorize("hasRole('ADMIN')")`. | `core.admin.arch` |
| `backend/core/admin/arch/MasterKeySentinelLeakTest.java` | CI gate scanning logs/response bodies/exceptions/YAML/audit JSON for `sk-`, `sk-ant-`, `AIza`, `sk-or-` and base64-encoded forms. | `core.admin.arch` |
| `backend/core/admin/arch/MasterKeyResolverConfinementTest.java` | ArchUnit: only `core.llm.gateway.springai.admin.ProviderMasterKeyResolver` may inject `ProviderMasterKeyRepository`. | `core.admin.arch` |

### Recommended Project Structure

```
backend/
├── api/
│   ├── controllers/
│   │   └── admin/                     # NEW: every controller @PreAuthorize("hasRole('ADMIN')")
│   │       ├── AdminAuditController.java
│   │       ├── AdminGrantController.java
│   │       ├── AdminMasterKeyController.java
│   │       ├── AdminCatalogController.java
│   │       ├── AdminTenantController.java
│   │       ├── AdminQueueController.java
│   │       └── AdminSpendController.java
│   ├── dto/
│   │   └── admin/                     # NEW: admin response/request DTOs (records); @JsonInclude(NON_NULL)
│   ├── security/
│   │   ├── SecurityConfig.java        # MODIFIED: @Order(1) adminChain + @Order(2) userChain
│   │   ├── TenantBindingFilter.java   # unchanged
│   │   ├── AdminBindingFilter.java    # NEW
│   │   ├── EnrollmentTokenGate.java   # NEW
│   │   └── AdminResponseBodyBanFilter.java  # NEW
│   └── config/
│       └── OpenApiConfig.java         # MODIFIED: add GroupedOpenApi publicApi + adminApi
├── core/
│   ├── admin/                          # NEW top-level Modulith module
│   │   ├── auth/                       # AdminContext, AdminTenantAccess, AdminUserDetailsService, WebAuthn config, bootstrap
│   │   ├── audit/                      # AdminAuditAppender, AdminReadAppender, HMAC helpers
│   │   ├── mkey/                       # ProviderMasterKey entity/repo, MasterKeyCipher, rotation service
│   │   ├── cat/                        # ProviderCatalog/ModelCatalog/FeatureBinding entities, Sync orchestrator/diff/confirm
│   │   ├── tenant/                     # 5-tab inspection projections (Spring Data JDBC)
│   │   ├── queue/                      # QueueHealthService aggregates
│   │   ├── spend/                      # SpendAggregationService aggregates
│   │   ├── arch/                       # ArchUnit tests
│   │   └── package-info.java
│   └── llm/
│       └── gateway/
│           └── springai/
│               └── admin/              # NEW: ProviderMasterKeyResolver + ProviderModelsClient
└── worker/
    ├── admin/                          # NEW: audit chain verify, admin_read_event cleanup, CatalogSyncFetchWorker
    └── ...

apps/
├── admin/                              # NEW Vite + React 19 SPA
│   ├── package.json                    # name: "@zeromail/admin"
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.ts              # shared tokens via @import from a workspace package OR duplicate
│   ├── index.html
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx                     # ROUTER + ADMIN MODE banner chrome
│   │   ├── routes/
│   │   │   ├── enroll.tsx
│   │   │   ├── login.tsx
│   │   │   ├── index.tsx               # dashboard
│   │   │   ├── audit.tsx
│   │   │   ├── role-grants.tsx
│   │   │   ├── master-keys/[provider].tsx
│   │   │   ├── catalog/[provider].tsx
│   │   │   ├── tenants/index.tsx
│   │   │   ├── tenants/[tenantId].tsx  # 5-tab via ?tab= query param
│   │   │   ├── queue.tsx
│   │   │   └── spend.tsx
│   │   ├── components/ui/              # copied shadcn primitives
│   │   ├── lib/
│   │   │   └── api/
│   │   │       ├── admin-schema.d.ts   # codegenned
│   │   │       └── admin-client.ts     # openapi-fetch wrapper
│   │   ├── features/                   # per-feature api hooks + query keys
│   │   └── webauthn/
│   │       ├── register.ts             # wraps @simplewebauthn/browser startRegistration
│   │       └── authenticate.ts         # wraps @simplewebauthn/browser startAuthentication
│   └── scripts/
│       └── generate-api.ts             # consumes /v3/api-docs/admin
└── web/                                # unchanged
```

### Pattern 1: Dual SecurityFilterChain with WebAuthn admin + OAuth2 user

```java
// Source: https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html
//         + Spring Security multi-chain pattern (WebFetch 2026-05-19)
package com.zeromail.api.security;

import com.zeromail.core.admin.auth.AdminWebAuthnConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!test")
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(
            HttpSecurity http,
            AdminBindingFilter adminBindingFilter,
            AdminResponseBodyBanFilter adminBodyBanFilter,
            EnrollmentTokenGate enrollmentTokenGate) throws Exception {
        http.securityMatcher("/api/admin/**", "/webauthn/**", "/login/webauthn", "/enroll")
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/enroll", "/webauthn/register/options", "/webauthn/register",
                                 "/webauthn/authenticate/options", "/login/webauthn").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .webAuthn(webAuthn -> webAuthn
                .rpName("Zero Mail Admin")
                .rpId("admin.zeromail.com")
                .allowedOrigins("https://admin.zeromail.com"))
            .csrf(csrf -> csrf.spa())
            .sessionManagement(Customizer.withDefaults())
            .addFilterBefore(enrollmentTokenGate, org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterAfter(adminBindingFilter,
                org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .addFilterAfter(adminBodyBanFilter, AdminBindingFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain userChain(
            HttpSecurity http,
            TenantBindingFilter tenantFilter,
            GoogleOAuthSuccessHandler successHandler,
            LoginRedirectAuthenticationFailureHandler failureHandler,
            GoogleAuthorizationRequestResolver authRequestResolver) throws Exception {
        // ... existing v1.1 chain config, body identical to current SecurityConfig.chain(...)
        return http.build();
    }
}
```

**Critical configuration notes:**
- The `Webauthn4JRelyingPartyOperations` bean (provided by `AdminWebAuthnConfig`) sets `setCustomizeCreationOptions` to enforce `userVerificationRequirement.REQUIRED` — the DSL alone defaults to `"preferred"` (verified via WebFetch of Spring Security docs).
- `securityMatcher` must include `/webauthn/**` and `/login/webauthn` because those are the auto-registered DSL endpoints that the SPA hits; without them, the userChain catches the WebAuthn requests with OAuth2 filters.
- The user chain SHOULD also explicitly exclude `/api/admin/**` requests it never sees due to ordering — but defense-in-depth: an ArchUnit rule verifies `userChain` lambda does not reference `WebAuthn` types.

### Pattern 2: WebAuthn `userVerificationRequirement=REQUIRED`

```java
// Source: WebFetch of Webauthn4JRelyingPartyOperations Javadoc (2026-05-19)
package com.zeromail.core.admin.auth;

import com.webauthn4j.WebAuthnManager;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

@Configuration
public class AdminWebAuthnConfig {

    @Bean
    public PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbcOperations) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations);
    }

    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcOperations jdbcOperations) {
        return new JdbcUserCredentialRepository(jdbcOperations);
    }

    @Bean
    public Webauthn4JRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) {
        PublicKeyCredentialRpEntity rpEntity =
                new PublicKeyCredentialRpEntity("admin.zeromail.com", "Zero Mail Admin");
        Set<String> allowedOrigins = Set.of("https://admin.zeromail.com");
        Webauthn4JRelyingPartyOperations relyingPartyOperations =
                new Webauthn4JRelyingPartyOperations(
                        userEntities, userCredentials, rpEntity, allowedOrigins);
        relyingPartyOperations.setCustomizeCreationOptions(creationOptionsBuilder ->
                creationOptionsBuilder.authenticatorSelection(authenticatorSelectionBuilder ->
                        authenticatorSelectionBuilder.userVerification(
                                UserVerificationRequirement.REQUIRED)));
        relyingPartyOperations.setCustomizeRequestOptions(requestOptionsBuilder ->
                requestOptionsBuilder.userVerification(UserVerificationRequirement.REQUIRED));
        return relyingPartyOperations;
    }
}
```

[CITED: https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html — JdbcPublicKeyCredentialUserEntityRepository / JdbcUserCredentialRepository constructors; setCustomizeCreationOptions signature from Webauthn4JRelyingPartyOperations Javadoc]

> **Planner trap:** The Spring docs DSL example does not call `setCustomizeCreationOptions`; the default `userVerification` is `"preferred"`. The locked decision D-01 requires `REQUIRED`. The customize-callback above is the ONLY place to enforce this — there is no DSL shortcut. Verify in a test that the registration options response contains `"userVerification": "required"`.

> **JDBC repository schema:** `JdbcPublicKeyCredentialUserEntityRepository` + `JdbcUserCredentialRepository` ship with their own DDL — they expect specific table shapes. The `admin_users` schema in SPEC.md (ADMIN-09) is for our app-domain admin identity (status, email, last_used_at), separate from Spring's internal credential storage. The planner has two options: (a) let the Jdbc repos manage their own tables and link to `admin_users` by `user_handle`; (b) override the repo interfaces to read from `admin_users` directly. **Recommendation: option (a)** — adopt Spring's stock schema for `webauthn_user` + `webauthn_credential` tables (rename if collision), and keep `admin_users` as our domain table joined via `user_handle`. This isolates Spring's schema churn (likely between 7.0 and 7.x) from our domain.

### Pattern 3: AdminContext mutex with TenantContext

```java
// Source: mirrors backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java (read 2026-05-19)
package com.zeromail.core.admin.auth;

import java.util.Optional;

public final class AdminContext {

    public static final ScopedValue<AdminUser> ADMIN = ScopedValue.newInstance();

    private AdminContext() {}

    public static AdminUser currentOrThrow() {
        if (com.zeromail.core.tenant.TenantContext.TENANT.isBound()) {
            throw new IllegalStateException(
                    "AdminContext and TenantContext are mutually exclusive; "
                            + "a tenant binding is active. Use AdminTenantAccess.readOnly(...) "
                            + "to legitimately enter a tenant scope from admin code.");
        }
        if (!ADMIN.isBound()) {
            throw new IllegalStateException("No admin bound on this thread");
        }
        return ADMIN.get();
    }

    public static Optional<AdminUser> currentOptional() {
        return ADMIN.isBound() ? Optional.of(ADMIN.get()) : Optional.empty();
    }

    public static boolean isBound() {
        return ADMIN.isBound();
    }

    public static void run(AdminUser admin, Runnable action) {
        if (com.zeromail.core.tenant.TenantContext.TENANT.isBound()) {
            throw new IllegalStateException(
                    "Cannot enter admin scope while a tenant binding is active");
        }
        ScopedValue.where(ADMIN, admin).run(action);
    }
}
```

```java
package com.zeromail.core.admin.auth;

import com.zeromail.core.admin.audit.AdminReadAppender;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminTenantAccess {

    private final AdminReadAppender adminReadAppender;

    public AdminTenantAccess(AdminReadAppender adminReadAppender) {
        this.adminReadAppender = adminReadAppender;
    }

    /**
     * The ONLY legitimate path for admin code to read tenant-scoped data.
     * Writes an admin_read_event row in a separate transaction (REQUIRES_NEW) so the audit row
     * is durable even if the supplier throws. Then binds TenantContext for the duration of the
     * supplier. The AdminContext binding remains active inside the supplier; consumers can
     * still assert "this was an admin-on-behalf read" via AdminContext.isBound().
     */
    public <T> T readOnly(UUID tenantId, String reason, Supplier<T> supplier) {
        AdminContext.currentOrThrow(); // asserts admin is bound
        writeReadEvent(tenantId, reason);
        // Note: AdminContext stays bound; we add a TenantContext binding on top.
        // This is the ONLY codepath where both contexts overlap, and it must be
        // syntactically traceable for ArchUnit to whitelist.
        var result = new Object() { T value; };
        TenantContext.runWith(tenantId, () -> result.value = supplier.get());
        return result.value;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void writeReadEvent(UUID tenantId, String reason) {
        adminReadAppender.append(/* ... */);
    }
}
```

> **Critical subtlety:** the AdminContext mutex check above forbids entering admin scope while tenant is bound, AND vice versa for `TenantContext.currentOrThrow` (existing impl doesn't enforce this — Phase 8 must add a symmetric check). But `AdminTenantAccess.readOnly` legitimately overlaps BOTH bindings — this is intentional and must be ArchUnit-whitelisted. The mutex enforcement lives in `AdminContext.run()` (refuses entry if tenant bound) and a new `TenantContext.runWith()` check (refuses entry if admin bound EXCEPT when caller is `AdminTenantAccess`). The whitelist mechanism: use a sentinel marker via a second ScopedValue `AdminTenantAccess.OVERRIDE_MARKER` that `readOnly` binds before entering `TenantContext.runWith`.

### Pattern 4: Append-only Postgres trigger with HMAC chain

```sql
-- Source: mirrors 042-chat-message-and-body-ban-trigger.yaml (existing pattern)
CREATE TABLE admin_audit_event (
    id BIGSERIAL PRIMARY KEY,
    actor_admin_id UUID REFERENCES admin_users(id),
    actor_email VARCHAR(320) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_kind VARCHAR(40) NOT NULL,
    target_id VARCHAR(120),
    before_state_json JSONB,
    after_state_json JSONB,
    reason VARCHAR(500),
    request_ip INET,
    request_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    hmac_chain_hash BYTEA NOT NULL,
    prev_hash BYTEA  -- nullable on row id=1
);

CREATE INDEX idx_admin_audit_event_created_at ON admin_audit_event (created_at DESC);
CREATE INDEX idx_admin_audit_event_actor_action ON admin_audit_event (actor_admin_id, action);

CREATE OR REPLACE FUNCTION admin_audit_event_block_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin_audit_event is append-only; UPDATE/DELETE forbidden (op=%, row=%)',
        TG_OP, OLD.id;
END;
$$;

CREATE TRIGGER admin_audit_event_no_mutation
    BEFORE UPDATE OR DELETE ON admin_audit_event
    FOR EACH ROW
    EXECUTE FUNCTION admin_audit_event_block_mutation();

-- Privilege revocation (in a separate Liquibase changeSet that runs after grants are applied):
REVOKE UPDATE, DELETE ON admin_audit_event FROM zeromail_app;
GRANT INSERT, SELECT ON admin_audit_event TO zeromail_app;
GRANT USAGE, SELECT ON SEQUENCE admin_audit_event_id_seq TO zeromail_app;
```

```java
// HMAC computation in Java (NOT in Postgres trigger — keep secret out of DB)
package com.zeromail.core.admin.audit;

import com.fasterxml.jackson.databind.SerializationFeature;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;  // Jackson 3 namespace per CLAUDE.md

@Service
public class AdminAuditHmacChain {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] chainSecret;        // from ZeroMailAdminProperties
    private final ObjectMapper canonicalMapper;

    public byte[] computeNextHash(byte[] previousHash, AdminAuditEvent event) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(chainSecret, HMAC_ALGORITHM));
            if (previousHash != null) mac.update(previousHash);
            byte[] canonical = canonicalMapper.writeValueAsBytes(event);
            mac.update(canonical);
            return mac.doFinal();
        } catch (Exception cryptoException) {
            throw new IllegalStateException("HMAC chain compute failed", cryptoException);
        }
    }
}
```

> **Canonicalization is load-bearing.** Jackson default field ordering is **not stable** across JVM restarts unless `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` is enabled. The planner MUST configure the canonical ObjectMapper with sorted keys + UTF-8 + no whitespace. Failing this, the nightly chain verification job will flag false-positive mismatches whenever the JVM restarts with reordered field iteration. **Imports note:** Jackson 3 moved core/databind to `tools.jackson.*` (CLAUDE.md "Hard do not use"); annotations remain `com.fasterxml.jackson.annotation`.

### Pattern 5: AdminResponseBodyBanFilter (Jackson 3 streaming-aware)

```java
// Source: standard Spring MVC OncePerRequestFilter + ContentCachingResponseWrapper
package com.zeromail.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdminResponseBodyBanFilter extends OncePerRequestFilter {

    private static final Pattern FORBIDDEN_KEY =
            Pattern.compile("^(body|bodyHtml|snippet|payload|prompt|completion|content)$",
                    Pattern.CASE_INSENSITIVE);
    private static final int LENGTH_THRESHOLD = 200;

    private final ObjectMapper objectMapper;

    public AdminResponseBodyBanFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapped);
        if ("application/json".equalsIgnoreCase(wrapped.getContentType())
                || (wrapped.getContentType() != null
                    && wrapped.getContentType().contains("application/problem+json"))) {
            byte[] body = wrapped.getContentAsByteArray();
            if (body.length > 0 && containsForbiddenField(body)) {
                // Write audit row (separate transaction), then replace response
                wrapped.resetBuffer();
                wrapped.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                wrapped.getWriter().write(
                    "{\"code\":\"error.admin.body_ban_tripped\",\"message\":\"admin response body ban\"}");
            }
        }
        wrapped.copyBodyToResponse();
    }

    private boolean containsForbiddenField(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return walk(root);
        } catch (Exception parseException) {
            // Non-JSON or truncated; if we can't parse, treat as safe (HTML, CSV export)
            return false;
        }
    }

    private boolean walk(JsonNode node) {
        if (node.isObject()) {
            var fields = node.properties().iterator();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (FORBIDDEN_KEY.matcher(entry.getKey()).matches()
                        && entry.getValue().isString()
                        && entry.getValue().asString().length() > LENGTH_THRESHOLD) {
                    return true;
                }
                if (walk(entry.getValue())) return true;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (walk(child)) return true;
            }
        }
        return false;
    }
}
```

> **Streaming response carve-out:** CSV export endpoints (admin audit CSV) MUST set a non-JSON content type (`text/csv`); the filter skips them. The planner MUST verify in tests that the audit CSV stream skips the filter cleanly. Don't try to ban-scan CSV — the model name field could legitimately exceed 200 chars in CSV.

### Anti-Patterns to Avoid

- **Don't share `userChain` with `/api/admin/**`.** A single filter chain with `requestMatchers("/api/admin/**").hasRole("ADMIN")` plus OAuth2 login is the v1.0 pattern but FAILS the locked decision D-03 — admin must use WebAuthn assertion path, OAuth2 login filter would still attempt to bind a Google user on admin paths.
- **Don't read tenant tables from admin paths via JPA.** Hibernate's `ScopedValueTenantResolver` returns `BOOTSTRAP_TENANT` UUID when no tenant is bound; `@TenantId`-scoped entities silently filter to zero rows. Use Spring Data JDBC for admin projection reads, OR explicitly enter `AdminTenantAccess.readOnly`.
- **Don't store master keys with tenantId AAD.** Existing `RefreshTokenCipher.encrypt(byte[], String tenantId)` binds `tenantId` as AAD. Master keys are platform-wide. Pass a stable sentinel string (`"master-key:" + provider.name()`) as the AAD. Inconsistent AAD between encrypt and decrypt makes decryption fail silently in production.
- **Don't put `userVerification: preferred` (default).** Spring DSL default is `"preferred"`; D-01 requires `REQUIRED`. Use `setCustomizeCreationOptions` + `setCustomizeRequestOptions`.
- **Don't log master-key sentinels.** Even masked forms (`sk-****abc1`) match the regex `sk-`. Use Logback message converter scrubbing OR avoid masked display in logs entirely — use a different placeholder like `[REDACTED]`.
- **Don't trust `pre-pivot` text in CONTEXT.md or research SUMMARY.md.** Several `users.is_admin` / `users.role` / `GrantedAuthoritiesMapper` references in SUMMARY.md and PITFALLS.md are obsolete after the WebAuthn pivot. The planner must read CONTEXT.md `## Decisions` (D-01..D-23) as the single source of truth.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebAuthn registration/assertion ceremony | Custom CBOR parsing, attestation verification, signature counter logic | Spring Security 7 `.webAuthn(...)` DSL + `webauthn4j-core` engine | WebAuthn spec is dense (CBOR, COSE, attestation formats, FIDO MDS); webauthn4j is the de facto JVM engine; Spring Security wraps it correctly with replay defense. |
| WebAuthn JS client (challenge encoding, navigator.credentials.create/get wrapping, base64url) | Custom `TextEncoder.encode` + `crypto.subtle` glue | `@simplewebauthn/browser` | Maintained by SimpleWebAuthn (2.1M dl/week); pairs natively with Spring Security DSL JSON shape; handles base64url encoding correctly. |
| AES-GCM encryption | New cipher class | Existing `RefreshTokenCipher` via tiny `MasterKeyCipher` facade | Reuse KEK rotation infrastructure; only override AAD. |
| Append-only audit | Application-level enforcement only | DB-level Postgres trigger + REVOKE UPDATE/DELETE | App-level only is bypassed by `psql`; DB-level forbids tampering by any role. |
| HMAC chain over audit rows | Roll-your-own hash chain | `javax.crypto.Mac` HmacSHA256 + canonical Jackson 3 serialization | Standard primitive; the load-bearing piece is canonicalization, not the HMAC algorithm. |
| Postgres queue | Kafka / RabbitMQ / SQS | `outbox` + `processing_job` SKIP LOCKED (existing v1.0 pattern in `CreditReserveWatchdogBatch`, `PubSubDeliveryRepository`) | Locked by CLAUDE.md (no Kafka in v1). |
| OpenAPI codegen split | Hand-maintained `admin-schema.d.ts` | `springdoc-openapi` `GroupedOpenApi` + `openapi-typescript` | Hand-maintenance drifts; springdoc 3.0.3 supports group split natively. |
| Admin SPA bundler | Custom Webpack/Rollup config | Vite 7 + `@vitejs/plugin-react` | Industry standard; zero-config TS + React 19; matches `apps/web` family. |
| 5-tab routing in admin tenant detail | Multiple React Router routes per tab | Single `/tenants/:tenantId` route + shadcn `<Tabs>` + `?tab=` query param | Locked by D-11; shareable URL; one `admin_read_event` per tab visit. |
| Charting (spend dashboard) | Hand-drawn SVG | shadcn `<Chart>` primitive wrapping `recharts` | Already in `apps/web/components/ui/chart.tsx`. |

**Key insight:** The Phase 8 hand-rolled surface is **smaller than it looks**. The risk surface is *integration* (dual chains, ScopedValue mutex, AAD rebinding, codegen wiring), not *building primitives*. Every primitive listed above already exists in shipped code or a battle-tested library.

## Runtime State Inventory

This is a greenfield admin surface — no runtime rename. Per template, this section is omitted (see below for `Nothing found in category` markers because the phase touches multiple subsystems that could be affected by future renames).

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 8 creates new tables (`admin_users`, `admin_audit_event`, `admin_read_event`, `llm_provider_master_key`, `provider_catalog`, `model_catalog`, `feature_binding`); no existing data renamed. | None |
| Live service config | NPM proxy config (live VPS — out of scope per D-22) and 9Router sidecar SQLite volume mounted under `/opt/zeromail/9router-data` | OPS-INFRA-02 ships compose definitions; live cutover deferred (D-22). |
| OS-registered state | None — no Task Scheduler / launchd / systemd state created | None |
| Secrets / env vars | NEW: `zeromail.admin.bootstrap-emails` (list, in application.yml); `zeromail.admin.audit.hmacChainSecretBase64` (KMS-style secret, in SOPS or env); KEK reuses existing `zeromail.crypto.refreshTokens.keysByVersion` infrastructure | Planner must add a new SOPS-managed secret for the audit HMAC chain key. AAD reuse: NO new KEK; reuse existing. |
| Build artifacts / installed packages | NEW: `apps/admin/dist` static build output; `admin-schema.d.ts` codegen artifact | Add `apps/admin/dist` to `.gitignore`; ensure NPM proxy serves `admin.zeromail.com → /opt/zeromail/apps-admin-dist`. |

## Common Pitfalls

### Pitfall 1: WebAuthn `userVerificationRequirement` silently defaults to `preferred`

**What goes wrong:** Spring Security `.webAuthn(...)` DSL alone does NOT enforce `REQUIRED`. The DSL surfaces only `rpId`, `allowedOrigins`, `creationOptionsRepository`, `messageConverter`. To set `userVerification: required` (locked D-01), you must provide a custom `Webauthn4JRelyingPartyOperations` bean and call `setCustomizeCreationOptions` + `setCustomizeRequestOptions`.
**Why it happens:** The reference docs example shows the minimal DSL; the customize callback is in the Webauthn4JRelyingPartyOperations Javadoc, not the main DSL guide.
**How to avoid:** Plan-phase wave 0 task: write an integration test asserting `POST /webauthn/register/options` returns JSON with `authenticatorSelection.userVerification == "required"` AND `POST /webauthn/authenticate/options` returns `userVerification: "required"`. Fail the build if either is `"preferred"`.
**Warning signs:** Registration ceremony succeeds with a software passkey that has no user-verification capability (e.g., a touchless key fob).

### Pitfall 2: Hibernate `BOOTSTRAP_TENANT` silently filters admin reads

**What goes wrong:** Admin code calls a `core.gmail.GmailConnectionRepository` from an admin controller (after passing ArchUnit because the repo isn't on the body-ban regex). `TenantContext` is not bound (we're in admin scope). `ScopedValueTenantResolver.resolveCurrentTenantIdentifier()` returns `BOOTSTRAP_TENANT = (0,0,...,0) UUID`. Hibernate applies `@TenantId` filter `WHERE tenant_id = '00000000-...'` and returns zero rows. The admin sees "tenant has no Gmail connection" — incorrectly.
**Why it happens:** ScopedValueTenantResolver (read 2026-05-19) returns `BOOTSTRAP_TENANT` instead of throwing or skipping the filter when no tenant is bound. This is correct for bootstrap, but it means **admin-on-behalf JPA reads silently return empty**.
**How to avoid:** (a) ArchUnit rule forbidding admin packages from injecting JPA repos that have `@TenantId` filtered entities; (b) all tenant-data reads from admin paths go through `AdminTenantAccess.readOnly(tenantId, () -> jdbcTemplate.queryForList(...))` using Spring Data JDBC (which doesn't honor `@TenantId`); (c) if JPA is unavoidable, `readOnly` must enter `TenantContext.runWith(tenantId, ...)` BEFORE invoking the JPA call.
**Warning signs:** Admin tenant inspection shows "no data" or empty result sets for tenants you know are active. CI test fixture: insert a Gmail connection for tenant T, then read it from an admin controller WITHOUT going through `AdminTenantAccess.readOnly` — expect zero rows (the test guards against accidental JPA usage in admin code).

### Pitfall 3: `RefreshTokenCipher` AAD requires tenantId; master keys have no tenant

**What goes wrong:** `RefreshTokenCipher.encrypt(byte[] plaintext, String tenantId)` binds `tenantId` as AAD. For master keys, there is no tenant. Passing an empty string works but encrypts and decrypts will not match if any caller passes "" vs null. Passing the provider name as a `tenantId`-shaped string works mechanically but is semantically wrong and conflates two concepts.
**Why it happens:** `RefreshTokenCipher` was designed for tenant-scoped secrets (OAuth refresh tokens).
**How to avoid:** Introduce a tiny `MasterKeyCipher` facade in `core.admin.mkey` that wraps `RefreshTokenCipher` with a stable AAD string `"master-key:" + provider.name()`. Document this clearly. Add an integration test that round-trips an encrypted master key per provider. Plan-phase decision: do we relocate `RefreshTokenCipher` to `core.shared.crypto` and generalize the AAD parameter from `tenantId` to `aad`? **Research recommendation: YES, relocate + rename parameter** — but only if the planner accepts the slight ripple to existing callers. Otherwise, keep the facade.
**Warning signs:** Decryption silently fails (returns garbage or throws `AEADBadTagException`).

### Pitfall 4: Two SecurityFilterChain beans share the same session cookie

**What goes wrong:** Spring Session Redis writes one `SESSION` cookie per app context by default. If a Google-OAuth-authenticated user has a session, then opens `admin.zeromail.com` in the same browser without WebAuthn assertion, the `SESSION` cookie is sent to admin chain. The admin chain's authorization filter sees the session has no `ROLE_ADMIN` authority and returns 401 — OK, not a bypass. BUT: if the same Authentication object carries both authorities (which would happen if a future refactor merges authority sources), the admin chain accepts.
**Why it happens:** Cookie scope is per-domain; both `zeromail.com` and `admin.zeromail.com` resolve to the parent registrable domain. If the cookie is set with `Domain=.zeromail.com` it crosses both. If set with `Domain=admin.zeromail.com` it scopes admin only.
**How to avoid:** (a) Configure Spring Session cookie attribute: `Domain` MUST be explicit per chain. The admin chain sets the cookie with `Domain=admin.zeromail.com`; the user chain sets `Domain=zeromail.com` (no leading dot). (b) Use **different cookie names** per chain: `ZM-SESSION` for user, `ZM-ADMIN-SESSION` for admin (via `SpringSessionRememberMeServices` + `DefaultCookieSerializer` per-chain bean). (c) ArchUnit + integration test asserting an admin-chain Authentication never carries any authority other than `ROLE_ADMIN`.
**Warning signs:** A Google-OAuth-authenticated user can access an admin endpoint without WebAuthn ceremony. Cookie inspection in browser dev tools shows `SESSION` cookie scoped to `.zeromail.com`.

### Pitfall 5: NPM forwarding strips `Host` header; WebAuthn `Origin` check fails

**What goes wrong:** NPM (`jc21/nginx-proxy-manager`) proxies `https://admin.zeromail.com` → `http://backend-api:8080`. The downstream Spring MVC sees `Host: backend-api:8080` unless `X-Forwarded-Host` is honored. The WebAuthn `Origin` check (`https://admin.zeromail.com` vs what the server thinks the request origin is) fails because the server believes it's serving `http://backend-api:8080`.
**Why it happens:** Spring MVC needs `server.forward-headers-strategy=framework` to honor `X-Forwarded-Host` / `X-Forwarded-Proto` / `X-Forwarded-For`.
**How to avoid:** (a) Set `server.forward-headers-strategy: framework` in `backend/api/application.yml`. (b) Confirm NPM is forwarding `X-Forwarded-Host` (default in jc21/nginx-proxy-manager v2.x — verify in compose). (c) The `Webauthn4JRelyingPartyOperations` `allowedOrigins` MUST be exactly `https://admin.zeromail.com` — not `http://`, not with trailing slash.
**Warning signs:** WebAuthn ceremony fails with "origin mismatch" in browser console; server logs show `Origin: https://admin.zeromail.com` but RP thinks request came from elsewhere.

### Pitfall 6: WebAuthn `signCount` regression on cross-device passkeys

**What goes wrong:** A platform passkey (e.g., synced iCloud Keychain or 1Password) MAY return `signCount = 0` on every assertion because the passkey is syncable across devices and the underlying spec does not require counter increment for synced credentials. Naive replay defense ("reject assertion if reported signCount <= stored signCount") will lock out legitimate cross-device users on the SECOND login.
**Why it happens:** WebAuthn spec L3 (passkey era) made signCount optional and explicitly allows `0` for syncable credentials.
**How to avoid:** Implement the counter-regression check as ADMIN-10 requires, BUT treat `reportedSignCount == 0 AND storedSignCount == 0` as legitimate (synced credential). Only `reportedSignCount < storedSignCount AND storedSignCount > 0` indicates an actual clone attack. Reference: W3C WebAuthn Level 3 §6.1.1 step 21.
**Warning signs:** Operator with a syncable platform passkey gets `WEBAUTHN_REPLAY_SUSPECTED` audit row on second login.

### Pitfall 7: Master-key test-connection oracle leaks via provider error body

**What goes wrong:** `POST /api/admin/master-keys/openai/test-connection` calls OpenAI `GET /v1/models` with the candidate key. On 401, OpenAI returns a JSON body like `{"error": {"message": "Incorrect API key provided: sk-****abc1...", "type": "invalid_request_error"}}`. If the admin response or logs echo this body, the masked-key bytes (and possibly more) leak. MKEY-03 mandates enum-only response.
**Why it happens:** Standard `RestClient.exchange` returns the response body in the exception; logging proxies capture this.
**How to avoid:** (a) The `/models` HTTP client lives in `core.llm.gateway.springai.admin.ProviderModelsClient`, isolated from the logging proxy (no Spring `RestTemplate` interceptor that logs response bodies); (b) the test-connection service catches exceptions, maps to the enum (`OK | INVALID_KEY | RATE_LIMITED | NETWORK_ERROR | TIMEOUT`), and rethrows ONLY the enum value — never the original exception message; (c) `MasterKeySentinelLeakTest` CI gate scans the entire response and log fixture for `sk-`, `sk-ant-`, `AIza`, `sk-or-` patterns.
**Warning signs:** Sentinel-leak test fires; manual inspection of `application.log` shows OpenAI error bodies post-test-connection.

### Pitfall 8: ArchUnit false-negatives on lambda-captured types

**What goes wrong:** `AdminPathBodyBanTest` checks "classes under `..controllers.admin..` do not reference `GmailClient` body methods". If an admin service method receives a `Function<GmailMessage, String>` parameter and is invoked with `gmailClient::getBody`, the field reference is on the *caller* side, not on the admin package. ArchUnit's `dependOnClassesThat` may not see the captured reference.
**Why it happens:** Method references and lambdas capture types at call sites; bytecode-level reference may not match ArchUnit's `Java*Class` model.
**How to avoid:** (a) Test the rule with a positive fixture (`fail because admin code references GmailMessage.getBody`) AND a negative fixture (`pass on clean admin code`); (b) supplement ArchUnit with a CI grep gate for the same regex over `controllers/admin/**` and `core/admin/**` source files; (c) prefer FQN-based `noClasses().that().resideInAPackage(...).should().accessClassesThat()...` over method-reference rules.
**Warning signs:** Rule reports green but a manual inspection shows admin code references `body` field via lambda.

### Pitfall 9: Catalog Sync `processing_job` table doesn't exist yet

**What goes wrong:** CONTEXT.md `code_context` claims `processing_job` + `outbox` tables exist as v1.0 infrastructure. **Verified by grep against `backend/core/src/main/resources/db/changelog/changes/*.yaml` (2026-05-19): neither `processing_job` nor `outbox` tables exist as Liquibase changelogs.** The existing SKIP LOCKED pattern is per-domain (`credit_reservation`, `pubsub_delivery`, `triage_audit`). Phase 8 must EITHER (a) create the generic `processing_job` + `outbox` tables OR (b) use a per-domain `catalog_sync_job` table.
**Why it happens:** Pre-existing research artifacts referred to generic tables that were never actually created in v1.0/v1.1.
**How to avoid:** Plan-phase wave 0 for 8D must include a Liquibase changelog creating `processing_job` (and optionally `outbox`) with shape: `id BIGSERIAL`, `job_type VARCHAR(40)`, `payload JSONB`, `status VARCHAR(20)`, `lease_owner UUID`, `leased_at TIMESTAMPTZ`, `lease_expires_at TIMESTAMPTZ`, `attempts INT`, `last_error TEXT`, `created_at TIMESTAMPTZ`. Worker consumes via `SELECT ... FROM processing_job WHERE status='PENDING' AND (lease_expires_at IS NULL OR lease_expires_at < now()) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT N`. **Decision for plan-phase:** (a) is preferred — also unblocks Phase 9 SET-AI flows.
**Warning signs:** OPS-QUEUE-01 ("aggregates over `outbox` + `processing_job`") cannot be implemented because the tables don't exist.

### Pitfall 10: ChatModel cache eviction misses tenants with BYOK on rotation

**What goes wrong:** `MasterKeyRotatedEvent` evicts cached `ChatModel` instances for the rotated provider across all tenants. But a tenant with a BYOK key for that provider builds its `ChatModel` from the tenant's BYOK key, NOT the master key. Evicting the BYOK tenant's `ChatModel` is wasted work (the tenant rebuilds from BYOK, same result) AND a no-op rotation security risk if the cache eviction logic conditionally skips tenants known to have BYOK (then the platform-default tenants ALSO get skipped if the predicate is wrong).
**Why it happens:** Cache key includes `(tenantId, feature, provider, modelId)`; eviction must operate on `(provider)` granularity.
**How to avoid:** Eviction strategy = "evict all entries where cache key contains the rotated provider, regardless of tenant". A BYOK-using tenant rebuilds its `ChatModel` from BYOK on next request (fine). Don't optimize the eviction to skip BYOK tenants — the optimization complexity exceeds the cost.
**Warning signs:** After master-key rotation, some tenants still successfully call the OLD master key (cache wasn't fully evicted).

## Code Examples

(Patterns 1-5 above contain the verified code snippets. Two additional minimal examples below.)

### `@simplewebauthn/browser` registration ceremony (admin frontend)

```typescript
// Source: https://simplewebauthn.dev/docs/packages/browser
import { startRegistration } from '@simplewebauthn/browser';

async function enrollPasskey(token: string, email: string): Promise<void> {
  const optionsResponse = await fetch('/webauthn/register/options', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken(),
      'X-Enrollment-Token': token,
    },
    body: JSON.stringify({ email }),
  });
  const creationOptions = await optionsResponse.json();
  const attestation = await startRegistration({ optionsJSON: creationOptions });
  const verifyResponse = await fetch('/webauthn/register', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken(),
    },
    body: JSON.stringify({ publicKey: { credential: attestation, label: 'admin-key-1' } }),
  });
  if (!verifyResponse.ok) throw new Error('registration failed');
}
```

### `GroupedOpenApi` split

```java
// Source: https://springdoc.org/#how-can-i-define-multiple-openapi-definitions-in-one-spring-boot-project
@Bean
public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/**")
            .pathsToExclude("/api/admin/**")
            .build();
}

@Bean
public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
}
```

Endpoints become `/v3/api-docs/public` + `/v3/api-docs/admin`. Existing `GlobalOpenApiCustomizer` (in `OpenApiConfig.java`) is already group-aware (per its source comment).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| HTTP Basic / Password+TOTP for admin | WebAuthn passkey (hardware bound) | OWASP ASVS L3 v4.0 deprecated HTTP Basic for admin | Locked D-01 |
| Username/password DB column | WebAuthn public-key credential storage | Spring Security 6.4 shipped `.webAuthn(...)` DSL (May 2024) | `admin_users` schema does NOT have a password column |
| Stateless JWT for admin sessions | Spring Session Redis cookie | Spring Security 7.x + sticky session design (project locked v1.1) | Both chains share Spring Session Redis |
| Pgcrypto `pgp_sym_encrypt` for secrets at rest | App-layer AES-GCM with KEK rotation | CLAUDE.md "Hard do not use" lists pgcrypto for OAuth tokens — same rationale extends to master keys | Reuse `RefreshTokenCipher` |
| JSONB-everything catalog | 3-table normalized catalog | Stale-pin failure analysis surfaced in v1.2 research SUMMARY | Locked D-14 |
| Hand-managed nginx on VPS | NPM (`jc21/nginx-proxy-manager`) + Let's Encrypt per subdomain | v1.2 operational complexity — second subdomain `admin.zeromail.com` | Locked OPS-INFRA-02 |
| Inline TS interfaces in frontend | `openapi-typescript` codegen from `springdoc-openapi` GroupedOpenApi | Already shipped in `apps/web`; extended to `apps/admin` | Two specs, two codegens |

**Deprecated/outdated patterns to avoid:**
- **`@EnableGlobalMethodSecurity`** → use `@EnableMethodSecurity` (Spring Security 5.6+, mandatory in 7.x).
- **`AntPathRequestMatcher`** → use `PathPatternRequestMatcher.withDefaults().matcher(...)` (Spring Security 7 deprecation; existing `SecurityConfig` already uses the new style).
- **`HttpSecurity.csrf().disable()`** → use `csrf(csrf -> csrf.spa())` for SPA + cookie-session apps (Spring Security 7 idiom; existing v1.1 code already uses this).
- **`Webauthn4JRelyingPartyOperations` constructor with positional `rpName` string** → use `PublicKeyCredentialRpEntity(rpId, rpName)`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Vite 7.x is the right bundler version for `apps/admin` in May 2026. | Standard Stack | LOW — Vite 5 / 6 would also work; choice is bundler ergonomics |
| A2 | `react-router` 7.x SPA usage matches Workspace conventions. | Standard Stack | LOW — alternative `@tanstack/router` is also viable; React Router 7 is the conservative default |
| A3 | Spring Session Redis allows different cookie names per filter chain via `DefaultCookieSerializer` per-chain bean. | Pitfall #4 | MEDIUM — if Spring Session enforces a single global cookie name, planner needs an alternative (path-scoped cookies or chain-specific session repository). Plan-phase MUST verify via Context7 `/spring-projects/spring-session` before locking the design. |
| A4 | `JdbcPublicKeyCredentialUserEntityRepository` / `JdbcUserCredentialRepository` ship with a stable schema in Spring Security 7.0.5. | Pattern 2 | MEDIUM — the schema is published in `spring-security-web-7.0.5.jar` resources; planner must extract and adopt it. If Spring 7.1 changes the schema, we adopt the new version (likely backward-compatible additive). |
| A5 | Spring AI 2.0.0-M6 OpenAI adapter supports configurable `base_url` for both OpenAI-format and 9Router. | § M (9Router) | MEDIUM — verified at high level in CLAUDE.md "Spring AI 2.0.0-M6 via OpenAI ... starters" but specific Spring AI option key (`spring.ai.openai.base-url`?) needs Context7 verification in plan-phase. |
| A6 | OpenRouter `GET /api/v1/models` returns a JSON array with stable model-id field. | § H | LOW — well-documented endpoint; failure mode is the per-provider JSON Schema validation step. |
| A7 | 9Router exposes both `OPENAI_FORMAT` and `ANTHROPIC_FORMAT` at the same `base_url` (configured by `key_format` enum). | § M | MEDIUM — 9Router is a third-party LLM router; plan-phase must verify behavior against 9Router's docs (CITED from CLAUDE.md "9Router toggles OPENAI_FORMAT ↔ ANTHROPIC_FORMAT adapter at fixed base_url" but no upstream link given). |
| A8 | Jackson 3's `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` is still the correct way to enable deterministic field ordering. | Pattern 4 (HMAC chain) | LOW — Jackson 3 namespace changed (`tools.jackson.*`) but feature enum is preserved per migration docs |
| A9 | Spring Security 7's `Webauthn4JRelyingPartyOperations.setCustomizeCreationOptions` accepts a `Consumer<PublicKeyCredentialCreationOptionsBuilder>` AND the builder exposes `authenticatorSelection(authenticatorSelectionBuilder -> ...)`. | Pattern 2 | MEDIUM — verified via WebFetch Javadoc; planner must compile against the actual API in 7.0.5. |

**Total assumptions:** 9 — none of which block research; all gated on planner-phase verification with Context7/Spring Security 7 actual API + source.

## Open Questions

1. **`processing_job` + `outbox` tables don't exist yet.**
   - What we know: existing SKIP LOCKED patterns are per-domain (`credit_reservation`, `pubsub_delivery`, `triage_audit`). No generic queue table.
   - What's unclear: should 8D create generic `processing_job` (preferred — Phase 9 SET-AI also benefits) OR per-domain `catalog_sync_job`?
   - Recommendation: **Create generic `processing_job` in 8A** (alongside other foundation Liquibase changelogs) so 8D and OPS-QUEUE-01 share the same primitive. Pull this decision forward into 8A so 8E (queue dashboard) has data to aggregate.

2. **Should the existing `RefreshTokenCipher` be relocated to `core.shared.crypto`?**
   - What we know: master keys reuse the AES-GCM cipher; AAD parameter is tied to `tenantId`.
   - What's unclear: relocation cost vs facade cost.
   - Recommendation: **Add facade `MasterKeyCipher` in 8B**; defer relocation to v1.3+ rule-of-three.

3. **Spring Session cookie scoping per chain — is it possible without forking the session repository?**
   - What we know: `DefaultCookieSerializer` configures cookie name/path/domain; one bean per app context by default.
   - What's unclear: Spring 4 / Spring Session 4 may have changed multi-cookie support.
   - Recommendation: plan-phase task: verify via Context7 `/spring-projects/spring-session` before 8A foundation lands. If multi-cookie not supported, fall back to **path-scoped cookies** (`/api/admin/*` admin cookie path; `/` for user cookie) — this requires NPM proxy to NOT strip path on forwarding.

4. **WebAuthn challenge persistence across SPA navigation.**
   - What we know: Spring Security ships `PublicKeyCredentialCreationOptionsRepository` defaulting to `HttpSession`-backed persistence.
   - What's unclear: does `HttpSession` survive the SPA's WebAuthn ceremony? It should (cookie remains set across the `register/options` → `register` round-trip), but Vite dev server proxying may interfere.
   - Recommendation: confirm in 8A integration test that `HttpSession`-based persistence works end-to-end via NPM proxy. If not, switch to Redis-backed `PublicKeyCredentialCreationOptionsRepository` (custom implementation).

5. **9Router `OPENAI_FORMAT` vs `ANTHROPIC_FORMAT` toggle — adapter switch implementation.**
   - What we know: locked by D-19 + MKEY-05.
   - What's unclear: in Spring AI 2.0.0-M6, can we toggle between `OpenAiChatModel` and `AnthropicChatModel` at runtime sharing the same `base_url` config?
   - Recommendation: 8B mandatory Context7 query against `/spring-projects/spring-ai` to confirm both adapter starters accept configurable `base-url` from properties OR runtime config. Worst case: ship 9Router as `OPENAI_FORMAT` only in v1.2, defer `ANTHROPIC_FORMAT` to v1.3 (would change MKEY-05 acceptance).

## Environment Availability

Phase 8 dependencies are mostly already shipped. New external dependencies:

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker + docker-compose | OPS-INFRA-01, 02 | Assumed available on dev + VPS (v1.0 baseline) | latest | — |
| Postgres 17.6 | All Liquibase changelogs | Available in docker-compose.yml | 17.6 | — |
| Redis 7.2 | Spring Session, 60s debounce lease, edit-session token store | Available in docker-compose.yml | 7.2 | — |
| `jc21/nginx-proxy-manager` Docker image | OPS-INFRA-02 | Available on Docker Hub | latest 2.x | None — hand-managed nginx would defer admin subdomain entirely |
| `decolua/9router:latest` | OPS-INFRA-01 (9Router sidecar) | Assumed third-party; planner must verify image exists + is maintained | latest | None — feature MKEY-05 + per-feature 9Router routing blocks |
| `com.webauthn4j:webauthn4j-core` 0.29.1.RELEASE | ADMIN-01, 10 | Maven Central | 0.29.1.RELEASE | None — required for `.webAuthn(...)` DSL |
| `@simplewebauthn/browser` 13.3.0 | apps/admin frontend ceremony | npm | 13.3.0 | None — required for client-side ceremony |
| Node 22.x + pnpm 11.0.8 | apps/admin build | Available on dev (apps/web baseline) | 22.x / 11.0.8 | — |
| Vite 7.x | apps/admin build | npm | 7.x | Vite 6.x acceptable |
| `nginx-proxy-manager` admin UI | OPS-INFRA-02 runbook | Configured at NPM boot | — | Manual nginx config (out of scope) |

**Missing dependencies with no fallback:**
- `decolua/9router:latest` image legitimacy — planner MUST verify before 8A merges. If image is unmaintained or yanked, Phase 8 ships without 9Router sidecar (MKEY-05 + per-feature 9Router routing must be re-scoped).

**Missing dependencies with fallback:** None.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework (backend) | JUnit 5 + Spring Boot Test (existing in v1.1) |
| Framework (frontend) | Vitest 2.x (matches apps/web baseline) + Playwright (apps/admin/e2e) |
| Backend config file | `backend/api/build.gradle.kts` test task; `application-test.yaml` |
| Frontend config file | `apps/admin/vitest.config.ts` (NEW); `apps/admin/playwright.config.ts` (NEW) |
| Quick run (backend) | `./gradlew :backend:api:test -PtestSlice=admin` (new slice tag for Phase 8 tests) |
| Quick run (frontend) | `pnpm --filter @zeromail/admin test` |
| Full suite (backend) | `./gradlew test` |
| Full suite (frontend) | `pnpm --filter @zeromail/admin test && pnpm --filter @zeromail/admin e2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OPS-INFRA-01 | 9Router sidecar service definition in docker-compose | smoke | `docker compose -f docker-compose.yml config -q` | ❌ Wave 0 |
| OPS-INFRA-02 | NPM service definition; runbook exists | smoke + doc-check | `docker compose config && test -f docs/ops/v1.2-deploy.md` | ❌ Wave 0 |
| OPS-INFRA-03 | Runbook covers 4 sections | doc-check | `grep -E "migration\|9router\|rollback\|backup" docs/ops/v1.2-deploy.md` | ❌ Wave 0 |
| ADMIN-01 | WebAuthn login → ROLE_ADMIN authority | integration | `./gradlew :backend:api:test --tests "*AdminWebAuthnLoginIntegrationTest"` | ❌ Wave 0 |
| ADMIN-02 | Chain isolation; OAuth filters never run on admin path | integration | `./gradlew :backend:api:test --tests "*ChainIsolationIntegrationTest"` | ❌ Wave 0 |
| ADMIN-02 | Every admin controller has @PreAuthorize | ArchUnit | `./gradlew :backend:core:test --tests "AdminControllerPreAuthorizeTest"` | ❌ Wave 0 |
| ADMIN-02 | adminChain does not use oauth2Login | ArchUnit | `./gradlew :backend:core:test --tests "AdminChainNoOauth2LoginTest"` | ❌ Wave 0 |
| ADMIN-03 | Startup runner emits enrollment URL to STDOUT only | integration | `./gradlew :backend:api:test --tests "*BootstrapAdminRunnerTest"` (capture System.out) | ❌ Wave 0 |
| ADMIN-04 | Audit row written in same transaction; rollback cancels both | integration | `./gradlew :backend:core:test --tests "*AdminAuditTransactionalityTest"` | ❌ Wave 0 |
| ADMIN-05 | admin_read_event written; nightly cleanup | integration + scheduled-job | `./gradlew :backend:worker:test --tests "*AdminReadEventCleanupTest"` | ❌ Wave 0 |
| ADMIN-06 | apps/admin builds standalone; <500KB gzipped | build-check | `pnpm --filter @zeromail/admin build && find apps/admin/dist -name "*.js.gz" -exec wc -c {} +` | ❌ Wave 0 |
| ADMIN-06 | apps/web bundle contains zero admin schema references | bundle-analysis | `pnpm --filter @zeromail/web build && grep -r "admin-schema" apps/web/.next/static && exit 1 \|\| exit 0` | ❌ Wave 0 |
| ADMIN-07 | Audit viewer CSV export | e2e | `pnpm --filter @zeromail/admin e2e --grep "audit csv export"` | ❌ Wave 0 |
| ADMIN-08 | Confirm-twice + reason on destructive | e2e + integration | `pnpm --filter @zeromail/admin e2e --grep "destructive confirm"` | ❌ Wave 0 |
| ADMIN-09 | admin_users schema; DELETE forbidden via Postgres | DB integration | `./gradlew :backend:core:test --tests "*AdminUsersSchemaTest"` | ❌ Wave 0 |
| ADMIN-10 | WebAuthn registration ceremony succeeds; expired token 410; signCount replay rejected | integration (mocked authenticator) | `./gradlew :backend:api:test --tests "*WebAuthnCeremonyIntegrationTest"` | ❌ Wave 0 |
| ARCH-08 | AdminContext + TenantContext mutex | unit + ArchUnit | `./gradlew :backend:core:test --tests "AdminContextMutexTest"` | ❌ Wave 0 |
| ARCH-09 | AdminPathBodyBanTest | ArchUnit | `./gradlew :backend:core:test --tests "AdminPathBodyBanTest"` | ❌ Wave 0 |
| ARCH-10 | Single Gmail send call site; admin packages cannot send | grep + ArchUnit | existing `ChatSendCallSiteTest` extended | ⚠️ extend existing |
| ARCH-11 | MasterKeySentinelLeakTest | CI gate | `./gradlew :backend:core:test --tests "MasterKeySentinelLeakTest"` | ❌ Wave 0 |
| ARCH-12 | Postgres trigger prevents UPDATE/DELETE | DB integration | `./gradlew :backend:core:test --tests "*AdminAuditAppendOnlyTest"` (asserts UPDATE throws) | ❌ Wave 0 |
| MKEY-01..08 | Set/test/rotate, mask, sentinel-leak, rotation cache eviction, 9Router toggle | unit + integration | `./gradlew :backend:core:test --tests "*MasterKey*Test"` | ❌ Wave 0 |
| CAT-01..07 | 3-table catalog, Sync flow, FK enforcement, Anthropic seed, soft-delete | unit + integration | `./gradlew :backend:core:test --tests "*Catalog*Test"` | ❌ Wave 0 |
| OPS-TENANT-01..05 | List + 5-tab + pause/disconnect/delete + body-ban filter + Gmail token confinement | integration + ArchUnit | `./gradlew :backend:api:test --tests "*TenantInspection*Test"` | ❌ Wave 0 |
| OPS-QUEUE-01, 02 | Aggregates over processing_job + outbox; re-queue dead-letter | integration | `./gradlew :backend:api:test --tests "*QueueHealth*Test"` | ❌ Wave 0 |
| OPS-SPEND-01, 02 | Spend aggregates; ArchUnit prompt accessor ban | integration + ArchUnit | `./gradlew :backend:core:test --tests "*Spend*Test"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test --parallel`
- **Per wave merge:** `./gradlew test && pnpm -r build && pnpm -r test`
- **Phase gate:** Full suite green + `pnpm --filter @zeromail/admin e2e` Playwright suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminContextMutexTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminChainNoOauth2LoginTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminControllerPreAuthorizeTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeyResolverConfinementTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeySentinelLeakTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/admin/audit/AdminAuditAppendOnlyTest.java` (covers ARCH-12 Postgres trigger)
- [ ] `backend/api/src/test/java/com/zeromail/api/security/ChainIsolationIntegrationTest.java`
- [ ] `backend/api/src/test/java/com/zeromail/api/security/AdminWebAuthnLoginIntegrationTest.java` (uses mocked authenticator — see Open Question #4 + § O testing strategy)
- [ ] `backend/api/src/test/java/com/zeromail/api/security/WebAuthnCeremonyIntegrationTest.java`
- [ ] `backend/api/src/test/java/com/zeromail/api/security/AdminResponseBodyBanFilterTest.java`
- [ ] `backend/api/src/test/java/com/zeromail/api/security/BootstrapAdminRunnerTest.java` (capture System.out)
- [ ] `apps/admin/vitest.config.ts` + initial smoke spec
- [ ] `apps/admin/playwright.config.ts` + first ceremony e2e
- [ ] Test infrastructure: extend test-profile `SecurityConfig` (per todo `2026-04-28-wr-06-test-profile-securityconfig-slice.md`) to mirror both chains under `@Profile("test")` — this todo activates in Phase 8 per CONTEXT.md `<deferred>` (reviewed but reactivated by chain split)

### WebAuthn integration testing strategy

Mocking a hardware authenticator at the JVM level requires generating ES256/RS256 keypairs in test setup and producing valid CBOR attestation/assertion blobs. Two options:

(a) **`webauthn4j-test`** — official test-utility module from the webauthn4j project; generates attestation objects programmatically. Verify availability via `com.webauthn4j:webauthn4j-test` Maven Central.

(b) **Spring Security `SecurityMockMvcRequestPostProcessors.webAuthn(...)`** — if Spring Security 7 ships a MockMvc post-processor for WebAuthn (likely yes given the DSL ships). Verify in plan-phase via Context7 `/spring-projects/spring-security`.

**Research recommendation:** Use option (b) for chain-isolation + filter-binding tests (cheap, MockMvc-only); use option (a) for full ceremony round-trip tests (heavier, full WebApplicationContext). The planner allocates 1-2 tasks for test infrastructure in 8A wave 0.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | YES | Spring Security 7 WebAuthn passkey DSL (hardware-bound, `userVerificationRequirement=REQUIRED`); meets ASVS V2.1.10 (cryptographic authenticator), V2.7 (out-of-band one-time enrollment URL with 10-min TTL) |
| V3 Session Management | YES | Spring Session Redis cookie (HttpOnly, Secure, SameSite=Lax); per-chain cookie name + domain (Pitfall #4 mitigation) |
| V4 Access Control | YES | Chain-level `securityMatcher("/api/admin/**").hasRole("ADMIN")` + method-level `@PreAuthorize` + ArchUnit gate; `AdminContext`/`TenantContext` mutex |
| V5 Input Validation | YES | Model ID regex `^[a-zA-Z0-9._:/\-]{1,128}$`; per-provider JSON Schema validation on `/models` response; Bean Validation on all admin DTOs |
| V6 Cryptography | YES | AES-GCM-256 via existing `RefreshTokenCipher` (KEK rotation, 96-bit nonce); HMAC-SHA256 for audit chain |
| V7 Errors & Logging | YES | Test-connection enum-only response (no provider error body); `MasterKeySentinelLeakTest` scans logs + responses + audit JSON; `AdminResponseBodyBanFilter` failsafe |
| V8 Data Protection | YES | No email body / chat content / prompt / completion ever in admin response (ArchUnit `AdminPathBodyBanTest`); 30-day retention on admin_read_event; indefinite on admin_audit_event |
| V9 Communication | YES | HTTPS-only via NPM + Let's Encrypt; HSTS via NPM config; `allowedOrigins` strict equality |
| V10 Malicious Code | YES (defense in depth) | Postgres BEFORE UPDATE OR DELETE trigger; REVOKE UPDATE/DELETE from app DB user; HMAC chain detects tampering even if DBA bypasses trigger via `ALTER TABLE DISABLE TRIGGER` (nightly verification job catches drift) |
| V11 Business Logic | YES | Confirm-twice + reason on destructive actions; edit-session token + 10 req/hour rate limit on master-key edits; 60s Redis debounce lease on Sync Fetch |
| V12 Files & Resources | N/A | No file uploads in admin path |
| V13 API & Web Service | YES | Separate OpenAPI doc per audience (`GroupedOpenApi`); CSRF tokens on all WebAuthn endpoints; CORS not needed (same-domain via NPM) |
| V14 Configuration | YES | `zeromail.admin.bootstrap-emails` in application.yml; SOPS-managed HMAC chain secret + KEK; no secrets in DB |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Admin session bleed into tenant path | Spoofing + Elevation of Privilege | Two SecurityFilterChain beans with `securityMatcher`; AdminContext mutex; per-chain cookie name + domain |
| WebAuthn signCount replay (cloned authenticator) | Spoofing | ADMIN-10 counter regression check, with Pitfall #6 carve-out for syncable passkeys |
| Master-key oracle via test-connection | Information Disclosure | Enum-only response; provider error body stripped server-side; isolated HTTP client (no logging interceptor); sentinel-leak CI gate |
| Audit log tampering by admin or DBA | Repudiation | Postgres trigger BEFORE UPDATE OR DELETE; INSERT+SELECT-only grant; HMAC chain detects tampering even with disabled trigger |
| Cross-tenant data leak via admin tenant inspection | Information Disclosure | `AdminPathBodyBanTest` ArchUnit; `AdminResponseBodyBanFilter`; metadata-only projections; chat-session "Show details" disabled |
| Catalog supply-chain poisoning via Sync | Tampering | Per-provider JSON Schema validation before diff; model ID regex allow-list; 3-step Fetch → Diff → Confirm; soft-delete (no auto-migrate); Anthropic manual-only |
| Privilege escalation via OAuth user with planted ROLE_ADMIN | Elevation of Privilege | NO `users.role` column added (D-04); admin authority sourced solely from `admin_users` table on admin chain |
| Auto-send action triggered by admin (regression) | Tampering / Elevation | ArchUnit ARCH-10 extension: admin packages cannot reference `GmailClient` send methods; grep gate stays at 1 send call site |
| CSRF on WebAuthn endpoints | Tampering | Spring Security DSL requires X-CSRF-TOKEN on all WebAuthn endpoints (verified via Spring docs); SPA cookie-CSRF via `csrf(c -> c.spa())` |
| NPM proxy strips Host header → WebAuthn Origin mismatch | Spoofing (origin) | `server.forward-headers-strategy=framework`; explicit `allowedOrigins=https://admin.zeromail.com` |
| Bootstrap enrollment URL captured from log file | Information Disclosure | `System.out.println(...)` direct write (never SLF4J); operator captures from terminal, not application.log |

## Sub-Phase Boundary Recommendations

ROADMAP locks an 8A → 8F decomposition. After dependency analysis (verified against locked decisions + reading existing code), the sub-phase split below validates and refines the ROADMAP recommendation.

### Recommended sub-phases

| Sub-phase | Scope | Hard prerequisites | Wave-parallelizable with |
|-----------|-------|---------------------|---------------------------|
| **8A FOUNDATION** (hard gate) | dual SecurityFilterChain + `.webAuthn(...)` DSL + `admin_users` schema + `AdminUserDetailsService` + `BootstrapAdminRunner` + `EnrollmentTokenGate` + `AdminContext` ScopedValue mutex + `AdminTenantAccess.readOnly` + `admin_audit_event` + `admin_read_event` + Postgres trigger + HMAC chain + 6 ArchUnit rules + `GroupedOpenApi` split + `apps/admin` Vite scaffold + admin-schema codegen + `AdminResponseBodyBanFilter` + `processing_job` + `outbox` tables (see Pitfall #9) + NPM compose changes + 9Router compose changes + `docs/ops/v1.2-deploy.md` | None (greenfield wave) | — |
| **8B MASTER KEYS** | `llm_provider_master_key` table + `MasterKeyCipher` + `ProviderMasterKeyResolver` + 6-provider set/test/rotate REST + edit-session token + rate limit + `MasterKeyRotatedEvent` + ChatModel cache eviction + 9Router `OPENAI_FORMAT`/`ANTHROPIC_FORMAT` toggle + `MasterKeySentinelLeakTest` + admin frontend `/master-keys/[provider]` pages | 8A complete | 8C, 8E, 8F |
| **8C TENANT INSPECTION** | `TenantInspectionService` + 5-tab projections (Spring Data JDBC) + tenant list + tenant detail + pause/disconnect/delete + `AdminTenantOauthRevokeService` (no token bytes to admin code) + ArchUnit OPS-TENANT-05 + admin frontend `/tenants` + `/tenants/[tenantId]?tab=` | 8A complete (depends on AdminResponseBodyBanFilter + AdminTenantAccess + body-ban ArchUnit) | 8B, 8E, 8F |
| **8D CATALOG** | `provider_catalog` + `model_catalog` + `feature_binding` + Liquibase Anthropic seed + `CatalogSyncOrchestrator` + `CatalogSyncFetchWorker` (worker) + `CatalogSyncConfirmService` + `CuratedCatalogQueryService` + `GET /api/settings/catalog` + per-provider JSON Schema + `ProviderModelsClient` (uses 8B `ProviderMasterKeyResolver`) + `CatalogChangedEvent` + admin frontend `/catalog/[provider]` + Sync diff UI | 8A + 8B (catalog Sync needs `/models` calls with master key) | 8C, 8E, 8F (after 8B) |
| **8E QUEUE HEALTH** | `QueueHealthService` + `/admin/queue` aggregates + dead-letter re-queue + admin frontend `/queue` | 8A complete (depends on `processing_job` + `outbox` tables from 8A foundation) | 8B, 8C, 8D, 8F |
| **8F SPEND DASHBOARD** | `SpendAggregationService` + ArchUnit OPS-SPEND-02 + admin frontend `/spend` with shadcn chart components + 90-day date-range picker + k-anonymity bucketing | 8A complete (depends on AdminResponseBodyBanFilter + admin frontend chrome) | 8B, 8C, 8D, 8E |

### Why this differs from the ROADMAP recommendation

- **`processing_job` + `outbox` tables move from 8E into 8A**, because 8D Catalog Sync also depends on the generic queue (Pitfall #9). Putting them in 8A unblocks both 8D and 8E.
- **`AdminResponseBodyBanFilter` moves from 8C into 8A**, because both 8C (tenant inspection) and 8F (spend dashboard) need it. ARCH-09 acceptance test (`fires on test fixture; does not fire on production code`) sensibly lives in 8A.
- **`apps/admin` Vite scaffold + `/enroll` + `/login` pages move entirely into 8A**, because every other sub-phase needs admin frontend chrome (ADMIN MODE banner, query-param-based routing, codegen pipeline) to ship its pages.

### Recommended PLAN.md structure

**Research recommendation: SPLIT into 6 plan files (`08A.PLAN.md` through `08F.PLAN.md`) in the same phase directory.**

Rationale:
- 8A alone is ~12-15 tasks (foundation + 6 ArchUnit rules + Liquibase changelogs + dual-chain Security config + WebAuthn DSL + AdminContext + audit + apps/admin scaffold + NPM compose + runbook). A single PLAN.md covering all 42 reqs would exceed 50+ tasks → review fatigue.
- Wave parallelism after 8A is real (8B/8C/8E/8F can run on independent branches with no merge conflict in `controllers/admin/` because each domain owns its sub-package).
- 8D's dependency on 8B is a soft prerequisite (catalog Sync needs `ProviderMasterKeyResolver` from 8B); a separate PLAN file lets the executor sequence them clearly.
- Sub-plans match the existing GSD convention of one PLAN per coherent merge gate.

The planner SHOULD allocate a sub-phase 0 ("8A") that is wider than the rest by ~2x — call this out clearly in 08A.PLAN.md scope.

## Top 10 Pitfalls (Quick Reference for Planner)

Already enumerated in § Common Pitfalls; restated as a numbered list for planner quick scan:

1. **WebAuthn `userVerificationRequirement` defaults to `preferred`** — must use `setCustomizeCreationOptions`/`setCustomizeRequestOptions` to enforce `REQUIRED` (D-01 violation if missed).
2. **Hibernate `BOOTSTRAP_TENANT` silently filters admin JPA reads** — admin tenant inspection MUST use Spring Data JDBC or `AdminTenantAccess.readOnly`.
3. **`RefreshTokenCipher` AAD ties to tenantId; master keys are tenant-less** — use `MasterKeyCipher` facade with AAD `"master-key:" + provider.name()`.
4. **Spring Session shared cookie crosses subdomains** — configure per-chain cookie name + explicit `Domain` attribute; ArchUnit asserts admin Authentication carries only `ROLE_ADMIN`.
5. **NPM strips Host header; WebAuthn Origin check fails** — set `server.forward-headers-strategy=framework` + verify NPM forwards `X-Forwarded-Host`.
6. **WebAuthn signCount regression on syncable passkeys (iCloud, 1Password) returns `0`** — replay defense must allow `reportedSignCount=0 AND storedSignCount=0`.
7. **Master-key test-connection leaks provider error body** — enum-only response; `MasterKeySentinelLeakTest` CI gate.
8. **ArchUnit false-negative on lambda-captured types** — supplement with CI grep gate over admin source files.
9. **`processing_job` + `outbox` tables don't exist in v1.0/v1.1** — create them in 8A (NOT 8E as ROADMAP implies).
10. **Master-key rotation ChatModel cache eviction must NOT skip BYOK tenants** — evict by `(provider)` granularity regardless of per-tenant BYOK state.

## Project Constraints (from CLAUDE.md)

Hard directives the planner must verify against:

- **Java 25**, **Spring Boot 4.0.6**, **Spring Security 7.0.5**, **Spring AI 2.0.0-M6** — locked stack.
- **No Lombok** — entities are classes, DTOs are records, no `@Data`.
- **No `javax.*`** — Jakarta only (`jakarta.servlet`, `jakarta.persistence`).
- **No Spring WebFlux** — Spring MVC + virtual threads.
- **No raw HTTP LLM calls or vendor SDK outside `core.llm.gateway.springai`** — `ProviderModelsClient` MUST live in `core.llm.gateway.springai.admin`.
- **Jackson 3 namespace** — `tools.jackson.databind.ObjectMapper`, but annotations remain `com.fasterxml.jackson.annotation`.
- **No pgcrypto** — AES-GCM at app layer.
- **No Kafka / RabbitMQ in v1** — Postgres SKIP LOCKED only.
- **No stateless JWT** — cookie + Spring Session Redis.
- **No `pgp_sym_encrypt`** — `RefreshTokenCipher` AES-GCM.
- **Enterprise-readable Java names** — no `req`, `res`, `svc`, `repo`, `cfg`, `ctx`, `msg`, `err`, `ex`, `e`, `conn`, `tx`. Use `request`, `response`, `gmailConnectionService`, `tenantContext`, etc.
- **Privacy logging format**: `event=<name> tenantId={}` + structured fields; no email content, no token bytes, no prompts/completions in logs.
- **Carve-out:** rule-builder chat (user-typed config + tool outputs) is DB-persistable — but Phase 8 doesn't touch chat. The body-content ban applies to *email content extracted via Gmail tools*, not user-authored draft data.
- **Spring AI prompt/completion capture disabled** — OTLP traces do NOT carry LLM bodies.
- **shadcn/ui copy-paste** — copy primitives into `apps/admin/components/ui/**`; treat as source (excluded from ESLint/Prettier per the workspace convention).
- **Subproject-owned configuration files** — `backend/api/application.yml` for API; do not introduce cross-module config.
- **JetBrains MCP tools available** — planner should call them out for refactors / symbol search / file problems checks after Java edits.

## Sources

### Primary (HIGH confidence)

- Spring Security 7 Passkeys reference docs — https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html — verified via WebFetch 2026-05-19: full DSL config, auto-registered endpoints, CSRF posture, JS flow, JDBC repository class names.
- Spring Security 7 Webauthn4JRelyingPartyOperations Javadoc — https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/webauthn/management/Webauthn4JRelyingPartyOperations.html — verified via WebFetch 2026-05-19: `setCustomizeCreationOptions` / `setCustomizeRequestOptions` signatures.
- Spring Security 7 Multiple SecurityFilterChain pattern — https://docs.spring.io/spring-security/reference/servlet/configuration/java.html — verified via WebFetch 2026-05-19: `@Order(1)` + `securityMatcher` example.
- Maven Central — `org.springframework.security:spring-security-web:7.0.5` pom (verified directly via raw GET): confirms pom does NOT pull webauthn4j transitively.
- Maven Central — `com.webauthn4j:webauthn4j-core:0.29.1.RELEASE` (verified via search API).
- npm registry — `@simplewebauthn/browser:13.3.0`, ~2.1M downloads/week, maintainer `MasterKale` / SimpleWebAuthn project (verified via `npm view` + `api.npmjs.org`).
- Existing project source (verified by reading on 2026-05-19):
  - `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — current single chain shape
  - `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` — ScopedValue binding pattern
  - `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` — ScopedValue surface for mirroring
  - `backend/core/src/main/java/com/zeromail/core/tenant/ScopedValueTenantResolver.java` — `BOOTSTRAP_TENANT` pitfall source
  - `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — AAD signature
  - `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml` — JSONB body-ban trigger pattern reference
  - `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` — `GlobalOpenApiCustomizer` already group-aware
- CONTEXT.md, SPEC.md, ROADMAP.md (project source of truth).
- Research artifacts `.planning/research/SUMMARY.md` + `PITFALLS.md` (pre-pivot, partially superseded by WebAuthn pivot — used as background only).

### Secondary (MEDIUM confidence)

- WebSearch results for Spring Security WebAuthn `userVerificationRequirement` — corroborated the `setCustomizeCreationOptions` approach but no single authoritative example.
- W3C WebAuthn Level 3 specification — referenced for signCount regression carve-out on syncable passkeys.

### Tertiary (LOW confidence)

- `decolua/9router:latest` image existence — assumed from CLAUDE.md + ROADMAP references; planner MUST verify before 8A merges.
- Spring AI 2.0.0-M6 OpenAI adapter `base_url` configurability — assumed from CLAUDE.md "OpenRouter via OpenAI adapter pattern"; planner MUST verify via Context7 `/spring-projects/spring-ai` before 8B locks 9Router toggle design.
- 9Router behavior at `OPENAI_FORMAT` ↔ `ANTHROPIC_FORMAT` adapter switch — assumed from project locked decisions; no upstream 9Router docs verified.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every recommended package version verified against Maven Central / npm registry on 2026-05-19.
- Architecture: HIGH — dual SecurityFilterChain + ScopedValue mutex + Postgres trigger patterns are all confirmed against either existing project code or Spring Security 7 official docs.
- Pitfalls: HIGH — all 10 pitfalls are grounded in either (a) read source code, (b) Spring Security docs, or (c) W3C WebAuthn spec.
- Sub-phase boundaries: HIGH — dependency analysis traced through SPEC.md + locked decisions.
- Test architecture: MEDIUM — WebAuthn integration test strategy depends on availability of `webauthn4j-test` or `SecurityMockMvcRequestPostProcessors.webAuthn` (open question #4).

**Research date:** 2026-05-19
**Valid until:** 2026-06-19 (30 days — Spring Security 7.0.5 is stable; webauthn4j-core 0.29.x is stable; `@simplewebauthn/browser` 13.x stable line; revalidate if planner sees a version skew >2 minor releases at plan-phase start).

## Followup Verification (2026-05-19)

This section answers the 5 open questions surfaced by the main research pass (lines 1015-1041). Verifications use Spring official docs, Maven Central API, Docker Hub, and the upstream GitHub repos. Each answer is tagged with provenance per the GSD research convention.

### Q1: Spring Session per-chain cookie scoping

**Verdict: per-chain `CookieSerializer` is NOT supported as a first-class bean.** `CookieSerializer` is a single, application-wide bean in Spring Session 4.x — instantiating two of them in one context will cause `@EnableRedisHttpSession` autoconfig to fail with a duplicate-bean error. The Spring Session reference documentation does not document any chain-scoped `HttpSessionIdResolver` injection point. [CITED: https://docs.spring.io/spring-session/reference/http-session.html] [VERIFIED: WebFetch + WebSearch corroborated, no GitHub issue contradicting this]

**Recommended pattern for Phase 8 (path-aware single-process resolver):**

```java
@Bean
public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("ZEROMAIL_SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyFlag(true);
    serializer.setUseSecureCookie(true);
    serializer.setSameSite("Lax");
    // Subdomain isolation enforced by Set-Cookie Domain attribute.
    // Do NOT set DomainNamePattern that bridges admin.zeromail.com and zeromail.com.
    return serializer;
}
```

Because the cookie name + domain are global, the **isolation mechanism shifts to the subdomain boundary**, not to the cookie name. Two options for the Phase 8 plan:

1. **Two separate Spring Boot processes** — `backend/api-admin` and `backend/api-user` as distinct runnable modules, each with its own `application.yml`, its own `CookieSerializer` bean, and its own Redis namespace (`spring.session.redis.namespace=zeromail:admin:session` vs `zeromail:user:session`). NPM routes `admin.zeromail.com` to admin process, `zeromail.com` to user process. This is the **clean answer** but doubles the JVM footprint on the VPS.

2. **Single process + custom `HttpSessionIdResolver`** — wrap `CookieHttpSessionIdResolver` so that on `/api/admin/**` it reads/writes `ADMIN_SESSION`; on all other paths it reads/writes `SESSION`. Redis namespaces still cannot be split per chain inside a single `@EnableRedisHttpSession` (`redisNamespace` is per-`@Configuration` annotation), so the planner must accept that **admin sessions and user sessions share the same Redis namespace** but are distinguished by the path-aware resolver. Risk: a custom `HttpSessionIdResolver` is undocumented territory and Spring Session 4.x may regress this pattern. [ASSUMED]

```java
public final class PathAwareCookieSessionIdResolver implements HttpSessionIdResolver {
    private final CookieHttpSessionIdResolver userResolver;   // cookieName=SESSION
    private final CookieHttpSessionIdResolver adminResolver;  // cookieName=ADMIN_SESSION

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        return pickResolver(request).resolveSessionIds(request);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        pickResolver(request).setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        pickResolver(request).expireSession(request, response);
    }

    private CookieHttpSessionIdResolver pickResolver(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/") ? adminResolver : userResolver;
    }
}
```

**Planner guidance:** Write 8A foundation task `8A-T-SESSION-ISOLATION` that picks **Option 2** (single-process + `PathAwareCookieSessionIdResolver`) for v1.2 — the VPS footprint cost of Option 1 is not justified by current scale, and admin and user code already share the JVM. Add an explicit acceptance test `ChainCookieIsolationTest` that asserts: (a) hitting `/api/admin/**` with a `SESSION` cookie returns 401; (b) hitting `/api/me` with `ADMIN_SESSION` cookie returns 401; (c) the `Set-Cookie` Domain attribute is `admin.zeromail.com` on admin responses and `zeromail.com` on user responses. ArchUnit rule `AdminSessionCookieNameTest` enforces that any code in `controllers/admin/**` returning a session-establishing response uses `adminResolver` only.

Citation URLs:
- https://docs.spring.io/spring-session/reference/http-session.html — `CookieSerializer` is global
- https://docs.spring.io/spring-session/docs/current/api/org/springframework/session/web/http/CookieSerializer.html — `DefaultCookieSerializer` API surface
- https://docs.spring.io/spring-session/docs/current/api/org/springframework/session/web/http/HttpSessionIdResolver.html — interface contract used by `PathAwareCookieSessionIdResolver`

---

### Q2: Spring AI 2.0.0-M6 OpenAI/Anthropic adapter base_url runtime configurability

**Verdict: YES — both `OpenAiChatModel` and `AnthropicChatModel` in Spring AI 2.0.0-M6 accept `baseUrl` and `apiKey` at construction time via `*ChatOptions.builder()` / `*Api.builder()`.** Runtime construction per `ChatModel` instance is the documented pattern, and our existing v1.1 BYOK code already uses it. The 9Router toggle (`OPENAI_FORMAT` vs `ANTHROPIC_FORMAT`) at the same `base_url` is implementable by selecting the adapter type at runtime and pointing both to the same URL. [CITED: https://docs.spring.io/spring-ai/reference/2.0/api/chat/anthropic-chat.html] [VERIFIED: official Spring AI 2.0 docs explicit on this surface]

**Code pattern for the Phase 8 9Router factory** (lives inside `core.llm.gateway.springai.admin`, the only package allowed to talk Spring AI per CLAUDE.md "Hard do not use"):

```java
public final class NineRouterChatModelFactory {

    public ChatModel buildOpenAiFormat(NineRouterConfig nineRouterConfig, String resolvedApiKey, String modelId) {
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(modelId)
                .build();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(nineRouterConfig.baseUrl())     // e.g. http://9router:20128/v1
                .apiKey(resolvedApiKey)
                .build();
        return new OpenAiChatModel(openAiApi, openAiChatOptions);
    }

    public ChatModel buildAnthropicFormat(NineRouterConfig nineRouterConfig, String resolvedApiKey, String modelId) {
        AnthropicChatOptions anthropicChatOptions = AnthropicChatOptions.builder()
                .model(modelId)
                .maxTokens(1024)
                .apiKey(resolvedApiKey)
                .baseUrl(nineRouterConfig.baseUrl())     // same URL, different format on adapter side
                .build();
        return new AnthropicChatModel(anthropicChatOptions);
    }
}
```

**Caching strategy:** Build is per-request expensive (HTTP client init). Cache `ChatModel` instances keyed by `(provider, baseUrl, adapterFormat, kekVersion, apiKeyDigest)` inside a Caffeine cache held by `core.llm.gateway.springai` (this matches the existing v1.1 BYOK ChatModel cache). On `MasterKeyRotatedEvent`, evict by `(provider)` granularity per Pitfall #10 — no carve-out for BYOK. [VERIFIED: pattern is documented for OpenAiApi.builder; AnthropicChatOptions.builder().baseUrl/apiKey surface confirmed in Spring AI 2.0 reference]

**Planner guidance:** Write `8B-T-NINEROUTER-FACTORY` to create `NineRouterChatModelFactory` with two methods (`buildOpenAiFormat`, `buildAnthropicFormat`). Add acceptance test `NineRouterAdapterToggleTest` that constructs both adapters against a WireMock stub on the same base URL and asserts request body shape matches the format (OpenAI: `messages: [{role, content}]`; Anthropic: `messages: [{role, content}], max_tokens: N`). The 6-provider cache key already exists in v1.1 — extend the cache key tuple to include `adapterFormat` for the 9Router slot only.

Citation URLs:
- https://docs.spring.io/spring-ai/reference/2.0/api/chat/anthropic-chat.html — `AnthropicChatOptions.builder().baseUrl().apiKey()` documented in 2.0 series
- https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html — `OpenAiApi.builder().baseUrl().apiKey()` + `OpenAiChatModel(api, options)` constructor

---

### Q3: springdoc-openapi GroupedOpenApi split idiom

**Verdict: confirmed standard pattern.** `springdoc-openapi` v2.x exposes one `GroupedOpenApi` bean per group with `pathsToMatch()` + `pathsToExclude()` filters, served at `/v3/api-docs/{group}`. The pattern is unchanged between Spring Boot 3 and Spring Boot 4 — both use the same springdoc-openapi-starter-webmvc-ui artifact and bean API. [CITED: https://springdoc.org] [VERIFIED: Spring Boot 3.x and 4.x both publish to springdoc 2.x compatibility track]

**Bean snippet for `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java`** (extends the existing `OpenApiConfig`, which already has `GlobalOpenApiCustomizer` per main RESEARCH.md):

```java
@Bean
public GroupedOpenApi userApi() {
    return GroupedOpenApi.builder()
            .group("user")
            .pathsToMatch("/api/**")
            .pathsToExclude("/api/admin/**")
            .build();
}

@Bean
public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
}
```

Served at:
- `https://zeromail.com/v3/api-docs/user` — consumed by `apps/web`'s existing codegen pipeline
- `https://admin.zeromail.com/v3/api-docs/admin` — new consumer in `apps/admin` Vite SPA

**openapi-typescript 7.13.0 consumption:** the CLI accepts a URL or local file. Add to `apps/admin/package.json`:

```json
{
  "scripts": {
    "codegen:admin-schema": "openapi-typescript http://localhost:8080/v3/api-docs/admin --output src/lib/api/admin-schema.d.ts"
  }
}
```

[VERIFIED: openapi-typescript 7.x docs confirm URL input is supported]

**Existing v1.1 setup:** `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` already imports springdoc (uses `GlobalOpenApiCustomizer`). Phase 8 does NOT need to introduce springdoc as a new dependency — it only adds two `GroupedOpenApi` beans. [VERIFIED: cross-referenced with main RESEARCH.md Sources section line 1267]

**Planner guidance:** Write `8A-T-OPENAPI-SPLIT` to add the two `GroupedOpenApi` beans inside the existing `OpenApiConfig` class (NOT a new file — keep config consolidated). Add npm script `codegen:admin-schema` to `apps/admin/package.json` once the Vite scaffold lands. Acceptance test: `curl -s http://localhost:8080/v3/api-docs/admin | jq '.paths | keys | all(startswith("/api/admin/"))'` returns `true`.

Citation URLs:
- https://springdoc.org/ — `GroupedOpenApi.builder()` API + `/v3/api-docs/{group}` endpoint
- https://github.com/springdoc/springdoc-openapi — Spring Boot 4 compatibility track

---

### Q4: decolua/9router Docker image existence + maintenance

**Verdict: image EXISTS, is ACTIVELY maintained, but the API surface for `ANTHROPIC_FORMAT` is NOT publicly documented — verification needed before locking MKEY-05.** [VERIFIED: Docker Hub + GitHub] [PARTIAL — Anthropic-format endpoint not confirmed via upstream docs]

**Confirmed facts:**
- `docker.io/decolua/9router:latest` exists; latest digest `sha256:efb23bc42…` (161.4 MB), pushed 1 day before research date (2026-05-18).
- Source repo: https://github.com/decolua/9router; build tag = git tag (GitHub Actions pipeline).
- Latest release: **v0.4.55** (2026-05-18) — 58+ releases on the repo indicates active development.
- Listening port: **20128** (single port for dashboard + API).
- Documented endpoint: `POST http://localhost:20128/v1/chat/completions` (OpenAI-compatible format).
- Translation capabilities advertised: "OpenAI ↔ Claude ↔ Gemini ↔ Cursor ↔ Kiro ↔ Vertex ↔ Antigravity ↔ Ollama ↔ OpenAI Responses".

**Unconfirmed (the gap that matters for MKEY-05):** the upstream README documents only the OpenAI-format endpoint. Whether `POST http://localhost:20128/v1/messages` (Anthropic-format) is exposed on the same port — or whether the toggle between formats is via request header / config flag / separate port — is **not stated in upstream docs**. The advertised "OpenAI ↔ Claude" capability appears to be **internal routing translation** (the 9Router converts between formats internally before hitting the upstream LLM), not **dual ingress API surface**. [ASSUMED — this distinction matters and is not documentable from current sources alone]

**Fallback recommendation:**
1. **Pin a specific version** — do not use `:latest`. Pin to `decolua/9router:0.4.55` in `docker-compose.yml` so a future breaking change in 9Router does not surprise the VPS deploy. Bump deliberately as part of a v1.2.x maintenance task.
2. **Add a 8A pre-implementation spike** — a 30-minute exploration task `8A-T-9ROUTER-API-SHAPE-SPIKE` where the executor runs `docker run -p 20128:20128 decolua/9router:0.4.55` locally and curls both `/v1/chat/completions` AND `/v1/messages` to confirm the dual-format ingress hypothesis. If `/v1/messages` returns 404, MKEY-05 acceptance criteria must be amended to "9Router ships as `OPENAI_FORMAT` only in v1.2; `ANTHROPIC_FORMAT` toggle deferred to v1.3" — this would require a discuss-phase amendment because Decision D-19 is locked. Per project policy [feedback_skip_derisking_spikes.md], surface the spike as an option, do not insist; the planner can also take the risk and let MKEY-05 acceptance discover the truth at integration time.
3. **Image legitimacy** — `decolua/9router` is a single-maintainer image. Add `OPS-INFRA-RUNBOOK` content: "9Router upstream maintenance status MUST be checked quarterly; if the repo is archived/abandoned, fork to internal mirror." [VERIFIED: only one maintainer @decolua on the GitHub org]

**Planner guidance:** Write `8A-T-9ROUTER-COMPOSE` pinning `decolua/9router:0.4.55` in `docker-compose.yml`. Write `8A-T-9ROUTER-API-SHAPE-SPIKE` (30 min, optional but recommended) before `8B-T-NINEROUTER-FACTORY` lands. If the spike fails, the planner amends 8B scope and surfaces a discuss-phase ticket for D-19 / MKEY-05.

Citation URLs:
- https://hub.docker.com/r/decolua/9router — image existence + tag + size + maintainer
- https://github.com/decolua/9router — source repo + release cadence
- Upstream README does NOT document `/v1/messages` ingress — this is the unconfirmed gap

---

### Q5: webauthn4j-test availability + Spring Security 7 WebAuthn test utilities

**Verdict on the artifact: EXISTS and matches our pinned core version exactly.** Maven Central confirms `com.webauthn4j:webauthn4j-test:0.29.1.RELEASE` is published (92 versions in the artifact's history; latest version returned by Maven Central solrsearch API is exactly `0.29.1.RELEASE`, timestamp 2025-05-01). Compatibility with `webauthn4j-core:0.29.1.RELEASE` is guaranteed because both are released together from the same Gradle multi-module project. [VERIFIED: Maven Central solrsearch API `q=g:com.webauthn4j+AND+a:webauthn4j-test`]

```
testImplementation("com.webauthn4j:webauthn4j-test:0.29.1.RELEASE")
```

**Verdict on Spring Security MockMvc support: `SecurityMockMvcRequestPostProcessors.webAuthn()` does NOT exist in Spring Security 7.0.5.** The reference docs list MockMvc post-processors for users, CSRF, form-login, http-basic, OAuth2, and logout — WebAuthn/Passkeys is absent. [VERIFIED: https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/index.html]

**Recommended integration test pattern for Phase 8** (replaces the speculative option (b) in main RESEARCH.md § WebAuthn integration testing strategy):

Use `webauthn4j-test`'s authenticator emulator end-to-end against the real Spring Security 7 WebAuthn endpoints via `MockMvc` or `WebApplicationContext`. Pattern:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AdminWebAuthnCeremonyIntegrationTest {

    private final EmulatorAuthenticator emulatorAuthenticator =
            new EmulatorAuthenticator(EmulatorAuthenticatorOption.builder()
                    .credentialId(new byte[32])
                    .userVerification(true)            // matches REQUIRED policy
                    .build());

    @Test
    void registration_then_login_round_trip() {
        // 1. POST /webauthn/register/options with enrollment token -> get challenge JSON.
        // 2. Use emulatorAuthenticator.makeCredential(challenge, rpId, origin) to forge attestation.
        // 3. POST /webauthn/register with attestation JSON -> assert 201 + admin_credential row.
        // 4. POST /webauthn/authenticate/options -> get assertion challenge.
        // 5. Use emulatorAuthenticator.getAssertion(challenge, rpId, origin) -> sign.
        // 6. POST /webauthn/authenticate with assertion -> assert 200 + ADMIN_SESSION cookie set.
    }
}
```

**Known gotchas (planner must encode as test invariants):**
1. **signCount in tests:** `EmulatorAuthenticator` increments its internal counter on each `getAssertion()` call by default. Tests asserting "signCount replay rejected" must construct two assertions with the SAME counter value (use the option override) and expect the second to return 403. Tests asserting "syncable passkey with counter=0 is accepted" must set counter=0 on both attestation and assertion (Pitfall #6 carve-out).
2. **Attestation format:** `webauthn4j-test` defaults to `packed` attestation. Spring Security 7 default policy may require `none` or `direct` based on the configured `AttestationConveyancePreference` — set explicitly in test setup.
3. **Origin / rpId mismatch:** `EmulatorAuthenticator.makeCredential(...)` needs the exact `origin` value Spring Security expects on the server side. In Phase 8 admin chain, `allowedOrigins=https://admin.zeromail.com` — tests must pass exactly that string; do NOT inject `http://localhost:8080` from `WebEnvironment.RANDOM_PORT`. Workaround: configure `allowedOrigins` to include test-only host `https://test.admin.local` and have the test pass that origin.
4. **Challenge persistence:** Open Question #4 in main research already flags `HttpSession`-backed `PublicKeyCredentialCreationOptionsRepository`. Integration tests under `MockMvc` automatically carry session via `mockMvc.session(...)`; tests under `TestRestTemplate` need explicit cookie jar. Default to MockMvc for ceremony tests.
5. **HMAC chain side-effect:** registration also writes to `admin_audit_event` with HMAC link. Tests must seed the genesis HMAC row in test fixture, otherwise the first registration row fails the chain invariant.

**Planner guidance:** Write `8A-T-WEBAUTHN-TEST-INFRA` adding `webauthn4j-test:0.29.1.RELEASE` to `backend/api/build.gradle.kts` testImplementation, plus a shared test fixture `AdminWebAuthnTestSupport` exposing a configured `EmulatorAuthenticator` + `withTestOrigin()` helper. Then `8A-T-CEREMONY-IT` writes `WebAuthnCeremonyIntegrationTest` covering registration + login + signCount replay rejection. Do NOT write a `SecurityMockMvcRequestPostProcessors.webAuthn()`-style chain-isolation test (Spring Security 7 does not ship it); instead, write chain isolation tests by setting an authenticated `Principal` via `SecurityMockMvcRequestPostProcessors.user(...).authorities("ROLE_ADMIN")` against the admin chain and asserting controllers respond — this proves the chain wiring without exercising the WebAuthn ceremony at all.

Citation URLs:
- Maven Central solrsearch API `q=g:com.webauthn4j+AND+a:webauthn4j-test` — confirmed `0.29.1.RELEASE` is latest (verified 2026-05-19)
- https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/index.html — `webAuthn()` post-processor is NOT listed
- https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html — production WebAuthn DSL (no test counterpart)
- https://github.com/webauthn4j/webauthn4j — `EmulatorAuthenticator` source under `webauthn4j-test` module

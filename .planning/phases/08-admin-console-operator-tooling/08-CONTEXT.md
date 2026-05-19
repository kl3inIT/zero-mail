# Phase 8: Admin Console & Operator Tooling — Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 8 delivers an operator-facing admin console for Zero Mail v1.2 covering: WebAuthn-passkey-based admin authentication on a dedicated `admin.zeromail.com` subdomain (decoupled from user-facing Google OAuth), append-only HMAC-chained audit infrastructure, master-key management for 6 LLM providers (AES-GCM via existing `RefreshTokenCipher`), curated catalog management with 3-step Sync-from-`/models` (3-table normalized schema), read-only tenant inspection (metadata only, no body/chat/prompt content), worker queue health dashboard, platform LLM spend dashboard, and VPS deployment artifacts (9Router sidecar + NPM proxy config + runbook). Phase ships 42 falsifiable requirements; user-facing app (`zeromail.com`) carries zero RBAC concept and zero admin schema types in its bundle.

**This phase merges original v1.2 Phase 8 (foundation) + Phase 9 (operator surface) per user directive 2026-05-19 during spec-phase. Phase 8 admin auth subsequently pivoted from Google OAuth to WebAuthn passkey during discuss-phase research 2026-05-19.**

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**42 requirements are locked.** See `08-SPEC.md` for full requirements with Current/Target/Acceptance per entry.

Downstream agents (`gsd-phase-researcher`, `gsd-planner`, `gsd-executor`) MUST read `08-SPEC.md` before planning or implementing. Requirements, boundaries, and acceptance criteria are NOT duplicated here.

**In scope (from SPEC.md):**

- VPS deployment artifacts: 9Router sidecar + NPM proxy services in `docker-compose.yml`, NPM subdomain routing for `admin.zeromail.com` + separate Let's Encrypt cert, `docs/ops/v1.2-deploy.md` runbook
- Backend: `admin_users` table (WebAuthn credentials), `@Order(1) adminChain` SecurityFilterChain with Spring Security 7 `.webAuthn(...)` DSL, `AdminUserDetailsService`, `EnrollmentTokenGate` filter, `AdminContext` ScopedValue, `AdminTenantAccess.readOnly`, `admin_audit_event` + `admin_read_event` tables with append-only trigger + HMAC chain, Liquibase seed + startup-runner enrollment ceremony, `POST /api/admin/grant-admin` endpoint returning one-time enrollment URL
- Backend: `llm_provider_master_key` table + `ProviderMasterKeyResolver` (in `core.llm.gateway.springai`) + 6-provider set/test/rotate REST endpoints with edit-session token + rate limit
- Backend: `provider_catalog` + `model_catalog` + `feature_binding` tables + 3-step Sync flow + `CuratedCatalogQueryService` + `GET /api/settings/catalog`
- Backend: tenant list + 5-tab detail read-only endpoints + pause/disconnect/delete write endpoints + `AdminResponseBodyBanFilter`
- Backend: `/admin/queue` + `/admin/spend` read-only aggregate endpoints
- Backend ArchUnit + CI gates: `AdminContextMutexTest`, `AdminPathBodyBanTest`, admin send-method ban (extends ARCH-10), `ProviderMasterKeyResolver` confinement, `every_admin_controller_must_have_preauthorize`, `admin_chain_does_not_use_oauth2login`, admin spend prompt-accessor ban, `MasterKeySentinelLeakTest`
- Frontend: NEW `apps/admin` Vite + React 19 SPA on `admin.zeromail.com` with ADMIN MODE banner chrome
- Frontend: `admin-schema.d.ts` + `admin-client.ts` typed client (codegenned from `springdoc-openapi` GroupedOpenApi admin spec) — lives only in `apps/admin/src/lib/api/`
- Frontend: `@simplewebauthn/browser` for WebAuthn ceremony client-side
- Frontend admin pages: `/enroll`, `/login`, Audit Log viewer (filter + CSV export), Role Grants (with one-time enrollment URL response), Master Keys per-provider, Catalog browser + Sync flow per provider, Tenant List + 5-tab Detail, Queue Health, Spend Dashboard
- Liquibase: 7 new YAML changelogs (`admin_users`, `admin_audit_event`, `admin_read_event`, `llm_provider_master_key`, `provider_catalog` + `model_catalog` + `feature_binding`, Anthropic catalog seed) — NO `users.role` column changelog
- Spring Modulith: NEW `core.admin` top-level module (sibling of `core.chat`, `core.llm`) with vertical sub-packages `auth/audit/mkey/cat/tenant/queue/spend`
- HTML prototype: `08-PROTOTYPE.html` covering all 7 admin screen groups (per UI phase prototype rule)

**Out of scope (from SPEC.md):**

- **Live VPS migration** from hand-managed nginx → NPM + 9Router sidecar boot on production VPS — Phase 8 ships compose changes + runbook; live cutover is deploy step tracked separately, not blocking phase merge
- **Google OAuth for admin login** — locked NO; admin chain uses WebAuthn passkey exclusively
- **HTTP Basic Auth, password+TOTP, or any password-based admin auth** — locked NO; OWASP ASVS deprecates HTTP Basic for admin
- **`users.role` column or any RBAC concept on user-facing side** — admin authority lives entirely in `admin_users` table + admin SecurityFilterChain; user codepath retains only `authenticated`
- **Self-service "I lost my passkey" recovery UI** — operator with shell access manually inserts fresh PENDING_ENROLLMENT row; documented in OPS-INFRA-03
- **Admin impersonation of a user (act-as-tenant)** — locked NO at architectural level (ARCH-08); tenant authority cannot be borrowed by admin
- **Auto-send / auto-forward triggered by admin action** — locked NO (v1.0 ARCH ban)
- **Free-form model-ID override in admin catalog** — only Sync diff confirm or manual entry through validated form
- **User-triggered Sync** — only admin can trigger Sync; `GET /api/settings/catalog` is read-only for users
- **Per-tenant master keys** — platform-wide only; BYOK keys (v1.0 `byok_credential`) unaffected by master-key rotation
- **HashiCorp Vault / GCP KMS / AWS KMS** — single VPS reuses `RefreshTokenCipher` AES-GCM
- **Embedding catalog curation** — forbidden by v1.0 privacy constraint
- **Audit log full-text search across tenants** — filter by actor/action/target/date range only
- **Per-rule model override UI** — per-feature `chat/triage/draft` only
- **Cron-based master-key rotation** — manual rotation only (90-day reminder tag is informational)
- **`AdminController` meta-annotation** — explicit `@PreAuthorize` per class until rule-of-three triggers (Phase 9+)
- **Shared SecurityFilterChain for admin + user paths** — chain split locked; chains never overlap
- **`(admin)` Next.js route group inside `apps/web`** — replaced by separate `apps/admin` Vite + React app
- **Admin SQL console** — direct DB query access banned
- **"Reveal master key once" workflow** — masked-only forever post-save
- **Worker stop/start admin UI** — read-only queue inspection + dead-letter re-queue only
- **Reveal/edit job `payload_json`** — never exposed via admin API
- **Per-prompt drill-down on spend dashboard** — aggregates only
- **v1.3+ deferred items** — Grafana dashboards, CASA evidence, formal GA tag, purple brand visual refresh of user pages, fine-grained admin permissions, admin chat-content inspection via tenant-bound support ticket grant

</spec_lock>

<decisions>
## Implementation Decisions

### Architectural Shape (POST-PIVOT 2026-05-19)
- **D-01:** Admin auth uses **WebAuthn passkey** via Spring Security 7 `.webAuthn(...)` DSL with `userVerificationRequirement=REQUIRED`. Not Google OAuth (decouples admin compromise surface from Google IdP), not HTTP Basic (OWASP ASVS deprecated), not password.
- **D-02:** Admin frontend is a **NEW `apps/admin` Vite + React 19 SPA** at `admin.zeromail.com` — no SSR, no SEO, no Next.js. DNS subdomain provides cognitive cue; public `apps/web` bundle stays free of admin schema types and admin route code.
- **D-03:** Backend = **1 JVM** (`backend/api`) with **2 `SecurityFilterChain` beans** via `securityMatcher`: `@Order(1) adminChain` matches `/api/admin/**` + `.webAuthn(...)`; `@Order(2) userChain` (no matcher) keeps `.oauth2Login(...)` current. Chains never share auth method. ArchUnit enforces non-overlap.
- **D-04:** Admin identity store = **NEW `admin_users` table** (separate from `users`). `users` table gains NO `role` column. No `GrantedAuthoritiesMapper` ROLE_ADMIN merge. Admin authority sourced via `AdminUserDetailsService` on admin chain only.
- **D-05:** First-admin bootstrap = **Liquibase seed of `admin_users` row(s)** from `zeromail.admin.bootstrap-emails` config + Spring Boot startup runner prints **10-min one-time enrollment URL to STDOUT** (never log file or DB). Admin uses URL to complete WebAuthn registration ceremony. Subsequent grants via `POST /api/admin/grant-admin {email}` return fresh one-time URL communicated out-of-band.

### Spring Modulith Module Structure
- **D-06:** Single `core.admin` top-level Modulith module with **vertical sub-packages**: `auth/`, `audit/`, `mkey/`, `cat/`, `tenant/`, `queue/`, `spend/`. Each sub-package follows `domain/usecases/projection/persistence/exception/` convention. Matches existing `core.chat` / `core.llm` / `core.gmail` shape. Cross-vertical events flow via Spring Modulith `@ApplicationModuleListener` in-JVM.
- **D-07:** **No `@NamedInterface` annotations** on sub-packages in Phase 8 — internal sub-packages free to reference each other; ArchUnit handles boundary cases (admin.opsspend cannot import admin.mkey.persistence directly). Todo `2026-05-12-make-backend-core-context-api-surfaces-explicit-with-namedin.md` stays open for Phase 11+ rule-of-three.
- **D-08:** `ProviderMasterKeyResolver` and `/models` HTTP client live in `core.llm.gateway.springai.admin` (preserve "one adapter package" boundary). Master-key storage + admin CRUD live in `core.admin.mkey`. Resolver reads via admin module's public API on the LLM hot path. `MasterKeyRotatedEvent` and `CatalogChangedEvent` are Spring Modulith events.

### Method-Security & RBAC
- **D-09:** **Explicit `@PreAuthorize("hasRole('ADMIN')")` per `@RestController`** in `controllers/admin/`. No `@AdminController` meta-annotation until rule-of-three triggers (Phase 9+). ArchUnit rule `every_admin_controller_must_have_preauthorize` + `admin_chain_does_not_use_oauth2login` enforced in CI.
- **D-10:** `AdminContext` ScopedValue + `TenantContext` ScopedValue are **mutually exclusive** — entering one binding makes `currentOrThrow()` of the other throw. Codepath-level belt-and-suspenders defense on top of `SecurityFilterChain` isolation. `AdminTenantAccess.readOnly(tenantId, supplier)` is the only legitimate path for cross-tenant admin reads + writes `admin_read_event` row before invoking supplier inside `TenantContext.run`.

### Tenant Detail Routing
- **D-11:** Tenant detail 5-tab page (`/tenants/:tenantId` in `apps/admin`) uses **single TanStack Router file route (`tenants.$tenantId.tsx`) + shadcn `<Tabs>` + `?tab=` query param validated via `validateSearch: zodSchema` (type-inferred enum)**. URL: `/tenants/abc?tab=health`. TanStack Query lazy per-tab (overview loads immediately; health/billing/spend/activity load on tab click). `admin_read_event` writes 1 row per tab visit (5 rows max per session) — useful audit granularity. URL shareable for co-admin reproducible-view. NOT React Router. See D-24 for full apps/admin stack.

### Audit & Append-Only Invariants
- **D-12:** `admin_audit_event` **indefinite retention**; `admin_read_event` **30-day retention** enforced by nightly cleanup job.
- **D-13:** `admin_audit_event` table grant: app DB user has **INSERT + SELECT only** (no UPDATE, no DELETE); Postgres `BEFORE UPDATE OR DELETE` trigger raises EXCEPTION regardless of role; HMAC-SHA256 `hmac_chain_hash` per row chains to previous row; nightly verification job re-derives chain and emits Prometheus alert metric on mismatch.

### Catalog & Master Keys
- **D-14:** Catalog = **3-table normalized** (`provider_catalog`, `model_catalog`, `feature_binding`) — NOT JSONB. FK + UNIQUE partial indexes prevent stale-pin failures from `assistant_settings.{chat|triage|draft}_model_id`.
- **D-15:** Sync-from-`/models` = **3-step Fetch → Diff → Confirm** via `processing_job` SKIP LOCKED with 60s Redis debounce lease. Auto-apply forbidden. Model IDs validated against `^[a-zA-Z0-9._:/\-]{1,128}$` + per-provider JSON Schema.
- **D-16:** Anthropic catalog: **Liquibase data seed** for initial Claude family (Claude 4.7 Opus, Claude 4.6 Sonnet, Claude 4.5 Haiku); Sync button disabled with manual-entry tooltip; new Claude models added via admin manual entry form.
- **D-17:** Master keys reuse existing **AES-GCM `RefreshTokenCipher`** (same KEK rotation infrastructure as OAuth refresh tokens). NOT pgcrypto (project do-not-use), NOT HashiCorp Vault / GCP KMS / AWS KMS (single VPS).
- **D-18:** Master-key rotation flow: enter new key → test against `GET /v1/models` (NEVER send method) → on OK, write new encrypted row + emit `MasterKeyRotatedEvent` → `@ApplicationModuleListener` evicts every cached `ChatModel` for that provider across all tenants → on test failure, old key preserved + audit `MASTER_KEY_ROTATION_FAILED`.
- **D-19:** 9Router master-key entry has `key_format` toggle (`OPENAI_FORMAT` | `ANTHROPIC_FORMAT`); `ProviderMasterKeyResolver` selects Spring AI adapter accordingly. Other 5 providers (OpenAI, Anthropic, Google, DeepSeek, OpenRouter) have fixed adapter type.

### Tenant Inspection
- **D-20:** Tenant chat-session inspection limited to **metadata only** (count, last activity, model selection). "Show details" disabled with tooltip referring to a future v1.3+ tenant-bound support ticket grant flow.
- **D-21:** `AdminResponseBodyBanFilter` failsafe runs on `/api/admin/**` responses: scans JSON for string fields whose key matches regex `body|bodyHtml|snippet|payload|prompt|completion|content` AND value length >200 → returns HTTP 500 + writes `admin_audit_event` row with action `ADMIN_RESPONSE_BODY_BAN_TRIPPED`.

### OPS-INFRA Gating
- **D-22:** Phase 8 merge gate ships docker-compose changes + NPM subdomain config for `admin.zeromail.com` + `docs/ops/v1.2-deploy.md` runbook. **Live VPS migration** from hand-managed nginx → NPM + 9Router sidecar boot + admin subdomain DNS = separate deploy step, tracked but not blocking phase merge.
- **D-23:** Optional **IP allowlist for `admin.zeromail.com`** at NPM proxy layer documented in OPS-INFRA-03 runbook — not mandatory v1.2 (solo operator accessibility trade-off) but trivial to enable later.

### Stack Versions Lock (added 2026-05-20 — verified live on npm registry + Maven Central direct HTTP probes)

- **D-24 (apps/admin frontend stack):** New Vite + React SPA pins these versions exactly (verified via `npm view <pkg> version` 2026-05-20):

  | Package | Version | Role |
  |---|---|---|
  | `vite` | **8.0.13** | Build tool (Rolldown bundler default in v8) |
  | `@vitejs/plugin-react-swc` | 4.3.1 | SWC React plugin (faster HMR than Babel) |
  | `react` + `react-dom` | 19.2.6 | UI runtime |
  | `typescript` | 6.0.3 | Compiler |
  | `@tanstack/react-router` | **1.170.4** | File-based routing — REPLACES initial "React Router" intent |
  | `@tanstack/router-plugin` | 1.168.6 | Vite plugin (MUST come BEFORE `react()` in `vite.config.ts` — Context7-verified silent-fail otherwise) |
  | `@tanstack/router-devtools` | 1.167.0 | Dev only |
  | `@tanstack/react-query` | 5.100.11 | Server state |
  | `@tanstack/react-query-devtools` | 5.100.11 | Dev only |
  | `@tanstack/react-form` | **1.32.0** | Form state — REPLACES initial "react-hook-form" intent |
  | `zod` | **4.4.3** | Validation — Zod 4 implements Standard Schema 1.0 spec; passed directly to `validators: { onChange: schema }` |
  | ~~`@tanstack/zod-form-adapter`~~ | ~~DEPRECATED~~ | DO NOT install — Zod 4 + TanStack Form v1 integrate via Standard Schema; the adapter package (last 0.42.1 early-2024) is end-of-life |
  | `tailwindcss` | 4.3.0 | Styling — CSS-only `@theme` block, NO `tailwind.config.ts`, NO `postcss.config.js` |
  | `@tailwindcss/vite` | 4.3.0 | Tailwind Vite plugin |
  | `openapi-fetch` | 0.17.0 | Typed HTTP client |
  | `openapi-typescript` | 7.13.0 | OpenAPI → TS codegen |
  | `@simplewebauthn/browser` | 13.3.0 | WebAuthn client ceremony |
  | `vitest` + `@vitest/ui` | 4.1.6 | Unit tests |
  | `@playwright/test` | 1.60.0 | E2E tests |

  **File-based routing convention** (D-11 implementation, supersedes earlier React-Router-style assumption):
  ```
  apps/admin/src/routes/
  ├── __root.tsx                    # AdminLayout + AdminModeBanner + <Outlet />
  ├── enroll.tsx                    # /enroll
  ├── login.tsx                     # /login
  ├── _authenticated.tsx            # layout route — WebAuthn session gate
  └── _authenticated/
      ├── index.tsx                 # /
      ├── audit.tsx                 # /audit
      ├── role-grants.tsx           # /role-grants
      ├── master-keys.tsx           # /master-keys              (8B)
      ├── catalog.tsx               # /catalog                  (8D)
      ├── tenants.tsx               # /tenants                  (8C)
      ├── tenants.$tenantId.tsx     # /tenants/:id?tab=…        (8C, D-11)
      ├── queue.tsx                 # /queue                    (8E)
      └── spend.tsx                 # /spend                    (8F)
  ```

  D-11's `?tab=` query param wired via TanStack Router `validateSearch: zodSchema` + `Route.useSearch()` (fully type-inferred from the Zod enum schema). NO React Router; NO manual route definitions.

- **D-25 (backend stack — Phase 8 additions):** Verified via direct Maven Central HTTP probes 2026-05-20 (Solr search index has lag; `repo1.maven.org/maven2/...pom` is authoritative). Phase 8 adds **ZERO new runtime dependencies**:
  - **Spring Boot 4.0.6** — locked (4.0.7 not released yet)
  - **Spring Framework 7.0.7** — locked (7.0.8 not released yet)
  - **Spring Security 7.0.5** — locked (7.0.6 not released yet); `.webAuthn(...)` DSL native; `webauthn4j-core 0.30.0.RELEASE` pulled transitively (do NOT add explicit declaration)
  - **Spring Modulith 2.0.2** — Boot 4.x compatible (BOM-managed)
  - **Spring AI 2.0.0-M6** — locked (M7 not released yet)
  - **Hibernate 7.0.x** — Boot 4.0.6 BOM pin (7.1.0.Final exists but accept BOM)
  - **Liquibase 5.0.3** — **optional patch bump** from project-locked 5.0.2 (safe semver bugfix)
  - **PostgreSQL JDBC 42.7.x** — Boot BOM-managed
  - **Jackson 3.1.x** — Boot BOM-managed
  - **springdoc-openapi 3.0.3** — locked (3.0.4 not released yet)
  - **ArchUnit 1.4.2** — optional patch bump

  Every Phase 8 capability sources from already-locked deps:
  - WebAuthn server → `org.springframework.security.web.webauthn.*` (built into spring-security-web)
  - HMAC chain audit → `javax.crypto.Mac.getInstance("HmacSHA256")` (JDK 25 stdlib)
  - Catalog `/v1/models` HTTP probe → `org.springframework.web.client.RestClient` (Spring Framework 7 stdlib)
  - Nightly verification job → `@Scheduled` (spring-context)
  - AES-GCM master keys → existing `RefreshTokenCipher` reuse

  Plan-phase research SUMMARY claim of "zero new backend runtime deps" verified accurate.

### Claude's Discretion
- **Tenant detail tab routing decision (D-11)** locked by Claude when user said "vụ này bạn decide". Recommended choice rationale: shadcn Tabs primitive already installed, query-param URL shareable, audit granularity per tab visit useful, balanced data-fetch strategy.
- **PLAN.md structure inside phase** (single PLAN.md covering 40+ reqs across waves vs. split sub-plans `8A.PLAN` through `8F.PLAN` matching research SUMMARY decomposition) deferred to plan-phase. Plan-phase should consider review fatigue + sequencing constraint (8A foundation must complete before 8B/8C/8D/8E/8F callers).
- **Liquibase changelog grouping** (one big `048-admin-foundation.yaml` vs per-domain split `048-admin-users.yaml` / `049-admin-audit.yaml` / `050-admin-read-event.yaml` / `051-llm-provider-master-key.yaml` / `052-catalog-tables.yaml` / `053-anthropic-seed.yaml`) — match existing per-feature convention (047 priors are per-feature).
- **Admin enrollment URL out-of-band delivery channel** (Signal, encrypted email, paper, in-person handover) — operator's choice per situation; runbook documents options without prescribing.
- **`(admin)` URL prefix within `apps/admin`** (root-level routes `/audit`, `/tenants/:id` vs `/admin/audit`, `/admin/tenants/:id`) — since `apps/admin` lives on its own subdomain, no prefix needed; routes start at root.
- **Audit log row presentation** (before/after JSON diff inline collapsible tree vs side-by-side columns vs JSON Patch format) — UI concern for ui-phase or plan-phase.
- **Catalog Sync Diff page layout** (tabular added/removed/changed vs side-by-side vs accordion grouped by model + pinned-tenant count display) — UI concern for ui-phase.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Locked Requirements (read first)
- `.planning/phases/08-admin-console-operator-tooling/08-SPEC.md` — **Locked requirements (42)**, boundaries, constraints, acceptance criteria, ambiguity report, interview log; MUST read before planning.
- `.planning/REQUIREMENTS.md` §§ 21-28 (ADMIN-01..10), §§ 14-16 (OPS-INFRA-01..03), §§ 102-106 (ARCH-08..12), MKEY/CAT/OPS-TENANT/OPS-QUEUE/OPS-SPEND rows — REQ-ID source of truth + traceability table.
- `.planning/ROADMAP.md` § Phase 8 — success criteria #1..#12 + locked decisions (Decision 1 through Decision 6, post-pivot).

### Project Context
- `CLAUDE.md` — project constraints (Java 25, Spring Boot 4.0.6, Spring Security 7.0.5, Spring AI 2.0.0-M6, Spring Modulith, PostgreSQL 17, Liquibase YAML, single VPS, bundled Google OAuth for users only, AES-GCM `RefreshTokenCipher` reuse).
- `CONVENTIONS.md` — backend domain package layout, DTO records, enum state machines, privacy logging format, Modulith vs direct calls.
- `TESTING.md` — Spring Boot 4 slice ladder, Spring AI three-layer testing, ArchUnit + grep-gate conventions.

### Research (consumed during this phase)
- `.planning/research/SUMMARY.md` — v1.2 executive summary including sub-phase decomposition 8A→8F, 7 pitfalls, 5 open decisions.
- `.planning/research/ARCHITECTURE.md` — module placement, RBAC + tenant context separation, catalog shape, master-key resolution path.
- `.planning/research/PITFALLS.md` — 7 documented pitfalls (admin session bleed, master-key oracle, body/chat leak, catalog supply chain, send-call-site regression, audit exfiltration, catalog cache race).
- `.planning/research/STACK.md` — stack additions, zero new runtime deps, 3-table catalog rationale, GroupedOpenApi split.
- `.planning/research/FEATURES.md` — 10 feature categories, differentiators, anti-features.

### Spring Security 7 (mandatory plan-phase research via Context7)
- Library ID: `/websites/spring_io_spring-security_reference_7_0` — preferred for current docs.
- Fallback: `/spring-projects/spring-security` (version `6.5.1` / `6.4.7` for legacy reference).
- **MUST cover for Phase 8:** `.webAuthn(...)` DSL (RP config, `WebAuthnRelyingPartyOperations`, `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`, signature-counter replay defense), multiple `SecurityFilterChain` + `securityMatcher` patterns, method security `@PreAuthorize` + `@EnableMethodSecurity`, OAuth2 OIDC userinfo (`GrantedAuthoritiesMapper` is NOT needed post-pivot but useful context for understanding what was rejected), CSRF on WebAuthn endpoints, `userVerificationRequirement=REQUIRED` enforcement.
- WebAuthn external reference: [Spring Security 7 Passkey Reference Docs](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passkeys.html).

### Spring AI M6 (mandatory plan-phase research via Context7)
- Library ID: `/spring-projects/spring-ai` — `StreamingChatModel`, `ChatModel` cache eviction, OpenAI + Anthropic + Google + DeepSeek adapters, OpenRouter via OpenAI adapter pattern.

### springdoc-openapi (mandatory plan-phase research via Context7)
- Library ID: `/springdoc/springdoc-openapi` — `GroupedOpenApi` split for public vs admin specs.

### WebAuthn frontend client (mandatory plan-phase research via Context7)
- Library: `@simplewebauthn/browser` — client-side `startRegistration` / `startAuthentication` ceremony invocation.

### Existing Liquibase changelogs (continuation reference)
- `backend/core/src/main/resources/db/changelog/changes/001-047-*.yaml` — Phase 8 continues at 048+; naming convention is per-feature.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — master changelog list.

### Existing security + identity code (integration points)
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — current single `@Order(3)` filter chain (extend with `@Order(1)` admin chain + retain `@Order(2)` user chain).
- `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java` — user chain success handler; **NOT modified by Phase 8**.
- `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java` — outgoing OAuth request shape; **NOT modified by Phase 8** (despite earlier discussion).
- `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` — user chain only; admin chain has its own admin-binding filter.
- `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` — existing `ScopedValue<String>` pattern; `AdminContext` mirrors this shape in `core.admin.auth`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — AES-GCM cipher to reuse for master keys (consider moving to `core.shared.crypto` if reuse warrants; plan-phase decision).

### Existing observability + ArchUnit conventions
- `backend/core/src/test/java/com/zeromail/core/arch/*ArchTest.java` — `DigestPayloadShapeArchTest`, `DomainPurityArchTest`, `RulesBoundaryArchTest`, `TriageAuditRepositoryBoundaryArchTest` (pattern reference for Phase 8 ArchUnit rules).
- `backend/api/src/test/java/com/zeromail/api/arch/*ArchUnitTest.java` — `I18nArchUnitTest`, `LaunchProfileArchUnitTest` (api-layer ArchUnit reference).
- `backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java` — path-confinement ArchUnit example relevant for `AdminPathBodyBanTest` shape.

### Frontend reference
- `apps/web/components/ui/*.tsx` — shadcn primitives source (table, tabs, sidebar, chart, command, popover, sheet, alert-dialog, switch, dialog) — reuse via copy or workspace re-export for `apps/admin`.
- `apps/web/scripts/generate-api.ts` — existing OpenAPI codegen pattern; extend to emit both `schema.d.ts` (public) and `admin-schema.d.ts` (admin) OR fork into `apps/admin/scripts/generate-api.ts`.

### Memory notes (project context for downstream agents)
- `project_v12_phase8_9_merged` — merge note (40 reqs from original Phase 8 + 9 → single Phase 8).
- `project_v12_admin_webauthn_pivot` — discuss-phase pivot details + rationale + lesson-learned about spec-phase gap.
- `project_phase8_spring_security_7_research` — research mandate for Spring Security 7 APIs (now expanded with WebAuthn DSL).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `TenantContext.java` (`backend/core/src/main/java/com/zeromail/core/tenant/`) — `ScopedValue<String>` with `currentOrThrow()` + `runWith(UUID, Runnable)` helpers. `AdminContext` will mirror this shape in `core.admin.auth` (`ScopedValue<AdminUser>` with `currentOrThrow()` + `run(AdminUser, Runnable)`).
- `RefreshTokenCipher.java` (`backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/`) — AES-GCM with 96-bit IV, 128-bit tag, KEK rotation infrastructure. Master-key encryption reuses this; plan-phase may decide to relocate the class to `core.shared.crypto` for explicit shared-by-multiple-modules ownership.
- `OAuthProvisioningService` (`backend/core/src/main/java/com/zeromail/core/account/usecases/`) — atomic user + tenant provisioning pattern; admin enrollment ceremony reuses the "atomic write under transaction" pattern but writes `admin_users` row state transitions instead.
- `processing_job` + `outbox` Postgres SKIP LOCKED queue tables — Catalog Sync Fetch step reuses this queue (no new infrastructure).
- `llm_call_audit` table — Spend dashboard aggregates this (no new instrumentation for billing).
- shadcn primitives (all needed: table, tabs, sidebar, chart, command, popover, sheet, alert-dialog, switch, dialog, hover-card, separator, scroll-area, sonner, skeleton, badge, alert, toggle, accordion, button-group, collapsible) already in `apps/web/components/ui/` — copyable to `apps/admin/src/components/ui/` (shadcn is copy-paste source).
- `apps/web/scripts/generate-api.ts` — OpenAPI codegen pattern; either extend to dual-emit or fork into `apps/admin/scripts/`.

### Established Patterns

- **Spring Modulith vertical modules** — `core.chat`, `core.llm`, `core.gmail` each are one Modulith module with sub-packages `domain/usecases/projection/persistence/exception` per `CONVENTIONS.md`. `core.admin` follows identical shape with 7 vertical sub-packages (`auth/audit/mkey/cat/tenant/queue/spend`).
- **`@ApplicationModuleListener` for cross-module events in-JVM** — used between `core.chat`, `core.gmail`, `core.account` already. `MasterKeyRotatedEvent` + `CatalogChangedEvent` follow this pattern. Memory note `feedback_modulith_listener_scope` confirms scope is in-JVM only.
- **ArchUnit per-module test pattern** — each module has its own `arch/` package with `*ArchTest.java` classes. `core.admin.arch.AdminPathBodyBanTest`, `AdminContextMutexTest`, `AdminControllerPreAuthorizeTest`, `AdminChainNoOauth2LoginTest`, `MasterKeyResolverConfinementTest`, `MasterKeySentinelLeakTest` are the Phase 8 set.
- **Liquibase numeric sequential changelogs** (001-047) — Phase 8 continues at 048+, per-feature files matching existing convention.
- **Privacy logging convention** — `event=<name> tenantId={}` opaque format; no email body, no Google subject, no token bytes. Admin audit DB writes are separate from logs — DB persistence allowed per Privacy scope (rule-builder chat persists similarly).
- **Spring Security 7 single filter chain with `@Profile("!test")`** — current pattern; Phase 8 adds `@Order(1)` admin chain (same `@Profile("!test")`) + retains current chain as `@Order(2)` user chain. `@Profile("test")` SecurityConfig (if exists per `WR-06` todo) must be updated to mirror both chains.
- **`@PreAuthorize` per controller** — `controllers/admin/` directory pattern with explicit annotation per class; ArchUnit verifies presence (not meta-annotation, per Decision D-09).

### Integration Points

- **`backend/api/SecurityConfig.java`** — add `@Bean @Order(1) SecurityFilterChain adminChain(...)` configuring `.webAuthn(...)` + `securityMatcher("/api/admin/**")`; existing chain becomes `@Order(2)` user chain. ArchUnit `admin_chain_does_not_use_oauth2login` enforces non-overlap.
- **`backend/api/Application.java`** — `@SpringBootApplication` already scans `com.zeromail.*`; Spring Modulith auto-discovers new `core.admin.*` module. No changes needed.
- **`backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`** — append entries for 048-054 (admin_users, admin_audit_event, admin_read_event, llm_provider_master_key, catalog tables, Anthropic seed, MKEY history if needed).
- **NPM proxy config in `docker-compose.yml`** — add NPM service definition + 9Router sidecar service + admin.zeromail.com subdomain route → backend/api:8080. Live VPS NPM admin UI configures the route post-deploy (per OPS-INFRA-02).
- **`apps/web/scripts/generate-api.ts`** — keep as-is for `apps/web` (consumes `/v3/api-docs/public`). `apps/admin/scripts/generate-api.ts` (new) consumes `/v3/api-docs/admin`.
- **`backend/api/config/OpenApiConfig.java`** — add `GroupedOpenApi publicApi` bean (excludes `/api/admin/**`) + `GroupedOpenApi adminApi` bean (only `/api/admin/**`).
- **`turbo.json` + `pnpm-workspace.yaml`** — register `apps/admin` workspace; turbo cache entry for `@zeromail/admin#build`.

</code_context>

<specifics>
## Specific Ideas

- **DNS subdomain `admin.zeromail.com`** — strongest visual cue for admin context (user-mentioned approach surfaced via discuss-phase research). Pairs with persistent ADMIN MODE banner inside `apps/admin` chrome for destructive-action context within admin tabs.
- **Memory `feedback_bundled_oauth_scopes`** — user-side OAuth flow stays exactly as v1.0/v1.1 (bundled login + Gmail scopes in one round-trip). Phase 8 does NOT touch the user-side OAuth path.
- **Spring Security 7 `.webAuthn(...)` DSL** — natively built-in (6.4+ feature). Spring Security ref docs at `/websites/spring_io_spring-security_reference_7_0` are authoritative; training data may underrepresent this DSL.
- **`@simplewebauthn/browser`** — community-vetted client-side WebAuthn library; pairs natively with Spring's WebAuthn endpoints.
- **Counter-replay defense** (D-10, REQ ADMIN-10) — reported `signCount` MUST be greater than stored `admin_users.signature_counter`; smaller or equal → audit row `WEBAUTHN_REPLAY_SUSPECTED`. Real defense against cloned authenticators.
- **One-time enrollment URL printed to STDOUT** — `System.out.println(...)` directly, NOT via SLF4J (logback would persist to file). Plan-phase: implement as `CommandLineRunner` bean reading from `ZeroMailAdminProperties.bootstrapEmails`, holding tokens in `ConcurrentHashMap<String,Instant>` with 10-min TTL cleanup, consuming token on `/enroll?token=...` access.
- **Lost-passkey recovery shell ceremony** (out of scope v1.2 UI) — runbook documents: SSH to VPS → `docker compose exec api psql ...` → `UPDATE admin_users SET status='PENDING_ENROLLMENT', credential_id=NULL WHERE email='...'` → restart backend → enrollment URL printed → admin re-registers passkey.

</specifics>

<deferred>
## Deferred Ideas

### Reviewed Todos (cross-referenced during discuss-phase)
- **`2026-05-12-make-backend-core-context-api-surfaces-explicit-with-namedin.md`** (score 0.9, area: architecture) — explicit `@NamedInterface` API surfaces across `backend/core` modules. **Reviewed but NOT folded into Phase 8** per Decision D-07: rule-of-three not yet triggered; defer to Phase 11+. Phase 8's single `core.admin` module is logically internal — when 5+ sibling modules are added or when v1.3+ work needs to import admin foundation across module boundaries, this todo activates.
- **`2026-04-28-wr-06-test-profile-securityconfig-slice.md`** (score 0.6, area: testing) — test-profile `SecurityConfig` slice for OAuth filter chain coverage. **Reviewed but NOT folded into Phase 8** scope per default policy: Phase 8 introduces a second chain (admin) which broadens this todo's surface — but coverage work is separate test infrastructure. Plan-phase should reference this todo if it touches `SecurityConfig` test slicing; otherwise carry to future security-hardening phase.
- **`2026-05-15-rules-ux-structured-builder-next-milestone.md`** (score 0.6, area: product-ui) — Rules UX structured When/Then builder. **Out of phase** — belongs in user-facing settings work; v1.3+ candidate.

### Future-phase ideas surfaced during discussion
- **`@AdminController` meta-annotation** (`@RestController + @PreAuthorize("hasRole('ADMIN')")` bundled) — defer to Phase 11+ when admin controllers count ≥6 (rule-of-three triggered).
- **Two-cookie session split** (separate admin cookie path, stricter SameSite) — current chain-split + DNS subdomain reduces need; revisit if real CSRF/impersonation vector surfaces in v1.3+.
- **Self-service passkey recovery UI** — invites social-engineering surface; defer to v1.3+ with proper out-of-band identity verification (e.g., second admin co-sign).
- **Multiple passkey enrollment per admin** (primary + backup hardware key) — `admin_users` schema in D-09 supports multiple credentials via separate row per credential or extension table; v1.3+ enhancement.
- **TOTP fallback for admin** — explicitly out of scope (WebAuthn-only locked); revisit if hardware key supply chain becomes accessibility issue.
- **Admin-side IP allowlist UI** (configure allowed IP ranges via admin console) — Phase 8 documents IP allowlist as NPM-level OPS-INFRA option; UI-based control is v1.3+.
- **Audit log forensic export with cryptographic chain proof** — current HMAC chain verification job emits alert metric; future ADR may add signed export bundle for legal hold scenarios.
- **Cross-process admin events** (when/if split to `backend/admin-api` happens later) — Postgres LISTEN/NOTIFY, HTTP internal, or outbox-based delivery; not relevant unless JVM split occurs (v1.3+ if compliance pressure or admin team scaling drives it).
- **Spring AI starter exclusion in admin path** — currently in-scope only via discipline (admin code doesn't import Spring AI beans); not enforced at dependency level. If a separate `backend/admin-api` JVM is ever extracted, that JVM excludes Spring AI starters entirely as a process-level defense.

</deferred>

---

*Phase: 08-admin-console-operator-tooling*
*Context gathered: 2026-05-19*
*Next step: `/gsd:plan-phase 8` — plan-phase researcher MUST pull Spring Security 7 docs via Context7 (esp. `.webAuthn(...)` DSL) + Spring AI M6 docs + springdoc-openapi docs + `@simplewebauthn/browser` docs; plan-phase planner decides PLAN.md single-vs-split (8A.PLAN through 8F.PLAN) and Liquibase changelog grouping.*

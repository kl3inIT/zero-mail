# Project Research Summary — Zero Mail v1.2

**Project:** Zero Mail v1.2 — Admin Console Foundation (Phase 8) + Settings UI on Curated Catalog (Phase 9)
**Domain:** Operator/admin surface + per-provider × per-feature LLM catalog + 4-tab user Settings on top of a shipped Java 25 / Spring Boot 4 / Spring Modulith / Next.js 16 multi-tenant SaaS
**Researched:** 2026-05-19
**Confidence:** HIGH overall

## Executive Summary

v1.2 stacks two surfaces on the v1.0 + v1.1 baseline: a `/admin/*` console gated by `ROLE_ADMIN` (Phase 8) and a four-tab user `/settings` rebuilt on the admin-curated LLM catalog (Phase 9). The four research outputs converge: **zero new backend/frontend runtime dependencies** — every capability is built from artifacts already on the classpath (Spring Security 7.0.5, springdoc-openapi 3.0.3, AES-GCM `RefreshTokenCipher`, Liquibase 5, openapi-typescript 7.13, all shadcn/ui primitives) plus a small set of new schema, new ArchUnit gates, and one new Modulith module (`core.admin`).

The architectural keystone is a **3-table normalized catalog** (`provider_catalog`, `model_catalog`, `feature_binding`) — not JSONB — with FK + UNIQUE preventing stale-pin failures from `assistant_settings` → catalog. Master keys reuse the existing AES-GCM `RefreshTokenCipher` via a new `llm_provider_master_key` table; resolution flows through a single `ProviderMasterKeyResolver` inside `llm.gateway.springai`, preserving the locked "one adapter" boundary. Sync-from-`/models` is **async** (existing `processing_job` SKIP LOCKED + Redis debounce lease); Anthropic has no `/models` endpoint so its catalog path is manual-only. The Settings AI tab pulls from `GET /api/settings/catalog` served by a distinct `CuratedCatalogQueryService` (different DTO shape from admin endpoints) so admin schemas never leak into the public OpenAPI document — `springdoc-openapi` `GroupedOpenApi` splits `public` vs `admin` specs and the frontend codegens **two** typed clients.

The three highest-risk seams are (1) **master-key handling** (test-connection oracle, response-roundtrip leaks, ChatModel cache not evicted on rotation), (2) **admin session bleed** into user endpoints (one-cookie design lets a rogue `if (isAdmin) skip tenant filter` fallback collapse tenant isolation), and (3) **tenant inspection silently leaking email body / chat content** the v1.0/v1.1 privacy contract bans. Three architectural enforcement layers — `AdminContext` Scoped Value mutually exclusive with `TenantContext`, ArchUnit `AdminPathBodyBanTest`, and an `AdminResponseBodyBanFilter` failsafe — must land in sub-phase 8A **before** any tenant-inspection view ships.

---

## Stack Additions for v1.2

**Backend — zero new runtime deps, three architectural switches:**

- **Spring Security 7.0.5 `@EnableMethodSecurity`** (already on classpath) — method-level `@PreAuthorize("hasRole('ADMIN')")` complementing URL-pattern `requestMatchers("/api/admin/**").hasRole("ADMIN")`. Two-layer defense at filter + method boundary.
- **DB-backed admin elevation via `users.role` column** — `ROLE_ADMIN` appended to existing `OAuth2User` authorities in `GoogleOAuthSuccessHandler`. Bundled-OAuth flow untouched; no second IdP, no Keycloak, no JWT. Cookie + Spring Session Redis stays.
- **`springdoc-openapi 3.0.3` `GroupedOpenApi` split** — two beans (`publicApi`, `adminApi`) producing `/v3/api-docs/public` (excludes `/api/admin/**`) and `/v3/api-docs/admin`. Existing `GlobalOpenApiCustomizer` was authored anticipating this split.
- **Master keys reuse existing AES-GCM `RefreshTokenCipher`** — same algorithm (AES-256-GCM, 96-bit IV, 128-bit tag), same KEK rotation, same `@Sensitive` Logback scrub. New `llm_provider_master_key` table mirrors `byok_credential` shape; KEK from `ZeroMailCoreProperties.crypto.masterKeys.kekBase64`. Not HashiCorp Vault, not GCP/AWS KMS (single-VPS locked), not pgcrypto (CLAUDE.md do-not-use list).
- **Sync-from-`/models`** via Spring AI provider starter clients or `RestClient` calls confined inside `core.llm.gateway.springai.admin` — vendor-SDK confinement ArchUnit rule stays in force.

**Frontend — zero new runtime deps:**

- All admin primitives (`table`, `tabs`, `alert-dialog`, `select`, `command`, `popover`, `sidebar`, `sheet`, `chart`, `switch`, `skeleton`, `sonner`) already in `apps/web/components/ui/**`.
- `apps/web/scripts/generate-api.ts` loops over two spec URLs → emits `schema.d.ts` (public) + `admin-schema.d.ts` (admin). New `admin-client.ts` is a 3-line wrapper over `openapi-fetch@0.17.0`.
- Memory note "Use raw shadcn primitives first" → defer `@tanstack/react-table`; hand-compose tables on `table.tsx` for Phase 8.

**New persistence (six Liquibase YAML changelogs):** `user.is_admin` column add, `admin_audit_event`, `llm_provider_catalog`, `llm_provider_model`, `llm_model_feature_capability`, `llm_provider_master_key`.

---

## Feature Categories — 10 Categories

| Category | Table stakes | Differentiators | Anti-features |
|---|---|---|---|
| **ADMIN** (RBAC + audit) | `/admin/*` 403 gate, DB-backed `ROLE_ADMIN`, append-only `admin_audit_event` + `admin_read_event`, tenant-keyed audit, confirm-twice destructive actions | Audit diff view, filter chips, CSV export | Admin-impersonate-user, SQL console, separate admin password |
| **CAT** (curated catalog) | Per-provider × per-feature catalog (chat/triage/draft), 3-step Sync (fetch → diff → confirm), sync run history, disable-with-dependent-count, provider status pill | Cost-per-1k display, "Recommended for" badge, deprecation tag, `GET /api/settings/catalog` | Free-form model-ID override, auto-approve Sync, embedding curation, per-tenant allowlist |
| **MKEY** (master keys) | Per-provider set/test/rotate with transactional rollback, masked display only, oracle hardening, key-history mini-list | Dependents count, 90-day rotation reminder | "Reveal key once", automatic cron rotation, per-tenant master keys |
| **OPS-TENANT** (read-only inspection) | Tenant list + detail tabs (Overview/Health/Billing/Spend/Activity), pause/disconnect/delete admin actions, metadata-only on PII | Spend sparkline, replay-watch-renewal, deletion preview counts | View tenant inbox, chat content, prompts/completions, OAuth token, edit-on-behalf |
| **OPS-QUEUE** (worker health) | Outbox lag + max age, `processing_job` depth by type, retry distribution, failure rate, dead-letter view + re-queue, 10s auto-refresh | Worker heartbeat, backpressure banner | Worker stop/start UI, view job payload, manually edit row |
| **OPS-SPEND** (global dashboard) | Top-line cards (today/7d/30d), stacked bar by provider, donut by feature, platform-vs-BYOK split, top-N tenants, date-range picker | Spend forecast, per-model p50/p95, cap-vs-actual chart | Drill-down to prompts, websocket streaming, public marketing page |
| **SET-AI** (carries SET-AI-01..04) | Per-feature provider+model picker reading catalog, BYOK key cards, use-BYOK-if-available toggle, cost-per-1k display, reset-to-recommended | Last-7d-cost hint, deprecation inline banner, per-feature spend cap | Free-form model-ID textbox, per-rule model override, show master-key bytes, user-triggered Sync |
| **SET-VOICE** (carries SET-VOICE-01..06) | Writing style, personal instructions (injection-hardened), signature, tone preset, AI output language (VI/EN), knowledge-snippet CRUD | — | Two-way settings↔rule-prompt sync, AI-learned style from sent mail |
| **SET-BEHV** (carries SET-BEHV-01..05) | Auto-draft toggle, draft confidence slider, follow-up reminders, daily digest opt-in, sensitive-data protection | — | "Always auto-send on confidence > X" (hard ban — TRG-03) |
| **SET-SAFE** (carries SET-SAFE-01..04) | VIP allow-list, never-archive, never-trash (future-proof), quick-add from triage audit | — | First-class block-list (expressible as rule) |

---

## Architecture Highlights

**Module placement.** `core.admin` is a **NEW top-level Modulith module**, sibling of `core.chat` and `core.llm` — **not** inside `core.llm`. Rationale: `llm` is horizontal capability (gateway); `admin` is vertical operator domain. Sub-packages follow `domain/ application/ projection/ persistence/ exception/`.

**RBAC + tenant context separation.** `AdminContext` is a separate `ScopedValue` from `TenantContext`, mutually exclusive — `TenantContext.currentOrThrow()` throws inside admin call, and vice versa. Cross-tenant admin reads route through `AdminTenantAccess.readOnly(tenantId, supplier)` which writes an `admin_audit_event` row.

**Catalog shape.** Three normalized tables — `provider_catalog`, `model_catalog`, `feature_binding` (M:N model × {CHAT, TRIAGE, DRAFT}, with `enabled`, `is_default`, `is_recommended`). **Not** JSONB. FK + UNIQUE partial indexes prevent stale-pin failures from `assistant_settings.{chat|triage|draft}_model_id`.

**Master-key resolution.** `ProviderMasterKeyResolver` lives inside `llm.gateway.springai` as single resolution point. Rotation emits `MasterKeyRotatedEvent` → `@ApplicationModuleListener` evicts all cached `ChatModel` instances for that provider across **every** tenant.

**Read views.** Tenant projections use **Spring Data JDBC `Repository<...>`** (not `CrudRepository`). ArchUnit gate forbids body-content field names (`body|bodyHtml|snippet|payload|prompt|completion`) in admin projection DTOs.

**Frontend layout.** `(admin)` is **sibling** Next.js route group of `(app)`, with own server-side `ROLE_ADMIN` gate in `layout.tsx`. Independent typed client from `admin-schema.d.ts`; admin code-splits so public bundle never ships admin types.

**Three separate audit tables** — `triage_audit` (rules), `assistant_send_audit` (chat-confirmed send, v1.1), `admin_audit_event` (new). Different retention/actors/queries.

**Build order — sub-phases:**

- **8A** RBAC + `AdminContext` + audit primitive (foundation; everything depends)
- **8B** Master-key management (gated behind 8A; sentinel-leak test in CI)
- **8C** Tenant read-only views (depends on 8A — parallel with 8B; ArchUnit body-ban + response filter land first)
- **8D** Catalog Sync flow (depends on 8B for master key)
- **8E** Worker queue health (depends on 8A — parallel with 8C/8D)
- **8F** Global spend dashboard (depends on 8A — parallel with 8C/8D/8E)
- **9A** Settings chrome + tab routing (independent)
- **9B** SET-VOICE + SET-BEHV + SET-SAFE (parallel with 9A)
- **9C** SET-AI tab (depends on 8B + 8D)

---

## Watch Out For (Top Pitfalls)

1. **Single-cookie admin session bleeds into user endpoints.** Mitigation: ArchUnit rule forbidding admin controllers from reading `TenantContext` directly; mandatory `AdminContext.currentOrThrow()` first statement; persistent red/yellow "ADMIN MODE" chrome bar.

2. **Master-key oracle via test-connection.** Mitigation: edit-session token (5-min TTL), 10 req/hour/admin rate limit, response strips provider error body (returns only enum codes `INVALID_KEY | RATE_LIMITED | NETWORK_ERROR`), `/models` HTTP client isolated from logging proxy, sentinel-leak CI test.

3. **Tenant inspection leaks body/chat/prompt content.** Mitigation: `AdminPathBodyBanTest` ArchUnit blocks admin packages from `GmailClient` body-exposing methods; `AdminResponseBodyBanFilter` failsafe scrubs JSON fields named `body|bodyHtml|snippet|prompt|completion|content` >200 chars; admin paths cannot resolve tenant OAuth credentials.

4. **Catalog Sync supply-chain.** Mitigation: strict per-provider JSON Schema validation; three-step flow (Fetch → Diff → Confirm); soft-delete only (`deprecated_at`); model-ID regex allow-list `^[a-zA-Z0-9._:/\-]{1,128}$`; per-feature toggles preserved across Sync; "ping completion" before enabling; Anthropic manual-only.

5. **Send call-site count regresses 1 → 2+.** Mitigation: ArchUnit extended — admin packages forbidden from calling Gmail send methods entirely; grep gate stays at exactly 1; master-key test-connection uses `/models` GET, never send.

6. **Audit log used as exfiltration channel OR admin edits own audit.** Mitigation: DB-level append-only Postgres trigger + app DB user has no `UPDATE`/`DELETE` grant on `admin_audit_event`; `reason VARCHAR(500)` with regex sanitizer rejecting key-prefix patterns; audit insert in separate same-request transactions; HMAC chain-hash per row with nightly verification; co-admin required to read full audit log.

7. **Catalog cache race on hot path.** Mitigation: Sync diff applies atomically in transaction with `SELECT ... FOR UPDATE` on small lock table (not catalog itself); `CuratedCatalogQueryService` reads `READ COMMITTED` with short Redis ETag cache; `MASTER_KEY_ROTATED` + `CATALOG_CHANGED` Modulith events evict per-tenant `ChatModel` cache.

---

## Implications for Roadmap

### Phase 8 — Admin Console Foundation

**8A: Admin foundation (RBAC + AdminContext + audit primitive)**
Rationale: Foundation; everything depends. Delivers: `user.is_admin` + Liquibase, `@EnableMethodSecurity`, `requestMatchers("/api/admin/**").hasRole("ADMIN")`, `AdminContext` ScopedValue mutex with `TenantContext`, `admin_audit_event` + `admin_read_event` with append-only trigger + HMAC chain hash, ArchUnit rules, `GroupedOpenApi` split, `(admin)` Next.js route group. Avoids: Pitfalls 1, 3, 6.

**8B: Master-key management** (depends on 8A)
Rationale: Catalog Sync (8D) needs master keys to call `/models`. Delivers: `llm_provider_master_key` reusing `RefreshTokenCipher`, `ProviderMasterKeyResolver` inside `llm.gateway.springai`, set/test/rotate UI with edit-session token + rate limit + error-body stripping, `MasterKeyRotatedEvent` → ChatModel cache eviction, sentinel-leak CI test. Avoids: Pitfall 2.

**8C: Tenant read-only views** (depends on 8A — parallel with 8B)
Rationale: Independent of catalog/master-keys. Delivers: Tenant list + 5-tab detail, pause/disconnect/delete actions, `AdminPathBodyBanTest`, `AdminResponseBodyBanFilter`. Avoids: Pitfalls 4, 5.

**8D: Catalog management + Sync flow** (depends on 8B)
Rationale: Sync calls `/models` with master key. Delivers: `provider_catalog` + `model_catalog` + `feature_binding`, 3-step Sync with JSON Schema validation, model-ID regex allow-list, soft-delete, Anthropic manual-only, `CuratedCatalogQueryService` + `GET /api/settings/catalog`. Avoids: Pitfalls 5, 7.

**8E: Worker queue health** (depends on 8A — parallel with 8C/8D)
Read-only aggregates over existing `outbox` + `processing_job`.

**8F: Global LLM spend dashboard** (depends on 8A — parallel with 8C/8D/8E)
Read aggregates over existing `llm_call_audit` (metadata-only).

### Phase 9 — Settings UI on Curated Catalog

**9A: Settings chrome + tab routing** (independent)
shadcn `<Tabs>` on single `/settings/page.tsx` route (flat-folder rule: no `/settings/ai` sub-route), query-param-driven active tab.

**9B: SET-VOICE + SET-BEHV + SET-SAFE** (parallel with 9A/9C)
15 of 19 deferred v1.1 reqs. All independent of catalog/master-keys.

**9C: SET-AI tab** (depends on 8B + 8D)
Per-feature provider+model picker from `GET /api/settings/catalog`; BYOK cards reusing v1.0 `byok_credential`; use-BYOK toggle; cost display; reset-to-recommended; deprecation banner.

### Research Flags

Needs deeper research (`/gsd:plan-phase --research-phase`): **8B** (master keys), **8D** (catalog Sync), **9C** (SET-AI cost display + reset UX).
Standard patterns (skip research): **8A**, **8C**, **8E**, **8F**, **9A**, **9B**.

---

## Open Decisions for Roadmap

### Decision 1: One-filter-chain RBAC vs two-cookie split

- **Architecture** recommends single `SecurityConfig` chain + `@EnableMethodSecurity` + DB-backed `user.is_admin` + one cookie.
- **Pitfalls (Pitfall 3)** argues two cookies (`zm_session` + `zm_admin_session`), two filter chains, separate `/admin/login` OAuth round-trip, `SameSite=Strict` on admin cookie, persistent admin-mode chrome bar.
- **Recommendation:** Start single-cookie + `AdminContext` ScopedValue + ArchUnit "no `TenantContext` in admin packages" rule (90% of safety at 20% of code); revisit two-cookie if real CSRF/impersonation vector surfaces.
- **Resolve in:** 8A planning.

### Decision 2: First-admin bootstrap mechanism

- Options: (a) Liquibase changeset (rejected — Pitfall 1 forever-admin), (b) env-var `ZEROMAIL_BOOTSTRAP_ADMIN_EMAIL` with idempotent guard, (c) CLI command.
- **Recommendation:** (b) env-var. Document in Constraints.
- **Resolve in:** 8A planning.

### Decision 3: Audit retention

- STACK suggests 90+ days; FEATURES says 90d write/30d read; PITFALLS implies indefinite for trust backstop.
- **Recommendation:** Indefinite for `admin_audit_event`; 30 days for `admin_read_event`. Revisit at first compliance milestone.
- **Resolve in:** 8A planning.

### Decision 4: Chat-session inspection scope in OPS-TENANT

- Question: zero chat surface, session metadata only, or metadata + redacted tool-output summaries?
- **Recommendation:** Session metadata only (count, last activity, model selection). Disable "Show details" with tooltip "Chat content access requires tenant-bound support ticket grant (v1.3+)."
- **Resolve in:** 8C planning.

### Decision 5: Anthropic manual-only catalog seeding cadence

- Anthropic has no public `/models` endpoint.
- **Recommendation:** Hybrid — Liquibase data seed for initial Anthropic catalog (Claude 3.5 Sonnet/Haiku, Claude 3 Opus); manual admin entry for new models. Sync button disabled on Anthropic provider page with tooltip.
- **Resolve in:** 8D planning.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Zero new runtime deps; verified via Context7 `/springdoc/springdoc-openapi` + existing classpath. |
| Features | **HIGH** (admin/audit/tenant) / **MEDIUM** (catalog UX) | RBAC + audit well-trodden; LiteLLM is closest curated-catalog peer. |
| Architecture | **HIGH** | Follows shipped v1.0/v1.1 conventions (CONVENTIONS.md package layout). |
| Pitfalls | **HIGH** | Well-documented patterns (CWE-522/532/798, NIST SP 800-57, OWASP A04/A09:2021). |

**Overall confidence:** **HIGH** — proceed to roadmap. Five open decisions are resolvable inside 8A/8C/8D planning; none block roadmap shape.

---

## Sources

**Primary (HIGH):** Context7 `/springdoc/springdoc-openapi`; Spring Security 7.0.x reference; Spring Framework 7 reference; existing repo (`SecurityConfig.java`, `OpenApiConfig.java`, `generate-api.ts`, `apps/web/components/ui/**`, `gradle/libs.versions.toml`); internal docs (CLAUDE.md, CONVENTIONS.md, PROJECT.md, SEED-011); npm registry.

**Secondary (MEDIUM):** Local Inbox Zero clone (rejected env-var allowlist pattern); WorkOS multi-tenant RBAC; Agnite Studio audit logging; Microsoft Learn multitenant identity; LiteLLM proxy admin UI (closest peer); PlanetScale Postgres queue; Neon `SKIP LOCKED`.

**Tertiary (LOW):** Idee cross-tenant impersonation; Crypteron PCI rotation cadence; Ubiq Security key wrapping.

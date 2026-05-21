# Stack Research — Zero Mail v1.2 (Admin Console Foundation + Settings UI on Curated Catalog)

**Domain:** Operator/admin surface added to an existing multi-tenant Spring Boot 4 + Next.js 16 SaaS
**Researched:** 2026-05-19
**Overall confidence:** HIGH on all additions/changes (verified via Context7 `/springdoc/springdoc-openapi`, Spring Security 7 docs, Spring Boot 4 reference, existing repo state, npm registry). Zero new "exotic" deps — every v1.2 capability is built from artifacts already on the v1.0/v1.1 classpath plus **one** new dev-time codegen output (a second OpenAPI group).

> **Scope of this document.** This is the **v1.2 delta**. The v1.0 baseline and v1.1 chat additions (Java 25 / Spring Boot 4.0.6 / Spring AI 2.0.0-M6 / PostgreSQL 17 / Redis 7 / Next.js 16.2 / React 19.2 / Tailwind 4 / shadcn/ui / TanStack Query 5 / openapi-typescript 7.13 / openapi-fetch 0.17 / Liquibase 5 / springdoc-openapi 3.0.3 / Spring Session Redis / AES-GCM at app layer / virtual threads / Micrometer + OTel agent 2.16 / `ai` 6 + `@ai-sdk/react` 3 + AI Elements) are **locked and validated** — see git history of this file before 2026-05-19 for v1.0 and v1.1. This document only catalogs what v1.2 **adds** or **changes**.

> **What v1.2 does not add or change:** no new auth provider (still single Google OAuth bundled flow, no Keycloak/Auth0); no JWT (cookie session via Spring Session Redis stays); no new database; no new queue; no new observability tool; no new LLM provider SDK; no GCP starter; no Kafka/RabbitMQ; no embedding store; no `spring-boot-starter-webflux`. **Admin RBAC is layered on top of the existing `OAuth2User` principal — no second IdP.**

---

## TL;DR — Prescriptive v1.2 Additions

**Backend — zero new runtime dependencies.** All v1.2 capabilities reuse artifacts already on the classpath:

| Capability | Already on classpath, used as |
|---|---|
| `/admin/**` RBAC | Spring Security 7.0.5 `authorizeHttpRequests(...).requestMatchers("/admin/**").hasRole("ADMIN")` + `@PreAuthorize("hasRole('ADMIN')")` for method-level checks. **One new annotation:** `@EnableMethodSecurity` on the existing `SecurityConfig`. |
| Admin action audit log | New Liquibase YAML changelog → `admin_audit_event` table. Same persistence stack as v1.0/v1.1 (Liquibase 5 + Spring Data JPA / JDBC). |
| Per-provider per-feature LLM catalog | Three new Liquibase YAML changelogs (`llm_provider_catalog`, `llm_provider_model`, `llm_model_feature_capability`). No new library. |
| Sync-from-`/models` for each provider | **Already-installed Spring AI provider starters** expose `*ModelsApi.listModels()` via their underlying clients (OpenAI starter ships `OpenAiApi`, Anthropic starter ships `AnthropicApi`, etc.). Where Spring AI does **not** expose a `/models` lister, fall back to a thin `RestClient` call in `core.llm.gateway.springai.admin` — still inside the locked single-adapter package. **No raw third-party SDKs.** |
| AES-GCM master key encryption | **Same AES-GCM app-layer crypto already shipped in LLM-04 for BYOK** — reuse `core.crypto.AesGcmEncryptor` (or equivalent) for master keys. Keys at rest in a new `llm_provider_master_key` table; KEK from existing `ZeroMailCoreProperties` secret (rotation = new KEK version + re-wrap rows in a single Liquibase data migration + admin-issued rotation command). |
| Test-connection per master key | Spring AI `ChatModel.call(Prompt.builder().messages(new UserMessage("ping")).build())` with token limit 1 — already on classpath. |
| Tenant read-only views | Existing Spring Data JDBC projections (`projection/` package per CONVENTIONS.md). No new lib. |
| Worker queue health (read-only) | Read queries against existing `outbox` + `processing_job` Postgres tables (Postgres MCP available for ops verification per Tooling section). No new lib. |
| Promoted global LLM spend dashboard | Aggregations over the existing metadata-only spend rows already recorded by LLM-10/11. No new lib. |
| Admin OpenAPI segregation | **Already-installed `springdoc-openapi-starter-webmvc-ui` 3.0.3** ships `GroupedOpenApi` — add two beans (`publicApi` + `adminApi`) and emit two specs. |

**Backend — three architectural switches (no dep changes):**

1. Add `@EnableMethodSecurity` to the existing `SecurityConfig` class.
2. Extend the existing `OAuth2UserService` / `GoogleOAuthSuccessHandler` to attach `ROLE_ADMIN` based on a DB-backed `user.is_admin` boolean (admin elevation is a DB row, not a Google-side claim).
3. Add a second `GroupedOpenApi` bean producing `openapi/admin-openapi.json` alongside the existing `openapi/openapi.json`.

**Frontend (`apps/web/package.json`) — ZERO new runtime dependencies.** Every admin UI primitive needed in v1.2 is **already in `apps/web/components/ui/**`** (verified by directory listing on 2026-05-19): `table`, `tabs`, `dialog`, `alert-dialog`, `dropdown-menu`, `select`, `command`, `popover`, `tooltip`, `badge`, `card`, `sheet`, `sidebar`, `switch`, `chart` (Recharts wrapper), `skeleton`, `scroll-area`, `spinner`, `accordion`, `button-group`, `input-group`, `hover-card`. The only **new** shadcn primitive **likely** wanted (`data-table` patterns / pagination) is composed on top of the already-installed `table` + `button` + `select` + `input` primitives — no extra `pnpm dlx shadcn add` required for v1.2 Phase 8. **One frontend codegen change:** the `apps/web/scripts/generate-api.ts` script needs to fetch and emit **two** schema files (one per OpenAPI group), or merge both groups into the existing single schema file. See "Frontend codegen change" below for the recommended split.

---

## What v1.2 Adds — Backend (No New Dependencies)

### Spring Security 7 admin RBAC pattern (HIGH — verified against Spring Security 7.0.x reference)

Spring Security 7.0.5 (already on classpath via Spring Boot 4.0.6) is the **same API surface** as Spring Security 6 for URL authorization. No breaking change for `authorizeHttpRequests`, `requestMatchers`, `hasRole`, `hasAuthority`, or `@PreAuthorize`. The canonical pattern is:

```java
// Inside the existing SecurityConfig.chain(...) — adds ONE requestMatchers row
http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
                "/login",
                "/actuator/health",
                "/actuator/health/**",
                "/v3/api-docs/**",
                "/v3/api-docs/admin",        // new — admin OpenAPI group
                "/swagger-ui/**",
                "/login/oauth2/**",
                "/oauth2/**")
            .permitAll()
        .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")  // ← only v1.2 addition
        .anyRequest().authenticated());
```

**Two complementary enforcement layers (defense in depth):**

| Layer | Where | What it catches |
|---|---|---|
| URL-pattern `requestMatchers("/api/admin/**").hasRole("ADMIN")` | `SecurityConfig.chain(...)` | Any HTTP request to admin paths bypassing the controller (filter chain runs before dispatch). Fail-fast 403 at the filter. |
| Method `@PreAuthorize("hasRole('ADMIN')")` on every admin controller / service method | `controllers/admin/**` + `application/admin/**` | Programmatic calls (Modulith events, scheduled jobs, tests) that try to invoke admin operations without going through `/api/admin/**`. Also makes intent explicit at the call site. |

**One new annotation on the existing class — no new dependency:**

```java
@Configuration
@EnableMethodSecurity   // ← add this for @PreAuthorize/@PostAuthorize support
@Profile("!test")
public class SecurityConfig { ... }
```

`@EnableMethodSecurity` lives in `org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity` — already on the classpath via `spring-boot-starter-security` (no Gradle change). Spring Security 7 keeps the same name and import path; verified Context7 `/spring-projects/spring-security`. **HIGH**.

### How `ROLE_ADMIN` gets attached to the existing cookie session

**Decision: admin elevation is a DB row, not a Google claim.** This keeps the bundled OAuth flow untouched (memory note "Bundle OAuth scopes (inbox-zero pattern)") and avoids granting Google control over our authorization model.

**Implementation outline (no new dep):**

1. Add a `is_admin BOOLEAN NOT NULL DEFAULT false` column to the existing `user` table via a new Liquibase YAML changelog.
2. In the existing `GoogleOAuthSuccessHandler` (or the corresponding `OAuth2UserService` if one is added), after provisioning, look up `user.is_admin` and append `new SimpleGrantedAuthority("ROLE_ADMIN")` to the principal's authorities **alongside** the existing `ROLE_USER` (or equivalent).
3. Spring Session Redis serializes the augmented principal automatically — the cookie session already carries arbitrary `GrantedAuthority` lists.
4. Admin elevation/demotion lives in a Liquibase seed script (initial admin) + an admin-only API endpoint guarded by `@PreAuthorize("hasRole('ADMIN')")` (existing admins promote new ones).

**Why not Google Workspace admin claims:** the SaaS targets prosumer Gmail users, not Workspace tenants — Google's `hd` (hosted domain) claim is not a reliable signal. DB-backed elevation is faithful to the multi-tenant + BYOK model.

**Why not Keycloak / Auth0:** would force a second IdP for **two** roles (`USER`, `ADMIN`); adds an entire deployment unit + cost; user has memory note rejecting "incremental authorization" detours; CLAUDE.md "Stateless JWT user sessions" is in the do-not-use list. Locked: stay on cookie + Spring Session Redis.

### Master-key management for OpenAI/Anthropic/Google/DeepSeek (reuses existing AES-GCM)

**Decision: reuse the AES-GCM app-layer encryptor already shipped for BYOK (LLM-04) — do NOT add a new crypto library.** The threat model and rotation requirements are identical to BYOK refresh tokens.

**Pattern:**

| Concern | v1.0 BYOK pattern (already shipped) | v1.2 master-key extension |
|---|---|---|
| Encryption algorithm | AES-256-GCM, 96-bit IV, 128-bit tag | **Same.** |
| Key Encryption Key (KEK) | `ZeroMailCoreProperties.crypto.byok.kekBase64` (env-injected) | **Add** `ZeroMailCoreProperties.crypto.masterKeys.kekBase64` (env-injected) and `kekVersion` for rotation tracking. |
| Storage | `byok_credential` table — ciphertext + IV + version | **Add** `llm_provider_master_key` table with same column shape: `(id, provider_id, ciphertext, iv, kek_version, status, created_at, rotated_at, last_tested_at, last_test_status)`. |
| Rotation | Re-wrap row with new KEK version | **Same.** Admin UI triggers a rotation command → service decrypts under old KEK → re-encrypts under new KEK → writes `kek_version+1`. Liquibase YAML changelog only bumps `kekVersion` in config; ciphertext rotation is a runtime command, not a migration. |
| Plaintext lifetime | Per-call buffer zeroed in `finally` | **Same.** |
| Logging | Never logged (LLM-04 + `@Sensitive` Logback scrub) | **Same.** |
| Test-connection | N/A (BYOK calls are per-user) | **New.** Admin clicks "Test" → server decrypts master key → builds a Spring AI `ChatModel` with that key → calls with a 1-token prompt → records `last_test_status`. Re-uses the existing `LlmGateway` adapter; no new code outside `core.llm.gateway.springai.admin`. |

**No new crypto library.** Java's built-in `Cipher.getInstance("AES/GCM/NoPadding")` (JDK 25) is what LLM-04 already uses. **HIGH** — verified against the repo's existing `byok_credential` flow.

**Pitfall (explicit):** do **NOT** introduce HashiCorp Vault, AWS KMS, or GCP KMS in v1.2. The single-VPS posture (CLAUDE.md "Distribution (v1)") and "No GCP hosting baseline" rule lock the deployment to one host — adding a managed KMS would (a) require a second deployment surface, (b) add network latency to every LLM call, (c) violate the locked "No GCP starter" rule. App-layer AES-GCM + env-injected KEK is the v1.2 design. Managed KMS is a v2+ migration.

### Sync-from-`/models` per provider (no new SDK)

Each Spring AI provider starter already on the classpath exposes a low-level client that can list models. **The rule "no raw HTTP LLM calls or vendor SDK usage outside the Spring AI adapter" remains in force** — the list-models call **must** live inside `core.llm.gateway.springai.admin`. Where the provider starter does not expose a `listModels()` method directly, a `RestClient` (Spring 7 built-in) call to `<base-url>/v1/models` with the master key is acceptable **only inside the locked adapter package**, guarded by the existing ArchUnit rule. **MEDIUM-HIGH** — confirmed by inspecting Spring AI 2.0.0-M6's `OpenAiApi` exposure in the existing v1.0 LLM gateway; verify the Anthropic/Google starters at implementation time.

| Provider | Endpoint shape | Notes |
|---|---|---|
| OpenAI | `GET /v1/models` returns `{ data: [{ id, owned_by, ... }] }` | Auth: `Authorization: Bearer <key>`. |
| OpenRouter | `GET /v1/models` — OpenAI-compatible | Use existing OpenAI starter pointed at `https://openrouter.ai/api/v1`; same endpoint shape. |
| Anthropic | `GET /v1/models` returns `{ data: [{ id, display_name, ... }] }` | Auth: `x-api-key: <key>` + `anthropic-version: 2023-06-01`. |
| Google GenAI | `GET https://generativelanguage.googleapis.com/v1beta/models?key=<key>` returns `{ models: [{ name, supportedGenerationMethods, ... }] }` | Auth: query param (or `x-goog-api-key` header). Different shape — see below. |
| DeepSeek | `GET /v1/models` — OpenAI-compatible | Use the existing DeepSeek starter (OpenAI-shape adapter). |

**Per-feature capability:** the catalog table `llm_model_feature_capability` records `(provider_id, model_id, feature)` rows where `feature ∈ {CHAT, TRIAGE, DRAFT}`. Sync-from-`/models` **proposes** discovered model IDs; admin **explicitly toggles** which features each model is enabled for. We do not auto-derive feature capability from the `/models` response because: (a) `supportedGenerationMethods` exists only on Google, (b) capability labels like "chat" vs "completion" are noisy across providers, (c) Zero Mail's three feature slots have distinct prompt/budget/safety profiles that providers don't model. Admin curation is the source of truth.

### Admin audit table (new Liquibase changelog)

```yaml
# Liquibase YAML — illustrative shape, not literal
- changeSet:
    id: 20260520-01-create-admin-audit-event
    changes:
      - createTable:
          tableName: admin_audit_event
          columns:
            - { name: id, type: BIGINT, autoIncrement: true, constraints: { primaryKey: true } }
            - { name: actor_user_id, type: BIGINT, constraints: { nullable: false } }
            - { name: action,        type: VARCHAR(64), constraints: { nullable: false } }  # e.g., CATALOG_MODEL_ENABLED, MASTER_KEY_ROTATED, TENANT_PAUSED
            - { name: target_kind,   type: VARCHAR(64) }                                    # e.g., PROVIDER, MODEL, TENANT
            - { name: target_id,     type: VARCHAR(128) }
            - { name: payload_jsonb, type: JSONB }                                          # diff before/after, NEVER email content
            - { name: created_at,    type: TIMESTAMPTZ, defaultValueComputed: NOW(), constraints: { nullable: false } }
            - { name: ip_address,    type: VARCHAR(64) }
            - { name: user_agent,    type: VARCHAR(512) }
```

**Distinct from TRG-05** (triage audit) — TRG-05 records what the rules engine did to user mail; `admin_audit_event` records what operators did to configuration. They live in separate tables, separate retention windows, separate read paths.

### Worker queue health (read-only views)

No new lib. The existing `outbox` + `processing_job` Postgres tables already carry everything the admin panel needs:

| Read | Source |
|---|---|
| Backlog (pending count) | `SELECT count(*) FROM outbox WHERE status = 'PENDING'` |
| Failed (retryable) | `SELECT count(*) FROM processing_job WHERE status = 'FAILED' AND retry_count < max_retries` |
| Failed (dead-lettered) | `SELECT count(*) FROM processing_job WHERE status = 'DEAD'` |
| Oldest pending lag | `SELECT now() - min(created_at) FROM outbox WHERE status = 'PENDING'` |
| Per-job-type throughput | `SELECT job_type, count(*) FROM processing_job WHERE status = 'DONE' AND completed_at > now() - interval '1 hour' GROUP BY job_type` |

All read via Spring Data JDBC projections (CONVENTIONS.md `projection/`). The Postgres MCP tools listed in CLAUDE.md ("Tooling" → Postgres MCP Pro) are for operator inspection of the same data, not for the runtime admin UI.

---

## What v1.2 Changes — Backend OpenAPI Segregation

**Problem.** All admin endpoints live under `/api/admin/**`. We do **not** want admin schemas to appear in the public OpenAPI document the frontend ships to every browser (information leakage about operator-only operations), and we **do** want a separate typed client for the admin UI so the public-facing client doesn't bloat with admin types.

**Solution.** `springdoc-openapi` 3.0.3 (already installed) ships `GroupedOpenApi` — a built-in mechanism for splitting one Spring app's endpoints into multiple OpenAPI documents. **Verified via Context7 `/springdoc/springdoc-openapi`** (snippet retrieved 2026-05-19):

```java
// Add to the existing OpenApiConfig — does NOT replace the existing customizers,
// it adds two new beans alongside them.
@Bean
GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
            .group("public")
            .displayName("Zero Mail Public API")
            .pathsToMatch("/api/**")
            .pathsToExclude("/api/admin/**")
            .build();
}

@Bean
GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("admin")
            .displayName("Zero Mail Admin API")
            .pathsToMatch("/api/admin/**")
            .addOperationCustomizer((operation, handlerMethod) -> {
                operation.addTagsItem("admin");
                return operation;
            })
            .build();
}
```

**Generated artifact paths (springdoc 3.0.3 convention):**

| URL | Content |
|---|---|
| `GET /v3/api-docs/public` | Public API spec (excludes `/api/admin/**`) — replaces the current default at `/v3/api-docs` for frontend codegen. |
| `GET /v3/api-docs/admin` | Admin API spec (`/api/admin/**` only) — used by the admin UI's separate typed client. |
| `GET /v3/api-docs` | Default merged spec (kept for compatibility; **not** consumed by frontend codegen). |
| `GET /swagger-ui/index.html` | Swagger UI with group selector top-right (public/admin). |

**Existing `OpenApiConfig.apiErrorCustomizer()` continues to apply:** the file explicitly uses `GlobalOpenApiCustomizer` precisely because of the doc-comment warning *"future grouping via `GroupedOpenApi` would silently bypass plain `OpenApiCustomizer` beans on the grouped paths."* v1.2 is the future this was anticipating. **HIGH** — confirmed by reading the existing `OpenApiConfig.java`.

**Security note.** The admin spec URL must be permit-listed in `SecurityConfig` (`/v3/api-docs/admin`) so the **admin user** can fetch it for codegen — but the admin UI itself **already requires `ROLE_ADMIN`**, so the spec's existence is not a real leak even if served to anonymous users. For belt-and-braces, gate `/v3/api-docs/admin` behind `hasRole("ADMIN")` instead of `permitAll()` and run admin codegen from an authenticated admin browser session or a build-time CI secret.

---

## What v1.2 Changes — Frontend Codegen Pipeline

**Two valid approaches; pick one.**

### Option A (recommended): Two schema files, one for each OpenAPI group

```typescript
// apps/web/lib/api/schema.d.ts          ← regenerated from /v3/api-docs/public
// apps/web/lib/api/admin-schema.d.ts    ← NEW, regenerated from /v3/api-docs/admin
```

**Why.** Two typed clients with **non-overlapping types** prevents the admin DTOs from being typo-imported into the public app bundle. The public bundle size stays the same; admin bundle only ships when the admin code-splits.

**Change to `apps/web/scripts/generate-api.ts`.** Today the script fetches **one** spec URL and emits **one** `.d.ts` file. v1.2 changes it to a loop over a two-entry config:

```typescript
const SPECS = [
  { spec: process.env.API_SPEC_URL ?? 'http://localhost:8080/v3/api-docs/public', out: 'lib/api/schema.d.ts' },
  { spec: process.env.ADMIN_SPEC_URL ?? 'http://localhost:8080/v3/api-docs/admin', out: 'lib/api/admin-schema.d.ts' },
];
```

**Companion to `apps/web/lib/api/client.ts`.** Add an `adminClient` alongside the existing typed client:

```typescript
// apps/web/lib/api/admin-client.ts (NEW)
import createClient from 'openapi-fetch';
import type { paths } from './admin-schema';
import { getBaseUrl } from './base-url';

export const adminClient = createClient<paths>({ baseUrl: getBaseUrl(), credentials: 'include' });
```

**No new npm package.** `openapi-typescript` 7.13.0 and `openapi-fetch` 0.17.0 — already installed — handle both files identically.

### Option B (rejected): One merged spec, manual `if (path.startsWith('/api/admin'))` segregation

Bloats the public bundle with admin types, allows accidental cross-imports, and provides no real benefit. Skip.

---

## What v1.2 Adds — Frontend (Zero New Runtime Deps)

**Verified `apps/web/components/ui/**` on 2026-05-19** — admin-relevant primitives **already present**:

| Admin UI need | Existing primitive | Source |
|---|---|---|
| Catalog table (models × features) | `table.tsx` | shadcn already installed |
| Settings tabs (4 tabs in Phase 9) | `tabs.tsx` | shadcn already installed |
| Master-key rotation confirm | `alert-dialog.tsx` | shadcn already installed |
| Provider/model picker dropdown | `select.tsx` + `command.tsx` + `popover.tsx` | shadcn already installed |
| Admin sidebar nav | `sidebar.tsx` | shadcn already installed |
| Tenant detail "view-only" card grid | `card.tsx` + `badge.tsx` + `separator.tsx` | shadcn already installed |
| Queue health charts | `chart.tsx` (Recharts wrapper) + `recharts@3.8.1` | already installed |
| Toggle on/off (catalog model enabled per feature) | `switch.tsx` + `checkbox.tsx` | shadcn already installed |
| Loading states | `skeleton.tsx` + `spinner.tsx` | shadcn already installed |
| Admin action toasts | `sonner.tsx` (already wired via `sonner@^2.0.7`) | shadcn already installed |
| Read-only key reveal | `input.tsx` + `button.tsx` with `eye` icon (`lucide-react` already installed) | already installed |
| Filterable search (e.g., tenants list) | `input.tsx` + `command.tsx` | shadcn already installed |
| Pagination | Compose from `button.tsx` + `select.tsx`; **shadcn does not ship a `pagination` primitive** — hand-compose | already installed |
| Long lists scroll container | `scroll-area.tsx` | shadcn already installed |
| Side-panel for tenant detail drawer | `sheet.tsx` | shadcn already installed |

**Net new shadcn primitives required: zero.**

**Optional (not required for Phase 8 functional scope):**

| Optional primitive | When to install | Cost |
|---|---|---|
| `pagination` block (community shadcn-style) | If the tenants table grows beyond ~50 rows and hand-composed pagination feels too custom | `pnpm dlx shadcn@latest add pagination` — single-file primitive. |
| `data-table` block (community block, depends on `@tanstack/react-table`) | If catalog/tenants tables need sorting + filtering + virtualization. **`@tanstack/react-table` is NOT in `apps/web/package.json` today.** | New runtime dep: `@tanstack/react-table` (~14 KB gz). **Defer until UI feedback shows hand-composed table is insufficient.** |

**Recommendation: ship Phase 8 with hand-composed tables on the existing `table.tsx` primitive.** Memory note "Use raw shadcn primitives first" applies — wait for the rule-of-three before installing `@tanstack/react-table`.

---

## What v1.2 Adds — Backend Persistence (New Liquibase Changelogs Only)

Six new Liquibase YAML changelogs. **No new database library.**

| Table | Owner module | Purpose |
|---|---|---|
| `user.is_admin` (column add) | `backend/core` (`auth` package, existing) | DB-backed admin elevation bit on the existing `user` aggregate. |
| `admin_audit_event` | `backend/core` (new `admin` package) | Append-only operator action log. **Never** stores email content; payload diff only. |
| `llm_provider_catalog` | `backend/core` (existing `llm` package) | One row per provider (OPENAI, ANTHROPIC, GOOGLE, DEEPSEEK, OPENROUTER). `(id, code, display_name, base_url, status, created_at, updated_at)`. |
| `llm_provider_model` | `backend/core` (existing `llm` package) | One row per discovered model. `(id, provider_id, model_id, display_name, status, discovered_at, last_synced_at)`. `status ∈ {DISCOVERED, ENABLED, DISABLED, DEPRECATED}`. |
| `llm_model_feature_capability` | `backend/core` (existing `llm` package) | Many-to-many between models and feature slots. `(provider_id, model_id, feature, enabled, default_for_feature)`. `feature ∈ {CHAT, TRIAGE, DRAFT}`. The "is this model offered for chat?" question is settled here, not in code. |
| `llm_provider_master_key` | `backend/core` (existing `llm` package) | One row per provider's server-managed master key. `(id, provider_id, ciphertext_b64, iv_b64, kek_version, status, last_test_status, last_tested_at, rotated_at)`. `status ∈ {ACTIVE, ROTATING, REVOKED}`. |

**Privacy & sensitivity:** `llm_provider_master_key.ciphertext_b64` is `@Sensitive` (Logback scrub). `admin_audit_event.payload_jsonb` MUST NOT include decrypted key bytes — only metadata (key id, kek version transitions, test result codes). Existing `@Sensitive` ArchUnit rule (FND-04) covers logging; payload sanitation is a code-review checklist item plus a unit test that asserts no field named `*Plaintext` / `*Decrypted` is ever written into the JSONB column.

---

## Version Compatibility Matrix (v1.2 Delta)

| Component | Version | Compatible with | Verified via |
|---|---|---|---|
| Spring Security 7.0.5 `authorizeHttpRequests().requestMatchers().hasRole()` | already on classpath (Spring Boot 4.0.6 transitive) | Cookie session, OAuth2 client login | Spring Security 7 reference + existing `SecurityConfig.java` |
| Spring Security 7.0.5 `@EnableMethodSecurity` + `@PreAuthorize` | already on classpath | `prePostEnabled=true` is the default in `@EnableMethodSecurity` | Spring Security 7 reference |
| springdoc-openapi 3.0.3 `GroupedOpenApi` | already on classpath | Spring Boot 4.0.6 (springdoc 3.x targets Boot 4.x; v2.8.x targets Boot 3.5.x) | Context7 `/springdoc/springdoc-openapi` + `gradle/libs.versions.toml` |
| AES-GCM via JDK `Cipher` | JDK 25 (already in toolchain) | Reuses existing `core.crypto.AesGcmEncryptor` pattern from LLM-04 | Existing repo |
| Spring Data JPA / JDBC | already on classpath | Existing `projection/` + `persistence/` packages handle read-side and aggregates | Existing repo |
| Liquibase 5.0.2 | already on classpath | YAML changelogs only, per CLAUDE.md constraint | Existing repo |
| `openapi-typescript` 7.13.0 | already in `apps/web/devDependencies` | Multiple specs handled by running the CLI twice; no version bump needed | `apps/web/package.json` |
| `openapi-fetch` 0.17.0 | already in `apps/web/dependencies` | Two `createClient<paths>(...)` instances (public + admin) — no version bump needed | `apps/web/package.json` |
| All shadcn primitives listed above | already in `apps/web/components/ui/**` | React 19.2.6 + Tailwind 4 + Base UI / Radix dependencies already present | Directory listing 2026-05-19 |

---

## What NOT to Use in v1.2

| Avoid | Why | Use Instead |
|---|---|---|
| **Keycloak / Auth0 / Ory / FusionAuth for admin RBAC** | Adds a full second IdP for **two** roles. Memory note rejects multi-IdP detours; CLAUDE.md locks cookie+Redis session. Operational cost (separate deploy, separate failure mode, separate cert) far exceeds the one-column-plus-one-annotation alternative. | DB-backed `user.is_admin` + `SimpleGrantedAuthority("ROLE_ADMIN")` appended in the existing `GoogleOAuthSuccessHandler`. |
| **Stateless JWT for admin sessions** | Already in CLAUDE.md "do not use" list ("Stateless JWT user sessions"). Admin uses **the same cookie** as regular users — the difference is the authority list inside the session, not the session medium. | Existing Spring Session Redis cookie. |
| **Separate admin subdomain (`admin.zero.mail`)** | Forces a second OAuth client, a second CORS origin, and breaks `SameSite=Lax` cookie sharing. Adds complexity for no security gain vs. path-based `/admin/**` + `ROLE_ADMIN`. | Path-prefix `/admin/**` on the same origin, layered RBAC. (If isolation later proves valuable, revisit in v2 with a second cookie scope.) |
| **HashiCorp Vault / AWS KMS / GCP KMS for master keys** | Locked: single-VPS posture; "No GCP hosting baseline"; CLAUDE.md `pgp_sym_encrypt` rejection already enforces app-layer encryption. Adds network latency to every LLM call. | Reuse existing AES-GCM app-layer pattern (LLM-04); KEK in env, rotation via re-wrap command. |
| **`pgp_sym_encrypt` (pgcrypto) for master keys** | Same reason BYOK doesn't use it — key in DB → key leak on DB leak. Already on CLAUDE.md "do not use" list. | AES-GCM app-layer with env-injected KEK. |
| **Raw OpenAI/Anthropic/Google Java SDKs for sync-from-`/models`** | CLAUDE.md "Raw HTTP LLM calls or vendor SDK usage outside the Spring AI adapter" — locked. | Spring AI provider starter clients (already on classpath) or `RestClient` calls **inside** `core.llm.gateway.springai.admin`, gated by the existing ArchUnit confinement rule. |
| **Spring `RestTemplate`** for `/models` HTTP calls | Spring Framework 7 deprecates `RestTemplate` in favor of `RestClient`. | `RestClient.create().get().uri(...).retrieve()` — already in Spring Framework 7. |
| **`@tanstack/react-table` for admin tables in Phase 8** | Yet-another runtime dep for tables that may stay small for the foreseeable future. Memory note "Use raw shadcn primitives first" + "Skip de-risking spikes" — ship hand-composed first. | Hand-compose pagination/sort on existing `table.tsx` + `select.tsx`. Install `@tanstack/react-table` later if rule-of-three triggers it. |
| **A second OAuth client (`google-admin`)** | Same pattern Phase 1.4 already rejected for Gmail scope splitting. Memory note "Bundle OAuth scopes". | One OAuth client, role appended in the success handler. |
| **A second Spring Boot module (`backend/admin`)** | CLAUDE.md backend topology is **locked** to `backend/core + backend/api + backend/worker`. Adding a fourth module ("admin") is out of scope; admin controllers live in `backend/api` under `controllers/admin/`, admin use-case services in `backend/core` under `application/admin/`. | Package-based separation per CONVENTIONS.md `domain/`, `application/`, `projection/`. |
| **Persisting LLM prompts/completions for admin "debug" features** | Privacy carve-out applies to user chat configuration text only — admin debugging of LLM exchanges does **not** unlock body persistence. | Use Micrometer + OTel metadata (model, tokens, latency, error class). Spring AI prompt/completion capture stays **disabled** (LLM-09). |
| **Storing the actual decrypted key in the admin UI even momentarily** | Server-side decrypt → display once → user copies is the prevailing pattern. Storing in client memory beyond one render risks DOM/devtools leak. | Test-connection runs **server-side** (admin clicks "Test" → server uses decrypted key → returns OK/FAIL). The plaintext key never leaves the server. |
| **OpenAPI generator (CodeGen, swagger-codegen) for the admin client** | `openapi-typescript` + `openapi-fetch` is the locked frontend pattern (CONVENTIONS.md #8). Switching generators per surface area would fragment the client model. | Reuse `openapi-typescript` 7.13.0 with two specs. |

---

## Stack Patterns by Variant

**If admin endpoint reads tenant data (e.g., "list tenants", "view Gmail connection state"):**
- Controller in `backend/api/controllers/admin/<domain>/` (e.g., `controllers/admin/tenants/`)
- `@PreAuthorize("hasRole('ADMIN')")` on the controller class
- Service in `backend/core/application/admin/<domain>/` returning projections from `projection/`
- Tenant context for the **read** is still important — the admin is reading data **about** a tenant, so the response includes `tenantId` in the audit row, but the request is **not** Scoped-Values-bound to that tenant (admin operates above tenancy). Pattern: log `event=admin_read tenantId=<viewed> actorUserId=<admin>` on every read.

**If admin endpoint mutates global state (e.g., "enable model X for feature CHAT", "rotate master key for provider Y"):**
- Same controller/service location.
- **Write an `admin_audit_event` row in the same transaction** as the state change (`@Transactional` boundary owns both).
- Emit a Spring Modulith event (`AdminCatalogChanged`, `MasterKeyRotated`) for cache invalidation in dependent modules (e.g., `LlmGateway` per-tenant `ChatModel` cache in Redis).

**If admin endpoint mutates tenant-specific state (e.g., "pause tenant", "release ledger hold"):**
- Same controller/service location.
- Audit row includes `target_kind=TENANT`, `target_id=<tenantId>`.
- The mutation **may** need to bind a Scoped Value for the tenant context if downstream services require it; emulate via `ScopedValue.where(TENANT_ID, target).run(() -> service.pauseTenant(target))`. CONVENTIONS.md tenant Scoped Values rule still holds.

**If admin endpoint exposes data NOT to be cached publicly:**
- `Cache-Control: no-store, max-age=0` response header on every admin controller (cross-cutting interceptor or `@RestController` base class).
- Existing privacy logging format (`event=admin_action`) per CONVENTIONS.md #5.

---

## Integration Points (where v1.2 touches v1.0/v1.1)

| Touch point | v1.2 change | Risk |
|---|---|---|
| `SecurityConfig.chain(...)` (existing) | Add one `requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")` row before `.anyRequest().authenticated()`. Add class-level `@EnableMethodSecurity`. | Low — `authorizeHttpRequests` ordering is preserved (specific before generic). Existing E2E tests stay green; new E2E test covers 403 for non-admin on `/api/admin/**`. |
| `GoogleOAuthSuccessHandler` (existing) | Look up `user.is_admin` post-provisioning; append `SimpleGrantedAuthority("ROLE_ADMIN")` to the principal's authorities. | Low — additive; existing tests still pass; new test for admin-authority attachment. |
| `OpenApiConfig` (existing) | Add `publicApi` + `adminApi` `GroupedOpenApi` beans. The existing `GlobalOpenApiCustomizer apiErrorCustomizer` was deliberately authored to survive grouping — verified in its doc comment. | Low — grouping was anticipated when `GlobalOpenApiCustomizer` was chosen. |
| `apps/web/scripts/generate-api.ts` | Loop over two spec URLs/paths, emit two `.d.ts` files. | Low — same CLI under the hood; one extra file. |
| `apps/web/lib/api/` | Add `admin-schema.d.ts` (generated) + `admin-client.ts` (3-line wrapper). | Low — additive; existing public client untouched. |
| `apps/web/components/ui/**` | Zero changes. | None. |
| Liquibase changelogs | Six new YAML files. | Low — standard pattern. |
| `core.llm.gateway.springai.admin` (new package, inside the locked adapter) | New service for list-models + master-key crypto + test-connection. ArchUnit rule confining vendor SDK usage **stays in force** — the new package is still inside `core.llm.gateway.springai.**`. | Low — package addition, not boundary change. |
| `LlmGateway` per-tenant ChatModel cache (Redis, existing) | Add a cache invalidation hook for `AdminCatalogChanged` + `MasterKeyRotated` Modulith events so model swaps take effect within seconds. | Low — Spring Modulith `@ApplicationModuleListener` pattern already in use. |
| `ArchUnit` rules | Add: `admin_audit_event.payload_jsonb` never receives `*Plaintext` / `*Decrypted` field names. Add: admin services never call rules-engine write paths (admin is read-only on tenant mail). | Low — ArchUnit is the existing enforcement layer for the same class of invariants. |
| Logback `@Sensitive` scrub (existing) | `LlmProviderMasterKey.ciphertext` is `@Sensitive`. New entity, same annotation. | Low — additive. |
| Micrometer + OTel agent 2.16 (existing) | New counters: `zero_mail_admin_action_total{action,actor_id}`, `zero_mail_master_key_test_total{provider,result}`, `zero_mail_catalog_sync_total{provider,result}`. | Low — additive labels. |

---

## Sources

**Context7 (HIGH confidence):**
- `/springdoc/springdoc-openapi` — `GroupedOpenApi` builder with `pathsToMatch`/`pathsToExclude`/`addOperationCustomizer`/`displayName`; multi-group split for public+admin APIs. Fetched 2026-05-19.

**Spring Security 7 official reference (HIGH confidence):**
- `https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html` — `authorizeHttpRequests().requestMatchers("/admin/**").hasRole("ADMIN")` is the current canonical pattern in 7.0.x; no breaking change from 6 to 7 for this API; deferred Authentication lookup is the 7.x improvement. Fetched 2026-05-19.

**Spring Framework 7 reference (already on classpath via Boot 4.0.6):**
- `RestClient` (replaces deprecated `RestTemplate`) — used for `/models` calls inside the locked LLM adapter package when Spring AI starter does not expose a `listModels()` directly.

**npm registry / existing `apps/web/package.json` (HIGH confidence):**
- `openapi-typescript@7.13.0` and `openapi-fetch@0.17.0` already installed; both transparently support multi-spec workflows via repeated invocations.
- All listed shadcn primitives (`table`, `tabs`, `dialog`, `alert-dialog`, `select`, `command`, `popover`, `sidebar`, `sheet`, `chart`, etc.) are present in `apps/web/components/ui/**` on the working tree at 2026-05-19.

**Existing repo (HIGH confidence — single source of truth for v1.0/v1.1 baseline):**
- `gradle/libs.versions.toml` — `springdoc = "3.0.3"`, Spring Boot 4.0.6, Spring AI 2.0.0-M6.
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — current `authorizeHttpRequests` chain; cookie session via `oauth2Login`; CSRF SPA mode; `@Order(3)` non-test profile.
- `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` — explicit use of `GlobalOpenApiCustomizer` to survive future `GroupedOpenApi` grouping (the doc comment in this file calls out v1.2 directly).
- `apps/web/scripts/generate-api.ts` — current single-spec codegen pipeline; single-file extension is mechanically straightforward.
- CLAUDE.md "do not use" list — JWT, Lombok, WebFlux, GCP starter, raw vendor SDKs, Kafka/RabbitMQ, pgcrypto for keys, vector DB.
- Memory notes — bundled OAuth scopes, no parallel admin IdP detour, raw shadcn first, skip de-risking spikes, coherent milestone over interim.

---

*Stack research for: Zero Mail v1.2 — admin console foundation + Settings UI on curated catalog*
*Researched: 2026-05-19 by gsd-researcher (Context7 `/springdoc/springdoc-openapi` + Spring Security 7 reference + existing repo state)*

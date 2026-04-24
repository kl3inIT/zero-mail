# Phase 1: Foundation & Safety Infrastructure - Context

**Gathered:** 2026-04-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 1 ships the tenant-isolation and log-scrubbing infrastructure that every later phase builds on, wires Google OAuth (two-step: sign-in → Gmail connect), publishes the skeleton OpenAPI contract consumed by `apps/web`, persists enough user/onboarding state for Phase 3 to bootstrap from, and kicks off CASA restricted-scope verification with the external lab.

**In scope:** Gradle/Modulith/module scaffolding (`backend/core` + `backend/api` + `backend/worker` shell + `apps/web` types & minimal UI), Scoped-Value tenant context + Hibernate DISCRIMINATOR multi-tenancy, Google OAuth (two-step scope flow), Spring Session + Redis session store, AES-GCM OAuth-refresh-token encryption (global key, version-aware envelope), Liquibase YAML baseline schema for `users` / `tenants` / `gmail_connections` / `onboarding_selections` / token encryption tables, JHipster-style structured Logback + AOP logging + `@Sensitive` + ArchUnit safety contract + thin scrub filter, skeleton OpenAPI (Phase 1 endpoints only), `apps/web` routes `/login` `/onboarding` `/settings`, CASA submission.

**Out of scope (enforced):** Gmail API calls other than OAuth scope grant (MAIL-01..06 are Phase 2A), any LLM wiring (LLM-01..11 are Phase 2C), billing ledger (BILL-01..07 are Phase 2B), rule compilation/evaluation (RULE-01..07 are Phase 3), triage orchestrator (TRG-01..08 are Phase 4), analytics/drafts/full web UI (Phase 5), full regex-based log pattern scanning (deferred to a later observability/security phase), key rotation job (schema ready, job deferred), proactive revocation probes (Phase 2A extends the `users.watch` renewal job).

</domain>

<decisions>
## Implementation Decisions

### A. Module Scaffolding & Build Setup
- **D-A1:** Scaffold three backend modules (`backend/core`, `backend/api`, `backend/worker`) plus `apps/web`. `backend/core` and `backend/api` are actively developed in Phase 1. `backend/worker` ships as a runnable Spring Boot shell with only a healthcheck scheduled task (no domain work) so Phase 2A can add real jobs without module-creation churn. `apps/web` ships the generated OpenAPI types plus a minimal UI (see D-F2).
- **D-A2:** Gradle build logic lives in `buildSrc/` as convention plugins (`zeromail.java-conventions`, `zeromail.spring-boot-conventions`, `zeromail.archunit-conventions`, `zeromail.modulith-conventions`). Each backend module applies only the plugins it needs. Shared Java 25 toolchain, Jackson 3, test setup, and ArchUnit wiring are declared once.
- **D-A3:** Spring Modulith is wired in Phase 1. Initial bounded-context packages inside `backend/core`: `tenant` (multi-tenancy primitives, Scoped-Value binding), `auth` (OAuth + session), `privacy` (log-scrub contract, `@Sensitive`). Each gets a `package-info.java` with `@ApplicationModule`. An `ApplicationModulesTest` runs `ApplicationModules.of(Application.class).verify()` in CI from day one.

### B. Tenant-Context Plumbing
- **D-B1:** Tenant-id Scoped Value is bound by a custom `OncePerRequestFilter` placed **after** Spring Security's authentication filter in the filter chain. The filter reads `tenantId` from the authenticated principal (populated by Spring Security from the session cookie via Spring Session) and wraps the remaining chain in `ScopedValue.where(TenantContext.TENANT, tenantId).run(...)`. No DB lookup occurs in the filter — the tenant id is already carried by the session principal, which was resolved at login and stored in Redis-backed Spring Session.
- **D-B2:** Hibernate multi-tenancy uses **DISCRIMINATOR** mode. Every tenant-owned entity declares a `tenant_id` column annotated with `@TenantId`. A custom `CurrentTenantIdentifierResolver` reads `TenantContext.TENANT` from the Scoped Value. An ArchUnit rule bans native SQL / `EntityManager.createNativeQuery` use outside a small allow-listed infrastructure package to prevent tenant-filter bypass.
- **D-B3:** FND-05 cross-tenant leak test covers **both** request-path concurrency and virtual-thread fan-out. A `TenantAwareTaskScope` (thin wrapper around `StructuredTaskScope` that re-binds `TenantContext.TENANT` on subtask start) ships in Phase 1 as the blessed primitive for any later phase that fans out work under a request. Tests: (a) N concurrent virtual-thread requests (N ≥ 100) with distinct tenants assert no cross-read; (b) a request bound to tenant A fans out 10 subtasks via `TenantAwareTaskScope` and every subtask observes tenant A; (c) forgetting to use the helper (raw `Thread.ofVirtual().start(...)`) is detected by an ArchUnit rule forbidding direct `Thread.ofVirtual()` / `CompletableFuture.supplyAsync` in application code (allow-listed to the scope helper).

### C. OAuth Scope Progression & Disconnection
- **D-C1:** Two-step incremental consent. First login requests only `openid profile email` — user lands in the app without granting Gmail access. A separate "Connect Gmail" action (explicit user click, shown in `/onboarding` and `/settings`) triggers a second OAuth authorization round that adds `https://www.googleapis.com/auth/gmail.modify`. Rationale: best CASA consent-minimization narrative, cleanest reconnect flow when grant is revoked, and lets users exist in the product without committing Gmail access upfront.
- **D-C2:** DISCONNECTED detection is **lazy in Phase 1**. Every outbound Google API call (in Phase 1: only the token-refresh helper and `GET /userinfo` used during onboarding) is wrapped so `invalid_grant` / revocation errors flip the tenant's `gmail_connection.status` to `DISCONNECTED` and emit a domain event. UI polls `GET /tenant/status` and surfaces the reconnect prompt when status is not `CONNECTED`. Phase 2A extends this by adding a proactive probe inside the already-scheduled `users.watch` renewal job — that's where active detection belongs, not in Phase 1.

### D. Guided Onboarding (AUTH-06)
- **D-D1:** Phase 1 onboarding ships a **real persistence path** for the template-rule step. Hardcoded UI shows 3–4 template cards (initial set: "Archive receipts", "Label newsletters", "Keep calendar invites at top"). "Enable" writes a row to `onboarding_selections (id, tenant_id, template_key, enabled, created_at)`. Phase 3's rules engine reads this table on first visit to the Rules page and materializes each selection into a real `Rule` row (compiled via the Phase 2C `LlmGateway`). This satisfies AUTH-06 literally ("through the template-rule step") and gives Phase 3 a bootstrap dataset rather than re-asking.
- **D-D2:** Explicit onboarding state machine: `users.onboarding_step` column of type `varchar` backed by a Java enum `{SIGNED_IN, GMAIL_CONNECTED, TEMPLATE_SELECTED, COMPLETE}`. Forward-only transitions. The `/onboarding` page dispatches on this value. Completion is recorded server-side, not client-inferred.

### E. Log Safety Contract
- **D-E1:** Baseline + minimal safety contract. Phase 1 ships:
  1. **JHipster-style baseline:** Spring AOP logging aspect for service/debug tracing, `logstash-logback-encoder`-based structured JSON Logback layout, stdout output shaped for Grafana Loki / ELK ingestion. Convention: no raw email bodies, LLM prompts, LLM completions, tokens, or OAuth secrets are ever passed to a logger.
  2. **Structural safety contract (non-negotiable, small surface):** `@Sensitive` wrapper type `Sensitive<T>` with a `toString()` that returns the literal string `"***REDACTED***"`. Fields/parameters carrying body/prompt/completion/token content must be typed as `Sensitive<String>` — enforced by an ArchUnit rule that fails the build if any `String`-typed field/parameter named in a small deny-list (`body`, `bodyText`, `prompt`, `completion`, `rawContent`, `refreshToken`, `accessToken`) escapes a `Sensitive<>` wrapper anywhere in `backend/core` + `backend/api` + `backend/worker`. A second ArchUnit rule fails any log statement (`Logger.info/debug/warn/error/trace`) whose argument list contains a reference to a `Sensitive`-typed field (i.e., the developer tried to log it directly instead of going through a redacting formatter).
  3. **Thin Logback scrub filter:** A `TurboFilter` that scans formatted log messages for the literal token `Sensitive(` (indicating a raw `Sensitive` instance slipped through without the `.toString()` redaction) and redacts it. No broad regex pattern-scan yet.
- **D-E2:** Thin filter behavior on match: **redact + structured marker.** The matching substring is replaced with `[REDACTED]`, the log line is kept, and structured fields `scrubbed=true` + `scrub_reason=sensitive_marker` are added to the JSON event. An alerting rule (deferred to observability phase) can watch for `scrubbed=true` count > 0 — any non-zero value is a bug, not normal operation.
- **D-E3 (deferred):** Full regex pattern-scan (email shapes, long base64 blobs, known prompt markers) is explicitly deferred to a later observability/security phase. That phase can add it as a second `TurboFilter` without touching the `@Sensitive` / ArchUnit contract.

### F. OpenAPI Skeleton & Web UI Depth
- **D-F1:** The Phase 1 OpenAPI spec (served by springdoc-openapi from `backend/api`) contains **only endpoints Phase 1 actually implements** — no 501 stubs for future phases. Initial surface: `POST /auth/google/callback`, `GET /me`, `POST /tenant/connect-gmail` (starts the second OAuth leg), `GET /auth/gmail/callback`, `POST /tenant/disconnect`, `DELETE /me/account`, `GET /tenant/status`, `POST /onboarding/select-template`, `POST /onboarding/complete`. Spec is versioned via `info.version` (Phase 1 = `0.1.0`); each subsequent phase bumps the minor version and the frontend regenerates types — cheap with `openapi-typescript`.
- **D-F2:** `apps/web` ships a real minimum UI in Phase 1:
  - Next.js 16.2.4 + React 19.2.5 scaffold
  - Tailwind CSS 4 + shadcn/ui init (2–4 components needed for these routes — Button, Card, Alert, Input)
  - TanStack Query 5 provider
  - `openapi-typescript` 7 + `openapi-fetch` 0.17 generated client, regenerated from `backend/api`'s OpenAPI doc as a CI step
  - Auth middleware (`middleware.ts`) that redirects unauthenticated requests to `/login`
  - Three routes:
    - `/login` — Google OAuth kickoff (redirects to backend `/oauth2/authorization/google`)
    - `/onboarding` — step-driven UI dispatching on `users.onboarding_step` (Connect Gmail → Select Templates → Done)
    - `/settings` — connection status, Connect / Disconnect Gmail, Delete account, **in-product privacy page section** (no stored bodies, no auto-send, BYOK option planned — satisfies WEB-03 in spirit for Phase 1)

### G. OAuth Refresh-Token Encryption
- **D-G1:** Single global AES-GCM-256 key pulled from GCP Secret Manager (via `spring-cloud-gcp-starter-secretmanager`) at application boot and held in memory for the process lifetime. Encrypt/decrypt happens entirely in-process (no per-call KMS round-trip). Trade-off accepted: a single leaked key exposes every tenant's refresh token; rotation is a batch re-encryption migration rather than per-tenant.
- **D-G2:** Ciphertext storage uses a forward-compatible envelope schema from day one. The `gmail_connections.refresh_token_encrypted` column stores bytes laid out as `[key_version:int32][nonce:12 bytes][ciphertext:variable]`. Phase 1 only ever writes `key_version = 1`. When rotation is needed later, key v2 is added to Secret Manager, new writes use v2, and a background re-encryption job migrates v1 rows — no schema change required.

### Claude's Discretion
These were not explicitly discussed — planner/executor have flexibility within CLAUDE.md constraints:
- Exact package naming inside `backend/core` (subject to Modulith module rules above)
- Liquibase changelog file naming convention and numbering scheme
- ArchUnit rule organization (one test class vs. split by concern)
- Redis session key prefix and TTL specifics (should default to Spring Session defaults)
- CSRF token storage mechanism (Spring Security 7's cookie-based default is fine)
- Test framework details (JUnit 5 + Testcontainers for Postgres/Redis integration)
- Docker image layering beyond what Spring Boot's CDS+AOT layered image produces by default

### Folded Todos
_No todos folded — none matched Phase 1 scope._

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `CLAUDE.md` — Full prescriptive stack, locked versions, anti-patterns to avoid, virtual-thread / Scoped-Value direction. **MUST read.**
- `.planning/PROJECT.md` — Product vision, core value ("AI auto-triage users trust"), privacy constraints, write-action allow-list.
- `.planning/REQUIREMENTS.md` — v1 requirement catalog. Phase 1 is mapped to `FND-01..07` and `AUTH-01..06`.
- `.planning/ROADMAP.md` — Phase 1 goal statement, success criteria, dependency ordering.

### External specs / standards (no local copy; paths are authoritative URLs agents should re-fetch via Context7 if details matter)
- Spring Boot 4.0.6 release notes and system requirements — Java 17–26, Jakarta-only, Jackson 3.
- Spring Security 7.0.5 OAuth2 Client + CSRF + Spring Session integration.
- Spring Modulith reference guide — `@ApplicationModule`, `ApplicationModules.verify()`.
- Spring AI 2.0.0-M4 release notes (Phase 1 does not invoke Spring AI, but `backend/core` should leave the module seam clean for Phase 2C).
- Hibernate 7.x multi-tenancy via `@TenantId` (DISCRIMINATOR mode) + `CurrentTenantIdentifierResolver`.
- Liquibase 5.0.2 YAML changelog format.
- Google OAuth 2.0 — incremental authorization, revocation detection (`invalid_grant`), Gmail API scopes.
- Google CASA restricted-scope verification workflow — submission payload requirements, Tier assignment.
- JEP 493 Scoped Values (Java 25) — binding semantics, structured concurrency interaction.
- OWASP ASVS 4.0 — session management, log injection, sensitive data handling chapters (input into the Phase 1 log-safety + session design; also inputs into the CASA submission).

### Local references (to be read during research/planning)
- `.planning/research/` — any prior research output from project-setup time.

_No external spec/ADR files exist in-repo yet — the repo is greenfield. If planner/researcher discover relevant specs during research, they should be added here before plan finalization._

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
_Greenfield repo — no existing source code. Only `CLAUDE.md` and `.planning/*` artifacts exist. This phase creates the initial Gradle multi-project structure, `buildSrc/` convention plugins, and the first Liquibase changelog._

### Established Patterns
_None in-code yet. This phase **sets** the patterns Phase 2A/2B/2C/3/4/5 inherit:_
- Every tenant-owned entity ends in a `@TenantId`-annotated `tenant_id` column.
- Every cross-thread operation under a request goes through `TenantAwareTaskScope` (ArchUnit-enforced).
- Every body/prompt/completion/token field is `Sensitive<T>`-typed (ArchUnit-enforced).
- Every new Spring Modulith package gets an `@ApplicationModule` `package-info.java` with an explicit dependency list.
- Every Gradle module declares its config via `buildSrc` convention plugins, not inline boilerplate.

### Integration Points
- `backend/api` exposes HTTP + OpenAPI; `apps/web` consumes types from its generated `openapi-fetch` client.
- `backend/core` owns domain types (tenant, user, gmail_connection, onboarding_selection) reused by both `backend/api` and `backend/worker`.
- Spring Session (Redis-backed) is the single source of `tenantId` on the request path — bound at auth time, read by the custom `OncePerRequestFilter` into the Scoped Value.

</code_context>

<specifics>
## Specific Ideas

- **Template card set (Phase 1 onboarding):** Three cards — "Archive receipts automatically", "Label newsletters as Newsletters and skip inbox", "Keep calendar invites and meeting notes on top". These are marketing copy only in Phase 1; Phase 3 compiles them into real rules via the LLM gateway.
- **CASA submission content:** Filed when two-step OAuth is wired and the `/settings` privacy page is live. Submission references the `Sensitive<T>` + ArchUnit contract and the in-product privacy page. Phase 6 closes out Tier verification.
- **User-picked deviation from the recommended option:** On token-encryption key design (G1), the user selected single global key over per-tenant envelope. Accepted; rotation envelope (G2) preserves the upgrade path.
- **User pushback on log scrubbing (E1 first pass):** User initially proposed deferring `@Sensitive` + scrub filter entirely. Workflow flagged the conflict with FND-03/FND-04 and the project's trust-is-the-product positioning. Resolution (E1-revised): keep the structural safety contract (`@Sensitive` + ArchUnit + thin filter) in Phase 1, defer the runtime regex pattern-scan layer to a later observability/security phase. This is the compromise — do not interpret Phase 1 log work as "just JHipster baseline."

</specifics>

<deferred>
## Deferred Ideas

These surfaced during discussion but belong to other phases:

- **Full runtime regex log pattern-scan** (email-shape detection, base64-blob detection, prompt-marker detection) — defer to a dedicated observability/security phase (likely between Phase 2C and Phase 4, or folded into Phase 6 hardening).
- **Proactive OAuth revocation probe** (daily `users.getProfile` / `tokeninfo` scan across all tenants) — defer to Phase 2A; extends the already-scheduled `users.watch` renewal job.
- **Key rotation job for OAuth token encryption** — schema is ready (key_version envelope); defer implementation until an operational trigger (incident, compliance cadence) requires it.
- **tenantId-bound MDC for log correlation** — not essential for FND-03/FND-04; revisit in the observability/security phase once full scrub layer lands.
- **`@RequireTenant` controller-method aspect** — rejected in favor of filter-based Scoped-Value binding; keep this as a possible secondary safety net if filter coverage ever feels insufficient.
- **Scope down to `gmail.readonly` first, upgrade to `gmail.modify` later** — rejected for Phase 1; `gmail.modify` is needed for label/archive/draft write actions in Phase 4 and incremental scope upgrades past sign-in are enough. Revisit only if CASA verification recommends it.

### Reviewed Todos (not folded)
_No todos reviewed — none existed matching Phase 1 scope._

</deferred>

---

*Phase: 01-foundation-safety-infrastructure*
*Context gathered: 2026-04-24*

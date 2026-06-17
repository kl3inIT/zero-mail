# Phase 10: Gmail Mailbox Foundation and Account Management - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 10 converts the one-Gmail-per-tenant model into a **workspace-owned, multi-Gmail mailbox** model and ships **backend + OAuth account management only**. The workspace is the existing tenant (no new login/session semantics); `gmail_connections.id` becomes the stable mailbox id. Business config stays workspace-shared; Gmail OAuth/connection state becomes mailbox-isolated.

**Requirements (locked, no SPEC.md was finalized — sourced from REQUIREMENTS.md):** WSP-01..07, GMA-01..07, AUD-04, VER-01 (16 requirements).

**In scope:**
- Liquibase migration: drop `uq_gmail_connections_tenant_id`, add duplicate-active prevention + primary marker, migrate each existing tenant to one primary mailbox preserving encrypted tokens/state byte-identical.
- Mailbox identity (`gmail_connections.id`), `is_primary` marker, display purpose/label.
- Workspace-shared vs mailbox-isolated ownership boundary (WSP-07).
- Mailbox ownership-resolution seam + fail-closed contract (WSP-05/06) on account-mgmt APIs.
- Mailbox-aware `GmailApiClientFactory` lookup + token cache re-keying; tenant-only lookup kept as deprecated legacy adapter; ArchUnit allow-list guard.
- OAuth flow split: first-login provisioning vs add-mailbox vs reconnect-mailbox.
- Connected-accounts backend APIs: list, add, reconnect, disconnect, set-primary, metadata-only status/health (AUD-04).
- Per-mailbox watch renewal, invalid-grant handling, `users.stop`, token revoke, disconnect state transitions.
- Duplicate-active Gmail address prevention with clear conflict error (GMA-06).

**Out of scope (→ Phase 11):**
- Pub/Sub mailbox routing, projection/event/audit/idempotency key changes, history cursor per mailbox (ING-*).
- Mailbox-owned rules, triage dispatch, outbound gateway mailbox wiring (AUTO-*, AUD-01/02/03).
- Connected-accounts UI, active-mailbox switcher, mailbox-scoped inbox/rules/audit views (UX-*).
- The full `MailboxContext` ScopedValue request filter and its consumers.
- Playwright/browser verification, OpenAPI→frontend regen for new mailbox endpoints (VER-02/04).

</domain>

<decisions>
## Implementation Decisions

### Carried from spec-phase (locked earlier this session)
- **D-00a:** Migration touches **only `gmail_connections` + any new account-mgmt tables**. All downstream tables (projection, `pubsub_delivery`, `mail_message_observed`, `triage_audit`, `rules`) keep their keys; Phase 11 changes them alongside runtime wiring. No half-wired nullable mailbox columns on downstream tables in Phase 10.
- **D-00b:** Inbox projection ciphertext keeps **AAD = `tenantId + gmailMessageId + field`** (tenant-based). **No re-encryption / no AAD versioning in v1.3.** Justified: projection key change is Phase 11 and one tenant ↔ one primary mailbox after migration, so AAD needs no mailbox id now.
- **D-00c:** Account management in Phase 10 = **backend REST APIs + OAuth flows only**. UI/switcher = UX-01/02 → Phase 11.

### OAuth flow split (GMA-01/04/07) — Decision: B+D+E (attributes-based intent)
- **D-01:** Distinguish the three intents (first-login provision / add-mailbox / reconnect-mailbox) by stamping `intent` + `targetMailboxId` + `initiatingTenantId` as **`OAuth2AuthorizationRequest.attributes(...)`** in a custom `OAuth2AuthorizationRequestResolver`, persisted server-side by Spring's `AuthorizationRequestRepository` (Redis-backed Spring Session). Intent is authenticated against the live session at flow-start; it never rides a tamperable URL param. Keep the **single bundled Google registration** and let the framework keep owning `state` for CSRF.
- **D-02:** Path-separate the triggers (D): first-login `/oauth2/authorization/google` vs a mailbox-management connect endpoint; use **session presence** (E) as the cheap first-login-vs-management discriminator.
- **D-03:** The **add path INSERTs a new `gmail_connections` row** (after the migration relaxes `uq_gmail_connections_tenant_id`) and must branch **before** `OAuthProvisioningService` so it never re-provisions user/tenant. Reconnect updates the targeted row only. A duplicate `(tenant_id, google_email)` add fails closed.
- **Gotcha:** `oauth2Login()` re-runs full login auth on callback even for add/reconnect (produces a fresh `OAuth2AuthenticationToken`/OidcUser); attributes are removed-on-callback, so the success handler needs a saved-request retrieval shim. Rejected: untrusted `?intent=` param (IDOR on mailboxId, signal lost by callback) and signed-state HMAC (duplicates framework `state`, matches the separate-endpoint architecture CLAUDE.md locks against).

### Mailbox-scoped request guard (WSP-05/06) — Decision: minimal seam now, full filter Phase 11
- **D-04:** Phase 10 ships **only** a `resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId)` seam (on `GmailConnectionService` / a `MailboxOwnership` helper) with a **fixed fail-closed contract: 404 not-owned/missing, 409 disconnected**, used by the add/reconnect/disconnect/set-primary endpoints. Add `findByIdAndTenantId(...)` to `GmailConnectionRepository`.
- **D-05:** Use **path segment** `/api/gmail/mailboxes/{gmailConnectionId}/...` (OpenAPI-native typed path param) for mailbox-scoped endpoints — not header/query.
- **D-06:** The full `MailboxContext` **ScopedValue bound by a servlet filter** (mirroring `TenantContext` + `TenantBindingFilter`) is the locked **end-state mechanism**, built in **Phase 11** against real inbox/triage consumers. Rationale: argument-resolvers/interceptors bind *inside* DispatcherServlet **after** the filter chain — too late for the Hibernate session (the `GmailAccessGuard` invariant). Phase 10 has no mailbox-scoped consumers, so building the full filter now is infra with no caller to validate. Pin the ownership contract now so Phase 11 just wraps it; tenant-only default fallback stays strictly for legacy/default surfaces, never internal write paths.

### Duplicate-active + primary marker (GMA-03/06, VER-01) — Decision: A1 + B1 (partial unique indexes)
- **D-07:** New changeset (**next free number — verify, ~119**), raw `sql:` change (Liquibase native `createIndex` cannot express `WHERE`/`lower(...)`), with paired `DROP INDEX` rollback, mirroring the existing raw-DDL changeset style (e.g. `042-chat-message-and-body-ban-trigger`):
  1. Drop `uq_gmail_connections_tenant_id`.
  2. `CREATE UNIQUE INDEX uq_gmail_conn_active_email ON gmail_connections (tenant_id, lower(google_email)) WHERE status = 'CONNECTED';`
  3. Add `is_primary boolean NOT NULL DEFAULT false`; backfill `is_primary = true` for each tenant's existing single row.
  4. `CREATE UNIQUE INDEX uq_gmail_conn_primary ON gmail_connections (tenant_id) WHERE is_primary = true;`
- **D-08:** Map the unique-violation on `uq_gmail_conn_active_email` → a clear "this Gmail address is already connected to this workspace" error (catch constraint name; app-level pre-check only for the friendly message, the index is the race-proof backstop).
- **D-09:** Switch-primary is transactional (clear old, set new). Duplicate-dedupe of any pre-existing CONNECTED dupes via `preConditions` **before** creating the active-email index, or creation aborts on legacy data. Native SQL does **not** inherit `@TenantId` filtering — switch-primary/dedupe queries must include `tenant_id` explicitly (indexes are tenant-keyed so the DB constraint is tenant-safe regardless). `GmailConnectionStatus` = NOT_CONNECTED / PENDING / CONNECTED / DISCONNECTED. Token columns untouched → ciphertext byte-identical. Gmail dot/plus normalization (A2) deferred; if added later it's a non-breaking column, host-gated to `@gmail.com`/`@googlemail.com`.

### GmailApiClientFactory mailbox-aware (WSP-06, foundation of ING-06/AUD-05) — Decision: A + C + E
- **D-10:** Introduce `buildClientForMailbox(MailboxRef)` where `MailboxRef(UUID tenantId, UUID gmailConnectionId)` is a record value object — makes a tenant-only call **un-typable** (kills the arg-swap cross-mailbox bleed). Re-key the access-token cache from `tenantId` to `gmailConnectionId`; re-key the existing `buildClientForConnection(entity, tenantId)` path to `entity.getId()` so there is one cache convention.
- **D-11:** **AES-GCM decrypt AAD stays `tenantId.toString()`** — the cache key changes, the cipher context does NOT. `MailboxRef` carries both ids precisely so AAD survives.
- **D-12:** Keep `buildClientForTenant` as `@Deprecated(forRemoval = true)` default adapter that resolves the single connected mailbox and **fails loud if a tenant has >1 connected**.
- **D-13:** Ship the **ArchUnit allow-list rule in Phase 10** (mirror `GmailWriteBoundaryTest`'s custom `ArchCondition` over `getMethodCallsFromSelf`, `allowEmptyShould(false)`): only the factory + an explicit, **non-empty** legacy allow-list may call `buildClientForTenant`. The call sites already exist (triage, chat read tools, outbound gateway, draft loaders) so the rule bites immediately; Phase 11 deletes one allow-list entry per migrated consumer until the list empties and the method is removed. (Avoids the no-op "mailbox flows must not use tenant lookup" phrasing.)

### Claude's Discretion
- **OAuth flow split mechanism** — user delegated ("best practice là được"). Chose **B+D+E (attributes-based intent)** per research recommendation; it is the only option preserving the locked single-registration + framework CSRF/state while authenticating intent against the live session.

### Folded Todos
- **WR-06 — test-profile SecurityConfig slice for OAuth filter-chain coverage** (`.planning/todos/2026-04-28-wr-06-test-profile-securityconfig-slice.md`): folded because Phase 10 creates the OAuth-split surface (first-login/add/reconnect through the bundled registration's filter chain) that this slice is meant to cover. Planner should include a test-profile SecurityConfig slice exercising the three OAuth intents' routing through the success/failure handlers.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap (no SPEC.md — these are the requirement source of truth)
- `.planning/REQUIREMENTS.md` — Phase 10 = WSP-01..07, GMA-01..07, AUD-04, VER-01. Workspace-shared vs mailbox-isolated boundaries, duplicate-active prevention, fail-closed, logging posture.
- `.planning/ROADMAP.md` § "Phase 10: Gmail Mailbox Foundation and Account Management" — goal, depends-on, expected plan areas.

### Code research (most important — drives almost every decision above)
- `.planning/research/V1.3-CODE-RESEARCH.md` — full mailbox-scoping research. Key sections: "Zero Mail Current One-Gmail Assumptions", "OAuth Linking and Additional Accounts", "Gmail Client and Token Lookup", "UI and API Account Context", "Logging and Restricted Scope Note", "Recommended v1.3 Build Order → Phase 10".

### Inbox Zero reference (product pattern only — do NOT port TS/Next/Prisma or its raw-email logging)
- `../inbox-zero/apps/web/utils/oauth/account-linking.ts`, `app/api/google/linking/{auth-url,callback}/route.ts` — add-account / reconnect intent handling pattern.
- `../inbox-zero/apps/web/utils/email-account-client.ts` — per-mailbox Gmail client lookup pattern.
- `../inbox-zero/apps/web/utils/middleware.ts` — per-request account ownership validation pattern (the eventual Phase 11 filter shape).

### Project guardrails
- `CLAUDE.md` — single bundled OAuth registration (reject incremental two-leg); no raw email/token/prompt in logs; Liquibase append-only/raw-SQL-for-special-DDL; no abbreviations; ScopedValue tenant pattern; privacy posture.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `core/tenant/TenantContext` (ScopedValue) + `TenantBindingFilter` (servlet filter) — exact pattern to mirror for the Phase 11 `MailboxContext`; Phase 10 only pins the ownership contract it will wrap.
- `GmailConnectionRepository` — `findById` (via `JpaRepository`), `findByTenantId`, `findByGoogleEmailIgnoreCase`, `findConnectionsNeedingWatchRenewal` (per-row `FOR UPDATE SKIP LOCKED`), `updateLastSyncedHistoryIdMonotonic`. Add `findByIdAndTenantId(...)` for ownership.
- `GmailApiClientFactory` — `buildClientForTenant`, `buildClientForConnection(entity, tenantId)`; `accessTokenCache` `ConcurrentMap` keyed by `tenantId` (re-key target); AAD `tenantId.toString()` (keep).
- `RefreshTokenCipher` (`core.gmail.persistence.crypto`) — AES-GCM; AAD unchanged by D-11.
- `OAuthProvisioningService.provisionBundledOAuth` (PROPAGATION_REQUIRED, atomic user+tenant+gmail) — add path must branch before this.
- `GmailWriteBoundaryTest` — ArchUnit `ArchCondition`/`getMethodCallsFromSelf` template for the D-13 allow-list rule.
- `GmailAccessGuard` (`backend/api/.../security`) — documents the bind-before-transaction invariant that forces the filter (not interceptor/resolver) mechanism.

### Established Patterns
- Thin controllers, service-owned `@Transactional`, controllers never inject repositories; DTOs own `from(...)`.
- Tests use `RestClient + @LocalServerPort` (not MockMvc) so servlet filters / ScopedValue bind — any new filter must stay compatible.
- Liquibase: append-only YAML under `db/changelog/changes/`, included from `db.changelog-master.yaml`; raw `sql:` change with explicit rollback for partial/expression indexes and triggers.
- Enums: `IdentifiedEnum`/`OrderedEnum` + `fromId` fail-loud; `@Enumerated(STRING)` with `name()` == id.

### Integration Points
- Google OAuth success/failure handlers + custom `OAuth2AuthorizationRequestResolver` (resolver stamps intent attributes; handler branches provision/add/reconnect).
- `gmail_connections` table (migration), `GmailConnectionEntity` (`is_primary` field, label/purpose).
- `GmailConnectionService` (mailbox-scoped methods + ownership seam; keep tenant-only methods as legacy/default adapters).
- New account-mgmt controller under `controllers/gmail/` exposing list/add/reconnect/disconnect/set-primary + metadata health; DTOs under `dto/gmail/`.

</code_context>

<specifics>
## Specific Ideas

- Use `gmail_connections.id` directly as the mailbox id (no new `email_account_id`) — per research, simpler and safer than a parallel identity.
- Logging posture: tenant id, mailbox id, technical status/reason, optional masked/hashed email only. Never raw connected email/subject/snippet/body/token in logs (DB/UI storage of `google_email` is allowed product state).

</specifics>

<deferred>
## Deferred Ideas

- All Phase 11 work: Pub/Sub mailbox routing, projection/event/audit key changes, mailbox-owned rules, triage/outbound mailbox wiring, connected-accounts UI + active-mailbox switcher, Playwright verification, OpenAPI→frontend regen.
- Gmail dot/plus address normalization (research option A2) — deferred; non-breaking follow-on column if product wants true alias dedupe, host-gated to `@gmail.com`/`@googlemail.com`.
- Full `MailboxContext` ScopedValue servlet filter — locked end-state mechanism, built in Phase 11 against real consumers.

### Reviewed Todos (not folded)
- **Phase 8 real-Gmail e2e smoke on dev VPS** (`.planning/todos/2026-05-21-optional-phase-08-e2e-smoke-real-gmail-vps.md`): reviewed, **not folded** — it's a pre-launch / Phase-11 concern (real ingestion + switching), not a backend-foundation deliverable. Revisit when Phase 11 ingestion lands.

</deferred>

---

*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Context gathered: 2026-06-08*

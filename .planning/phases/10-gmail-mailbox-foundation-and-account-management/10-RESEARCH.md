# Phase 10: Gmail Mailbox Foundation and Account Management - Research

**Researched:** 2026-06-09
**Domain:** Spring Security 7 OAuth2 flow control, Liquibase partial/expression indexes, multi-mailbox token-cache re-keying, Gmail disconnect/revoke state machine, tenant→mailbox migration backfill
**Confidence:** HIGH (the locked decisions are sound and the codebase confirms every assumption; the one MEDIUM area — exact Spring Security 7 callback-survival shim — is now pinned to a concrete mechanism with source citation)

## Summary

Phase 10 is a backend-only refactor that breaks the one-Gmail-per-tenant invariant and ships OAuth account-management plumbing. Every architectural decision is already locked in CONTEXT.md (D-00a..D-13); this research concretizes the *implementation* of those decisions and verifies them against the live codebase rather than re-deciding anything.

All five high-risk areas are now de-risked against real source:
1. **OAuth callback survival** — `OAuth2LoginAuthenticationFilter.attemptAuthentication()` calls `authorizationRequestRepository.removeAuthorizationRequest(request, response)` **at the very start of authentication**, and the `OAuth2AuthenticationToken` delivered to the success handler does **not** carry the `OAuth2AuthorizationRequest` attributes `[CITED: github.com/spring-projects/spring-security OAuth2LoginAuthenticationFilter.java]`. The correct shim is a **custom `AuthorizationRequestRepository` decorator** that, on `removeAuthorizationRequest`, copies the `intent`/`targetMailboxId`/`initiatingTenantId` attributes into the `HttpSession` (re-`setAttribute` to dirty the Redis-backed session) before delegating removal, so the success handler reads them back from the session. This is the single biggest implementation risk and is now pinned.
2. **Liquibase partial/expression indexes** — confirmed current max changeset is **118**, so the new changeset is **119** (CONTEXT's ~119 guess is correct). The drop target `uq_gmail_connections_tenant_id` is an `addUniqueConstraint` (a Postgres unique *constraint*, not a bare index) created in `003-create-gmail-connections.yaml`, so the rollback uses `dropUniqueConstraint`/`ADD CONSTRAINT`, not `DROP INDEX`/`CREATE INDEX`. Partial+expression indexes (`WHERE`, `lower(...)`) must use a raw `sql:` change with `splitStatements: false`, mirroring `042-chat-message-and-body-ban-trigger.yaml`.
3. **Token-cache re-keying blast radius** — there are **9 production call sites** of `buildClientForTenant` and **5 production call sites** of `buildClientForConnection`. These form the non-empty ArchUnit allow-list. The cache today is `ConcurrentMap<UUID,TokenRefreshResult>` keyed by `tenantId`; both factory paths must collapse to a single `gmailConnectionId` key so two mailboxes in one tenant cannot share a cached access token.
4. **Disconnect/revoke/users.stop state machine** — the codebase already has the correct ordering (`users.stop` first, then OAuth revoke, then DB flip) in `GmailConnectionService.disconnect`; Phase 10 must make each step mailbox-scoped and resolve the primary-on-disconnect question (recommendation below).
5. **Backfill safety** — the `is_primary` backfill must handle three failure modes (zero rows, one row, pre-existing multiple CONNECTED rows) with `preConditions onFail=MARK_RAN` guards and explicit `tenant_id` in all SQL (native SQL does not inherit Hibernate's `@TenantId` filter).

**Primary recommendation:** Implement the OAuth intent shim as a `@Component` decorator over `HttpSessionOAuth2AuthorizationRequestRepository`, wired via `.authorizationEndpoint().authorizationRequestRepository(...)` in the existing `@Order(4)` user chain; do the migration as changeset `119` with raw SQL + `preConditions`; re-key the factory cache to `gmailConnectionId` and ship the `@Deprecated(forRemoval=true)` `buildClientForTenant` adapter plus a non-empty ArchUnit allow-list of the 9 current callers.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OAuth intent routing (first-login/add/reconnect) | API (`backend/api` security filters/handlers) | — | OAuth filter chain + success/failure handlers live in `backend/api`; intent is authenticated against the live session at flow start |
| Mailbox identity + migration | Database / Storage (Liquibase) | Core persistence (entity field) | `gmail_connections.id` is the mailbox id; schema-level constraints enforce uniqueness race-proof |
| Ownership seam `resolveOwnedConnectionOrThrow` | API/Backend (`GmailConnectionService` use-case) | — | Business invariant (fail-closed 404/409); controllers delegate, never query repos directly (CONVENTIONS §1) |
| Mailbox-aware Gmail client + token cache | Core (`GmailApiClientFactory`) | — | Single Gmail-client seam; cache coherence is a core concern |
| Connected-accounts REST APIs | API (controllers/gmail) | Core (service) | Thin controller → service-owned `@Transactional` |
| Disconnect/revoke/users.stop | Core (`GmailConnectionService`) + external Google API | — | State machine + external side effects centralized in the service |
| ArchUnit allow-list guard | Test (`core/src/test/.../arch`) | — | Architectural test, mirrors `GmailWriteBoundaryTest` |

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-00a:** Migration touches **only `gmail_connections` + any new account-mgmt tables**. All downstream tables (projection, `pubsub_delivery`, `mail_message_observed`, `triage_audit`, `rules`) keep their keys; Phase 11 changes them alongside runtime wiring. No half-wired nullable mailbox columns on downstream tables in Phase 10.
- **D-00b:** Inbox projection ciphertext keeps **AAD = `tenantId + gmailMessageId + field`** (tenant-based). No re-encryption / no AAD versioning in v1.3.
- **D-00c:** Account management in Phase 10 = **backend REST APIs + OAuth flows only**. UI/switcher = UX-01/02 → Phase 11.
- **D-01:** Distinguish the three intents by stamping `intent` + `targetMailboxId` + `initiatingTenantId` as **`OAuth2AuthorizationRequest.attributes(...)`** in a custom `OAuth2AuthorizationRequestResolver`, persisted server-side by Spring's `AuthorizationRequestRepository` (Redis-backed Spring Session). Keep the single bundled Google registration; framework keeps owning `state` for CSRF.
- **D-02:** Path-separate the triggers: first-login `/oauth2/authorization/google` vs a mailbox-management connect endpoint; use session presence as the cheap first-login-vs-management discriminator.
- **D-03:** The add path INSERTs a new `gmail_connections` row and must branch **before** `OAuthProvisioningService`. Reconnect updates the targeted row only. Duplicate `(tenant_id, google_email)` add fails closed.
- **Gotcha (D-01):** `oauth2Login()` re-runs full login auth on callback even for add/reconnect; attributes are removed-on-callback, so the success handler needs a saved-request retrieval shim.
- **D-04:** Phase 10 ships **only** a `resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId)` seam with a fixed fail-closed contract: **404 not-owned/missing, 409 disconnected**. Add `findByIdAndTenantId(...)` to `GmailConnectionRepository`.
- **D-05:** Path segment `/api/gmail/mailboxes/{gmailConnectionId}/...` for mailbox-scoped endpoints.
- **D-06:** The full `MailboxContext` ScopedValue servlet filter is **Phase 11**. Phase 10 only pins the ownership contract.
- **D-07:** New changeset (next free number), raw `sql:` change with paired rollback: drop `uq_gmail_connections_tenant_id`; create `uq_gmail_conn_active_email` partial unique on `(tenant_id, lower(google_email)) WHERE status='CONNECTED'`; add `is_primary boolean NOT NULL DEFAULT false` + backfill; create `uq_gmail_conn_primary` partial unique on `(tenant_id) WHERE is_primary=true`.
- **D-08:** Map unique-violation on `uq_gmail_conn_active_email` → friendly "already connected" error (catch constraint name; app-level pre-check only for the message).
- **D-09:** Switch-primary transactional; `preConditions` dedupe pre-existing CONNECTED dupes before creating active-email index; native SQL includes `tenant_id` explicitly. Status enum NOT_CONNECTED/PENDING/CONNECTED/DISCONNECTED. Token columns untouched. Gmail dot/plus normalization deferred.
- **D-10:** `buildClientForMailbox(MailboxRef)` where `MailboxRef(UUID tenantId, UUID gmailConnectionId)` record. Re-key access-token cache from `tenantId` → `gmailConnectionId`; re-key `buildClientForConnection(entity, tenantId)` to `entity.getId()`.
- **D-11:** AES-GCM decrypt AAD stays `tenantId.toString()`. `MailboxRef` carries both ids.
- **D-12:** Keep `buildClientForTenant` as `@Deprecated(forRemoval=true)` adapter; fails loud if a tenant has >1 connected.
- **D-13:** Ship the ArchUnit allow-list rule in Phase 10 (mirror `GmailWriteBoundaryTest` `ArchCondition`/`getMethodCallsFromSelf`, **non-empty** allow-list); `allowEmptyShould(false)`.
- **Folded WR-06:** include a test-profile SecurityConfig slice exercising the three OAuth intents' routing through success/failure handlers.

### Claude's Discretion
- **OAuth flow split mechanism** — delegated; chose B+D+E (attributes-based intent). Locked above.

### Deferred Ideas (OUT OF SCOPE)
- All Phase 11 work: Pub/Sub mailbox routing, projection/event/audit key changes, mailbox-owned rules, triage/outbound mailbox wiring, connected-accounts UI + active-mailbox switcher, Playwright verification, OpenAPI→frontend regen.
- Gmail dot/plus address normalization (option A2) — deferred; non-breaking follow-on column, host-gated to `@gmail.com`/`@googlemail.com`.
- Full `MailboxContext` ScopedValue servlet filter — Phase 11.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WSP-01 | Existing tenant = one workspace, no login/session change | Tenant stays the workspace boundary; `TenantContext`/`TenantBindingFilter` unchanged. No new session semantics — verified `SecurityConfig` `@Order(4)` chain unaffected. |
| WSP-02 | Migrate one-Gmail data → one primary mailbox preserving tokens/state byte-identical | Changeset 119 leaves `refresh_token_encrypted`, `scopes_granted`, watch/history columns untouched; only adds `is_primary` + backfills `true` for the existing single row. AAD unchanged (D-11). |
| WSP-03 | Stable mailbox id on new mailbox-scoped records | Use `gmail_connections.id` directly (no parallel `email_account_id`). |
| WSP-04 | Distinguish workspace/user/mailbox in APIs/labels/logs without team controls | Logging posture in "Common Pitfalls"; path segment `/api/gmail/mailboxes/{gmailConnectionId}`. |
| WSP-05 | Fail closed on missing/invalid/disconnected/not-owned mailbox id | `resolveOwnedConnectionOrThrow` → 404 (not-owned/missing) / 409 (disconnected). |
| WSP-06 | Shared guard validates `(tenantId, gmailMailboxId)` before controller/service; tenant-only fallback legacy-only | Seam now (`findByIdAndTenantId`); full filter Phase 11 (D-06). ArchUnit guards tenant-only lookup. |
| WSP-07 | Workspace-shared vs mailbox-isolated boundary | Documented boundary table below; Phase 10 only enforces it for Gmail OAuth/connection state. |
| GMA-01 | Connect additional mailbox without replacing existing | Add path INSERTs new row (D-03), branches before `OAuthProvisioningService`. |
| GMA-02 | View all connected mailboxes with metadata | List endpoint returns id, email, label/purpose, status, primary marker, watch expiry, ingestion health, last sync. Fields already on entity (watch_expires_at, ingestion_health, last_synced_history_id). |
| GMA-03 | Choose one primary/default mailbox | `set-primary` transactional (clear old, set new); `uq_gmail_conn_primary` partial unique enforces exactly-one. |
| GMA-04 | Reconnect one mailbox, refresh token/scopes without touching others | Reconnect intent targets `targetMailboxId`; updates that row only. |
| GMA-05 | Disconnect one mailbox; stop watch/ingestion/automation without disconnecting workspace | Mailbox-scoped `disconnect(MailboxRef)`: users.stop + revoke + status flip on that row. |
| GMA-06 | Prevent duplicate active Gmail address; clear conflict error | `uq_gmail_conn_active_email` partial unique + constraint-name catch (D-08). |
| GMA-07 | Separate first-login/add/reconnect OAuth flows | Intent attributes via custom resolver + repository shim (D-01/02/03). |
| AUD-04 | Admin metadata-only multi-mailbox health, no tokens/bodies/prompts | Read-side projection excludes ciphertext; reuse `GmailConnectionProjection` shape extended with mailbox metadata. |
| VER-01 | Liquibase migration roll-forward, preserves tenants, covers old single-account fixtures | Real Liquibase-backed test with old single-row fixture; preConditions guard legacy dupes. |

## Workspace-Shared vs Mailbox-Isolated Boundary (WSP-07)

| State | Owner | Phase 10 action |
|-------|-------|-----------------|
| Credits, billing, AI provider/model/BYOK, global pause/auto-send, safety policy, templates/catalog | Workspace (tenant) | None — already tenant-scoped; do NOT touch |
| Gmail OAuth, refresh token, scopes, watch/history, connection health, display identity (`google_email`, label/purpose, `is_primary`) | Mailbox (`gmail_connections` row) | This phase makes these per-row instead of per-tenant-singleton |
| Inbox data, rules, Gmail actions, outbound execution, audit provenance | Mailbox | **Phase 11** (D-00a — keys untouched in Phase 10) |

## Standard Stack

### Core (no new runtime dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Security OAuth2 Client | 7.0.5 (Boot-managed) | `OAuth2AuthorizationRequestResolver`, `AuthorizationRequestRepository`, `oauth2Login` | Already the project's bundled-OAuth mechanism `[VERIFIED: backend/api SecurityConfig]` |
| Spring Session (Redis/Lettuce) | Boot-managed | Persists `OAuth2AuthorizationRequest` between auth init and callback | Already configured; the shim must re-dirty the session attribute for Redis to persist `[CITED: github.com/spring-projects/spring-security #7327]` |
| Liquibase | 5.0.2 (YAML) | Changeset 119 raw SQL + rollback + preConditions | Project standard; raw `sql:` for partial/expression indexes |
| Spring Data JPA / Hibernate | 7 | `findByIdAndTenantId`, entity `is_primary`/`label` fields | Existing `GmailConnectionRepository` pattern |
| Google API Client (`com.google.api.services.gmail.Gmail`) | existing | `users().stop("me")` for watch teardown | Already used in `GmailConnectionService.tryStopWatch` |
| ArchUnit (`com.tngtech.archunit`) | existing | Allow-list rule mirroring `GmailWriteBoundaryTest` | Existing arch-test infra in `core/src/test/.../arch` |

**Installation:** No new packages. Phase 10 ships **zero new runtime dependencies** (matches the Phase 8 finding that WebAuthn/HMAC/RestClient were already native). All mechanisms — custom `AuthorizationRequestRepository`, partial unique indexes, `users.stop`, OAuth revoke — use APIs already present in the codebase.

### Alternatives Considered (all rejected by locked decisions — listed for completeness only)
| Instead of | Could Use | Why rejected |
|------------|-----------|--------------|
| Attributes-based intent | `?intent=` query param | IDOR on `mailboxId`, signal lost by callback (D-01 reject) |
| Attributes-based intent | Signed-state HMAC | Duplicates framework `state`; matches separate-registration architecture CLAUDE.md forbids (D-01 reject) |
| Custom `AuthorizationRequestRepository` shim | Read attributes from `OAuth2AuthenticationToken` in success handler | Token does NOT carry the authorization-request attributes (verified) |
| New `email_account_id` | Reuse `gmail_connections.id` | Parallel identity is more surface area; reuse is simpler/safer (CONTEXT specifics) |

## Package Legitimacy Audit

> Not applicable — Phase 10 installs **no external packages**. All libraries are already present and Boot-managed. slopcheck not run because no install step exists.

## OAuth Callback Survival — The #1 Risk, Pinned

### What actually happens (verified against Spring Security source)

The user chain (`SecurityConfig` `@Order(4)`) wires `oauth2Login()` with the custom `GoogleAuthorizationRequestResolver` but **no custom `AuthorizationRequestRepository`** — so it uses the default `HttpSessionOAuth2AuthorizationRequestRepository` `[VERIFIED: backend/api/.../security/SecurityConfig.java lines 210-219]`.

Flow on the **callback** (`/login/oauth2/code/google`):
1. `OAuth2LoginAuthenticationFilter.attemptAuthentication()` runs first.
2. It immediately calls `authorizationRequestRepository.removeAuthorizationRequest(request, response)` — **before** building any token `[CITED: github.com/spring-projects/spring-security OAuth2LoginAuthenticationFilter.java]`.
3. The removed `OAuth2AuthorizationRequest` (carrying our `attributes`) is embedded only in the transient `OAuth2LoginAuthenticationToken` / `OAuth2AuthorizationExchange` used during authentication. The **`OAuth2AuthenticationToken` handed to `GoogleOAuthSuccessHandler.onAuthenticationSuccess` does NOT expose those attributes** `[CITED: same source]`.

**Conclusion:** Stamping attributes in the resolver alone is insufficient — they are consumed and discarded by the filter before the success handler runs.

### The correct shim (recommended mechanism)

Implement a `@Component` **decorator** over `HttpSessionOAuth2AuthorizationRequestRepository`:

```java
// Source pattern: Spring Security AuthorizationRequestRepository contract
// (docs.spring.io/spring-security/reference/6.5 + 7.0 servlet/oauth2/client/authorization-grants)
@Component
public class IntentCarryingAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String INTENT_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_INTENT";
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(request, response);
        if (removed != null) {
            Object intent = removed.getAttribute("intent");
            if (intent != null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    // Re-set to DIRTY the Redis-backed Spring Session so the change persists.
                    session.setAttribute(
                            INTENT_SESSION_ATTRIBUTE,
                            new OAuthIntentSnapshot(/* intent, targetMailboxId, initiatingTenantId */));
                }
            }
        }
        return removed;
    }
}
```

The success handler reads `session.getAttribute(INTENT_SESSION_ATTRIBUTE)`, branches (provision / add / reconnect), then **removes the attribute** (one-shot). Wire it via:

```java
.oauth2Login(oauth2Login -> oauth2Login
    .successHandler(successHandler)
    .failureHandler(failureHandler)
    .authorizationEndpoint(endpoint -> endpoint
        .authorizationRequestResolver(authRequestResolver)
        .authorizationRequestRepository(intentCarryingRepository)))  // NEW
```

### Critical Redis-session gotcha [CITED: github.com/spring-projects/spring-security#7327]

With Spring Session backed by Redis, mutating a value already in the session map does NOT automatically mark the session dirty. The shim MUST call `session.setAttribute(...)` with a fresh value object so `SessionRepositoryFilter` persists it. Do not store the raw `OAuth2AuthorizationRequest` in the session attribute (it is large and already removed) — store a small immutable snapshot record (`OAuthIntentSnapshot(String intent, UUID targetMailboxId, UUID initiatingTenantId)`).

### Intent stamping in the resolver (D-01)

Extend `GoogleAuthorizationRequestResolver.customizeAuthorizationRequest` to add intent attributes when the mailbox-management connect endpoint triggered the flow. Use `.attributes(attrs -> attrs.put("intent", ...))` (not `additionalParameters` — attributes are server-side-only and never sent to Google; `additionalParameters` go on the wire). The `initiatingTenantId` is read from the authenticated session at flow start (D-02: session presence discriminates first-login from management).

### Three-intent routing

| Trigger endpoint | Session at flow start | `intent` attribute | Success-handler branch |
|------------------|----------------------|--------------------|------------------------|
| `/oauth2/authorization/google` (login page) | none (anonymous) | absent / `first_login` | `provisionBundledOAuth` (current path) |
| New mailbox-management add endpoint | authenticated | `add_mailbox` | INSERT new `gmail_connections` row, **branch before** `OAuthProvisioningService` |
| Mailbox-management reconnect endpoint (with `targetMailboxId`) | authenticated | `reconnect_mailbox` | `resolveOwnedConnectionOrThrow(tenantId, targetMailboxId)` then update that row only |

**Note on `?reconnect=true`:** the existing `GoogleAuthorizationRequestResolver` uses `?reconnect=true` only to add `prompt=consent` (UX, forces re-consent + new refresh token). Add/reconnect mailbox flows should also set `prompt=consent` (need a fresh refresh token for the new/targeted row). Keep `?reconnect=true` as the prompt trigger; carry the *intent* via attributes. These are orthogonal concerns — do not overload the query param with intent.

## Liquibase Migration — Changeset 119 (verified)

### Confirmed facts
- **Current max changeset number: 118** (`118-cleanup-sender-projection.yaml`). Next free = **119** `[VERIFIED: ls db/changelog/changes]`. (Note: numbers 086/087/094/108/109/110 have duplicate-prefix files — the *numeric max* is 118.)
- The drop target is a **unique constraint**, not an index: `003-create-gmail-connections.yaml` uses `addUniqueConstraint: { columnNames: tenant_id, constraintName: uq_gmail_connections_tenant_id }` `[VERIFIED: 003-create-gmail-connections.yaml lines 17-20]`. In Postgres a unique constraint is backed by an index of the same name, so `DROP INDEX uq_gmail_connections_tenant_id` would fail (must `ALTER TABLE ... DROP CONSTRAINT` or `dropUniqueConstraint`). Rollback re-adds the constraint.
- Master include pattern: each new file is appended to `db.changelog-master.yaml` (append-only; CONVENTIONS §10). Verify the include block when adding 119.
- Raw `sql:` change with `splitStatements: false` + paired `rollback:` `sql:` block is the established style `[VERIFIED: 042-chat-message-and-body-ban-trigger.yaml]`.

### Recommended changeset shape (single changeset, ordered)

```yaml
databaseChangeLog:
  - changeSet:
      id: 119-gmail-connections-multi-mailbox
      author: zeromail
      comment: >
        Relax one-Gmail-per-tenant: drop uq_gmail_connections_tenant_id, add partial unique
        on active email + primary marker. Tokens/scopes/watch columns untouched (byte-identical,
        AAD unchanged per D-11). preConditions guard pre-existing CONNECTED duplicates so the
        active-email index creation cannot abort on legacy data.
      preConditions:
        - onFail: MARK_RAN
        - sqlCheck:
            expectedResult: 0
            # No tenant already has >1 CONNECTED row for the same lower(email). If legacy data
            # violates this, the changeset is MARKED_RAN (not applied) and an operator must dedupe
            # manually before re-running — fail-safe over silently corrupting data.
            sql: >
              SELECT count(*) FROM (
                SELECT tenant_id, lower(google_email)
                FROM gmail_connections WHERE status = 'CONNECTED'
                GROUP BY tenant_id, lower(google_email) HAVING count(*) > 1
              ) duplicates;
      changes:
        - sql:
            splitStatements: false
            sql: |
              ALTER TABLE gmail_connections DROP CONSTRAINT uq_gmail_connections_tenant_id;

              CREATE UNIQUE INDEX uq_gmail_conn_active_email
                ON gmail_connections (tenant_id, lower(google_email))
                WHERE status = 'CONNECTED';

              ALTER TABLE gmail_connections
                ADD COLUMN is_primary boolean NOT NULL DEFAULT false;

              -- Backfill: each tenant's existing single row becomes primary.
              -- DISTINCT ON picks one row deterministically if (defensively) more than one exists.
              UPDATE gmail_connections gc SET is_primary = true
              WHERE gc.id IN (
                SELECT DISTINCT ON (tenant_id) id
                FROM gmail_connections
                ORDER BY tenant_id, (status = 'CONNECTED') DESC, connected_at NULLS LAST, id
              );

              CREATE UNIQUE INDEX uq_gmail_conn_primary
                ON gmail_connections (tenant_id)
                WHERE is_primary = true;
      rollback:
        - sql:
            splitStatements: false
            sql: |
              DROP INDEX IF EXISTS uq_gmail_conn_primary;
              ALTER TABLE gmail_connections DROP COLUMN IF EXISTS is_primary;
              DROP INDEX IF EXISTS uq_gmail_conn_active_email;
              ALTER TABLE gmail_connections
                ADD CONSTRAINT uq_gmail_connections_tenant_id UNIQUE (tenant_id);
```

Also add `label varchar` / `purpose` and (later) optional `is_primary` accessors on the entity — but the *display label* column can be a separate small additive change or folded here. Recommendation: fold the `label`/`display_purpose` column into 119 so the entity and migration land together (keep one logical change = "multi-mailbox identity"); document it in the changeset comment.

### Why a single changeset (not four)
Mirror `042`'s "single changeSet by design" rationale: the drop + partial indexes + backfill must be atomic. A partial apply (e.g. dropped the old constraint but failed before creating the new active-email index) leaves a window where duplicate active emails could be inserted. One changeset = one transaction = no window.

### Constraint-name catch (D-08)
On INSERT/reconnect, a `org.springframework.dao.DataIntegrityViolationException` wrapping a Postgres `23505` with constraint name `uq_gmail_conn_active_email` → translate to a 409 with message "this Gmail address is already connected to this workspace". The app-level pre-check (`findByGoogleEmailIgnoreCase` scoped to tenant + CONNECTED) is only for the friendly message; the partial index is the race-proof backstop. Inspect the constraint name via the SQLState/`getConstraintName()` on the underlying `PSQLException`.

## Token Cache Re-Keying — Blast Radius (verified)

### Production call sites (the non-empty ArchUnit allow-list, D-13)

**`buildClientForTenant` — 9 production callers** `[VERIFIED: grep across backend/*/src/main]`:
1. `core.triage.usecases.TriageGmailWriter` (2 calls: lines 283, 474)
2. `core.outbound.usecases.GmailOutboundSendGateway` (line 28)
3. `core.outbound.usecases.ForwardMessageAssembler` (line 126)
4. `core.chat.usecases.tools.SearchInboxToolHandler` (line 55)
5. `core.chat.usecases.tools.ListLabelsToolHandler` (line 41)
6. `core.chat.usecases.tools.GetThreadToolHandler` (line 53)
7. `core.chat.usecases.tools.GetMessageToolHandler` (line 56)
8. `core.chat.usecases.settings.GmailSentMessagesReader` (line 45)
9. `core.draft.usecases.ToneContextBuilder` (line 221) + `core.draft.usecases.DraftReplySourceLoader` (line 47)
10. `api.chat.AssistantPendingActionReconciler` (line 163) — note this is in `backend/api`, so the `core`-scoped ArchUnit rule won't see it; the allow-list rule covers `core` callers, and the `api` caller is migrated in Phase 11 too.

**`buildClientForConnection` — 5 production callers** (already pass an entity + tenantId; re-key to `entity.getId()`):
1. `core.cleanup.usecases.SenderMessageReadService` (line 254)
2. `core.gmail.usecases.RecentInboxReadService` (line 541)
3. `core.gmail.usecases.InboxBackfillService` (line 129)
4. `core.gmail.usecases.GmailPreviewReadService` (lines 275 via tenant, 337 via connection)
5. `core.gmail.usecases.GmailDeliveryProcessingService` (line 101)

The `@Deprecated(forRemoval=true)` `buildClientForTenant` adapter (D-12) must: resolve the single connected mailbox via `findByTenantId`-style lookup and **throw `IllegalStateException` if >1 CONNECTED rows exist** for the tenant (fail loud — the whole point is to surface un-migrated callers in Phase 11).

### Cache-coherence implication (the real risk)

Today: `accessTokenCache` is `ConcurrentMap<UUID, TokenRefreshResult>` keyed by **tenantId** (`GmailApiClientFactory` lines 47, 111-128). `buildClientForConnection` *also* keys the cache by the `tenantId` argument, not the connection. After migration a tenant can have 2 CONNECTED mailboxes → **with the current code, the first mailbox's access token would be served to the second mailbox** (silent cross-mailbox token bleed). This is exactly the bug the re-key prevents.

**Fix:** re-key the map to `ConcurrentMap<UUID /*gmailConnectionId*/, TokenRefreshResult>`. `buildClientForMailbox(MailboxRef)` keys on `ref.gmailConnectionId()`; `buildClientForConnection(entity, tenantId)` keys on `entity.getId()`. The **AES-GCM decrypt AAD stays `tenantId.toString()`** (D-11) — `MailboxRef` carries both ids precisely so the AAD survives the cache-key change. The `InvalidGrantException` cache eviction (`accessTokenCache.remove(...)`) must also switch to `gmailConnectionId`.

**Test the invariant:** two CONNECTED mailboxes in one tenant → request client for mailbox A then mailbox B → assert B's refresh path runs (cache miss on B's key) and B never receives A's cached token. This maps directly to the Verification Focus item "Gmail client token cache is keyed by mailbox id."

## Disconnect / Revoke / users.stop State Machine

### Current ordering is already correct (reuse it, make it mailbox-scoped)
`GmailConnectionService.disconnect(tenantId)` `[VERIFIED]`:
1. `tryStopWatch` — refresh access token from stored ciphertext, `gmail.users().stop("me").execute()` (halts Pub/Sub watch). Runs FIRST while ciphertext still present. Swallows exceptions.
2. `revokeStoredRefreshToken` — decrypt + `GoogleOAuthRevokeClient.revoke(token)` → POST `https://oauth2.googleapis.com/revoke?token=...`. Best-effort (200 or 400+`invalid_token` = success). `[VERIFIED: GoogleOAuthRevokeClient]`
3. `markDisconnected` — status→DISCONNECTED, `disconnectedAt=now`, NULL the ciphertext + watch fields, reset `watchConsecutiveFailures=0`, `ingestionHealth=HEALTHY`.

CASA Tier 2 V3.3.1 / V13.1.5 require token revocation on user-initiated disconnect — already encoded.

### Phase 10 changes
- Every method (`disconnect`, `tryStopWatch`, `revokeStoredRefreshToken`, `markDisconnected`, `markHistoryLost`, `markWatchUnhealthy`, `recordWatchSuccess`, `incrementWatchFailure`, `clearForReconnect`, `upsert`) currently keys on `findByTenantId`. Add mailbox-scoped overloads keyed on `gmailConnectionId` (resolved via `findByIdAndTenantId` for ownership). Keep tenant-only methods as `@Deprecated` legacy adapters per D-12 posture.
- `GmailAccessGuard.on(OAuth2TokenRefreshFailed)` (the invalid_grant listener) currently calls `findByTenantId`. The refresh-failure event carries only tenantId today; in Phase 10 it can stay tenant-scoped (one primary mailbox after migration) — flag for Phase 11 to carry mailbox id. Document as a known tenant-only legacy surface.
- `users.stop` is the documented Gmail call to halt a watch `[CITED: Gmail API users.stop]`; revoke endpoint is `https://oauth2.googleapis.com/revoke` `[VERIFIED: GoogleOAuthRevokeClient]`.

### Disconnect status transitions
`CONNECTED → DISCONNECTED` (user disconnect). `DISCONNECTED → CONNECTED` (reconnect via OAuth). `PENDING` is a transient add-in-progress state. Disconnect is **idempotent**: `markDisconnected` is a no-op if the row is already DISCONNECTED (the `ifPresent` + status set is naturally idempotent; assert it in a test).

### Primary-on-disconnect question (RECOMMENDATION — needs planner/user confirmation)
Can a user disconnect the **primary** mailbox? Two valid options:
- **(Recommended) Allow disconnect of primary; auto-promote another CONNECTED mailbox to primary in the same transaction.** If no other CONNECTED mailbox exists, the tenant has zero primary (acceptable: `uq_gmail_conn_primary` is a partial index `WHERE is_primary=true`, so zero primary rows is legal). Set-primary later re-establishes one.
- (Alternative) Block disconnecting the primary while other mailboxes exist (force explicit set-primary first). Cleaner invariant but worse UX.

This is the one genuine open decision in the disconnect path. `[ASSUMED]` that auto-promote is preferred (matches "disconnect one mailbox without disconnecting the workspace" GMA-05 intent) — confirm with user before locking.

## Backfill Safety (VER-01, WSP-02)

| Failure mode | Pre-migration reality | Handling |
|--------------|----------------------|----------|
| Tenant with exactly 1 row (the common case) | Single Gmail per tenant invariant held | `DISTINCT ON (tenant_id)` picks it → `is_primary=true`. Tokens untouched. |
| Tenant with 0 gmail_connections rows | Tenant provisioned but never granted Gmail (edge) | No row to backfill — fine; `is_primary` default false; no primary until first connect. |
| Tenant with >1 CONNECTED rows (should not exist pre-migration) | Would violate the old `uq_gmail_connections_tenant_id` constraint — impossible by construction | `preConditions sqlCheck` guards anyway (fail-safe); `DISTINCT ON` deterministic if it somehow occurs. |
| DISCONNECTED + CONNECTED rows for same tenant | Possible if old upsert left a DISCONNECTED row? No — old invariant was one row per tenant total. | `ORDER BY (status='CONNECTED') DESC` prefers the connected row as primary. |

`onFail: MARK_RAN` on preConditions means: if legacy data violates the dedup assumption, the changeset is recorded as run-but-skipped and a human must intervene — strictly safer than aborting mid-migration or silently corrupting. Document this in the changeset comment and the migration test.

**Byte-identical token preservation:** the migration never reads/writes `refresh_token_encrypted`, `scopes_granted`, or any watch/history column — only `is_primary` and (optionally) `label`. AAD stays `tenantId.toString()` (D-11), so existing ciphertext decrypts unchanged. Assert in a test: persist an old-style row with known ciphertext → run migration → decrypt still yields the original token.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────── ADD / RECONNECT (authenticated) ───────────────────┐
                         │                                                                        │
[Browser] ──GET──► /api/gmail/mailboxes/connect?intent=add ──► ConnectMailbox controller          │
              ──GET──► /api/gmail/mailboxes/{id}/reconnect ──► (302 → /oauth2/authorization/google)│
                         │                                                                        │
                         ▼                                                                        │
   GoogleAuthorizationRequestResolver.resolve()                                                   │
     stamps attributes{ intent, targetMailboxId, initiatingTenantId }  ◄── read from live session │
                         │ saveAuthorizationRequest (Redis session)                               │
                         ▼                                                                        │
            [Google consent screen] ──code──► /login/oauth2/code/google                           │
                         │                                                                        │
                         ▼                                                                        │
   OAuth2LoginAuthenticationFilter.attemptAuthentication()                                        │
     ① removeAuthorizationRequest(req,resp)  ──► IntentCarryingRepository copies intent           │
     │                                            snapshot into HttpSession (re-set = dirty Redis) │
     ② builds OAuth2AuthenticationToken (NO attributes attached)                                  │
                         │                                                                        │
                         ▼                                                                        │
   GoogleOAuthSuccessHandler.onAuthenticationSuccess()                                            │
     reads session.getAttribute(INTENT) ──► branch:                                               │
        first_login    → OAuthProvisioningService.provisionBundledOAuth (user+tenant+row)         │
        add_mailbox    → resolveOwned? no; INSERT new gmail_connections row (before provisioning)──┘
        reconnect_…    → resolveOwnedConnectionOrThrow(tenant, targetMailboxId); UPDATE that row
                         │ (then remove session attribute — one-shot)
                         ▼
                  gmail_connections  (mailbox = id; is_primary; status; refresh_token_encrypted)
                         ▲
                         │  resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId)
   [Account-mgmt REST] ──┤  list / set-primary / disconnect ──► GmailConnectionService (mailbox-scoped)
   /api/gmail/mailboxes  │
                         ▼
   GmailApiClientFactory.buildClientForMailbox(MailboxRef(tenant, connId))
     accessTokenCache keyed by gmailConnectionId  (AAD = tenantId.toString())
        on disconnect: users.stop("me") → revoke token → status=DISCONNECTED + NULL ciphertext
```

### Recommended package layout (mirrors existing conventions)
```
backend/api/.../controllers/gmail/
  ├── ConnectMailboxController          # add/reconnect OAuth trigger endpoints (302 → /oauth2/...)
  └── ConnectedMailboxesController      # list / set-primary / disconnect (REST, thin)
backend/api/.../dto/gmail/
  └── (MailboxSummaryResponse, etc. — records with from(...) + @Schema)
backend/api/.../security/
  ├── IntentCarryingAuthorizationRequestRepository   # the shim
  └── (GoogleAuthorizationRequestResolver extended; GoogleOAuthSuccessHandler extended)
backend/core/.../gmail/gateway/
  ├── GmailApiClientFactory             # buildClientForMailbox + re-keyed cache + @Deprecated adapter
  └── MailboxRef                        # record(UUID tenantId, UUID gmailConnectionId)
backend/core/.../gmail/usecases/
  └── GmailConnectionService            # mailbox-scoped methods + resolveOwnedConnectionOrThrow
backend/core/.../gmail/persistence/
  └── GmailConnectionRepository         # + findByIdAndTenantId
backend/core/src/test/.../arch/
  └── GmailClientLookupBoundaryTest     # D-13 allow-list rule
```

### Anti-Patterns to Avoid
- **Carrying intent in `additionalParameters`** instead of `attributes` — `additionalParameters` are sent to Google on the wire; `attributes` are server-side-only. Intent + mailboxId must be attributes.
- **Reading intent from the `OAuth2AuthenticationToken`** in the success handler — it isn't there (verified).
- **Building the full `MailboxContext` ScopedValue filter now** — D-06: no consumers in Phase 10, filter must bind before DispatcherServlet for the Hibernate-session invariant; building it without a caller is unvalidated infra.
- **`DROP INDEX uq_gmail_connections_tenant_id`** — it's a constraint; use `DROP CONSTRAINT`.
- **Keying the token cache by tenantId after migration** — causes silent cross-mailbox token bleed.
- **Logging `google_email`** — storing in DB/UI is allowed product state; logging is not (privacy posture).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OAuth state/CSRF | Custom signed-state HMAC | Framework `state` (kept) | CLAUDE.md locks single registration; HMAC duplicates `state` |
| Persisting authorization request across redirect | Custom session map | `HttpSessionOAuth2AuthorizationRequestRepository` (delegate) | Battle-tested; just decorate it |
| Token revoke | Raw HTTP | `GoogleOAuthRevokeClient` (exists) | Already handles 400+invalid_token, privacy logging |
| Watch teardown | Custom Pub/Sub unsubscribe | `gmail.users().stop("me")` | Official Gmail API; already used |
| Duplicate-active prevention | App-level check only | Postgres partial unique index | App check races; index is the backstop |
| Exactly-one-primary | App-level check only | Partial unique `WHERE is_primary=true` | DB-enforced invariant |
| Allow-list arch rule | New ArchUnit scaffolding | Mirror `GmailWriteBoundaryTest` `ArchCondition`/`getMethodCallsFromSelf` | Proven template; consistent style |

**Key insight:** Every mechanism this phase needs already exists in the codebase or the framework. The work is *re-keying and re-scoping* existing seams, not building new infrastructure.

## Runtime State Inventory

> Phase 10 is a schema + code refactor that changes a uniqueness invariant and re-keys an in-memory cache. Runtime state matters here.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `gmail_connections` rows (one per tenant today). Encrypted `refresh_token_encrypted` (AES-GCM, AAD=tenantId). Downstream tables (`gmail_inbox_projection` PK `(tenant_id, gmail_message_id)`, `pubsub_delivery`, `mail_message_observed`, `triage_audit`, `rules`) all tenant-keyed. | Migration: add `is_primary`+`label`, backfill. Tokens byte-identical (no re-encrypt, D-11). **Downstream tables NOT touched in Phase 10** (D-00a) — Phase 11. |
| Live service config | Gmail Pub/Sub **watch** is registered per Google mailbox via `users.watch` (state in `watch_expires_at`/`watch_history_id` columns, renewed by `findConnectionsNeedingWatchRenewal`). Disconnect must call `users.stop` to halt the live watch at Google. | Mailbox-scoped disconnect calls `users.stop("me")` on the targeted mailbox's client (existing pattern). No exported-config drift — watch state lives in the DB row. |
| OS-registered state | None — no Task Scheduler / cron embeds mailbox identity. Watch renewal is a Postgres `SKIP LOCKED` job inside the worker. | None. |
| Secrets/env vars | `INBOX_PROJECTION_KEY_BASE64`, `_SENDER_HASH_KEY_BASE64`, Google `client-id`/`client-secret`. None reference per-mailbox identity. AAD uses `tenantId` (unchanged). | None — verified: cipher AAD stays tenant-based (D-11). |
| Build artifacts | None — no generated code keyed on the connection. OpenAPI regen for new endpoints is explicitly **Phase 11** (VER-02/04 deferred). | None in Phase 10. |
| In-memory runtime cache | `GmailApiClientFactory.accessTokenCache` — `ConcurrentMap` keyed by `tenantId`, lives in the JVM. After migration, two CONNECTED mailboxes in one tenant collide on this key → cross-mailbox token bleed. | **Re-key to `gmailConnectionId`** (D-10). This is the single most important runtime-state change in the phase. |

## Common Pitfalls

### Pitfall 1: Intent attributes silently discarded on callback
**What goes wrong:** Stamping intent in the resolver but reading it (or trying to) from the success-handler's `Authentication` token → always null.
**Why:** `removeAuthorizationRequest` runs at the start of `attemptAuthentication`; the token doesn't carry attributes.
**How to avoid:** Custom `AuthorizationRequestRepository` decorator copies intent to the `HttpSession` on `removeAuthorizationRequest`.
**Warning signs:** Add/reconnect flow falls through to first-login provisioning (re-provisions / replaces the row).

### Pitfall 2: Redis session not persisting the intent attribute
**What goes wrong:** Shim writes the intent to a session map but Redis never persists it → success handler reads null.
**Why:** Mutating an existing session value doesn't mark Spring Session dirty `[CITED: spring-security#7327]`.
**How to avoid:** `session.setAttribute(KEY, new freshSnapshot)` — a fresh value object forces a dirty write.
**Warning signs:** Works with in-memory session (tests) but fails on the Redis-backed dev/prod profile.

### Pitfall 3: `DROP INDEX` on a unique constraint
**What goes wrong:** Rollback or drop fails with "uq_gmail_connections_tenant_id is a constraint."
**Why:** `003` created it via `addUniqueConstraint`, not `createIndex`.
**How to avoid:** `ALTER TABLE ... DROP CONSTRAINT` (and re-`ADD CONSTRAINT` on rollback).

### Pitfall 4: Cross-mailbox access-token bleed
**What goes wrong:** Mailbox B receives mailbox A's cached Gmail access token.
**Why:** Cache keyed by `tenantId`; two mailboxes share a tenant post-migration.
**How to avoid:** Re-key cache (and `InvalidGrantException` eviction) to `gmailConnectionId`. AAD stays tenant-based.
**Warning signs:** Reading mailbox B returns mailbox A's messages — catch with the two-mailbox cache isolation test.

### Pitfall 5: Migration aborts on legacy duplicate data
**What goes wrong:** `CREATE UNIQUE INDEX uq_gmail_conn_active_email` fails because two CONNECTED rows share `(tenant_id, lower(email))`.
**Why:** Legacy/dirty data (shouldn't exist under old invariant, but defense-in-depth).
**How to avoid:** `preConditions onFail=MARK_RAN` sqlCheck before index creation; operator dedupes then re-runs.

### Pitfall 6: ScopedValue not bound before transaction (only relevant if tempted to build the filter early)
**What goes wrong:** Hibernate captures the bootstrap sentinel tenant instead of the real one.
**Why:** Arg-resolvers/interceptors bind inside DispatcherServlet, after the filter chain.
**How to avoid:** D-06 — defer the full `MailboxContext` filter to Phase 11; Phase 10 uses the explicit `resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId)` seam at the service boundary instead.

### Pitfall 7: Logging raw connected email
**What goes wrong:** Restricted-scope/privacy violation in durable logs (copied to Loki).
**Why:** Convenience logging of `google_email` during add/reconnect debugging.
**How to avoid:** Log `tenantId`, `gmailConnectionId`, event name, technical status/reason, optional masked/hashed email only. DB/UI storage of `google_email` is allowed.

## Code Examples

### Resolver: stamp intent as server-side attributes (not additionalParameters)
```java
// Source: docs.spring.io/spring-security/reference/7.0 servlet/oauth2/client/authorization-grants
// (OAuth2AuthorizationRequest.attributes are server-side-only; additionalParameters go on the wire)
return OAuth2AuthorizationRequest.from(authorizationRequest)
        .additionalParameters(Map.copyOf(additionalParameters)) // access_type, prompt — to Google
        .attributes(existing -> {                                // intent — stays server-side
            existing.put("intent", intent);                      // "add_mailbox" | "reconnect_mailbox"
            existing.put("targetMailboxId", targetMailboxId);    // UUID for reconnect, null for add
            existing.put("initiatingTenantId", initiatingTenantId);
        })
        .build();
```

### ArchUnit allow-list rule (D-13, mirrors GmailWriteBoundaryTest)
```java
// Source: backend/core/src/test/java/.../arch/GmailWriteBoundaryTest.java (existing template)
static final List<String> ALLOWED_TENANT_LOOKUP_CALLERS = List.of(
    "com.zeromail.core.triage.usecases.TriageGmailWriter",
    "com.zeromail.core.outbound.usecases.GmailOutboundSendGateway",
    "com.zeromail.core.outbound.usecases.ForwardMessageAssembler",
    "com.zeromail.core.chat.usecases.tools.SearchInboxToolHandler",
    "com.zeromail.core.chat.usecases.tools.ListLabelsToolHandler",
    "com.zeromail.core.chat.usecases.tools.GetThreadToolHandler",
    "com.zeromail.core.chat.usecases.tools.GetMessageToolHandler",
    "com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader",
    "com.zeromail.core.draft.usecases.ToneContextBuilder",
    "com.zeromail.core.draft.usecases.DraftReplySourceLoader");
// ArchCondition over getMethodCallsFromSelf, target = GmailApiClientFactory.buildClientForTenant,
// violated unless javaClass.getName() is in the allow-list. .allowEmptyShould(false)
// (the list is non-empty by construction — these callers exist today; Phase 11 deletes one per migration).
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| One `gmail_connections` row per tenant (`upsert` replaces) | Many rows; `id`=mailbox; `is_primary` marker | Phase 10 | "Add Gmail" stops meaning "replace Gmail" |
| Token cache by `tenantId` | Token cache by `gmailConnectionId` | Phase 10 | No cross-mailbox token bleed |
| `buildClientForTenant` everywhere | `buildClientForMailbox(MailboxRef)`; tenant adapter `@Deprecated` | Phase 10 (callers migrate Phase 11) | Typed mailbox identity; arch-test guarded |
| `?reconnect=true` is the only flow signal | Intent attributes (add/reconnect/first-login) via session shim | Phase 10 | Three distinct OAuth flows |

**Deprecated/outdated:** `buildClientForTenant` → `@Deprecated(forRemoval=true)`, removed in Phase 11 once the allow-list empties.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Auto-promote another CONNECTED mailbox to primary when the primary is disconnected (vs. blocking primary disconnect) | Disconnect state machine | UX/invariant divergence; low blast radius — both are valid, planner/user picks |
| A2 | `label`/`display_purpose` column folded into changeset 119 (vs. separate changeset) | Liquibase migration | Cosmetic; either works. Folding keeps entity+migration atomic |
| A3 | The `api.chat.AssistantPendingActionReconciler` tenant-only caller stays as-is in Phase 10 (out of the `core` ArchUnit scope) | Token cache blast radius | Phase 11 must migrate it; flagged so it isn't forgotten |
| A4 | Gmail `users.stop` is the correct watch-teardown call and revoke endpoint is `oauth2.googleapis.com/revoke` | Disconnect state machine | Low — verified against existing working code, not just docs |

## Open Questions (RESOLVED)

> Both items below were resolved in-plan during Phase 10 planning. Kept here as a decision record; nothing remains open.

1. **Disconnect of the primary mailbox** — RESOLVED (A1: auto-promote).
   - What we knew: GMA-05 says disconnect one mailbox without disconnecting the workspace; `uq_gmail_conn_primary` allows zero primary rows.
   - Was unclear: auto-promote vs. block.
   - **Resolution:** Auto-promote the next CONNECTED mailbox (earliest `connected_at`) to primary in the same disconnect transaction; if none remain, leave zero primary (legal under the partial index). Implemented by `GmailConnectionService.disconnect(MailboxRef)` in **Plan 04 Task 3** (recorded as a must_have and in the plan `<objective>` DECISION note).

2. **`PENDING` status usage in the add flow** — RESOLVED (insert CONNECTED only).
   - What we knew: enum has NOT_CONNECTED/PENDING/CONNECTED/DISCONNECTED.
   - Was unclear: does the add path INSERT a `PENDING` row at flow start (before callback) or only INSERT a `CONNECTED` row in the success handler?
   - **Resolution:** INSERT `CONNECTED` in the OAuth success handler only — a single write with no orphan `PENDING` rows if the user abandons consent; the `uq_gmail_conn_active_email` partial index + the `assertNoActiveDuplicate` pre-check cover duplicate detection without a pre-flow `PENDING` row. Implemented by the add/reconnect success-handler branch in **Plan 05**.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (dev via SSH tunnel :5555) | Liquibase migration test, partial indexes | ✓ (when tunnel up) | 18.x | none — tunnel must be up for OpenAPI regen / migration tests (MEMORY: ssh -L 5555) |
| Redis (local docker) | Spring Session — intent shim persistence test | ✓ | 7.2 | none for the Redis-dirty-session test; in-memory session for unit slices |
| Google OAuth (live) | Real add/reconnect e2e | ✗ in CI | — | mock `OAuth2AuthorizedClientService` + test-profile SecurityConfig slice (WR-06); real e2e is Phase 11 / pre-launch todo |

**Missing dependencies with no fallback:** none blocking — all Phase 10 verification is achievable with Testcontainers Postgres + local Redis + mocked Google.

## Validation Architecture

> nyquist_validation is enabled. The highest-risk invariants (tenant/mailbox isolation, fail-closed ownership, token-cache correctness, migration backfill integrity, AAD continuity) map to concrete automated checks.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test 4.x; Testcontainers Postgres for migration/repo tests; `RestClient + @LocalServerPort` for HTTP (NOT MockMvc — so servlet filters / ScopedValue bind) |
| Config file | Gradle `:backend:core:test` / `:backend:api:test`; skills: `spring-jpa-testing`, `spring-security-testing` encode Boot 4 / Security 7 API specifics |
| Quick run command | `./gradlew :backend:core:test --tests "*GmailConnection*" :backend:core:test --tests "*MailboxArch*"` |
| Full suite command | `./gradlew :backend:core:test :backend:api:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VER-01 / WSP-02 | Migration preserves old single-Gmail tenant; backfills exactly one primary; tokens byte-identical | integration (Testcontainers + Liquibase) | `./gradlew :backend:core:test --tests "*Migration119*"` | ❌ Wave 0 |
| GMA-06 | Two CONNECTED mailboxes coexist; duplicate active email fails closed (23505 → 409) | integration (@DataJpaTest + real DB) | `./gradlew :backend:core:test --tests "*DuplicateActiveEmail*"` | ❌ Wave 0 |
| GMA-03 | Exactly-one-primary enforced; set-primary clears old + sets new transactionally | integration | `./gradlew :backend:core:test --tests "*SetPrimary*"` | ❌ Wave 0 |
| WSP-05/06 | `resolveOwnedConnectionOrThrow` → 404 not-owned/missing, 409 disconnected | integration (RestClient + @LocalServerPort) | `./gradlew :backend:api:test --tests "*MailboxOwnership*"` | ❌ Wave 0 |
| D-10 (token cache) | Cache keyed by gmailConnectionId; mailbox B never gets mailbox A's token | unit (mocked refresh) | `./gradlew :backend:core:test --tests "*GmailApiClientFactory*"` | partial (existing factory tests) |
| D-11 (AAD continuity) | Existing ciphertext decrypts after migration (AAD=tenantId unchanged) | integration | `./gradlew :backend:core:test --tests "*RefreshTokenCipherContinuity*"` | ❌ Wave 0 |
| GMA-07 / WR-06 | Three OAuth intents route through success/failure handlers correctly | slice (test-profile SecurityConfig) | `./gradlew :backend:api:test --tests "*OAuthIntentRouting*"` | ❌ Wave 0 |
| D-01 shim | Intent survives callback via session (incl. Redis-dirty behavior) | integration (Redis Testcontainer or local) | `./gradlew :backend:api:test --tests "*IntentCarryingRepository*"` | ❌ Wave 0 |
| GMA-05 | Disconnect calls users.stop + revoke + status flip; idempotent; primary handling | unit + integration | `./gradlew :backend:core:test --tests "*GmailConnectionServiceDisconnect*"` | partial |
| D-13 | ArchUnit: only allow-list calls `buildClientForTenant`; rule non-empty | arch | `./gradlew :backend:core:test --tests "*GmailClientLookupBoundary*"` | ❌ Wave 0 |
| Privacy | No raw email/token in logs on add/reconnect/disconnect | review + log-assert | existing privacy-sweep pattern (`TriagePrivacySweepTest`) | pattern exists |

### Sampling Rate
- **Per task commit:** the specific `--tests "*<Feature>*"` quick run for the touched area.
- **Per wave merge:** `./gradlew :backend:core:test :backend:api:test`.
- **Phase gate:** full suite green + migration test against a real old-single-account fixture before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `Migration119Test` — Testcontainers Postgres, apply through 119, assert backfill + byte-identical tokens (VER-01)
- [ ] `DuplicateActiveEmailTest` — partial unique index fires; constraint-name → 409 (GMA-06)
- [ ] `SetPrimaryTransactionalTest` + exactly-one-primary index (GMA-03)
- [ ] `MailboxOwnershipSeamTest` — 404/409 contract (WSP-05/06)
- [ ] `GmailApiClientFactoryMailboxCacheTest` — two-mailbox isolation (D-10)
- [ ] `RefreshTokenCipherContinuityTest` — AAD unchanged (D-11)
- [ ] `OAuthIntentRoutingTest` + test-profile SecurityConfig slice (GMA-07 / WR-06)
- [ ] `IntentCarryingRepositoryTest` — session survival + Redis dirty (D-01)
- [ ] `GmailClientLookupBoundaryTest` — ArchUnit allow-list (D-13)
- [ ] Shared fixtures: an "old single-account" `gmail_connections` seed fixture for migration/continuity tests

## Security Domain

> security_enforcement enabled, ASVS level 1.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | OAuth2 bundled Google; framework-owned `state` for CSRF; intent authenticated against live session (never trusted from URL) |
| V3 Session Management | yes | Redis-backed Spring Session; intent shim must dirty session; `HttpOnly`/`SameSite=Lax`/`Secure` cookie (existing) |
| V4 Access Control | yes | `resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId)` fail-closed; prevents IDOR on mailboxId (the rejected `?intent=` approach was an IDOR vector) |
| V5 Input Validation | yes | `targetMailboxId` validated for ownership before any state change; path-param UUID typed |
| V6 Cryptography | yes | AES-GCM via existing `RefreshTokenCipher`; never hand-rolled; AAD continuity preserved (D-11) |
| V13 Token revocation | yes | `users.stop` + OAuth revoke on disconnect (CASA Tier 2 V3.3.1 / V13.1.5 — already encoded) |

### Known Threat Patterns for Spring Security 7 / Gmail OAuth
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR on mailboxId in add/reconnect | Elevation of Privilege | Ownership seam validates `(tenantId, gmailConnectionId)`; intent carried server-side as attributes, not URL |
| Cross-mailbox token bleed | Information Disclosure | Token cache keyed by gmailConnectionId; isolation test |
| Stale OAuth grant after disconnect | Repudiation / Spoofing | `users.stop` + revoke on disconnect (best-effort, ordered) |
| Raw email/token in durable logs | Information Disclosure | Privacy logging posture; masked/hashed only |
| Duplicate active mailbox race | Tampering | Postgres partial unique index (DB-enforced, race-proof) |

## Sources

### Primary (HIGH confidence)
- `/websites/spring_io_spring-security_reference_6_5` (Context7) — `AuthorizationRequestRepository`, `HttpSessionOAuth2AuthorizationRequestRepository` API, `oauth2Login().authorizationEndpoint().authorizationRequestRepository(...)` config
- [Spring Security `OAuth2LoginAuthenticationFilter.java` source](https://github.com/spring-projects/spring-security/blob/main/oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client/web/OAuth2LoginAuthenticationFilter.java) — `removeAuthorizationRequest` timing, token does not carry attributes
- Codebase `[VERIFIED]`: `GmailApiClientFactory.java`, `GmailConnectionService.java`, `GmailConnectionRepository.java`, `GmailConnectionEntity.java`, `GmailConnectionStatus.java`, `SecurityConfig.java`, `GoogleAuthorizationRequestResolver.java`, `GoogleOAuthSuccessHandler.java`, `GoogleOAuthRevokeClient.java`, `GmailWriteBoundaryTest.java`, `TenantBindingFilter.java`, `GmailAccessGuard.java`, `003-create-gmail-connections.yaml`, `042-chat-message-and-body-ban-trigger.yaml`, changeset directory listing (max=118)

### Secondary (MEDIUM confidence)
- [Spring Security #7327 — removeAuthorizationRequest does not dirty the session](https://github.com/spring-projects/spring-security/issues/7327) — Redis-session persistence gotcha
- [Spring Security Authorization Grant Support reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html) — custom repository config
- [OAuth2LoginAuthenticationFilter API docs](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/client/web/OAuth2LoginAuthenticationFilter.html)

### Tertiary (LOW confidence)
- Gmail `users.stop` / `oauth2.googleapis.com/revoke` semantics — cross-checked against working codebase rather than docs alone

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new deps; all mechanisms verified in-repo
- OAuth callback shim: MEDIUM-HIGH — mechanism pinned against Spring Security source + the Redis-dirty gotcha cited; exact `OAuthIntentSnapshot` shape is an implementation detail for the planner
- Liquibase migration: HIGH — changeset number, constraint-vs-index distinction, and raw-SQL style all verified against real files
- Token cache re-key: HIGH — all 14 production call sites enumerated from grep
- Disconnect state machine: HIGH — current ordering verified; only the primary-on-disconnect policy is an open decision (A1)
- Backfill safety: HIGH — failure modes enumerated; preConditions posture defined

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (stable — Spring Security 7 OAuth2 client APIs and the codebase shape are not fast-moving; re-verify only if Spring Boot/Security minor bumps land)

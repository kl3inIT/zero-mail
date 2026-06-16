# Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 18 new/modified targets across backend (Java), DB (Liquibase), and web (Next.js)
**Analogs found:** 18 / 18 (this is a refactor/threading phase — every target already has a tenant-scoped analog in the repo)

> All excerpts below are real code read this session. Paths are repo-relative. Use them directly in `<read_first>` / `<action>` blocks. Java naming follows the enterprise-readability rule (no `req`/`svc`/`ctx`); UI follows AGENTS.md (generated `schema.d.ts`, shadcn-first, raw `DropdownMenu`, token classes only).

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `core/mailbox/MailboxContext.java` (NEW) | context/ScopedValue | request-response | `core/tenant/TenantContext.java` | exact |
| `api/security/MailboxBindingFilter.java` (NEW) | middleware/filter | request-response | `api/security/TenantBindingFilter.java` | exact |
| `api/security/ActiveMailboxResolver.java` (NEW) | service (session read) | request-response | `api/security/GoogleAuthorizationRequestResolver.java` (session attr) | role-match |
| `api/security/SecurityConfig.java` (MOD — register filter) | config | request-response | self (`addFilterAfter(tenantFilter, ...)` line 248) | exact |
| `core/gmail/persistence/lowlevel/PubSubTenantLookupRepository.java` (MOD) | repository | CRUD/lookup | self (`findConnectedTenantIdByEmail`) | exact |
| `core/gmail/usecases/GmailDeliveryProcessingService.java` (MOD) | service | event-driven/streaming | self (tenant-scoped today) | exact |
| `core/gmail/event/MailMessageObserved.java` + `MailOutboundObserved` (MOD) | domain event | pub-sub (Modulith) | self (record carries `tenantId`) | exact |
| Liquibase `120+` mailbox columns (NEW changesets) | migration | batch/DDL | `119-gmail-connections-multi-mailbox.yaml` (HALT + backfill) | exact |
| Liquibase `12x-triage-audit-mailbox` (NEW) | migration | batch/DDL | `086-triage-audit-source.yaml` + `025` idem index | exact |
| `core/rules/persistence/RuleEntity.java` (MOD — add `gmailConnectionId`) | entity | CRUD | self + `021-rules-engine-schema.yaml` | exact |
| Liquibase `12x-rules-mailbox-ownership` (NEW) | migration | batch/DDL | `021` template-key unique index + `119` backfill | exact |
| `core/outbound/usecases/GmailOutboundSendGateway.java` (MOD) | gateway | request-response | self (`buildClientForTenant` → `buildClientForMailbox`) | exact |
| `core/triage/usecases/TriageGmailWriter.java` (MOD) | service | request-response | self (allow-list entry) | exact |
| `core/triage/persistence/TriageAuditWriter.java` (MOD — provenance) | repository/writer | CRUD | self | exact |
| `api/security/GmailAccessGuard.java` (MOD — per-mailbox disconnect) | service | event-driven | self | exact |
| `core/arch/GmailClientLookupBoundaryTest.java` (MOD + NEW rule) | test (ArchUnit) | — | self (drain allow-list; add `findByTenantId` rule) | exact |
| `apps/web/components/shell/AppSidebar.tsx` (MOD — switcher in `AccountMenu`) | component | request-response | self (`AccountMenu`/`ReconnectRow`) | exact |
| `apps/web/features/mailbox/{api,query-keys,hooks}` (NEW) | feature api/hooks | CRUD | `features/gmail/{api/gmail-api,query-keys,hooks/*}` | exact |

---

## Pattern Assignments

### `core/mailbox/MailboxContext.java` (NEW — ScopedValue)

**Analog:** `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` (read all 59 lines)

Mirror exactly. The new ScopedValue holds the `gmailConnectionId` (UUID). Keep `currentOrThrow()`, `currentOptional()`, `runWith(UUID, Runnable)` shapes. Drop the AdminContext mutex unless mailbox is also forbidden in admin scope (it is — admin never operates a mailbox; keep a `requireUnbound()` if symmetry is wanted).

```java
public final class TenantContext {
    public static final ScopedValue<String> TENANT = ScopedValue.newInstance();
    public static UUID currentTenantUuid() { return UUID.fromString(currentOrThrow()); }
    public static void runWith(UUID tenantId, Runnable action) {
        ScopedValue.where(TENANT, tenantId.toString()).run(action);
    }
}
```
**Difference to apply:** `MailboxContext.MAILBOX` should be `ScopedValue<UUID>` directly (no String round-trip — `MailboxRef.gmailConnectionId()` is already a UUID). `runWith(MailboxRef, Runnable)` for the worker/triage async rebind path (triage orchestrator must rebind both tenant + mailbox).

---

### `api/security/MailboxBindingFilter.java` (NEW — binding filter)

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` (read all 60 lines)

Copy the `OncePerRequestFilter` + `ScopedValue.where(...).run(...)` + checked-exception-unwrap structure verbatim. The IOException/ServletException unwrap idiom (lines 43-58) is load-bearing — `ScopedValue.run` only takes a `Runnable`, so the filter wraps `chain.doFilter` and rethrows the unwrapped cause.

```java
// TenantBindingFilter.java:42-58 — copy this exact try/run/unwrap shape
final String tenantId = user.getTenantId().toString();
try {
    ScopedValue.where(TenantContext.TENANT, tenantId)
            .run(() -> {
                try { chain.doFilter(request, response); }
                catch (IOException | ServletException filterException) {
                    throw new RuntimeException(filterException);
                }
            });
} catch (RuntimeException runtimeException) {
    if (runtimeException.getCause() instanceof IOException ioException) throw ioException;
    if (runtimeException.getCause() instanceof ServletException servletException) throw servletException;
    throw runtimeException;
}
```
**Differences to apply (per RESEARCH Pattern 1 + GmailAccessGuard invariant):**
- Guard: `if (!TenantContext.TENANT.isBound()) { chain.doFilter(...); return; }` — must run *after* tenant is bound.
- Resolve `activeMailboxId` via `ActiveMailboxResolver.resolveOrPrimary(request, tenantId)`; if null (no connected mailbox) pass through unbound.
- Bind `MailboxContext.MAILBOX` **before** the JPA transaction opens — see the GmailAccessGuard Javadoc invariant below; do NOT annotate the filter `@Transactional`.

---

### `api/security/GmailAccessGuard.java` (bind-before-Hibernate invariant — READ FIRST, do not skip)

**Analog/Source:** `backend/api/src/main/java/com/zeromail/api/security/GmailAccessGuard.java:32-40`

This Javadoc *is* the rule the new filter must satisfy. Quote it in the plan:

```
/**
 * Invariant: the ScopedValue must be bound BEFORE the JPA transaction opens, so
 * Hibernate's CurrentTenantIdentifierResolver captures the real tenant when the session
 * is created ... this method does not use @Transactional on itself — it would open the
 * transaction before our wrap. Instead it binds the ScopedValue, then uses
 * TransactionTemplate inside the bound scope.
 */
```
This same class is also a **modification target** (AUD-02): the `on(OAuth2TokenRefreshFailed)` listener currently calls `connectionRepository.findByTenantId(tenant)` (the primary shim, lines 55-56) and disconnects only the primary mailbox. Phase 11 must disconnect the **specific** mailbox whose grant failed (the event needs a `gmailConnectionId`, or resolve by failing email). Wrap with `MailboxContext` too if any mailbox-filtered repo read runs inside.

---

### `api/security/SecurityConfig.java` (MOD — register filter)

**Analog:** self — `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java:248`

```java
.addFilterAfter(tenantFilter, AuthorizationFilter.class);
```
Register `MailboxBindingFilter` immediately **after** `tenantFilter` so `TenantContext.TENANT` is already bound when it runs: `.addFilterAfter(mailboxFilter, TenantBindingFilter.class)`. The `tenantFilter` is injected as a method parameter (line 169 pattern) — inject `mailboxFilter` the same way.

---

### `api/security/ActiveMailboxResolver.java` (NEW — session-attr read, D-03)

**Analog (session attribute round-trip):** `IntentCarryingAuthorizationRequestRepository.java:59` (`session.setAttribute`), `GoogleAuthorizationRequestResolver.java:110-114` (`getAttribute`/`removeAttribute`), `ConnectMailboxController.java:68-69` (`PENDING_INTENT_SESSION_ATTRIBUTE` write).

Store `active_gmail_mailbox_id` as a Spring Session attribute (RESEARCH Pattern 2 — lighter fit, reuses Redis session already proven by the OAuth-intent flow). Resolver contract:
1. read session attr; if absent → fall back to tenant primary via `GmailConnectionRepository.findByTenantId(tenantId)` (the primary shim, see below);
2. **re-validate ownership every request** (mailbox may have been disconnected) via the Phase 10 `resolveOwnedConnectionOrThrow` seam / `findByIdAndTenantId`; on miss fall back to primary;
3. never trust the stored value blindly (RESEARCH Pitfall 2).

---

### `core/gmail/persistence/lowlevel/PubSubTenantLookupRepository.java` (MOD — ING-01)

**Analog:** self — `findConnectedTenantIdByEmail` (lines 28-41).

Add `findConnectedMailboxByEmail` returning `Optional<TenantMailboxRef>` (new record `(UUID tenantId, UUID gmailConnectionId)`). Same `JdbcTemplate.query` + `LIMIT 1` + `LOWER(google_email)` + `status='CONNECTED'` shape; just add `id AS gmail_connection_id` to the SELECT and map both columns. `uq_gmail_conn_active_email` (changeset 119) guarantees at most one CONNECTED row per `(tenant, email)`. Empty result → drop delivery safely (ING-01).

```java
// existing — extend SELECT to also return id
jdbcTemplate.query("""
    SELECT tenant_id FROM gmail_connections
    WHERE LOWER(google_email) = ? AND status = 'CONNECTED' LIMIT 1
    """, (resultSet, _) -> resultSet.getObject("tenant_id", UUID.class),
    emailAddress.toLowerCase(Locale.ROOT));
```

---

### `core/gmail/usecases/GmailDeliveryProcessingService.java` (MOD — ingestion thread)

**Analog:** self (read all 499 lines — this is the central ingestion path to thread).

Concrete edits, keyed to current lines:
- **Lines 87-101:** replace `connectionRepository.findByTenantId(tenantId)` + `buildClientForConnection(connection, tenantId)` — resolve the **specific connection from the delivery's `gmailConnectionId`** (delivery row now carries it after ING-02 migration), then `buildClientForMailbox(new MailboxRef(tenantId, gmailConnectionId))`.
- **Line 137:** `updateLastSyncedHistoryIdMonotonic(tenantId, ...)` must key by **connection id**, not tenant (cursor is per-mailbox — see repository note). Add a `...ByConnectionId` variant.
- **Lines 311-322:** `insertObservedIfAbsent(tenantId, msgId, ...)` add `gmailConnectionId` as first key (PK now includes it — ING-03/06).
- **Lines 336-348:** `InboxProjectionUpsertCommand` gains `gmailConnectionId` (PK includes it; AAD UNCHANGED — do NOT touch `InboxProjectionCipher`).
- **Lines 353-378:** `MailMessageObserved`/`MailOutboundObserved` constructors gain `gmailConnectionId`.
- **Log lines (113, 139-142, 158, 363-365, 375-377):** keep `event=... tenantId={}` shape, **add `gmailConnectionId={}`**, never add email/subject/sender (AUD-07).

---

### `core/gmail/event/MailMessageObserved.java` + `MailOutboundObserved` (MOD)

**Analog:** self — `MailMessageObserved.java` (12 lines).

```java
public record MailMessageObserved(
        UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {}
```
Add `UUID gmailConnectionId` (place after `tenantId`). In-core Modulith event — safe to extend within `backend/core`; cross-process api↔worker handoff carries the id via the Postgres `processing_job`/`pubsub_delivery` rows, not the event (CLAUDE.md rule 6). Keep the "never subject/snippet/body/display-name" Javadoc.

---

### Liquibase `120+` add-mailbox-column (NEW changesets) — VER-01/03, ING-02/03/06

**Analog (HALT precondition + deterministic backfill ordering):** `backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml` (read all 67 lines).

The 119 pattern to mirror — `preConditions onFail: HALT` + `sqlCheck expectedResult: 0`, raw `sql: splitStatements: false`, `DISTINCT ON (tenant_id) ... ORDER BY` deterministic primary pick, explicit `rollback`:

```yaml
preConditions:
  - onFail: HALT
  - sqlCheck:
      expectedResult: 0
      sql: |
        SELECT count(*) FROM <table> m
        WHERE NOT EXISTS (SELECT 1 FROM gmail_connections gc WHERE gc.tenant_id = m.tenant_id);
changes:
  - sql:
      sql: |
        UPDATE <table> m SET gmail_connection_id = chosen.id
        FROM ( SELECT DISTINCT ON (tenant_id) tenant_id, id FROM gmail_connections
               ORDER BY tenant_id, is_primary DESC, (status='CONNECTED') DESC, connected_at NULLS LAST, id
        ) chosen
        WHERE chosen.tenant_id = m.tenant_id AND m.gmail_connection_id IS NULL;
```
Apply per table (RESEARCH recommends nullable → backfill → NOT NULL + PK-swap split, see RESEARCH Code Examples for the 3-changeset shape): `pubsub_delivery`, `mail_message_observed`, `gmail_inbox_projection` (+ its 3 indexes), `gmail_inbox_sync_state`, `processing_job` (incl. `INBOX_PROJECTION_BACKFILL` idempotency_key suffix — RESEARCH A4). Next id starts at **120**. Backfill ordering uses `is_primary DESC, (status='CONNECTED') DESC, connected_at NULLS LAST, id` to match the `findPrimaryMailboxCandidatesByTenantId` shim ordering (RESEARCH Pitfall 1). Use append-only files; include each from `db.changelog-master.yaml` (CLAUDE.md rule 10).

---

### Liquibase `12x-triage-audit-mailbox` (NEW) — AUD-01/02

**Analogs:** `086-triage-audit-source.yaml` (addColumn + CHECK + partial index + rollback), and `025-triage-audit.yaml:127-129` (the idempotency index to rebuild).

Add `source_mailbox_id` + `executing_mailbox_id` columns (mirror `086` addColumn shape). The idempotency index must be **dropped and recreated** to include the mailbox (keep `NULLS NOT DISTINCT` for nullable `rule_id`):

```sql
-- current (025:129) — rebuild with gmail_connection_id added
CREATE UNIQUE INDEX ux_triage_audit_idem ON triage_audit
  (tenant_id, gmail_message_id, rule_id, action_type, args_hash) NULLS NOT DISTINCT
```

---

### `core/rules/persistence/RuleEntity.java` (MOD) + `12x-rules-mailbox-ownership` (NEW) — AUTO-01..04, D-04

**Analogs:** `RuleEntity.java` (entity, read all 226 lines) + `021-rules-engine-schema.yaml:212-217` (template-key unique + GIN indexes).

Entity: add `@Column(name = "gmail_connection_id", nullable = false) private UUID gmailConnectionId;` and surface it through the constructor + `toStatusProjection()`. Note `RuleEntity extends AbstractTenantOwnedEntity` (tenant id is inherited) — mailbox is a *second* ownership axis, not a replacement.

Migration: nullable → backfill-to-primary (same 119 ordering) → NOT NULL. **Widen the template-key unique index** so two mailboxes can each materialize the same default template (RESEARCH A2 — required for the UX-05 add-more-mailbox seeding flow + the "default rules seeded on first login" MEMORY note):

```sql
-- 021:214 currently:  (tenant_id, template_key)
CREATE UNIQUE INDEX uq_rules_tenant_template_key_present
  ON rules (tenant_id, template_key) WHERE template_key IS NOT NULL
-- Phase 11 → drop + recreate as (tenant_id, gmail_connection_id, template_key)
```
Runtime triage loads `WHERE gmail_connection_id = :sourceMailbox AND enabled = true`. Copy-rules (D-04) clones `matcher_ast` + `action_intents` JSONB into the target mailbox with `enabled = false` — reuse `replaceDefinition(...)` (lines 172-189). The compiler must NOT infer mailbox from natural language (CLAUDE.md rules); `gmailConnectionId` is structured input, `sourceText` stays metadata.

---

### `core/outbound/usecases/GmailOutboundSendGateway.java` (MOD) — AUTO-06/AUD-02

**Analog:** self (read all 44 lines).

```java
// line 28 — current (breaks with 2 connected mailboxes; throws ">1 connected")
Gmail gmail = gmailApiClientFactory.buildClientForTenant(command.tenantId());
```
Change to `buildClientForMailbox(command.mailboxRef())` — `OutboundSendCommand` gains an executing `MailboxRef` (RESEARCH Pitfall 6). Keep `@AllowedSendCallSite` and the `InvalidGrantException | IllegalStateException → OutboundSendException` mapping (lines 32-34). Undo must target the same executing mailbox (AUD-02). Remove this class from the ArchUnit allow-list once migrated.

**Mailbox-aware client target (for ALL read/write/send migrations):** `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` — `backend/core/.../gmail/gateway/GmailApiClientFactory.java:107-128`. It does `findByIdAndTenantId` (ownership, throws `MailboxNotOwnedException`), `requireConnectedGrant` (409 via `MailboxDisconnectedException`), and caches the access token **per `gmailConnectionId`** (line 55, 194) so two mailboxes never share tokens. The deprecated `buildClientForTenant` (lines 130-158) throws if a tenant has `>1` connected mailbox — that is why every consumer must migrate.

---

### `core/arch/GmailClientLookupBoundaryTest.java` (MOD + NEW rule) — AUD-05

**Analog:** self (read all 85 lines).

Two actions:
1. **Drain** `ALLOWED_TENANT_LOOKUP_CALLERS` (lines 19-32): remove each class name as its caller migrates to `buildClientForMailbox` (`GmailOutboundSendGateway`, `TriageGmailWriter`, `GmailPreviewReadService`, `ForwardMessageAssembler`, etc.). `allowEmptyShould(false)` (line 78) stays — list shrinks toward (but per RESEARCH A3 chat tools may legitimately remain; document them).
2. **Add a complementary rule** forbidding `GmailConnectionRepository.findByTenantId` (the primary shim) in new mailbox-scoped packages — the existing rule only catches `buildClientForTenant`, not the raw shim (RESEARCH Pitfall 2). Mirror the `isTenantLookupCall` / `getMethodCallsFromSelf` condition shape (lines 38-84) with `methodName.equals("findByTenantId")` and a target owner of `GmailConnectionRepository`.

Note the shim itself: `GmailConnectionRepository.findByTenantId` (lines 27-29) delegates to `findPrimaryMailboxCandidatesByTenantId(..., Limit.of(1))` — it silently returns the **primary**, which is exactly the trap the new ArchUnit rule guards.

---

### `apps/web/components/shell/AppSidebar.tsx` (MOD — switcher in `AccountMenu`, D-01/UX-02)

**Analog:** self — `AccountMenu` (lines 118-222) + `ReconnectRow` (lines 224-248).

Merge the switcher into the existing `DropdownMenuContent` (raw shadcn primitives, line 27-35 imports — no custom wrapper per CLAUDE.md rule 13 / "raw shadcn first"). The top identity line (lines 176-191, the Google user from `useCurrentUser`) stays the **workspace identity**; add a new `DropdownMenuGroup` **below the separator** listing connected mailboxes (active marker + primary badge + status), a Switch action, and an "Add Gmail" entry. Do not conflate logged-in user with active mailbox (D-01 — they are different concepts now).

`ReconnectRow` (lines 224-248) currently hard-navigates to the tenant-singular `getApiUrl('/api/tenant/connect-gmail')` (line 238) — make it mailbox-aware (the Phase 10 add/reconnect intent endpoint). Token classes only (`bg-sidebar-accent`, `text-destructive`, etc. — already used; never hex). `data-testid` attributes (`sidebar-footer-account`, `reconnect-gmail-button`) are the Playwright handles — add new ones for switch/add.

---

### `apps/web/features/mailbox/{api,query-keys,hooks}` (NEW — UX-01/02)

**Analog:** `features/gmail/{api/gmail-api.ts, query-keys.ts, hooks/useTenantStatus.ts, hooks/useDisconnectGmail.ts}` (all read).

**API file** (`features/mailbox/api/mailbox-api.ts`) — derive types from generated `schema.d.ts`, use the typed `api.GET`/`api.POST` client, throw on `error || !response.ok || data === undefined`:
```ts
import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';
export type MailboxSummary = components['schemas']['MailboxSummaryResponse']; // Phase 10 DTO, after regen
export async function listMailboxes(): Promise<MailboxSummary[]> {
  const { data, error, response } = await api.GET('/api/gmail/mailboxes', { /* ... */ });
  if (error || !response.ok || data === undefined) throw error ?? new Error(`... ${response.status}`);
  return data;
}
```
**Query keys** (`features/mailbox/query-keys.ts`) — factory shape from `gmail/query-keys.ts`:
```ts
export const mailboxQueryKeys = {
  all: ['mailbox'] as const,
  list: () => [...mailboxQueryKeys.all, 'list'] as const,
  active: () => [...mailboxQueryKeys.all, 'active'] as const,
} as const;
```
**Hooks** — `useMailboxList`/`useActiveMailbox` mirror `useTenantStatus` (useQuery + key + queryFn). `useSetActiveMailbox` mirrors `useDisconnectGmail` (useMutation + `onSuccess` invalidate). **Switch must invalidate all mailbox-scoped query keys** (inbox/needs-reply/rules/audit/analytics) so stale mailbox-A data does not render under mailbox B (RESEARCH Open Question 1) — either include active mailbox id in those keys or invalidate on switch. New mutations use `meta.successMessage`/`meta.errorMessage` (AGENTS.md), not local `toast`.

After backend DTO changes: boot backend → `pnpm --filter web run generate:api` → commit `schema.d.ts` (CLAUDE.md rule 11 / AGENTS.md). Never hand-edit `schema.d.ts`.

---

## Shared Patterns

### ScopedValue bind-before-Hibernate (cross-cutting — the phase keystone)
**Source:** `core/tenant/TenantContext.java`, `api/security/TenantBindingFilter.java:42-58`, `api/security/GmailAccessGuard.java:32-40`
**Apply to:** `MailboxContext`, `MailboxBindingFilter`, and every async rebind (triage orchestrator, worker). Bind the ScopedValue, run `chain.doFilter`/`TransactionTemplate` *inside* the bound scope, unwrap checked exceptions. Never `@Transactional` on the filter.

### Mailbox-aware Gmail client
**Source:** `core/gmail/gateway/GmailApiClientFactory.java:107-128` (`buildClientForMailbox`) + `MailboxRef.java`
**Apply to:** `GmailOutboundSendGateway`, `TriageGmailWriter`, `GmailPreviewReadService`, `RecentInboxReadService`, `InboxBackfillService`, `ForwardMessageAssembler`, `GmailDeliveryProcessingService`, invalid-grant listener. Replace `buildClientForTenant`/`findByTenantId` with a `MailboxRef`-carrying call.

### Liquibase HALT + deterministic backfill
**Source:** `119-gmail-connections-multi-mailbox.yaml` (HALT precondition, `DISTINCT ON` ordering, explicit rollback)
**Apply to:** every `120+` add-mailbox-column changeset. Backfill ordering `is_primary DESC, (status='CONNECTED') DESC, connected_at NULLS LAST, id`. Append-only; include from master (CLAUDE.md rule 10).

### Privacy logging shape
**Source:** `GmailDeliveryProcessingService.java` log lines (e.g. 139-142) + CLAUDE.md convention 5 / AUD-07
**Apply to:** all touched backend paths. `event=<name> tenantId={} gmailConnectionId={}` + technical fields only. Never email/subject/sender/body/token/prompt/completion.

### Frontend feature triad (api + query-keys + hooks) with generated types
**Source:** `features/gmail/*`
**Apply to:** new `features/mailbox/*` and the active-mailbox-aware edits to `features/{inbox,needs-reply,rules,analytics,triage,account}`. Typed `api.*` client from `schema.d.ts`; key factory; one hook per use case; `meta` toasts; token classes.

### Session-attribute round-trip (D-03 storage)
**Source:** `IntentCarryingAuthorizationRequestRepository.java:59`, `GoogleAuthorizationRequestResolver.java:110-114`, `ConnectMailboxController.java:68-69`
**Apply to:** `ActiveMailboxResolver` get/set of `active_gmail_mailbox_id` in the Redis-backed Spring Session.

---

## No Analog Found

None. Every Phase 11 target is a threading/refactor of an existing tenant-scoped analog already in the repo. The only "new" artifacts (`MailboxContext`, `MailboxBindingFilter`, `features/mailbox/*`, `120+` changesets) are direct structural copies of `TenantContext`, `TenantBindingFilter`, `features/gmail/*`, and `119`/`021`/`025`/`086` respectively. No `RESEARCH.md`-only fallback patterns are required.

---

## Metadata

**Analog search scope:** `backend/core/.../{tenant,mailbox,gmail,rules,triage,outbound,inbox}`, `backend/api/.../security`, `backend/core/.../db/changelog/changes`, `backend/core/.../arch`, `apps/web/{components/shell,features/gmail}`
**Files scanned:** ~30 (Glob/Grep), 16 read in full or targeted
**Pattern extraction date:** 2026-06-09

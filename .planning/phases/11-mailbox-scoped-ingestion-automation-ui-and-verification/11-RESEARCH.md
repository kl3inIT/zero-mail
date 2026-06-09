# Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification - Research

**Researched:** 2026-06-09
**Domain:** Multi-mailbox runtime threading (Pub/Sub ingestion → projection → rules → triage → outbound → audit) + web active-mailbox UX, on Java 25 / Spring Boot 4 / Spring AI M7 / PostgreSQL 18 / Liquibase / Next.js 16
**Confidence:** HIGH (all findings derive from direct codebase reads of the exact files this phase modifies; no new external packages; locked stack)

## Summary

Phase 11 is an **internal integration/refactor phase, not a greenfield build**. It threads a stable mailbox id (`gmail_connections.id`) through every downstream table, event, service, request context, and web surface that Phase 10 left tenant-scoped. There are **zero new runtime dependencies** — the work is Liquibase migrations, a second `ScopedValue` + binding filter mirroring the existing `TenantContext`/`TenantBindingFilter`, draining a 12-entry ArchUnit allow-list, adding a `gmail_connection_id` column to rules, and active-mailbox-aware web rendering. [VERIFIED: codebase read — `GmailApiClientFactory`, `TenantBindingFilter`, `RuleEntity`, all changesets]

The single most important enabling finding for **ING-06**: the inbox-projection AES-GCM AAD is `"<tenantId>:<gmailMessageId>:<fieldName>"` and **D-00b locks AAD as tenant-based with no re-encryption**. Adding `gmail_connection_id` to the projection **primary key and indexes does NOT touch the AAD**, so all existing ciphertext stays decryptable. The AAD risk flagged in V1.3-CODE-RESEARCH is therefore *avoided by design* — do not change `InboxProjectionCipher.associatedData(...)`. [VERIFIED: codebase read — `InboxProjectionCipher.java:112-115`; CONTEXT D-00b]

The second key finding: Phase 10 already shipped a **compatibility shim** (`findByTenantId` → `findPrimaryMailboxCandidatesByTenantId(..., Limit.of(1))`) so the 8+ legacy single-row callers operate on the tenant's *primary* mailbox instead of crashing. Phase 11's job is to migrate those callers from "operate on primary" to "operate on the *active/source* mailbox carried in `MailboxContext`/event payloads," then drain the ArchUnit allow-list one caller at a time. [VERIFIED: codebase read — 10-REVIEW-FIX.md CR-01; `GmailClientLookupBoundaryTest.ALLOWED_TENANT_LOOKUP_CALLERS`]

**Primary recommendation:** Build in the CONTEXT D-03 suggested wave order. Migration-first (Wave 1) lands all `gmail_connection_id` columns nullable→backfill-to-primary→NOT NULL as *new* changesets 120+. Store D-03 active-mailbox state as a **Spring Session attribute** (not a new column) — the Redis-backed session is already in use and the OAuth-intent flow already proves session-attribute writes work. Mirror `TenantBindingFilter` exactly for `MailboxBindingFilter`, binding `MailboxContext` immediately *inside* the already-bound `TenantContext` scope and *before* the JPA transaction opens.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pub/Sub `(tenant, mailbox)` resolution (ING-01) | API/Backend (ingestion) | Database (lookup) | Mailbox unknown at envelope arrival; resolved by email→connection lookup before history fetch |
| Per-mailbox history cursor / watch / backfill (ING-02/06) | Backend (worker + gmail usecases) | Database (sync state PK) | Operational state is mailbox-isolated per WSP-07 |
| Inbox projection mailbox PK/index/read (ING-06) | Database (PK + index) | Backend (read/write services) | Same Gmail message id can exist in two mailboxes; PK must disambiguate |
| Active-mailbox request binding (D-03, WSP-06) | Frontend Server / API filter | Spring Session (Redis) | Server-side per-user sticky state; binds before Hibernate session |
| Mailbox-owned rules + copy-rules (AUTO-01..04, D-04) | Backend (rules usecases) + DB column | Frontend (rules UI) | Ownership is a `gmail_connection_id` column; runtime filters candidates by source mailbox |
| Mailbox-aware Gmail writes/sends (AUTO-05/06) | Backend (triage writer, outbound gateway) | — | All write paths route through `buildClientForMailbox(MailboxRef)` |
| Audit mailbox provenance + idempotency (AUD-01/02) | Database (columns + idem index) | Backend (audit saga) | Source + executing mailbox recorded; idempotency key includes mailbox |
| Active-mailbox switcher + settings (UX-01/02/06) | Frontend (Next.js) | API (connected-accounts REST, Phase 10) | Footer AccountMenu merge; set-active mutation + refetch |
| Privacy: no raw email in logs (AUD-07, ING-05) | Backend (logging discipline) | — | Cross-cutting; tenantId + mailboxId + masked/hash only |

## Standard Stack

**No new packages.** This phase adds zero runtime dependencies. The entire stack is already locked in `CLAUDE.md` / `STACK.md` and verified present in the codebase:

| Concern | Existing mechanism (verified in repo) | Phase 11 use |
|---------|---------------------------------------|--------------|
| Request-scoped context | `java.lang.ScopedValue` (`TenantContext.TENANT`) | New `MailboxContext.MAILBOX` ScopedValue |
| Request binding | `OncePerRequestFilter` (`TenantBindingFilter`) | New `MailboxBindingFilter` |
| Active-mailbox storage | Spring Session (Redis, Lettuce) — already wired | Session attribute `active_gmail_mailbox_id` |
| Mailbox-aware Gmail client | `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` (Phase 10) | All read/write/send consumers switch to it |
| Migrations | Liquibase YAML changesets, append-only | Changesets 120+ adding `gmail_connection_id` |
| JSONB matchers | `jsonb_path_ops` GIN (rules table) | Unchanged; add mailbox column alongside |
| Architecture tests | ArchUnit `GmailClientLookupBoundaryTest` | Drain allow-list per migrated caller |
| Crypto | `InboxProjectionCipher` (AES-GCM, AAD tenant-based) | **Unchanged** — AAD stays tenant-based (D-00b) |
| Tests | `RestClient + @LocalServerPort`, Testcontainers singleton, `@DataJpaTest` | Cross-account isolation + migration tests |

**Installation:** none.

**Version verification:** Not applicable — no package additions. All framework versions are locked in `CLAUDE.md` (Spring Boot 4.0.6, Liquibase 5.0.2, Hibernate 7, PostgreSQL 18.4) and were verified by Phase 8/10 probes. [CITED: CLAUDE.md Technology Stack]

## Package Legitimacy Audit

> Not applicable. Phase 11 installs **no external packages** (backend or frontend). It is internal refactoring + schema migration + UI changes against already-vendored dependencies. slopcheck/registry verification is moot. [VERIFIED: codebase read — phase scope is column/context/UI threading]

## Architecture Patterns

### System Architecture Diagram

```
Gmail Pub/Sub push (HTTP POST, OIDC-verified)
        │  email address in payload
        ▼
┌──────────────────────────────────────────────────────────────┐
│ PubSubIngestionService (ack-fast)                              │
│   PubSubTenantLookupRepository.findConnectedTenantIdByEmail    │  ← CHANGE: return (tenantId, gmailConnectionId)
│   → insert pubsub_delivery (tenant_id, gmail_connection_id)    │  ← CHANGE: add column + dedup key
└──────────────────────────────────────────────────────────────┘
        │ delivery row (carries mailbox id)
        ▼
┌──────────────────────────────────────────────────────────────┐
│ GmailDeliveryProcessingService.processDelivery                 │
│   buildClientForMailbox(MailboxRef(tenant, connectionId))      │  ← CHANGE: was findByTenantId + buildClientForConnection
│   history.list from connection.lastSyncedHistoryId             │  ← already per-row (uses the resolved connection)
│   updateLastSyncedHistoryIdMonotonic(connectionId, ...)        │  ← CHANGE: key by connection, not tenant
│   insertObservedIfAbsent(tenant, connectionId, msgId, ...)     │  ← CHANGE: PK includes connection
│   publish MailMessageObserved(tenant, connectionId, ...)       │  ← CHANGE: event carries mailbox
│   publish MailOutboundObserved(tenant, connectionId, ...)      │  ← CHANGE: event carries mailbox
│   inboxProjection.upsert(tenant, connectionId, msgId, ...)     │  ← CHANGE: PK includes connection (AAD UNCHANGED)
└──────────────────────────────────────────────────────────────┘
        │ MailMessageObserved (Spring Modulith event, in-core)
        ▼
┌──────────────────────────────────────────────────────────────┐
│ TriageOrchestratorService                                      │
│   TriageDispatchContext { sourceMailboxId, executingMailboxId }│  ← CHANGE: carry mailbox
│   load enabled rules WHERE gmail_connection_id = sourceMailbox │  ← CHANGE: rules now mailbox-owned
│   TriageGmailWriter.<action>(MailboxRef, ...)                  │  ← CHANGE: buildClientForMailbox
│   GmailOutboundSendGateway.send(cmd{mailboxRef})               │  ← CHANGE: was buildClientForTenant
│   triage_audit (source_mailbox_id, executing_mailbox_id)       │  ← CHANGE: columns + idempotency index
└──────────────────────────────────────────────────────────────┘

Web request (authenticated, Spring Session cookie)
        ▼
TenantBindingFilter  → binds TenantContext.TENANT
        ▼
MailboxBindingFilter → reads session attr active_gmail_mailbox_id
                       (fallback: tenant primary mailbox)
                       validates ownership (resolveOwnedConnectionOrThrow → 404/409)
                       binds MailboxContext.MAILBOX  ← BEFORE Hibernate session opens
        ▼
GmailInboxController / RulesController / AuditController
   read/write the active mailbox (no path change needed for read surfaces)
```

### Pattern 1: Second ScopedValue + binding filter (mirror TenantContext exactly)

**What:** `MailboxContext` is a `ScopedValue<UUID>` (the gmail_connection_id) bound by `MailboxBindingFilter` *inside* the tenant scope.
**When to use:** All read/triage/outbound consumers that today operate on "the tenant's primary mailbox" via the Phase 10 shim.
**Critical invariant (from `GmailAccessGuard` Javadoc, verified):** the ScopedValue MUST be bound *before* the JPA transaction opens, so Hibernate's `CurrentTenantIdentifierResolver` (`ScopedValueTenantResolver`) captures the real value at session creation. The filter binds the ScopedValue then runs `chain.doFilter` inside it — it does NOT use `@Transactional` on itself.

```java
// Source: codebase pattern — backend/api/.../security/TenantBindingFilter.java (mirror)
@Component
public class MailboxBindingFilter extends OncePerRequestFilter {
    // ordered AFTER TenantBindingFilter so TenantContext.TENANT is already bound
    @Override protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {
        if (!TenantContext.TENANT.isBound()) { chain.doFilter(request, response); return; }
        UUID tenantId = TenantContext.currentTenantUuid();
        // read server-side per-user active mailbox; fall back to primary (D-03)
        UUID activeMailboxId = activeMailboxResolver.resolveOrPrimary(request, tenantId);
        if (activeMailboxId == null) { chain.doFilter(request, response); return; } // no connected mailbox
        // ownership validated via Phase 10 seam (404 not-owned, 409 disconnected)
        ScopedValue.where(MailboxContext.MAILBOX, activeMailboxId)
                   .run(() -> { /* chain.doFilter wrapped, IOException/ServletException unwrapped */ });
    }
}
```
Filter ordering must place `MailboxBindingFilter` after `TenantBindingFilter` (use `@Order` or `FilterRegistrationBean`/`SecurityFilterChain` placement consistent with how `TenantBindingFilter` is registered in `SecurityConfig`). [VERIFIED: codebase read — `TenantBindingFilter.java`, `GmailAccessGuard.java:32-40`, `TenantContext.java`]

### Pattern 2: Active-mailbox storage = Spring Session attribute (D-03 lighter fit)

**What:** Store `active_gmail_mailbox_id` as a Spring Session attribute, not a new DB column.
**Why this is the lighter fit (recommendation, MEDIUM confidence — both satisfy the requirement):**
- Redis-backed Spring Session is already in use; the OAuth-intent flow already writes/reads a session attribute (`OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE`), proving the round-trip works including the Redis dirty-write behavior tested in `IntentCarryingRepositoryTest`. [VERIFIED: codebase read — Grep for HttpSession; `IntentCarryingRepositoryTest`]
- No migration, no entity change, no new repository.
- "Sticky across reloads" is satisfied by the session cookie; "sticky across devices" (CONTEXT rationale) is the one tradeoff — a session is per-cookie, so a second device starts at the primary fallback until the user switches. If true cross-device stickiness is required, use a `users.active_gmail_mailbox_id` column instead.
**Tradeoff table:**

| | Session attribute (recommended) | `users.active_gmail_mailbox_id` column |
|---|---|---|
| Migration cost | none | one changeset + entity field |
| Cross-device sticky | no (per session) | yes |
| Survives session expiry | no (falls back to primary) | yes |
| Reuses proven infra | yes (intent flow) | no |
| FK integrity on disconnect | resolver re-validates each request | needs ON DELETE SET NULL / cleanup on disconnect |

Either way the resolver MUST re-validate ownership every request (mailbox may have been disconnected since selection) and fall back to the tenant primary. Do not trust the stored value blindly. [CITED: CONTEXT D-03]

### Pattern 3: Path-stable read surfaces vs. mailbox-segment management surfaces

**What:** Phase 10 management endpoints use `/api/gmail/mailboxes/{gmailConnectionId}/...` (explicit segment). Phase 11 *read* surfaces (`/api/gmail/inbox`, `/api/gmail/inbox/{gmailMessageId}`, rules, audit) can stay **path-stable** and resolve the mailbox from `MailboxContext` (set by the active-mailbox session state). This avoids a large routing/URL rewrite (explicitly rejected in D-03).
**When to use:** Read/list/detail/triage surfaces driven by the active mailbox. Reserve explicit `{gmailConnectionId}` path segments for management actions where the target is not the active mailbox (Phase 10 list/set-primary/disconnect/reconnect, and the copy-rules *source* mailbox).
**Consequence for OpenAPI/FE (VER-02):** read endpoints keep their paths but their *response DTOs* gain mailbox provenance fields (badges per UX-04/UX-06), so OpenAPI regen is still required. [VERIFIED: codebase read — `GmailInboxController.java` `@RequestMapping("/api/gmail/inbox")`; CONTEXT D-03; V1.3-CODE-RESEARCH UI/API section]

### Pattern 4: Mailbox-owned rules + copy-rules clone (D-04)

**What:** Add `gmail_connection_id` (NOT NULL after backfill) to `rules`. Runtime triage loads `WHERE gmail_connection_id = :sourceMailbox AND enabled = true`. Copy-rules is an explicit bulk action cloning the structured `When/Then` schema (matcher_ast + action_intents JSONB) into the target with `enabled = false`.
**When to use:** All rule CRUD, preview/test, and runtime candidate loading.
**Backfill target:** existing rows → the tenant's primary mailbox (mirrors the projection/audit backfill). The `uq_rules_tenant_template_key_present` unique index currently scopes template idempotency per `(tenant_id, template_key)`; with mailbox ownership this should become `(tenant_id, gmail_connection_id, template_key)` so two mailboxes can each materialize the same default template (the "default rules seeded on first login" flow seeds per mailbox). [VERIFIED: codebase read — `021-rules-engine-schema.yaml:213-214`, `RuleEntity.java`; MEMORY default-rules-seeded note]
**Compiler/editor contract (AUTO-02):** owning mailbox id is persisted as structured data; natural-language `source_text` stays metadata only (existing rule). Do not infer mailbox from NL.

### Anti-Patterns to Avoid

- **Changing `InboxProjectionCipher` AAD to include mailbox id.** Existing ciphertext was encrypted with `tenantId:msgId:field`. D-00b locks AAD tenant-based. Adding mailbox to the *PK* is orthogonal and safe; adding it to the *AAD* breaks decryption of every existing row. [VERIFIED: D-00b; `InboxProjectionCipher.java`]
- **Reusing `findByTenantId` for new mailbox-scoped writes.** It is now a single-row shim returning the *primary*. New write paths must take a `MailboxRef`, not silently target primary. The ArchUnit allow-list exists precisely to flag this. [VERIFIED: 10-REVIEW-FIX CR-01; `GmailClientLookupBoundaryTest`]
- **Logging raw email/subject/sender on the now-mailbox-aware paths.** The ingestion service logs `tenantId={} gmailMessageId={}` — keep that shape, add `gmailConnectionId={}`, never add email/subject. [VERIFIED: `GmailDeliveryProcessingService` log lines; AUD-07]
- **One big single backfilling DDL changeset.** Prefer per-table nullable→backfill→NOT NULL split so the HALT precondition pattern (as in 119) can dedupe/diagnose, and so a backfill on a large table doesn't lock DDL + data in one statement. (Discretion item — see Liquibase section.)
- **Crossing api↔worker with Spring events.** `MailMessageObserved`/`MailOutboundObserved` are in-core Modulith events; the worker reads via the Postgres `processing_job`/delivery tables. Adding mailbox id to events is fine within core; cross-process handoff carries mailbox id in the table rows. [VERIFIED: CLAUDE.md rule 6; CONTEXT code_context]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Request-scoped active mailbox | A ThreadLocal or request attribute | `ScopedValue` mirroring `TenantContext` | Virtual-thread safe, matches existing pattern, Hibernate resolver already reads ScopedValues |
| Active-mailbox persistence | New table + cache | Spring Session attribute (or one `users` column) | Redis session already wired; intent flow proves it |
| Mailbox ownership check | Ad-hoc `findByIdAndTenantId` in each controller | Phase 10 `resolveOwnedConnectionOrThrow` (404/409) | Single fail-closed seam, already tested |
| Mailbox Gmail client | `findByTenantId` + `buildClientForConnection` | `buildClientForMailbox(MailboxRef)` | Cache keyed per connection, status re-check, domain exceptions (Phase 10) |
| Enforcing the boundary | Manual code review | ArchUnit `GmailClientLookupBoundaryTest` allow-list | Compile-time-ish gate; drain entries as you migrate |
| Duplicate-active / primary invariants | New constraints | Phase 10 partial unique indexes (`uq_gmail_conn_active_email`, `uq_gmail_conn_primary`) | Already shipped in 119 |

**Key insight:** Phase 11 is almost entirely "wire the Phase 10 primitives into the Phase 9-era tenant-scoped runtime." The hard parts are *correctness of the migration backfill* and *not regressing the privacy/AAD invariants*, not building new abstractions.

## Runtime State Inventory

> This is a refactor/threading phase. The "renamed thing" is the *scope* (tenant → tenant+mailbox). Below is what holds tenant-only state at runtime that Phase 11 must thread or backfill.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data (DB keys/PKs) | `pubsub_delivery (tenant_id, pubsub_message_id)` PK/uniq; `mail_message_observed (tenant_id, gmail_message_id)` PK; `gmail_inbox_projection (tenant_id, gmail_message_id)` PK + 3 indexes; `gmail_inbox_sync_state` PK=`tenant_id`; `triage_audit` idempotency `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)` NULLS NOT DISTINCT; `rules` tenant-scoped + `uq_rules_tenant_template_key_present`; `processing_job` tenant_id nullable + `INBOX_PROJECTION_BACKFILL` idempotency_key=tenantId | **Data migration**: add `gmail_connection_id`, backfill to tenant primary, then include in PK/index/idempotency. New changesets 120+. |
| Live service config | Gmail `users.watch` registration + history cursor live in `gmail_connections` columns (`last_synced_history_id`, `watch_history_id`, `watch_expires_at`, `ingestion_health`) — already **per-row** (per mailbox) since they're on the connections table. | **Code edit only**: ensure watch renewal / history-lost / health updates target the specific connection row, not "primary via shim." No data migration. |
| OS-registered state | None — no OS task scheduler / cron registration embeds tenant or mailbox identity (worker uses Postgres `processing_job` + ShedLock). | None — verified by changeset survey (`017-shedlock`, `081-processing-job-tenant-scope`). |
| Secrets/env vars | `INBOX_PROJECTION_KEY_BASE64`, `_SENDER_HASH_KEY_BASE64`, OAuth refresh-token KEK — all keyed by tenant in AAD, **unchanged** (D-00b). No new secrets. | None — AAD stays tenant-based; refresh-token envelope AAD already uses tenantId per `MailboxRef` Javadoc. |
| Build artifacts | None — no generated artifact embeds mailbox identity. Generated OpenAPI `schema.d.ts` regenerates from backend DTOs (VER-02). | **Regen**: `pnpm --filter web run generate:api` after DTO changes (not a stale-artifact risk, a required step). |

**The canonical question — after every file is updated, what runtime state still has the old (tenant-only) shape?** Answer: every DB row written *before* the Phase 11 migration has no `gmail_connection_id`. The nullable→backfill-to-primary→NOT NULL pattern resolves this. Live Gmail watch/cursor state is already per-connection (good). No external service or OS registration holds the scope. [VERIFIED: full changeset survey + service reads]

## Common Pitfalls

### Pitfall 1: Backfilling NOT NULL on a table whose primary mailbox is ambiguous
**What goes wrong:** A tenant with zero CONNECTED mailboxes (all disconnected) has no primary after the 119 fix (CR-03). Backfilling `gmail_connection_id` NOT NULL for that tenant's old projection/audit rows has no primary to point at.
**Why it happens:** 119 intentionally leaves disconnected-only tenants with no primary.
**How to avoid:** Backfill to "the primary CONNECTED mailbox, else the most-recently-connected mailbox of any status, else the only mailbox." Use a deterministic `DISTINCT ON (tenant_id) ... ORDER BY is_primary DESC, (status='CONNECTED') DESC, connected_at NULLS LAST, id` mirroring the 119 / shim ordering. Add a HALT precondition (like 119) that fails the migration if any target tenant has *zero* `gmail_connections` rows but *has* projection/audit/rule rows, so an operator can investigate rather than the migration crashing on NOT NULL. [VERIFIED: 119 backfill; 10-REVIEW CR-03; `findPrimaryMailboxCandidatesByTenantId` shim ordering]

### Pitfall 2: `findByTenantId` shim silently routing a new write to primary
**What goes wrong:** A newly written mailbox-scoped path calls `findByTenantId` (the shim) and silently writes to the *primary* mailbox even when the active/source mailbox is a secondary. No crash, wrong data.
**How to avoid:** New paths take `MailboxRef`/`MailboxContext`. The ArchUnit allow-list catches `buildClientForTenant` but NOT raw `findByTenantId` calls — add a complementary ArchUnit rule (AUD-05) forbidding `GmailConnectionRepository.findByTenantId` in new mailbox-scoped packages, or convert each migrated caller and assert it no longer appears. [VERIFIED: 10-REVIEW-FIX CR-01 shim; `GmailClientLookupBoundaryTest` only covers `buildClientForTenant`]

### Pitfall 3: Same Gmail message id collision across two mailboxes
**What goes wrong:** Gmail message ids are unique per mailbox, not globally. Before the PK change, mailbox B's message with the same id as mailbox A's would `ON CONFLICT DO NOTHING` against A's observed/projection row, silently dropping B's mail.
**How to avoid:** `gmail_connection_id` MUST be in the PK of `mail_message_observed` and `gmail_inbox_projection`, and in the `triage_audit` idempotency index, before two mailboxes are CONNECTED for any tenant. This is the AUD-06 isolation test's core assertion: "same Gmail message id in two mailboxes creates two observed/projection/audit rows." [VERIFIED: V1.3-CODE-RESEARCH verification focus; table PKs]

### Pitfall 4: Postgres connection-pool exhaustion in `:backend:core:test`
**What goes wrong:** The Testcontainers singleton caches ~15 Spring contexts; full `:backend:core:test` flakes on connection exhaustion on the dev machine. A phase that adds many new `@SpringBootTest`/`@DataJpaTest` slices makes this worse.
**How to avoid:** Prefer `@DataJpaTest` (lighter, shares the JPA slice context) for repository/migration/idempotency tests over full `@SpringBootTest`. Reuse existing base classes (`PostgresContainerTest`, `ApiPostgresTestBase`) so contexts are shared, not multiplied. Run focused scopes (`--tests`) when verifying. For the cross-account isolation test, one `@SpringBootTest` exercising two CONNECTED mailboxes through `RestClient + @LocalServerPort` is enough — do not spread it across many contexts. [VERIFIED: CONTEXT code_context; `PostgresContainerTest`, `ApiPostgresTestBase`]

### Pitfall 5: `@DataJpaTest` false-pass on migration/idempotency assertions
**What goes wrong:** Asserting a new PK/idempotency conflict without `flush()`+`clear()` reads from Hibernate L1 cache — the conflict never hits the DB and the test passes falsely.
**How to avoid:** Per `spring-jpa-testing` SKILL: `TestEntityManager.persistAndFlush(...)` then `em.clear()` before the read/conflict assertion. For the duplicate-active-email and same-message-two-mailboxes assertions, force a real DB round-trip. (This also closes the WR-02 test gap residual.) [VERIFIED: `spring-jpa-testing/SKILL.md`; 10-REVIEW-FIX WR-02 residual]

### Pitfall 6: Outbound send still using `buildClientForTenant`
**What goes wrong:** `GmailOutboundSendGateway.send` currently calls `buildClientForTenant(command.tenantId())` — with two CONNECTED mailboxes this throws (`> 1 connected`) or sends from the wrong mailbox via the single-tenant guard.
**How to avoid:** `OutboundSendCommand` must carry the executing `MailboxRef`; the gateway calls `buildClientForMailbox`. This is AUTO-06 + AUD-02 (undo targets the same executing mailbox). It is also on the ArchUnit allow-list to drain. [VERIFIED: `GmailOutboundSendGateway.java:28`; allow-list entry]

## Code Examples

### Adding a mailbox column: nullable → backfill-to-primary → NOT NULL (recommended split)
```yaml
# Source: codebase pattern — mirrors 119 HALT precondition + backfill ordering
# Changeset 120-a: add nullable column
- changeSet:
    id: 120-mail-message-observed-mailbox-add-column
    author: zeromail
    changes:
      - addColumn:
          tableName: mail_message_observed
          columns:
            - column: { name: gmail_connection_id, type: uuid }   # nullable for now
# Changeset 120-b: backfill to the tenant's primary (else most-recent) mailbox
- changeSet:
    id: 120-mail-message-observed-mailbox-backfill
    author: zeromail
    preConditions:
      - onFail: HALT
      - sqlCheck:
          expectedResult: 0
          sql: |
            SELECT count(*) FROM mail_message_observed m
            WHERE NOT EXISTS (SELECT 1 FROM gmail_connections gc WHERE gc.tenant_id = m.tenant_id);
    changes:
      - sql:
          sql: |
            UPDATE mail_message_observed m SET gmail_connection_id = chosen.id
            FROM (
              SELECT DISTINCT ON (tenant_id) tenant_id, id FROM gmail_connections
              ORDER BY tenant_id, is_primary DESC, (status='CONNECTED') DESC, connected_at NULLS LAST, id
            ) chosen
            WHERE chosen.tenant_id = m.tenant_id AND m.gmail_connection_id IS NULL;
# Changeset 120-c: enforce NOT NULL + swap PK to include the mailbox
- changeSet:
    id: 120-mail-message-observed-mailbox-notnull-pk
    author: zeromail
    changes:
      - addNotNullConstraint: { tableName: mail_message_observed, columnName: gmail_connection_id }
      - dropPrimaryKey: { tableName: mail_message_observed, constraintName: pk_mail_message_observed }
      - addPrimaryKey:
          tableName: mail_message_observed
          columnNames: tenant_id, gmail_connection_id, gmail_message_id
          constraintName: pk_mail_message_observed
    rollback:
      # explicit roll-forward-safe rollback per CLAUDE.md rule 10
      - ...
```
Notes: keep each table's add/backfill/notnull as separate changeset ids under one file or sequentially numbered files; include each from `db.changelog-master.yaml`; use raw `sql:` for partial/expression index rebuilds (e.g. the projection list index becomes `(tenant_id, gmail_connection_id, received_at DESC, gmail_message_id DESC) WHERE inbox_state='INBOX'`). The triage_audit idempotency `CREATE UNIQUE INDEX ... NULLS NOT DISTINCT` must be dropped and recreated with `gmail_connection_id` added (keep NULLS NOT DISTINCT for nullable rule_id). [VERIFIED: 119 pattern; 108 projection indexes; 025 idempotency index]

### Pub/Sub lookup returning (tenant, mailbox)
```java
// Source: codebase — extend PubSubTenantLookupRepository.findConnectedTenantIdByEmail
public Optional<TenantMailboxRef> findConnectedMailboxByEmail(String emailAddress) {
    return jdbcTemplate.query("""
        SELECT tenant_id, id AS gmail_connection_id
        FROM gmail_connections
        WHERE LOWER(google_email) = ? AND status = 'CONNECTED'
        LIMIT 1
        """,
        (rs, _) -> new TenantMailboxRef(
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("gmail_connection_id", UUID.class)),
        emailAddress.toLowerCase(Locale.ROOT)).stream().findFirst();
}
// ING-01: when this returns empty, the delivery is dropped safely (no cross-account processing).
// The partial-unique uq_gmail_conn_active_email guarantees at most one CONNECTED row per (tenant, email).
```
[VERIFIED: `PubSubTenantLookupRepository.java`; 119 `uq_gmail_conn_active_email`]

## State of the Art

| Old Approach (pre-Phase 11) | Current Approach (Phase 11 target) | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `findByTenantId` returns the single connection | Shim returns *primary* (10) → migrate callers to `MailboxRef` (11) | Phase 10→11 | Removes crash, then removes shim reliance |
| Tenant-only event payloads | `MailMessageObserved`/`MailOutboundObserved`/`TriageDispatchContext` carry mailbox id | Phase 11 | Triage + audit get provenance |
| Projection/observed/audit PK = `(tenant, msgId)` | PK includes `gmail_connection_id` | Phase 11 | Cross-mailbox id collisions impossible |
| Rules tenant-scoped, all run per tenant | Rules `gmail_connection_id`-owned; runtime filters by source mailbox | Phase 11 | AUTO-01/04 isolation |
| Outbound `buildClientForTenant` | `buildClientForMailbox(MailboxRef)` | Phase 11 | AUTO-06/AUD-02 |

**Deprecated/outdated:**
- `GmailApiClientFactory.buildClientForTenant` — `@Deprecated(forRemoval = true)`; allow-list drains to empty as callers migrate. Some chat-tool callers (GetMessage/GetThread/SearchInbox/ListLabels) may legitimately remain tenant-primary if chat is not yet multi-mailbox — decide per-caller, document any that stay. [VERIFIED: `GmailApiClientFactory.java:130`; allow-list]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Session attribute is the lighter D-03 fit vs. a `users` column | Pattern 2 | Cross-device stickiness lost; if PM wants it, use the column (low risk — CONTEXT explicitly delegates this choice) |
| A2 | `uq_rules_tenant_template_key_present` should widen to include `gmail_connection_id` for per-mailbox default-rule seeding | Pattern 4 | If left tenant-scoped, second mailbox can't materialize the same default template — would block UX-05 add-more flow |
| A3 | Chat-tool tenant-lookup callers (GetMessage/GetThread/etc.) may stay on `buildClientForTenant`-via-primary in v1.3 if chat is single-active-mailbox | State of the Art | If chat must be mailbox-aware, more allow-list entries to drain than estimated |
| A4 | `processing_job` `INBOX_PROJECTION_BACKFILL` idempotency_key (currently tenantId) needs mailbox suffix | Runtime State Inventory | If left tenant-only, a second mailbox's backfill is deduped away and never runs |

**These four are the highest-value items for discuss-phase to confirm before planning locks.**

## Open Questions

1. **Does the active-mailbox switch need to invalidate/refetch which client caches?**
   - What we know: TanStack Query caches per query-key; switching active mailbox changes server-resolved scope without a URL change.
   - What's unclear: which query keys must reset on switch (inbox, needs-reply, rules, audit, analytics) so stale mailbox A data doesn't show under mailbox B.
   - Recommendation: include the active mailbox id in the query keys for all mailbox-scoped features, OR call `queryClient.clear()`/invalidate the mailbox-scoped keys on switch (Inbox Zero resets SWR cache on account change — same idea). Decide in planning; lean toward keying.
2. **Is chat assistant in-scope for active-mailbox in v1.3?**
   - What we know: chat tools call `buildClientForTenant` (allow-listed). CONTEXT lists rules/inbox/needs-reply/audit/analytics surfaces, not chat, for active-default rendering.
   - Recommendation: treat chat as out-of-scope for mailbox-awareness in v1.3 unless discuss-phase says otherwise; document the remaining allow-list entries as intentional.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (dev via SSH tunnel :5555) | All migrations, tests | ✓ (when tunnel up) | 18.x dev | none — tunnel down blocks Liquibase/boot (MEMORY note) |
| Redis (local docker) | Spring Session active-mailbox attr | ✓ | 7.x | none — required for session |
| Testcontainers (Docker) | `:backend:core:test`, isolation tests | ✓ | singleton, ~15-context ceiling | run focused `--tests` scopes |
| Node/pnpm | OpenAPI regen (VER-02), Playwright (VER-04) | ✓ | per repo | none |
| Real Gmail mailbox(es) on dev VPS | VER-04 smoke (connect→ingest→switch→send) | ⚠ manual | — | folded Phase 8 todo; needs ≥2 real Gmail grants on dev |

**Missing dependencies with no fallback:** none blocking code; VER-04 real-Gmail smoke needs two real Gmail accounts connected on the dev VPS (manual, human-driven) — plan a `checkpoint:human-verify` for it.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Testcontainers (singleton Postgres) + ArchUnit (backend); Vitest + Playwright (web) |
| Config file | Gradle `:backend:core` / `:backend:api` test source sets; `apps/web` vitest + `e2e/` Playwright |
| Quick run command | `./gradlew :backend:core:test --tests "*MailboxIsolation*"` (focused — avoids connection ceiling) |
| Full suite command | `./gradlew :backend:core:test :backend:api:test` then `pnpm --filter web test && pnpm --filter web exec playwright test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ING-01 | Pub/Sub resolves (tenant, mailbox); unknown drops safely | integration | `./gradlew :backend:core:test --tests "*PubSubMailboxLookup*"` | ❌ Wave 0 |
| ING-03/06 | same msg id in 2 mailboxes → 2 observed/projection rows | @DataJpaTest | `./gradlew :backend:core:test --tests "*ObservedMailboxPk*"` | ❌ Wave 0 |
| ING-06 | existing projection ciphertext still decrypts after PK change (AAD unchanged) | @DataJpaTest | `./gradlew :backend:core:test --tests "*ProjectionAadContinuity*"` | ❌ Wave 0 |
| VER-01/03 | migration 120+ backfills old rows to primary; HALT on tenant w/o connection | migration test | `./gradlew :backend:core:test --tests "*Migration12*"` | ❌ Wave 0 |
| AUTO-04 | runtime triage loads only source-mailbox rules | integration | `./gradlew :backend:core:test --tests "*MailboxOwnedRules*"` | ❌ Wave 0 |
| AUTO-06/AUD-02 | outbound sends via executing mailbox; undo targets same mailbox | integration | `./gradlew :backend:core:test --tests "*OutboundMailbox*"` | ❌ Wave 0 |
| AUD-05 | ArchUnit forbids tenant-only lookup in mailbox-scoped flows | ArchUnit | `./gradlew :backend:core:test --tests "*GmailClientLookupBoundary*"` | ✅ extend allow-list drain |
| AUD-06 | two CONNECTED mailboxes — crafted id cannot read/write/send cross-account (covers CR-01 shim gap) | @SpringBootTest | `./gradlew :backend:api:test --tests "*CrossAccountIsolation*"` | ❌ Wave 0 |
| WSP-02/duplicate | partial-unique violation → 409 (closes WR-02 residual) | @DataJpaTest | `./gradlew :backend:core:test --tests "*DuplicateActiveEmail*"` | ✅ extend |
| AUD-07 | logs emit no raw email/subject/body/token on mailbox paths | review/log assert | `./gradlew :backend:core:test --tests "*PrivacyLog*"` or manual review | ⚠ partial |
| VER-02 | OpenAPI regen + FE generated types | build step | `pnpm --filter web run generate:api` (boot backend first) | n/a step |
| VER-04 | connect/list/switch/rules/send-from/audit in browser + 1 real-Gmail smoke | Playwright | `pnpm --filter web exec playwright test e2e/mailbox-*.spec.ts` | ❌ Wave 0 + manual smoke |

### Sampling Rate
- **Per task commit:** focused `--tests` for the touched invariant (avoid full `:backend:core:test` due to connection ceiling).
- **Per wave merge:** the wave's invariant suite + `GmailClientLookupBoundary` ArchUnit (catches un-drained callers).
- **Phase gate:** full backend + web suites green; OpenAPI regenerated and committed; VER-04 Playwright + manual real-Gmail smoke before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `*/PubSubMailboxLookupTest.java` — covers ING-01
- [ ] `*/ObservedMailboxPkTest.java`, `ProjectionMailboxPkTest.java` — covers ING-03/06 collision
- [ ] `*/ProjectionAadContinuityTest.java` — proves AAD-unchanged decryption (ING-06)
- [ ] `*/Migration12xBackfillTest.java` + reuse `OldSingleAccountFixture` — covers VER-01/03
- [ ] `*/MailboxOwnedRulesRuntimeTest.java` — covers AUTO-04
- [ ] `*/OutboundMailboxRoutingTest.java`, `UndoSameMailboxTest.java` — AUTO-06/AUD-02
- [ ] `backend/api/.../CrossAccountIsolationTest.java` (two CONNECTED mailboxes, RestClient+@LocalServerPort) — AUD-06 + CR-01 shim gap
- [ ] New ArchUnit rule forbidding `GmailConnectionRepository.findByTenantId` in new mailbox-scoped packages (complements existing `buildClientForTenant` rule) — AUD-05
- [ ] `apps/web/e2e/mailbox-switch.spec.ts`, `mailbox-rules.spec.ts`, `mailbox-send-from.spec.ts` — VER-04
- [ ] Frontend feature hooks: `useMailboxList`, `useActiveMailbox`/`useSetActiveMailbox` (Vitest)

## Security Domain

> `security_enforcement: true`, ASVS L1, block_on: high. [VERIFIED: config.json]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Mailbox isolation boundary (WSP-07); fail-closed mailbox resolution (WSP-05) |
| V4 Access Control | yes | `resolveOwnedConnectionOrThrow` 404/409 ownership seam on every mailbox-scoped request; ArchUnit boundary (AUD-05/06) |
| V5 Validation/Sanitization | yes | Mailbox id is a server-resolved UUID from session/primary — never trust a client-supplied mailbox id without the ownership seam |
| V7 Error/Logging | yes | AUD-07: no raw email/subject/body/token in logs; tenantId+mailboxId+masked/hash only |
| V6 Cryptography | yes | AAD stays tenant-based (D-00b); do not hand-roll re-encryption; `InboxProjectionCipher` unchanged |
| V8 Data Protection | yes | ING-05: no long-term raw body/prompt/completion/embedding; connected email storable in DB/UI, not logs |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Crafted `gmailConnectionId` to read/write another mailbox | Elevation / Info disclosure | `resolveOwnedConnectionOrThrow` (404 not-owned, 409 disconnected); AUD-06 isolation test |
| Cross-account cursor poisoning (mailbox A delivery advances B's history) | Tampering | Per-mailbox history cursor keyed by connection id; monotonic update by connection (ING-02) |
| Same Gmail msg id collision silently drops mail | Tampering / DoS | `gmail_connection_id` in observed/projection PK (ING-03/06) |
| Disconnected mailbox still usable via cached token | Elevation | Phase 10 `evictAccessToken` + status re-check in `buildClientForConnection` (already shipped) |
| Raw email leaking to Loki/Sentry | Info disclosure | AUD-07 logging discipline; restricted-scope posture from V1.3-CODE-RESEARCH |
| Outbound send from wrong mailbox | Spoofing | `OutboundSendCommand` carries executing `MailboxRef`; single send gateway; AllowedSendCallSite |

## Project Constraints (from CLAUDE.md)

- **Liquibase append-only (rule 10):** new changesets 120+ only; never edit 119 or earlier; explicit rollback; `preConditions onFail: HALT` for data-dependent backfills; raw `sql:` for partial/expression indexes; include from master.
- **Generated OpenAPI never hand-edited (rule 11):** after DTO changes, boot backend → `pnpm --filter web run generate:api` → commit. VER-02.
- **Outbound gateway boundary:** all Gmail send goes through `GmailOutboundSendGateway` / `OutboundSendGateway`; `@AllowedSendCallSite`; no direct send call sites.
- **Modulith vs direct call (rule 6):** `MailMessageObserved`/`MailOutboundObserved` are in-core after-commit events; api↔worker handoff uses Postgres tables; mailbox id rides in both.
- **Privacy (ING-05/AUD-07):** no raw body/prompt/completion/embedding storage; no raw email/subject/sender/token in logs; `event=<name> tenantId={} gmailConnectionId={}` shape only.
- **No Lombok, no WebFlux, virtual threads, Jakarta-only, no polling Gmail, no pgcrypto for tokens, AES-GCM app-layer crypto.**
- **Enterprise naming:** `gmailConnectionId`, `mailboxRef`, `tenantContext` — no `req`/`svc`/`ctx`/`repo` abbreviations.
- **UI (rule 13 + AGENTS.md):** no global UI skill; shadcn-first; raw `DropdownMenu` primitives for the AccountMenu switcher (D-01); tokens not hex; TanStack Query `meta` toasts; feature API from generated `schema.d.ts`; verify in real browser (Playwright MCP).
- **Tests:** `RestClient + @LocalServerPort` (not MockMvc) so filters/ScopedValue bind; `@DataJpaTest` + `flush()/clear()` for repository/migration; mind the ~15-context Testcontainers connection ceiling — run focused scopes.

## Sources

### Primary (HIGH confidence — direct codebase reads, this session)
- `backend/core/.../tenant/TenantContext.java`, `ScopedValueTenantResolver.java` — ScopedValue pattern to mirror
- `backend/api/.../security/TenantBindingFilter.java`, `GmailAccessGuard.java` — binding filter + bind-before-Hibernate invariant
- `backend/core/.../gmail/gateway/GmailApiClientFactory.java`, `MailboxRef.java` — mailbox-aware client + cache + deprecated tenant adapter
- `backend/core/.../inbox/usecases/InboxProjectionCipher.java` — AAD `tenantId:msgId:field` (AAD stays tenant-based → projection PK change is safe)
- `backend/core/.../gmail/usecases/GmailDeliveryProcessingService.java`, `PubSubTenantLookupRepository.java` — ingestion path to thread
- `backend/core/.../outbound/usecases/GmailOutboundSendGateway.java` — still uses `buildClientForTenant` (must migrate)
- `backend/core/.../rules/persistence/RuleEntity.java`, `021-rules-engine-schema.yaml` — rules tenant-scoped + template-key unique index
- Changesets `011`, `012`, `108`, `025`, `081-processing-job-tenant-scope`, `086-triage-audit-source`, `119`, `db.changelog-master.yaml` — table shapes, next id = 120
- `backend/core/.../arch/GmailClientLookupBoundaryTest.java` — 12-entry allow-list to drain
- `apps/web/components/shell/AppSidebar.tsx` — AccountMenu / ReconnectRow to extend (D-01)
- `.claude/skills/spring-jpa-testing/SKILL.md` — flush/clear discipline
- `.planning/config.json` — nyquist + security enforcement enabled

### Secondary (MEDIUM confidence — phase docs)
- `11-CONTEXT.md` (D-01..D-04, scope), `10-CONTEXT.md` (D-00a/b, D-06, D-10..13), `10-REVIEW.md` + `10-REVIEW-FIX.md` (CR-01 shim, residuals), `REQUIREMENTS.md`, `ROADMAP.md`, `V1.3-CODE-RESEARCH.md`
- MEMORY notes: default-rules-seeded-first-login, dev-inbox-projection-keys, dev-db-ssh-tunnel, phase8-backend-stack-verified

### Tertiary (LOW confidence)
- None — all claims traced to repo or phase docs.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all mechanisms read directly from repo.
- Architecture (filter/ScopedValue/migration): HIGH — mirrors existing verified `TenantContext`/`TenantBindingFilter`; AAD safety confirmed by code + D-00b.
- Pitfalls: HIGH — derived from Phase 10 review residuals + actual table PKs + connection-ceiling note.
- D-03 storage choice: MEDIUM — recommendation (session attr) is sound but the column alternative is equally valid; CONTEXT delegates the choice.

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (stable — internal refactor against locked stack; re-verify only if Phase 10 residuals change or chat goes multi-mailbox)

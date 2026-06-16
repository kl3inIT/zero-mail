# Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 11 is the **final integration phase** of v1.3. It threads the **active Gmail mailbox scope** through the entire runtime (Pub/Sub ingestion → projection → rules → triage → outbound → audit) and the web app, building on the Phase 10 mailbox foundation (multi-mailbox `gmail_connections`, `MailboxRef`, `buildClientForMailbox`, ownership seam, OAuth intent split). After this phase a user can connect several Gmail mailboxes, switch the active one quickly, and each mailbox stays operationally isolated (no cross-account read/write/send).

**Requirements (locked, sourced from REQUIREMENTS.md — no SPEC.md):** ING-01..06, AUTO-01..06, AUD-01/02/03/05/06/07, UX-01..06, VER-02/03/04 (27 requirements).

**In scope:**
- Pub/Sub delivery resolves `(tenant, mailbox)`; unknown-mailbox delivery fails/drops safely (ING-01).
- Mailbox scope added to downstream tables/events: `pubsub_delivery`, `mail_message_observed` / `MailMessageObserved`, `MailOutboundObserved`, processing jobs, inbox sync state, inbox projection, `triage_audit`, rules (ING-02/03, WSP-03 propagation). Per-mailbox history cursor, backfill, watch renewal, ingestion health.
- Inbox projection PK/index/cursor/read/detail/thread paths include mailbox id (ING-06).
- `MailboxContext` ScopedValue + servlet filter (the Phase 10 locked end-state mechanism) built against real consumers; resolves active mailbox **server-side per user** (D-03), fail-closed via the Phase 10 ownership seam.
- Mailbox-owned rules end to end: compiler, manual editor contract, persistence, preview/test, runtime triage, UI (AUTO-01..04, UX-03). Explicit **copy-rules** action across mailboxes (D-04).
- Mailbox-aware Gmail actions + outbound: `TriageGmailWriter`, `GmailOutboundSendGateway`, forward/reply assemblers, undo/revert, audit saga (AUTO-05/06, AUD-01/02).
- Audit/safety mailbox provenance: `triage_audit` source + executing mailbox, idempotency includes mailbox, sender safety-net stays tenant-owned with triggering-mailbox metadata (AUD-01/02/03/07).
- ArchUnit + cross-account isolation tests (AUD-05/06); deletes Phase 10 `buildClientForTenant` allow-list entries per migrated consumer until empty.
- Web app: connected-accounts settings surface (UX-01), **active-mailbox switcher merged into the footer AccountMenu** (D-01, UX-02), active-mailbox-default rendering on inbox/needs-reply/rules/audit/analytics, send/write previews show source + executing mailbox (UX-06), onboarding keeps one-Gmail simple + add-more after first connect (UX-05).
- OpenAPI regen + generated FE types for new/changed endpoints (VER-02); backend invariant tests (VER-03); Playwright real-browser flows + one real-Gmail smoke (VER-04).

**Out of scope (deferred / future):**
- Unified all-mailboxes inbox / cross-mailbox roll-up view (D-02 — deferred to future; v1.3 is active-mailbox-only).
- Team collaboration (TEAM-*), Zalo OA / CRM / omnichannel (CHAN-*), Microsoft provider.
- Operational carry-forwards OPS-FUT-01..04 (shadow-mode toggle, paste-import safety-net, per-entry protect/escalate, GA gate) unless already in-scope elsewhere.
- AAD re-encryption / AAD versioning (locked out by Phase 10 D-00b — AAD stays `tenantId`-based).

</domain>

<decisions>
## Implementation Decisions

### Active-mailbox switcher placement (UX-02) — D-01
- **D-01:** The switcher is **merged into the existing footer `AccountMenu`** in `components/shell/AppSidebar.tsx` (not a new sidebar-header switcher, not a per-page top bar). The dropdown must now express **two distinct concepts**: the top identity line stays the **workspace/Google user** (current behavior, profile read transiently from OAuth session), and a **mailbox list section** below it lists connected Gmail mailboxes with active marker + primary badge + status, a "Switch" affordance, and an "Add Gmail" entry. Do not conflate "logged-in user" with "active mailbox" — they are now different. Keep raw shadcn `DropdownMenu` primitives (no custom wrapper) per project convention.

### Unified inbox / all-mailboxes scope (ING-04, UX-04) — D-02
- **D-02:** v1.3 is **active-mailbox-only**. Every surface (inbox, needs-reply, rules, audit, analytics) renders the single active mailbox by default. **No unified/all-mailboxes roll-up view is built in v1.3** — matches REQUIREMENTS' "any *future* all-mailboxes roll-up" language and avoids the UX-06 "must select a concrete mailbox before acting" complexity. The ING-04/UX-04 read-only-roll-up constraints remain as *guardrails for the future*, not a v1.3 deliverable. Action context is always unambiguous because there is exactly one active mailbox.

### Active-mailbox state persistence (foundation for MailboxContext filter) — D-03
- **D-03:** "Active mailbox" is **server-side per-user state** (stored in the cookie+Redis-backed Spring Session, or a small per-user persisted column — planner/researcher to pick the lighter fit). The `MailboxContext` ScopedValue servlet filter resolves the active `gmailMailboxId` from this server state, **falling back to the tenant's primary mailbox** when none is set. Switching = one mutating call to set active mailbox, then client refetch. Rationale: sticky across devices/reloads, reuses the existing session infra, and keeps the request-binding mechanism consistent with `TenantBindingFilter` (no per-page URL/routing rewrite). Rejected client-side URL/localStorage (not sticky cross-device, large routing surface change).
- Ownership is still validated by the Phase 10 `resolveOwnedConnectionOrThrow` seam (404 not-owned/missing, 409 disconnected). The filter must bind **before** the Hibernate session opens (the `GmailAccessGuard` invariant) — mirror `TenantContext` + `TenantBindingFilter` exactly.

### Cross-mailbox rules reuse (AUTO-01, UX-03) — D-04
- **D-04:** Reuse is an explicit **bulk "Copy rules from [mailbox A]"** action surfaced when managing a mailbox's rules. It clones the structured `When/Then` schema into the target mailbox (default `enabled = false` so the user reviews before activation). This satisfies the requirement that applying a rule to another mailbox is an explicit copy/template action and **never** silently creates an all-mailbox runtime rule. Per-rule duplicate is a nice-to-have, not required for v1.3; manual-only was rejected as poor ergonomics for a 2nd-mailbox onboarding.

### Claude's Discretion
- Exact storage of D-03 active-mailbox state (Spring Session attribute vs a per-user `active_gmail_mailbox_id` column) — planner picks the lighter implementation; both satisfy "server-side per-user, fallback to primary".
- Wave/sequencing of the 27 requirements. Phase boundary stays fixed (ROADMAP); planner decides waves. Suggested ordering: (1) backend mailbox-scope migration + ingestion/projection/cursor, (2) mailbox-owned rules + triage + outbound + audit, (3) `MailboxContext` filter + consumers, (4) web switcher/settings/active-default rendering + OpenAPI regen, (5) verification (ArchUnit, isolation tests, Playwright). If the user later wants hard sub-phases, use `/gsd-phase`.
- Migration shape for adding `gmail_mailbox_id` to downstream tables (nullable-then-backfill-then-NOT NULL vs single backfilling changeset) — follow Liquibase append-only discipline (CLAUDE.md rule 10); backfill existing rows to the tenant's primary mailbox.

### Folded Todos
- **Phase 8 real-Gmail e2e smoke on dev VPS** (`.planning/todos/2026-05-21-optional-phase-08-e2e-smoke-real-gmail-vps.md`): folded into **VER-04**. Phase 11 is the first phase with real multi-mailbox ingestion + switching, so the real-Gmail smoke (connect → ingest → switch → send-from) is the natural home alongside the Playwright browser flows.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap (requirement source of truth — no SPEC.md)
- `.planning/REQUIREMENTS.md` — Phase 11 = ING-01..06, AUTO-01..06, AUD-01/02/03/05/06/07, UX-01..06, VER-02/03/04. Workspace-shared vs mailbox-isolated boundary (WSP-07), privacy posture, fail-closed.
- `.planning/ROADMAP.md` § "Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification" — goal, depends-on, expected plan areas.

### Phase 10 foundation (direct dependency — decisions Phase 11 wraps)
- `.planning/phases/10-gmail-mailbox-foundation-and-account-management/10-CONTEXT.md` — locked: D-06 (MailboxContext ScopedValue + servlet filter is the end-state mechanism, built in Phase 11), D-00a (downstream tables get mailbox columns here), D-00b (AAD stays tenant-based, no re-encryption), D-10..13 (`MailboxRef`, `buildClientForMailbox`, deprecated `buildClientForTenant` + ArchUnit allow-list to drain), ownership seam contract (404/409).
- `.planning/phases/10-gmail-mailbox-foundation-and-account-management/10-REVIEW.md` + `10-REVIEW-FIX.md` — known residuals: account-deletion does not yet revoke non-primary mailbox grants at Google; CR-01 multi-row primary shim needs a two-CONNECTED-mailbox isolation test (natural to add in Phase 11).

### Code research (drives mailbox-scoping approach)
- `.planning/research/V1.3-CODE-RESEARCH.md` — single-Gmail assumptions inventory, Inbox Zero mailbox-isolation pattern, recommended build order, logging/restricted-scope note.

### Inbox Zero reference (product pattern only — do NOT port TS/Next/Prisma or its raw-email logging)
- `../inbox-zero/apps/web/utils/email-account-client.ts` — per-mailbox Gmail client lookup.
- `../inbox-zero/apps/web/utils/middleware.ts` — per-request account ownership validation (the MailboxContext filter shape).
- `../inbox-zero/apps/web/utils/oauth/account-linking.ts` — add/reconnect intent (already implemented in Phase 10).

### Project guardrails
- `CLAUDE.md` — privacy scope (no raw body/prompt/completion/embedding storage or logging in the triage path; chat carve-outs), outbound gateway boundary, Liquibase append-only (rule 10), generated-OpenAPI-never-hand-edited (rule 11), Modulith vs direct-call (rule 6), enterprise naming, no WebFlux / virtual threads.
- `apps/web/AGENTS.md` — generated `schema.d.ts` regen workflow, shadcn-first, TanStack Query meta toasts, no hardcoded hex tokens.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `components/shell/AppSidebar.tsx` → `AccountMenu` (footer dropdown) + `ReconnectRow` — the switcher merges into `AccountMenu` (D-01); `ReconnectRow` currently calls tenant-singular `/api/tenant/connect-gmail` and must become mailbox-aware.
- `features/gmail/hooks/useTenantStatus` + `useCurrentUser` — currently tenant-singular; Phase 11 needs a mailbox-list hook (Phase 10 connected-accounts REST: list/set-primary/disconnect + add/reconnect triggers, `MailboxSummaryResponse`) and an active-mailbox set/get hook.
- `features/{inbox,needs-reply,rules,analytics,triage,account}` — existing feature folders to make active-mailbox aware.
- Backend: `core/tenant/TenantContext` (ScopedValue) + `TenantBindingFilter` — exact pattern to mirror for `MailboxContext` + its binding filter (bind before Hibernate session per `GmailAccessGuard`).
- Backend: `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` (Phase 10) — the mailbox-aware client all write/read paths must switch to; drains the `buildClientForTenant` ArchUnit allow-list.
- Backend single-Gmail consumers to migrate (from Phase 10 review): `RecentInboxReadService`, `GmailPreviewReadService`, `InboxBackfillService`, `GmailDeliveryProcessingService`, `GmailConnectionService.findByTenantId` shim, triage writer/loaders, outbound gateway, invalid-grant listener, `GmailAccessGuard`.

### Established Patterns
- Thin controllers + service-owned `@Transactional`; DTOs own `from(...)`; OpenAPI accuracy annotations (CLAUDE.md rule 3).
- Mailbox-scoped endpoints use path segment `/api/gmail/mailboxes/{gmailConnectionId}/...` (Phase 10 D-05).
- Tests: `RestClient + @LocalServerPort` (not MockMvc) so filters/ScopedValue bind; `PostgresContainerTest` singleton Testcontainers — **note the ~15-cached-context connection ceiling**; full `:backend:core:test` currently flakes on connection exhaustion on the dev machine (run focused scopes to verify).
- Liquibase: append-only YAML under `db/changelog/changes/`, raw `sql:` for partial/expression indexes, explicit rollback, preconditions for data-dependent DDL.
- Spring Modulith events for after-commit side effects; direct calls for transaction-critical paths; events do NOT cross api↔worker (use Postgres outbox/processing tables).

### Integration Points
- Pub/Sub push controller → must resolve mailbox before history fetch (ING-01); processing jobs + sync state carry mailbox id.
- `MailboxContext` filter binds active mailbox (server-side, D-03) into the request for read/triage/outbound consumers.
- Connected-accounts REST (Phase 10) → web settings surface (UX-01) + footer switcher (UX-02).
- OpenAPI regen → `pnpm --filter web run generate:api` after backend DTO changes (VER-02, CLAUDE.md rule 11).

</code_context>

<specifics>
## Specific Ideas

- Footer `AccountMenu` must visually separate **workspace user identity** (top line) from the **active Gmail mailbox** (list below) — they are now different concepts after multi-mailbox.
- Copy-rules clones into the target with `enabled = false` so the user reviews before activation.
- Active-mailbox falls back to the tenant's **primary** mailbox when no active selection exists (ties D-03 to Phase 10's `is_primary`).
- Add a two-CONNECTED-mailbox cross-account isolation test (covers the Phase 10 CR-01 shim gap) under AUD-06.

</specifics>

<deferred>
## Deferred Ideas

- Unified/all-mailboxes inbox roll-up view (D-02) — future milestone; ING-04/UX-04 read-only + provenance constraints are pre-written guardrails for when it lands.
- Per-rule duplicate-to-mailbox (D-04) — nice-to-have beyond the bulk copy action.
- Account-deletion revoke of non-primary mailbox grants at Google (Phase 10 review residual) — follow-up hardening.
- Team collaboration, Zalo OA / CRM / omnichannel, Microsoft provider, OPS-FUT-01..04 (per REQUIREMENTS "Future Requirements").

### Reviewed Todos (not folded)
- **WR-06 — test-profile SecurityConfig slice for OAuth filter-chain coverage** (`.planning/todos/2026-04-28-wr-06-test-profile-securityconfig-slice.md`): reviewed, **not folded** — already satisfied in Phase 10 (`OAuthIntentRoutingTest` + test-profile SecurityConfig slice exercising first-login/add/reconnect intents). No remaining work for Phase 11.

</deferred>

---

*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Context gathered: 2026-06-09*

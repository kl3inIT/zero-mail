# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- ✅ **v1.2 Admin Console + User Settings UI** — Phases 8, 08.1, 9 (+ 08-bulk-unsubscribe) (shipped 2026-06-01) — see [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)
- 🚧 **v1.3 Gmail Workspace Foundation** — Phases 10-11 (active, started 2026-06-07) — requirements in [REQUIREMENTS.md](REQUIREMENTS.md), code research in [research/V1.3-CODE-RESEARCH.md](research/V1.3-CODE-RESEARCH.md)

## Phases

<details>
<summary>✅ v1.0 MVP (shipped 2026-05-15) — 17 phases, 123 plans</summary>

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Email assistant chat (shipped 2026-05-19) — Phase 7 only</summary>

- [x] Phase 7: Chat Email Assistant — 6/6 plans, completed 2026-05-18

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

<details>
<summary>✅ v1.2 Admin Console + User Settings UI (shipped 2026-06-01) — 4 phases, 28 plans</summary>

- [x] Phase 8: Admin Console & Operator Tooling — 6/6 plans, completed 2026-05-20
- [x] Phase 08.1: Inbox Zero-style Rule Actions & Admin-managed Examples Catalog — 6/6 plans, completed 2026-05-25
- [x] Phase 08-bulk-unsubscribe: Bulk Unsubscribe Campaign (UNS-01..07) — shipped alongside v1.2
- [x] Phase 9: User Settings UI on Curated Catalog — 7/7 plans, completed 2026-05-29

70/73 v1.2 requirements complete; 3 deferred to v1.3 (SET-BEHV-05, SET-SAFE-02, SET-SAFE-03).

Full details: [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)

</details>

<details open>
<summary>🚧 v1.3 Gmail Workspace Foundation (active) — 2 phases</summary>

- [x] Phase 10: Gmail Mailbox Foundation and Account Management (completed 2026-06-09)
- [ ] Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification

16/43 v1.3 requirements complete; 27/43 pending. Scope is Gmail-only workspace-shared, mailbox-isolated foundation; Microsoft, Zalo OA, CRM, and full team collaboration remain deferred.

</details>

## v1.3 Gmail Workspace Foundation (active)

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 6/6 | Complete | 2026-05-20 |
| 08.1. Inbox Zero-style Rule Actions & Examples Catalog | v1.2 | 6/6 | Complete | 2026-05-25 |
| 08-bulk-unsubscribe. Bulk Unsubscribe Campaign | v1.2 | — | Complete | 2026-05 |
| 9. User Settings UI on Curated Catalog | v1.2 | 7/7 | Complete | 2026-05-29 |
| 10. Gmail Mailbox Foundation and Account Management | v1.3 | 6/6 | Complete    | 2026-06-09 |
| 11. Mailbox-Scoped Ingestion, Automation, UI, and Verification | v1.3 | 2/6 | In Progress | — |

### Phase 10: Gmail Mailbox Foundation and Account Management

**Goal:** Convert one-Gmail-per-tenant into a workspace-owned multi-Gmail mailbox model where business configuration is shared at workspace level and mail automation is isolated per active mailbox.
**Requirements:** WSP-01..07, GMA-01..07, AUD-04, VER-01
**Depends on:** Phase 9 (User Settings UI), current Gmail OAuth/connection schema, code research in `.planning/research/V1.3-CODE-RESEARCH.md`
**Mode:** sequential foundation before Phase 11
**Plans:** 6/6 plans complete

Plans:
**Wave 1**

- [x] 10-01-PLAN.md — Wave 0 validation spine: 8 RED test scaffolds + old-single-account fixture (Nyquist)
- [x] 10-02-PLAN.md — Liquibase changeset 119 (drop tenant-unique, add duplicate-active + primary partial indexes, backfill) + entity is_primary/display_purpose + findByIdAndTenantId

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 10-03-PLAN.md — Mailbox-aware GmailApiClientFactory: MailboxRef, buildClientForMailbox, cache re-key to gmailConnectionId, @Deprecated tenant adapter, ArchUnit allow-list

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 10-04-PLAN.md — Ownership seam (resolveOwnedConnectionOrThrow 404/409) + mailbox-scoped disconnect/set-primary + duplicate-active mapping + metadata-only list projection

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 10-05-PLAN.md — OAuth intent split: resolver attributes + IntentCarrying session shim + success-handler first-login/add/reconnect branching + SecurityConfig wiring (WR-06)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 10-06-PLAN.md — Connected-accounts REST: list / set-primary / disconnect + add/reconnect OAuth triggers + MailboxSummaryResponse DTO

Expected plan areas:

- Liquibase migration from single `gmail_connections.tenant_id` invariant to tenant-owned mailbox rows.
- Existing tenant backfill to one primary/default Gmail mailbox while preserving encrypted tokens, connection state, history state, and metadata/audit continuity where possible.
- Stable mailbox id (`gmail_connections.id`), display purpose/label, and primary/default marker for legacy/default surfaces.
- Explicit workspace-shared vs mailbox-isolated ownership boundary: shared credits/billing/provider/global safety/templates; isolated Gmail OAuth/watch/history/inbox/rules/actions/audit.
- Mailbox-scoped request guard/context for Spring MVC APIs validating `(tenantId, gmailMailboxId)` ownership.
- Mailbox-aware Gmail client lookup and token cache; tenant-only default lookup kept only as a compatibility adapter.
- Split first-login provisioning from add-mailbox and reconnect-mailbox OAuth flows.
- Connected Gmail accounts/settings APIs for list, reconnect, disconnect, set-primary, and metadata-only status/health.
- Per-mailbox watch renewal, invalid-grant handling, users.stop, token revoke, and disconnect state transitions.
- Duplicate active Gmail address prevention and clear conflict errors.
- Projection encryption AAD compatibility or re-encryption decision recorded before implementation.
- Architecture tests forbidding accidental tenant-only Gmail lookup in mailbox-scoped flows.

### Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification

**Goal:** Route Gmail ingestion, projection, rules, outbound execution, audit, and the web app through active mailbox scope so users can switch quickly while each mailbox remains operationally isolated.
**Requirements:** ING-01..06, AUTO-01..06, AUD-01..03, AUD-05..07, UX-01..06, VER-02..04
**Depends on:** Phase 10 mailbox foundation and account metadata
**Mode:** final integration phase
**Plans:** 2/6 plans executed

Plans:

**Wave 1**

- [x] 11-01-PLAN.md — Validation spine: RED invariant tests + two-mailbox fixture + new findByTenantId ArchUnit rule + cross-account isolation harness (Nyquist)

**Wave 2** *(blocked on Wave 1)*

- [x] 11-02-PLAN.md — Liquibase 120-126: gmail_connection_id columns + backfill-to-primary + PK/idempotency/template-key swaps; thread RuleEntity + domain events

**Wave 3** *(blocked on Wave 2; 03 and 04 run in parallel — disjoint packages)*

- [ ] 11-03-PLAN.md — Ingestion threading: Pub/Sub (tenant,mailbox) lookup, per-connection cursor, mailbox-keyed observed/projection/events, buildClientForMailbox
- [ ] 11-04-PLAN.md — Mailbox-owned rules + copy-rules + triage dispatch + mailbox-aware writes/outbound send + audit provenance

**Wave 4** *(blocked on Waves 3)*

- [ ] 11-05-PLAN.md — MailboxContext ScopedValue + MailboxBindingFilter + ActiveMailboxResolver + active-mailbox endpoint + read-consumer migration + allow-list drain + cross-account isolation green

**Wave 5** *(blocked on Wave 4)*

- [ ] 11-06-PLAN.md — Web: OpenAPI regen + features/mailbox triad + AccountMenu switcher + copy-rules dialog + active-default rendering + Playwright + real-Gmail smoke checkpoint

Expected plan areas:

- Pub/Sub lookup returns tenant + mailbox, not tenant only.
- `pubsub_delivery`, `mail_message_observed`, `MailMessageObserved`, `MailOutboundObserved`, processing jobs, and sync state include mailbox scope.
- Per-mailbox monotonic history cursor updates, history-lost handling, backfill, and ingestion health.
- Inbox projection primary key/index/cursor/read/detail/thread paths include mailbox id.
- Inbox, needs-reply, audit, and analytics read paths render the active mailbox by default; any all-mailboxes roll-up is read-only, provenance-labeled, and never an implicit Gmail action context.
- Mailbox-owned rules in compiler, manual editor contracts, persistence, preview, test runs, UI, and runtime; applying a rule to another mailbox requires explicit copy/template action.
- Triage dispatch context carries source mailbox and executing mailbox.
- `TriageGmailWriter`, `GmailOutboundSendGateway`, forward/reply assemblers, undo/revert, and audit saga use mailbox-aware Gmail clients.
- `triage_audit` records source/executing mailbox and idempotency includes mailbox context.
- Cross-account isolation tests and ArchUnit boundary tests cover read/write/send paths.
- Connected Gmail accounts/settings surface for add, reconnect, disconnect, display purpose/label, status, and primary selection.
- Persistent active-mailbox switcher in app chrome; risky write/send previews always show source and executing mailbox.
- Backend OpenAPI regen and frontend API code switched to generated types where endpoints are emitted.
- Playwright browser verification for connect/list/switch/mailbox-owned-rules/send-from/audit workflows.
- Privacy posture preserved: no long-term raw body, prompt/completion, embedding storage, or raw email logging.

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 archived 2026-06-01 — Phases 8 + 08.1 + 9 (+ bonus 08-bulk-unsubscribe campaign), 70/73 requirements complete, 3 deferred to v1.3. v1.3 active as of 2026-06-07 with Gmail-only multi-mailbox foundation; Telegram/Zalo/Microsoft/CRM/team shared-inbox work remains deferred.*

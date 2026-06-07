# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- ✅ **v1.2 Admin Console + User Settings UI** — Phases 8, 08.1, 9 (+ 08-bulk-unsubscribe) (shipped 2026-06-01) — see [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)
- 🚧 **v1.3 Gmail Workspace Foundation** — Phases 10-14 (active, started 2026-06-07) — requirements in [REQUIREMENTS.md](REQUIREMENTS.md), code research in [research/V1.3-CODE-RESEARCH.md](research/V1.3-CODE-RESEARCH.md)

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
<summary>🚧 v1.3 Gmail Workspace Foundation (active) — 5 phases</summary>

- [ ] Phase 10: Mailbox Identity, Schema, and Context Foundation
- [ ] Phase 11: Multi-Gmail Account Management and Watch Renewal
- [ ] Phase 12: Mailbox-Scoped Pub/Sub, History, Backfill, and Inbox Projection
- [ ] Phase 13: Mailbox-Scoped Rules, Outbound, Audit, and Safety
- [ ] Phase 14: Web UI, OpenAPI, Tests, and Docs

42/42 v1.3 requirements pending. Scope is Gmail-only multi-mailbox foundation; Microsoft, Zalo OA, CRM, and full team collaboration remain deferred.

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 6/6 | Complete | 2026-05-20 |
| 08.1. Inbox Zero-style Rule Actions & Examples Catalog | v1.2 | 6/6 | Complete | 2026-05-25 |
| 08-bulk-unsubscribe. Bulk Unsubscribe Campaign | v1.2 | — | Complete | 2026-05 |
| 9. User Settings UI on Curated Catalog | v1.2 | 7/7 | Complete | 2026-05-29 |
| 10. Mailbox Identity, Schema, and Context Foundation | v1.3 | 0/? | Pending | — |
| 11. Multi-Gmail Account Management and Watch Renewal | v1.3 | 0/? | Pending | — |
| 12. Mailbox-Scoped Pub/Sub, History, Backfill, and Inbox Projection | v1.3 | 0/? | Pending | — |
| 13. Mailbox-Scoped Rules, Outbound, Audit, and Safety | v1.3 | 0/? | Pending | — |
| 14. Web UI, OpenAPI, Tests, and Docs | v1.3 | 0/? | Pending | — |

### Phase 10: Mailbox Identity, Schema, and Context Foundation

**Goal:** Convert the current one-Gmail-per-tenant foundation into a workspace-owned Gmail mailbox model while preserving existing tenants and keeping login/session semantics unchanged.
**Requirements:** WSP-01..06, VER-01
**Depends on:** Phase 9 (User Settings UI), current Gmail OAuth/connection schema, code research in `.planning/research/V1.3-CODE-RESEARCH.md`
**Mode:** sequential foundation before Phases 11-14

Expected plan areas:

- Liquibase migration from single `gmail_connections.tenant_id` invariant to tenant-owned mailbox rows.
- Existing tenant backfill to one primary/default Gmail mailbox.
- Mailbox-scoped request guard/context for Spring MVC APIs.
- Mailbox-aware Gmail client lookup and token cache; tenant-only default lookup kept only as a compatibility adapter.
- Projection encryption AAD compatibility or re-encryption decision recorded before implementation.
- Architecture tests forbidding accidental tenant-only Gmail lookup in mailbox-scoped flows.

### Phase 11: Multi-Gmail Account Management and Watch Renewal

**Goal:** Let one workspace connect, list, reconnect, disconnect, and choose a primary Gmail mailbox without replacing other connected mailboxes.
**Requirements:** GMA-01..07, AUD-04
**Depends on:** Phase 10
**Mode:** sequential after mailbox foundation

Expected plan areas:

- Split first-login provisioning from add-mailbox and reconnect-mailbox OAuth flows.
- Connected Gmail accounts/settings APIs and metadata-only status projections.
- Per-mailbox watch renewal, invalid-grant handling, users.stop, token revoke, and disconnect state transitions.
- Duplicate active Gmail address prevention and clear conflict errors.
- Primary/default mailbox selection for legacy/default surfaces.

### Phase 12: Mailbox-Scoped Pub/Sub, History, Backfill, and Inbox Projection

**Goal:** Route Gmail Pub/Sub deliveries, history sync, backfill, and inbox projections through the correct mailbox so same Gmail ids across accounts never collide.
**Requirements:** ING-01..06
**Depends on:** Phase 10, Phase 11 watch/account metadata
**Mode:** sequential; inbox projection changes depend on mailbox schema and AAD decision

Expected plan areas:

- Pub/Sub lookup returns tenant + mailbox, not tenant only.
- `pubsub_delivery`, `mail_message_observed`, `MailMessageObserved`, `MailOutboundObserved`, processing jobs, and sync state include mailbox scope.
- Per-mailbox monotonic history cursor updates and history-lost handling.
- Inbox projection primary key/index/cursor/read/detail/thread paths include mailbox id.
- Backfill and needs-reply/analytics read paths can filter one mailbox or all mailboxes.
- Privacy posture preserved: no long-term raw body, prompt/completion, or embedding storage.

### Phase 13: Mailbox-Scoped Rules, Outbound, Audit, and Safety

**Goal:** Ensure automation runs only for the intended mailbox and every Gmail write/send/audit decision records source and executing mailbox provenance.
**Requirements:** AUTO-01..06, AUD-01..03, AUD-05..07
**Depends on:** Phase 10 mailbox context, Phase 12 observed-event mailbox contracts
**Mode:** sequential after ingestion contracts are mailbox-aware

Expected plan areas:

- Structured rule mailbox scope (`all` vs selected Gmail mailboxes) in compiler, manual editor contracts, persistence, preview, and runtime.
- Triage dispatch context carries source mailbox and executing mailbox.
- `TriageGmailWriter`, `GmailOutboundSendGateway`, forward/reply assemblers, undo/revert, and audit saga use mailbox-aware Gmail clients.
- `triage_audit` idempotency includes mailbox context.
- Cross-account isolation tests and ArchUnit boundary tests cover read/write/send paths.
- Raw email logging ban enforced/reviewed while DB/UI still store connected mailbox email as product state.

### Phase 14: Web UI, OpenAPI, Tests, and Docs

**Goal:** Surface multi-Gmail account management and mailbox filtering in the web app, regenerate API types, and verify the full workflow in a browser.
**Requirements:** UX-01..05, VER-02..05
**Depends on:** Phases 10-13
**Mode:** final integration phase

Expected plan areas:

- Connected Gmail accounts/settings surface for add, reconnect, disconnect, status, and primary selection.
- Inbox, needs-reply, audit, and analytics mailbox filters plus mailbox badges.
- Rule UI mailbox scope while preserving the existing When/Then mental model.
- Backend OpenAPI regen and frontend API code switched to generated types where endpoints are emitted.
- Playwright browser verification for connect/list/select/filter/rule-scope/audit workflows.
- Docs/runbooks covering multi-Gmail setup, limits, privacy/logging posture, and future team/shared-inbox boundary.

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 archived 2026-06-01 — Phases 8 + 08.1 + 9 (+ bonus 08-bulk-unsubscribe campaign), 70/73 requirements complete, 3 deferred to v1.3. v1.3 active as of 2026-06-07 with Gmail-only multi-mailbox foundation; Telegram/Zalo/Microsoft/CRM/team shared-inbox work remains deferred.*

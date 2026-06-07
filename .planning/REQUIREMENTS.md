# Requirements: Zero Mail v1.3 Gmail Workspace Foundation

**Defined:** 2026-06-07
**Status:** Reconciled after code research (`.planning/research/V1.3-CODE-RESEARCH.md`). The initial requirements were draft v0; this version reflects the single-Gmail assumptions found in Zero Mail and the mailbox-scoping pattern found in Inbox Zero.
**Core Value:** AI auto-triage that users trust with their real Gmail inbox.

## Scope Inputs

- `SEED-005` - team-collaboration-shared-email-workspace. Included as the business/product reason for building a workspace-ready multi-Gmail foundation now, while deferring full team collaboration.
- `SEED-019` - ai-communication-ops-zalo-crm-vietnam. Directional context only: Gmail remains the production channel for v1.3; Zalo OA, CRM, and omnichannel work stay deferred.
- Lightweight market check: Crisp validates shared inbox + CRM positioning for SMB support teams; Shortwave validates team collaboration primitives such as shared threads, private comments, assignment, shared labels, and shared prompts/templates. v1.3 intentionally stops before those full collaboration features.
- Codebase constraint: current Gmail integration is single-connection-per-tenant (`gmail_connections.tenant_id` unique, `findByTenantId(...)`, tenant-only idempotency and projection keys). v1.3 must treat this as a core migration, not a UI-only account picker.
- Code research: Inbox Zero's transferable implementation pattern is a stable mailbox/account id (`emailAccountId`) that scopes OAuth tokens, watch state, rules, actions, executed rules, labels, messages, API request context, and UI switching. Zero Mail should copy that mailbox isolation concept, not its user-owned account model, Next/Prisma architecture, all-account UX assumptions, or raw-email logging posture.

## v1.3 Requirements

### Workspace and Mailbox Model

- [ ] **WSP-01**: User's existing tenant is represented as one workspace without changing login/session semantics.
- [ ] **WSP-02**: Existing one-Gmail data migrates to one primary Gmail mailbox while preserving encrypted tokens, connection state, history state, and metadata/audit continuity where possible.
- [ ] **WSP-03**: System stores a stable Gmail mailbox identifier on every new mailbox-scoped record that can contain per-account state or provenance.
- [ ] **WSP-04**: Backend APIs, UI labels, and logs consistently distinguish workspace, user, and Gmail mailbox without exposing future team/member controls.
- [ ] **WSP-05**: System fails closed when a mailbox id is missing, invalid, disconnected, or not owned by the current tenant/workspace.
- [ ] **WSP-06**: Mailbox-scoped API requests go through a shared backend guard/context that validates `(tenantId, gmailMailboxId)` ownership before controller/service execution; tenant-only default mailbox fallback is allowed only for explicitly legacy/default surfaces, never for internal Gmail write paths.
- [ ] **WSP-07**: Workspace-level state owns shared business configuration such as credits, billing, AI provider/model/BYOK, global pause/auto-send controls, safety policy, templates/catalog, and future business context; mailbox-level state owns Gmail OAuth, watch/history, connection health, inbox data, rules, Gmail actions, outbound execution, audit provenance, and display identity.

### Gmail Account Management

- [ ] **GMA-01**: User can connect an additional Gmail or Google Workspace mailbox without replacing the existing connected mailbox.
- [ ] **GMA-02**: User can view all connected Gmail mailboxes with email, display name/purpose label, status, primary/default marker, watch expiry, ingestion health, and last sync metadata.
- [ ] **GMA-03**: User can choose one primary/default Gmail mailbox for surfaces that need a default account.
- [ ] **GMA-04**: User can reconnect one mailbox and refresh its encrypted token/scopes without touching other mailboxes.
- [ ] **GMA-05**: User can disconnect one mailbox; the app stops watch renewal, ingestion, and automation for that mailbox without disconnecting the workspace.
- [ ] **GMA-06**: System prevents duplicate active Gmail addresses in the same workspace and returns a clear error when a Gmail address is already connected elsewhere.
- [ ] **GMA-07**: OAuth flow separates first-login Gmail provisioning from add-mailbox and reconnect-mailbox flows, so connecting another Gmail never replaces the current mailbox row by accident.

### Ingestion and Inbox Data

- [ ] **ING-01**: Pub/Sub delivery resolves the correct Gmail mailbox before fetching Gmail history, and unknown mailbox delivery fails or drops safely without cross-account processing.
- [ ] **ING-02**: History sync, backfill, watch renewal, and ingestion health run independently per Gmail mailbox.
- [ ] **ING-03**: Idempotency keys for Pub/Sub deliveries, observed messages, processing jobs, and inbox projections include mailbox scope wherever Gmail ids are not sufficient across accounts.
- [ ] **ING-04**: Inbox, needs-reply, and analytics default to the active mailbox context; any future all-mailboxes roll-up is read-only, carries explicit mailbox provenance, and cannot become an implicit Gmail action context.
- [ ] **ING-05**: Multi-Gmail ingestion preserves the existing no-long-term raw body, prompt/completion, and embedding storage posture.
- [ ] **ING-06**: Gmail client lookup, access-token cache, watch renewal, backfill, history cursor updates, and projection encryption/decryption compatibility are mailbox-aware; any encryption AAD change has an explicit compatibility or app-level re-encryption plan.

### Account-Scoped Automation

- [ ] **AUTO-01**: Rules belong to one Gmail mailbox by default; applying the same rule to another mailbox requires explicit copy/template action and must not silently create an all-mailbox runtime rule in v1.3.
- [ ] **AUTO-02**: Rule compiler and manual editor persist the owning Gmail mailbox id as structured data; original natural-language input remains metadata only.
- [ ] **AUTO-03**: Rule preview and test runs sample only messages from the owning mailbox and show active mailbox context in results.
- [ ] **AUTO-04**: Runtime triage evaluates only rules owned by the source Gmail mailbox.
- [ ] **AUTO-05**: Gmail label, archive, draft, read/unread, star, spam, and digest actions resolve Gmail state against the executing mailbox, not a tenant-global Gmail client.
- [ ] **AUTO-06**: Rule-triggered and chat-triggered send/reply/forward actions use the shared outbound gateway with the correct Gmail mailbox and record blocked/failed outcomes without executing under another mailbox.

### Audit, Safety, and Authorization

- [ ] **AUD-01**: Triage audit rows expose the source Gmail mailbox and executing Gmail mailbox for every Gmail write/read-derived action.
- [ ] **AUD-02**: Undo/revert targets the same Gmail mailbox that originally executed the action.
- [ ] **AUD-03**: Sender safety-net/protected-sender decisions remain tenant-owned and record triggering mailbox metadata without leaking content.
- [ ] **AUD-04**: Admin/operator tenant inspection shows metadata-only multi-mailbox health without exposing tokens, raw bodies, prompts, or completions.
- [ ] **AUD-05**: Architectural tests forbid tenant-only Gmail client lookup in new mailbox-scoped flows where mailbox context is required.
- [ ] **AUD-06**: Cross-account isolation tests prove one mailbox cannot read, write, archive, draft, or send as another mailbox through crafted ids.
- [ ] **AUD-07**: Application logs and external error telemetry do not emit raw connected mailbox emails, sender/recipient emails, subjects, snippets, bodies, raw headers, tokens, prompts, or completions; use tenant id, mailbox id, technical status, and optional masked/hash values instead. Storing connected mailbox email in DB/UI remains allowed product state.

### User Experience

- [ ] **UX-01**: User can access a connected accounts/settings surface to add, reconnect, disconnect, label/purpose, and inspect Gmail mailboxes.
- [ ] **UX-02**: User can switch the active mailbox quickly from persistent app chrome; inbox, needs-reply, rules, audit, and analytics render the active mailbox by default.
- [ ] **UX-03**: Rules UI displays mailbox-owned rules for the active mailbox without replacing the existing When/Then mental model.
- [ ] **UX-04**: Audit and analytics UI use active-mailbox context by default and show mailbox badges only where provenance would otherwise be unclear, such as optional read-only roll-ups.
- [ ] **UX-05**: Onboarding keeps the one-Gmail setup simple for new users and offers add-more-Gmail after first connection.
- [ ] **UX-06**: Any Gmail write, send/reply/forward preview, or action confirmation clearly shows the source and executing mailbox; actions started from any read-only roll-up must open or select a concrete mailbox/thread before execution.

### Verification and Migration

- [ ] **VER-01**: Liquibase migration is roll-forward, preserves existing tenants, and has coverage for old single-account fixtures.
- [ ] **VER-02**: OpenAPI is regenerated after DTO/API changes and frontend feature APIs use generated types.
- [ ] **VER-03**: Backend tests cover migration, repository lookup, Pub/Sub routing, watch renewal, idempotency, mailbox-owned rules, outbound gateway, and audit invariants.
- [ ] **VER-04**: Frontend tests and Playwright cover connect, list, active-mailbox switching, mailbox-owned rules, send-from visibility, and audit workflows in a real browser.

## Future Requirements

### Team Collaboration and Shared Inbox

- **TEAM-01**: Workspace owner can invite teammates and assign workspace roles.
- **TEAM-02**: User can share a thread with teammates without forwarding screenshots.
- **TEAM-03**: User can add private internal comments to email threads.
- **TEAM-04**: User can assign owner, due date, and done status to a thread.
- **TEAM-05**: Workspace can share labels, rule templates, prompts, and snippets across members.
- **TEAM-06**: Workspace has a team-wide audit log for AI and human actions with user-visible access history.

### Communication Ops Expansion

- **CHAN-01**: Workspace can connect Zalo OA as a business support channel.
- **CHAN-02**: Workspace can manage a lightweight contact/customer timeline across Gmail and future Zalo OA events.
- **CHAN-03**: Workspace can route conversation events into lead/status/follow-up workflows without becoming a full CRM clone.

### Operational Carry-Forward

- **OPS-FUT-01**: User can toggle shadow mode from settings (SET-BEHV-05 carry-forward).
- **OPS-FUT-02**: User can paste-import multiple safety-net entries (SET-SAFE-02 carry-forward).
- **OPS-FUT-03**: User can choose protect/escalate mode per safety-net entry (SET-SAFE-03 carry-forward).
- **OPS-FUT-04**: Project can complete hostile-corpus eval, Grafana dashboards, CASA refresh, LAUNCH-GO-NOGO, and a formal GA tag.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Microsoft Outlook / Microsoft 365 | v1.3 keeps production mail-provider scope Gmail-only. |
| Zalo OA production integration | Strategic Vietnam SMB direction, but after Gmail workspace foundation. |
| CRM/contact timeline | Valuable future direction, but would change data model and privacy/retention expectations. |
| Full team collaboration | SEED-005 is foundation context only; comments, assignments, roles, seats, and team audit are later scope. |
| Omnichannel shared inbox | Crisp-style omnichannel is too broad for this milestone. |
| Long-term raw Gmail body storage or embeddings | Still incompatible with Zero Mail's trust-first privacy posture. |
| Full replacement mail client | Gmail remains the native client; Zero Mail augments automation and visibility. |
| Tracking pixels/link tracking | Sales-engagement feature with recipient privacy and deliverability risks; defer. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| WSP-01 | Phase 10 | Pending |
| WSP-02 | Phase 10 | Pending |
| WSP-03 | Phase 10 | Pending |
| WSP-04 | Phase 10 | Pending |
| WSP-05 | Phase 10 | Pending |
| WSP-06 | Phase 10 | Pending |
| WSP-07 | Phase 10 | Pending |
| GMA-01 | Phase 10 | Pending |
| GMA-02 | Phase 10 | Pending |
| GMA-03 | Phase 10 | Pending |
| GMA-04 | Phase 10 | Pending |
| GMA-05 | Phase 10 | Pending |
| GMA-06 | Phase 10 | Pending |
| GMA-07 | Phase 10 | Pending |
| AUD-04 | Phase 10 | Pending |
| VER-01 | Phase 10 | Pending |
| ING-01 | Phase 11 | Pending |
| ING-02 | Phase 11 | Pending |
| ING-03 | Phase 11 | Pending |
| ING-04 | Phase 11 | Pending |
| ING-05 | Phase 11 | Pending |
| ING-06 | Phase 11 | Pending |
| AUTO-01 | Phase 11 | Pending |
| AUTO-02 | Phase 11 | Pending |
| AUTO-03 | Phase 11 | Pending |
| AUTO-04 | Phase 11 | Pending |
| AUTO-05 | Phase 11 | Pending |
| AUTO-06 | Phase 11 | Pending |
| AUD-01 | Phase 11 | Pending |
| AUD-02 | Phase 11 | Pending |
| AUD-03 | Phase 11 | Pending |
| AUD-05 | Phase 11 | Pending |
| AUD-06 | Phase 11 | Pending |
| AUD-07 | Phase 11 | Pending |
| UX-01 | Phase 11 | Pending |
| UX-02 | Phase 11 | Pending |
| UX-03 | Phase 11 | Pending |
| UX-04 | Phase 11 | Pending |
| UX-05 | Phase 11 | Pending |
| UX-06 | Phase 11 | Pending |
| VER-02 | Phase 11 | Pending |
| VER-03 | Phase 11 | Pending |
| VER-04 | Phase 11 | Pending |

**Coverage:**
- v1.3 requirements: 43 total
- Mapped to phases: 43
- Unmapped: 0

---
*Requirements defined: 2026-06-07*
*Last updated: 2026-06-07 after v1.3 code research reconciliation*

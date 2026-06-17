# Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-09
**Phase:** 11-mailbox-scoped-ingestion-automation-ui-and-verification
**Areas discussed:** Active-mailbox switcher placement, Unified inbox scope, Active-mailbox persistence, Cross-mailbox rules reuse

---

## Active-mailbox switcher placement (UX-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Sidebar header (IZ-style) | Dropdown switcher under ZERO MAIL wordmark; active mailbox + email + primary badge + Add Gmail | |
| Merge into footer AccountMenu | Add mailbox list to existing bottom user/plan dropdown | ✓ |
| Top bar per-page | Selector in each page's top bar | |

**User's choice:** Merge into footer AccountMenu
**Notes:** Captured the nuance that the footer dropdown now expresses two distinct concepts — workspace user identity (top line) vs active Gmail mailbox (list below). Must not conflate logged-in user with active mailbox.

---

## Unified inbox scope (ING-04, UX-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Active-only, defer unified | Every screen renders the single active mailbox; no all-mailboxes roll-up in v1.3 | ✓ |
| Include read-only roll-up now | Build unified all-mailboxes view, read-only + provenance badges | |

**User's choice:** Active-only, defer unified
**Notes:** User first asked "unified inbox là gì" — clarified it's an Apple-Mail-style "All Inboxes" merged view, and that it forces the UX-06 "select a concrete mailbox before acting" flow. After explanation, chose active-mailbox-only. Matches REQUIREMENTS' "any *future* all-mailboxes roll-up" language.

---

## Active-mailbox persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side per-user | Store active mailbox id in session/DB; MailboxContext filter resolves from session; sticky cross-device | ✓ |
| Client-side (URL/localStorage) | Active mailbox in URL path/param or localStorage; shareable links; large routing change | |

**User's choice:** Server-side per-user
**Notes:** Reuses cookie+Redis Spring Session infra; falls back to primary mailbox when unset; binds before Hibernate session (GmailAccessGuard invariant), mirroring TenantContext/TenantBindingFilter.

---

## Cross-mailbox rules reuse (AUTO-01, UX-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Bulk "Copy rules from…" | Clone all structured When/Then rules from one mailbox into another (enabled=false) | ✓ |
| Per-rule duplicate | Duplicate a single rule into another mailbox | |
| Manual only (v1.3) | No copy helper; user recreates rules | |

**User's choice:** Bulk "Copy rules from…"
**Notes:** Clones into target with enabled=false for review. Satisfies the requirement that cross-mailbox application is an explicit copy/template action, never a silent all-mailbox runtime rule.

---

## Claude's Discretion

- Exact storage of active-mailbox state (Spring Session attribute vs per-user column).
- Wave/sequencing of the 27 requirements (planner decides; phase boundary fixed).
- Migration shape for adding `gmail_mailbox_id` to downstream tables (Liquibase append-only).

## Deferred Ideas

- Unified/all-mailboxes inbox roll-up view (future milestone).
- Per-rule duplicate-to-mailbox.
- Account-deletion revoke of non-primary mailbox grants at Google (Phase 10 residual).
- Team collaboration, Zalo OA / CRM / omnichannel, Microsoft provider, OPS-FUT-01..04.

### Todo handling
- Folded: Phase 8 real-Gmail e2e smoke → VER-04.
- Reviewed, not folded: WR-06 (already satisfied by Phase 10 OAuthIntentRoutingTest + test SecurityConfig slice).

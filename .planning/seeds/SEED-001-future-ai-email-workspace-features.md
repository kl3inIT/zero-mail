---
id: SEED-001
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
trigger_when: "when planning Milestone 1.1 or any post-Phase-6 product expansion after CASA submission"
scope: large
---

# SEED-001: Future AI Email Workspace Features From Shortwave + Inbox Zero

## Why This Matters

Zero Mail should not lock itself to a narrow "inbox zero only" product shape if broader email-workspace features make users materially more productive. The current v1 promise is trust-first Gmail triage: natural-language rules, safe label/archive/draft actions, audit/undo, analytics, and CASA-ready data handling. That remains the right Phase 6 launch posture.

After Phase 6, we should deliberately evaluate features inspired by Shortwave and Inbox Zero that move Zero Mail toward an AI email workspace: AI search, mailbox chat, workflow automation, richer inbox primitives, and optional integrations. The core decision is not "can we build it?" but "which privacy/compliance posture are we willing to own?"

## When to Surface

**Trigger:** when planning Milestone 1.1 or any post-Phase-6 product expansion after CASA submission.

Surface this seed during:

- Milestone 1.1 planning for admin UI, chat UI, support tooling, or AI assistant surfaces.
- Any phase proposing Google Calendar, Slack, Notion, task-management, or team-collaboration integrations.
- Any phase proposing full-history import, semantic search, vector indexing, mailbox memory, or chat over mailbox history.
- Any update to privacy policy, CASA materials, OAuth scopes, or data-retention promises.

## Scope Estimate

**Large**. This is not one task. It should become one or more roadmap phases, likely split into a low-risk V1.1 assistant track and a higher-risk V2 AI workspace track.

## Candidate Tracks

Detailed child seeds created from this umbrella:

- `SEED-002` — AI mailbox search and answer engine.
- `SEED-003` — screen-aware AI assistant and command center.
- `SEED-004` — inbox splits, bundles, todos, snooze, and delivery schedules.
- `SEED-005` — team collaboration and shared email workspace.
- `SEED-006` — calendar scheduling and meeting briefs.
- `SEED-007` — messaging assistant via Slack, Telegram, and Zalo.
- `SEED-008` — Tasklet-style agentic workflow automation.
- `SEED-009` — bulk cleanup, cold email blocker, and smart filing.
- `SEED-010` — sales engagement, CRM sync, read receipts, and link tracking.
- `SEED-011` — admin, support, and compliance console.

### Track A: V1.1 Privacy-Preserving Assistant

Goal: add useful assistant features without changing the current "no long-term raw body / no email embeddings" posture.

Candidate features:

- Thread-level chat: user asks questions about the currently opened thread only.
- Gmail API search bridge: use Gmail `q=` search on demand, fetch selected results, summarize in memory, then discard content.
- AI-assisted rule editing: chat UI helps create, explain, test, and refine natural-language rules.
- Command palette: quick actions for rules, pause, undo, draft reply, billing, analytics, settings.
- Inbox productivity primitives: snooze, to-reply / waiting-on-them buckets, bundles, delivery schedule, keyboard shortcuts.
- Admin/support console: tenant health, Gmail connection state, Pub/Sub backlog, watch-renewal state, credit ledger status, CASA evidence export.
- Optional Calendar phase: only if there is a real user flow, use incremental authorization and the narrowest scope such as free/busy before broader event scopes.

Privacy posture:

- Do not persist raw email bodies long-term.
- Do not persist email embeddings.
- Do not store prompt/completion payloads.
- Treat fetched email content as short-lived request data.
- Keep Phase 6 CASA submission simple unless a new Google scope is truly required.

### Track B: V2 Full AI Email Workspace

Goal: compete closer to Shortwave-style AI workspace behavior.

Candidate features:

- Full Gmail history import and sync.
- Server-side searchable message/thread index.
- Semantic search over mailbox history.
- "Chat with my mailbox" across historical threads.
- Personal memory by sender/company/thread history.
- AI autocomplete and tone personalization from sent-mail history.
- Team/shared inbox workflows: assignment, private comments, shared labels, shared templates.
- Workflow integrations: Calendar, Slack, Notion, Asana/Linear/Jira, HubSpot/CRM, MCP-style tool integrations.
- Stronger automation agent: "organize this inbox", "find all unpaid invoices", "turn these emails into tasks", "schedule follow-up".

Privacy/compliance posture:

- This track likely requires changing current Zero Mail commitments.
- Full semantic search probably requires storing parsed email content and/or embeddings.
- Privacy policy, Terms, Google scope justification, CASA evidence, retention/deletion controls, encryption design, employee-access policy, and subprocessor list must be updated before launch.
- User consent should be explicit and feature-scoped: users opt into indexing/history import rather than receiving it silently.

## Product Principles

- Do not request Google scopes for future-only features. Add scopes only when the feature is implemented, demonstrable, and user-facing.
- Prefer incremental OAuth for optional integrations.
- Keep "never auto-send" unless explicitly re-decided; save drafts remains the safe default.
- Every AI feature touching mailbox content needs auditability, deletion, and tenant-isolation tests.
- Be honest in CASA/privacy docs. Do not claim "no long-term body storage" if a future feature introduces full-history indexing.
- If a feature requires a stronger privacy posture, make that an explicit product decision rather than a hidden implementation detail.

## Breadcrumbs

- `.planning/PROJECT.md` — product lineage: inspired by Inbox Zero but independent architecture and brand.
- `.planning/ROADMAP.md` — Phase 6 launch hardening and CASA-verified launch gate.
- `.planning/phases/04-triage-convergence-hero/04-AI-SPEC.md` — domain framing, trust story, and Shortwave/Inbox Zero competitive context.
- `.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md` — to-reply / awaiting-their-reply buckets modeled on Inbox Zero Reply Zero.
- `docs/casa/` — current CASA/privacy artifacts that must be updated before any broader mailbox-content indexing.
- Discussion on 2026-05-14 — Shortwave feature review, Gmail/Calendar scope strategy, AI search permissibility, and future product direction.

## Notes

Phase 6 should still submit the current V1 trust-first app as soon as it is demo-ready. Do not wait for admin/chat UI unless those features require new Google scopes or materially change data handling.

For Milestone 1.1, the safest next step is Track A: assistant and admin features that preserve the current data-retention posture. Track B should be a deliberate V2 milestone with updated compliance materials.

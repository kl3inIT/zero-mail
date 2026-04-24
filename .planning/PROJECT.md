# Zero Mail (placeholder name)

## What This Is

Zero Mail is a multi-tenant SaaS that helps busy professionals and founders reach inbox zero in Gmail by using AI to auto-triage, categorize, archive, and draft replies to incoming email based on user-defined natural-language rules. It is an architectural re-build inspired by Inbox Zero (https://github.com/elie222/inbox-zero), but with a Java 25 / Spring Boot 4 backend, Spring AI for model orchestration, and our own branding.

## Core Value

**AI auto-triage that users trust with their real inbox.** If triage quality, safety (no destructive actions, no data leakage), and reliability aren't excellent, nothing else matters — users will uninstall the Gmail grant within a day.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward these. Hypotheses until shipped & validated. -->

**Auth & onboarding**
- [ ] User can sign up and sign in with Google OAuth (Gmail scopes)
- [ ] User can connect one Gmail / Google Workspace account in v1
- [ ] User can revoke access and delete their account + data

**AI triage (hero feature)**
- [ ] System receives near-real-time new-mail notifications via Gmail Pub/Sub push
- [ ] System classifies each new message against the user's active rules using an LLM
- [ ] System can apply labels to messages automatically
- [ ] System can archive (skip inbox) messages automatically
- [ ] System can save a draft reply in Gmail (never auto-sends in v1)
- [ ] User sees a per-message audit trail of what triage did and why

**Natural-language user rules**
- [ ] User writes rules in plain English (e.g., "Archive receipts from Stripe and label them Finance")
- [ ] AI interprets the rule into a structured matcher + action set
- [ ] User can preview a rule against recent mail before enabling
- [ ] User can enable, disable, reorder, edit, and delete rules

**AI draft replies**
- [ ] User can request an AI-generated draft reply for a thread
- [ ] Draft is created in Gmail as a normal draft (user reviews & sends)
- [ ] Draft tries to match the user's writing tone from prior sent mail

**Analytics dashboard**
- [ ] User sees volume triaged, time saved, top senders, rule hits over time
- [ ] Metrics are derived from minimal metadata (not from stored email bodies)

**LLM routing & BYOK**
- [ ] Default LLM traffic routes through OpenRouter behind a Spring AI abstraction
- [ ] User can bring their own API key (OpenAI, Anthropic, etc.) — BYOK
- [ ] BYOK usage bypasses platform LLM cost (user pays their provider directly)

**Credits & billing (pay-as-you-go)**
- [ ] User buys prepaid credits upfront
- [ ] Each billable action (triage, draft, attachment analysis) deducts credits
- [ ] User sees real-time credit balance and per-action cost
- [ ] System blocks billable actions when balance is insufficient
- [ ] (Credit unit economics finalized during roadmap / billing phase)

**Privacy & safety posture**
- [ ] Email bodies are sanitized (HTML stripped, hidden text removed) before LLM calls
- [ ] Bodies are truncated to a safe token budget before LLM calls
- [ ] All email content is treated as untrusted input; prompt-injection hardening applied
- [ ] No long-term storage of raw email bodies, LLM prompts, LLM completions, or embeddings
- [ ] Only minimal derived metadata + short-lived draft caches persist, with strict retention limits

**Web UI**
- [ ] Next.js / React frontend (separate module in the monorepo) talks to Spring Boot via REST
- [ ] UI covers onboarding, rules CRUD, triage audit log, draft review, analytics, billing

### Out of Scope

<!-- Explicit boundaries. Reasoning included so we don't silently re-add them. -->

- **Auto-send replies (no human review)** — safety risk too high for v1; opt-in advanced feature post-validation.
- **Outlook / Microsoft 365 support** — Gmail-only in v1 to ship focused; re-evaluate after product-market fit.
- **Generic IMAP/SMTP support** — different auth, push, and label model; would double provider surface area.
- **Self-hosted / open-source distribution** — Cloud SaaS only in v1; OSS model is a separate strategic decision.
- **Team / seat-based plans** — v1 targets individual prosumers; team features deferred until SMB signals appear.
- **Cold-email blocker as a distinct feature** — can be modeled as a user rule in v1; revisit as a first-class feature later.
- **Bulk unsubscribe as a distinct feature** — same: expressible as a rule; first-class feature deferred.
- **Reply-tracker / follow-up nudges** — nice-to-have, deferred past v1.
- **Long-term storage of email content, LLM prompts, completions, or embeddings** — privacy constraint, not a future feature.
- **Enterprise features (SSO, SCIM, audit exports, DPA-grade compliance)** — target is busy pros/founders, not enterprise buyers in v1.

## Context

**Product lineage.** Inspired by Inbox Zero (https://github.com/elie222/inbox-zero). We are not forking; this is an independent architecture and brand. Inbox Zero's Next.js + Node implementation is a reference for UX patterns and feature coverage, not for code.

**Target user.** Busy professionals and founders who get 100-500+ emails/day and want an AI agent that actually does inbox work for them, not just summarizes it. They are technical enough to understand rules and BYOK but expect prosumer-grade polish.

**Existing internal reference.** User pointed at an existing `D:\DTH\ai-agent-core\ai-agent` project as the pattern for OpenRouter routing. Planning phases should inspect that repo (if still available) before designing the LLM gateway module.

**Runtime posture.** Multi-tenant cloud SaaS. Every request is in the context of a tenant (user). Gmail push webhooks arrive asynchronously via Google Pub/Sub and must be processed with strong idempotency and per-tenant isolation.

**Safety posture.** The app has write access to people's primary email accounts. Any destructive, irreversible, or silently-sent action is a product-killing risk. In v1, every triage action must be reversible (labels, archive, draft) and every autonomous action must leave an auditable trail.

## Constraints

- **Language/runtime**: Java 25 — locked by user directive.
- **Framework**: Spring Boot 4 — locked by user directive.
- **Build**: Gradle with Kotlin DSL — locked by user directive.
- **AI**: Spring AI for LLM orchestration (model abstraction, prompts, tool calls) — locked by user directive.
- **Structure**: Monorepo / multi-module Gradle project — locked by user directive. Exact module topology (domain-driven vs hexagonal vs layered) will be decided during roadmap/research phase.
- **Frontend**: Next.js / React as a separate module inside the monorepo — locked by product decision.
- **Mail provider (v1)**: Gmail / Google Workspace only, via Gmail API + Google Pub/Sub push — locked by product decision.
- **Distribution (v1)**: Cloud SaaS, multi-tenant — locked by product decision.
- **LLM routing**: Default via OpenRouter behind Spring AI; BYOK supported — locked by product decision.
- **Billing model**: Prepaid credits, pay-as-you-go; unit economics TBD — locked direction, details deferred.
- **Privacy**: No long-term storage of raw email bodies, LLM prompts/completions, or embeddings. Content always sanitized + truncated + prompt-injection-hardened before hitting any LLM — locked.
- **Write actions allowed in v1**: label, archive (skip inbox), save Gmail draft. **Auto-send is forbidden.**
- **Primary datastore**: PostgreSQL (confirmed). Redis / vector DB additions deferred to roadmapper/research decision.
- **Timeline**: Exploratory project — learning-oriented, no hard ship deadline. Favor architectural quality and defensibility over speed.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Gmail-only in v1 | One provider halves mail-integration scope; Gmail covers the busy-pro/founder target; Outlook is a separate effort | — Pending |
| Pub/Sub push over polling | User expects near-real-time triage; polling would cap responsiveness and still cost API quota | — Pending |
| OpenRouter default + BYOK | Matches Inbox Zero's flexibility, lets us switch models without code change, and gives users cost control | — Pending |
| No auto-send in v1 | A single bad auto-sent reply is a trust-ending event; draft-only keeps safety floor high | — Pending |
| Prepaid credits, pay-as-you-go | Aligns revenue with actual LLM cost; avoids the freemium abuse surface | — Pending |
| No long-term storage of email bodies / LLM I/O / embeddings | Privacy is the #1 blocker to installing an AI mail agent; makes the trust story simple to explain | — Pending |
| Next.js frontend, separate module | Keeps frontend talent pool open; backend stays a clean API boundary; matches Inbox Zero DX | — Pending |
| Monorepo module layout — deferred | Topology depends on bounded contexts that will be clearer after feature research; roadmapper picks | — Pending |
| Name "Zero Mail" — placeholder | Directory-derived; final brand will be chosen before public launch to avoid rework | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-24 after initialization*

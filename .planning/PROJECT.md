# Zero Mail (placeholder name)

## What This Is

Zero Mail is a multi-tenant SaaS that helps busy professionals and founders reach inbox zero in Gmail by using AI to auto-triage, categorize, archive, and draft replies to incoming email based on user-defined natural-language rules. **As of v1.0 (2026-05-15) it ships:** Java 25 / Spring Boot 4 backend (`backend/core` + `backend/api` + `backend/worker`), Spring AI 2.0.0-M6 LLM gateway with BYOK + per-tenant credit ledger, deterministic rules engine, hero triage orchestrator with audit/undo/shadow-mode/sender safety net, AI draft replies, analytics + daily digest, Next.js 16 / React 19 / shadcn frontend with Vietnamese-default i18n, and a CASA-ready safety posture.

## Current State

**Shipped:**
- v1.0 MVP — `v1.0.0-rc1` tagged 2026-05-15.
- v1.1 Email assistant chat — `v1.1` tagged 2026-05-19 (Phase 7 only; Phase 8 deferred to v1.2).

- **Backend:** ~18 phases (v1.0 + Phase 7), Java 25 + Spring Boot 4.0.6 + Spring Modulith + Hibernate 7 + Liquibase 5 + Spring AI 2.0.0-M6.
- **Frontend:** Next.js 16.2.4 + React 19.2.5 + Tailwind 4 + shadcn/ui + TanStack Query + typed OpenAPI client; Vietnamese-default with English secondary. Brand palette shifted teal `#0E5E5A` → purple `#867AEB` in PR #40 (2026-05-19) — user-page visual refresh queued for v1.2.
- **Infra:** Single VPS — Postgres 17 + Redis 7 + reverse proxy + api + worker + web on one host. No GCP / Kafka / vector DB.
- **Trust posture:** Auto-send architecturally blocked (ArchUnit + safety policy + repo-wide grep enforced; v1.1 Phase 7 flipped Gmail send call sites 0 → exactly 1 via `AssistantSendExecutor`); no long-term storage of raw email bodies, email-content LLM prompts/completions, or embeddings (rule-builder assistant chat excluded — see Privacy scope); per-tenant Scoped Values + multi-tenant leak test green; @Sensitive Logback scrub end-to-end verified; chat_message body-ban enforced 3-layer (sanitizer + ArchUnit + Postgres trigger).
- **Launch state:** OAuth Testing mode (production CASA verification deferred to dormant SEED-012). v1.1 chat surface ships **without** hostile-corpus eval gate (deferred to v1.2 hardening); v1.0 LAUNCH-GO-NOGO still applies; v1.1 GA tag annotated with deferred-eval caveat.

## Next Milestone: v1.2 — Admin console + Settings + Visual refresh + GA discipline

**Goal:** Build admin console as foundation, then ship Settings UI on top of admin-curated catalog. Bundle user-page visual refresh aligned with PR #40 palette, plus deferred Phase 8 hardening + hostile-corpus eval + GA discipline. Tag v1.2 GA on successful hardening sweep.

**Planned scope (formalize via `/gsd-new-milestone`):**

1. **Admin console foundation** — auth/role + `/admin/*` route + RBAC + catalog persistence + admin UI to curate provider/model catalog + admin master key management. Activates **SEED-011** (admin-support-and-compliance-console) and **OPS-02** (deferred from v1.1).
2. **Visual refresh of user pages** — audit Phase 7 chat UI, Settings, Triage, Rules, Analytics; align with PR #40 brand palette (teal → purple); fix hardcoded color/visual hierarchy regressions.
3. **Settings page (full)** — 4 tabs (Personalization, Behavior, Safety Net, AI Provider/Model) via shadcn `<Tabs>` query-param-driven; carries forward 19 v1.1 reqs (SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04, SET-AI-01..04). AI tab depends on admin-curated catalog from #1.
4. **Hardening + GA discipline** — hostile-corpus `aiEval` suite (15 hostile emails + 10 hostile personal_instructions + VIP send refusal + VI/EN fidelity); Grafana dashboards (lease residuals, audit-vs-state mismatch, ordering violations, leak counters, BUDGET_EXHAUSTED rate); CASA evidence refresh for chat surface; README/CONTRIBUTING send-call-site discipline doc; LAUNCH-GO-NOGO checklist; **v1.2 GA tag**.

**Seeds activating in v1.2:**

- `SEED-011` — admin-support-and-compliance-console (promoted to v1.2 Phase 1)

**Still deferred to v1.3+:** provider expansion (Bedrock/Azure/Groq/Perplexity/native OpenRouter/OpenAI-compatible/Vertex), waitlist OAuth provisioning, learned patterns, multi-rule selection, browser extension sync, image attachments in chat, CASA production verification (SEED-012).

## Core Value

**AI auto-triage that users trust with their real inbox.** Validated through v1.0: trust posture (no auto-send, no stored bodies, undoable actions) is the architectural backbone — every phase reinforced it, and the launch go/no-go signoff is bound to it.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. Each row carries the milestone where it landed. -->

**Auth & onboarding** *(v1.0)*
- ✓ Google OAuth + Gmail scopes signup/signin (AUTH-01, v1.0)
- ✓ One Gmail / Workspace account per tenant (AUTH-02, v1.0)
- ✓ Revoke + delete account + all stored data (AUTH-03, v1.0)
- ✓ Cookie-based session via Spring Session Redis, not JWT (AUTH-04, v1.0)
- ✓ DISCONNECTED state + reconnect prompt on `invalid_grant` (AUTH-05, v1.0)
- ✓ Guided onboarding: connect Gmail → enable template rule → first triage preview (AUTH-06, v1.0)

**Foundation & safety** *(v1.0)*
- ✓ Tenant-scoped context via Scoped Values, never ThreadLocal (FND-01, v1.0)
- ✓ ArchUnit fails new ThreadLocal in request/worker paths (FND-02, v1.0)
- ✓ `@Sensitive` wrapper + Logback scrub filter end-to-end (FND-03, v1.0)
- ✓ ArchUnit fails Sensitive-typed log args (FND-04, v1.0)
- ✓ Multi-tenant virtual-thread leak test green (FND-05, v1.0)
- ✓ Skeleton OpenAPI consumed by `apps/web` via `openapi-typescript` (FND-06, v1.0)
- ✓ CASA restricted-scope kicked off at OAuth wiring (FND-07, v1.0; production close in SEED-012)

**Mail ingestion** *(v1.0)*
- ✓ Gmail `users.watch` + Pub/Sub push, idempotent per `(tenantId, historyId, messageId)` (MAIL-01..05, v1.0)
- ✓ Global pause toggle wired UI ↔ backend (MAIL-06, v1.0)

**Billing (prepaid credits)** *(v1.0)*
- ✓ SePay/VietQR top-up + signed webhook (BILL-01, v1.0)
- ✓ Double-entry Postgres ledger with reserve/settle/release + concurrency safety (BILL-02..04, v1.0)
- ✓ Real-time balance + per-action cost in UI; insufficient-credit blocks billable actions (BILL-05..06, v1.0)
- ✓ BYOK actions bypass platform credits (BILL-07, v1.0)

**LLM gateway** *(v1.0)*
- ✓ Single `LlmGateway` (Spring AI 2.0.0-M6); ArchUnit confines vendor SDKs (LLM-01, v1.0)
- ✓ OpenRouter default + per-call model pin + 3-provider BYOK (LLM-02..03, v1.0)
- ✓ AES-GCM BYOK encryption + per-call zeroing; never logged or persisted in plaintext (LLM-04, v1.0)
- ✓ HTML sanitize + NFC + Unicode-tag strip + ≤4k token truncate (LLM-05..08, v1.0)
- ✓ Tool-call allow-list + structured schema; safety violation rejects pre-execution (LLM-07, v1.0)
- ✓ No raw body/prompt/completion persistence beyond short-lived in-memory cache (LLM-09, v1.0)
- ✓ Per-tenant daily LLM spend cap + golden-set drift detection (LLM-10..11, v1.0)

**Rules engine** *(v1.0)*
- ✓ NL-to-AST compile via Spring AI tool-call (no free-form runtime LLM output) (RULE-01..02, v1.0)
- ✓ Deterministic evaluator; `SEMANTIC_INTENT` deferred to triage batched LLM (RULE-03..04, v1.0)
- ✓ Side-effect-free preview before enable; CRUD + reorder + edit (RULE-05..06, v1.0)
- ✓ DB-backed template gallery (receipts, newsletters, calendar starters) (RULE-07, v1.0)

**Triage convergence (hero)** *(v1.0)*
- ✓ Per-message orchestration: rules in order → safety policy → allow-listed Gmail writes (TRG-01..02, v1.0)
- ✓ Auto-send blocked at gateway; ArchUnit grep proves zero send call sites (TRG-03, v1.0)
- ✓ Label / archive / save-draft only (TRG-04, v1.0)
- ✓ Immutable audit + undo within 30-day window (TRG-05..06, v1.0)
- ✓ Tenant-wide opt-in shadow mode + sender safety net (TRG-07..08, v1.0)

**Draft replies** *(v1.0)*
- ✓ On-demand AI draft per thread; saved as Gmail draft with correct `In-Reply-To` / `References` (DRFT-01..02, v1.0)
- ✓ In-request tone matching, no persisted embeddings (DRFT-03, v1.0)
- ✓ Never auto-sends; user reviews in Gmail (DRFT-04, v1.0; classifier eval 22/22 = 100%)

**Analytics & daily digest** *(v1.0)*
- ✓ Volume / time saved / top senders / rule hits over selectable window (ANL-01, v1.0)
- ✓ Metadata-only metrics; ArchUnit content-ban test enforces (ANL-02, v1.0)
- ✓ Daily digest email via Resend with idempotency (ANL-03, v1.0; live sender setup deferred)

**Web UI** *(v1.0)*
- ✓ Next.js 16 / React 19 frontend in `apps/web`, typed OpenAPI client (WEB-01, v1.0)
- ✓ End-to-end UI: onboarding, rules + live preview, triage audit + undo, draft review, analytics, billing (WEB-02, v1.0 across 5A/5B/5C)
- ✓ In-product privacy page (no stored bodies, no auto-send, BYOK option) (WEB-03, v1.0)
- ✓ Persistent chrome with global pause + credit balance + connection health (WEB-04, v1.0)

### Active

<!-- Next milestone scope. Define via `/gsd:new-milestone`. -->

**v1.1 in progress** (started 2026-05-17). Scope: Email assistant chat + Settings page (see "Current Milestone" section above). Requirements: `.planning/REQUIREMENTS.md`. Roadmap: `.planning/ROADMAP.md`.

*Deferred from v1.1 candidate list:* CASA production verification (SEED-012), live Resend deliverability + payment-provider smoke tests, Outlook/Microsoft 365, Auto-send opt-in for narrow rules with per-rule approval flow, bulk unsubscribe, cold-email blocker, reply-tracker, attachment auto-filing, team/seat plans, waitlist OAuth provisioning.

### Out of Scope

<!-- Explicit boundaries. Reasoning included so we don't silently re-add them. -->

- **Auto-send replies (no human review)** — single bad auto-send is trust-ending; opt-in narrow auto-send deferred to v2 per v1 trust story.
- **Outlook / Microsoft 365** — Gmail-only in v1 to ship focused; v2 candidate.
- **Generic IMAP/SMTP** — different auth/push/label model; doubles provider surface area.
- **Self-hosted / open-source distribution** — Cloud SaaS only in v1; OSS is a separate strategic decision.
- **Team / seat-based plans** — v1 targets individual prosumers; team features wait for SMB signal.
- **Long-term storage of raw email bodies, email-content LLM prompts/completions, or embeddings** — privacy constraint, permanent (not a deferred feature). Rule-builder assistant chat is excluded — see Privacy scope in Constraints.
- **RAG over user mail bodies** — requires persistent derived features; incompatible with privacy stance.
- **Vector DB in v1 infra** — no embedding persistence → no vector DB need.
- **Full in-app mail client UI** — Gmail remains primary client; we augment, not replace.
- **Enterprise SSO / SCIM / DPA** — target buyer is busy pro / founder, not enterprise procurement in v1.
- **Cold-email blocker / bulk unsubscribe / reply-tracker** as distinct first-class features — expressible as user rules in v1; first-class feature deferred to v2.

## Context

**Product lineage.** Architecturally re-built, inspired by Inbox Zero (https://github.com/elie222/inbox-zero) — UX/feature reference, not code. Local clone at `../inbox-zero` for inspection only.

**Target user.** Busy professionals and founders with 100-500+ daily emails who want an AI agent that *does* inbox work, not just summarizes it. Technical enough for rules + BYOK; expect prosumer-grade polish.

**Runtime posture.** Multi-tenant cloud SaaS. Every request is tenant-scoped (Scoped Values, never ThreadLocal). Gmail Pub/Sub push arrives asynchronously and is processed with strong idempotency (`ON CONFLICT DO NOTHING`).

**Safety posture.** App has write access to people's primary email. Every triage action is reversible (label / archive / draft); auto-send is forbidden at the gateway and ArchUnit-enforced; every autonomous action leaves an audit trail with 30-day undo.

**v1.0 scale.** ~17 phases / 123 plans / 221 tasks, locked Vietnamese-default i18n + English secondary, single-VPS deployment baseline, OAuth Testing mode at launch (production OAuth gated by SEED-012 CASA closure).

## Constraints

- **Language/runtime**: Java 25 — locked.
- **Framework**: Spring Boot 4.0.6 — locked.
- **Build**: Gradle 9.x with Kotlin DSL + libs.versions.toml — locked.
- **AI**: Spring AI 2.0.0-M6 — locked (M6 → GA churn possible; all usage confined to one adapter package).
- **Structure**: Monorepo — `backend/core` + `backend/api` + `backend/worker` + `apps/web`. Internal backend boundaries package-based, enforced by Spring Modulith + ArchUnit.
- **Frontend**: Next.js 16 / React 19 — locked.
- **Mail provider (v1)**: Gmail / Workspace only via Gmail API + Pub/Sub push — locked.
- **Distribution (v1)**: Self-hosted SaaS on a single VPS — locked.
- **LLM routing**: Default OpenRouter behind Spring AI; BYOK supported — locked.
- **Billing model**: Prepaid credits, pay-as-you-go. Vietnam beta uses SePay/VietQR + Postgres ledger + configurable VND-per-credit; global Merchant-of-Record/card provider deferred.
- **Privacy**: No long-term storage of raw email bodies, LLM prompts/completions, or embeddings — locked. **Scope:** the email-content processing pipeline (triage, draft generation). User-typed rule-builder assistant chat (chat messages + structured tool outputs) persists normally — it is UI configuration input, not extracted email content. Still forbidden inside chat: inlining email bodies into long-term assistant prompts (use short-lived in-memory cache) and embeddings of user mail.
- **Write actions allowed**: (1) **Rules engine** (auto-triggered by incoming mail): label, archive (skip inbox), save Gmail draft only — **auto-send forbidden** (= rule firing → send without per-message user click). (2) **Chat assistant** (user-initiated): same as rules engine + (v1.1+) user-confirmed send/reply/forward via chat preview card. AI drafts message → chat UI renders preview with edit + send + cancel → send executes only on explicit per-message user click. Auto-send (rule-triggered, no per-message click) remains forbidden in all pathways.
- **Primary datastore**: PostgreSQL 17 self-hosted on the VPS. Redis 7 same VPS for cache/session/rate-limit only; no vector DB.
- **Schema migrations**: Liquibase YAML — locked.
- **Timeline**: Exploratory / learning-oriented; favor architectural quality over speed.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Gmail-only in v1 | Halves mail-integration scope; covers target user | ✓ Good — shipped v1.0 |
| Pub/Sub push over polling | Near-real-time triage; preserves API quota | ✓ Good — MAIL-01 verified |
| OpenRouter default + BYOK via Spring AI | Model flexibility + user cost control | ✓ Good — LLM-01..04 shipped |
| No auto-send in v1 | One bad auto-send is trust-ending | ✓ Good — TRG-03 + ArchUnit + grep gate |
| Prepaid credits, pay-as-you-go | Aligns revenue with LLM cost; avoids freemium abuse | ✓ Good — BILL-01..07 shipped |
| No long-term body/prompt/completion/embedding storage | Privacy is #1 install blocker | ✓ Good — repo-wide privacy sweeps green |
| Next.js frontend separate module | Open frontend talent pool; clean API boundary | ✓ Good — WEB-01..04 shipped |
| Monorepo `backend/core + api + worker + apps/web` | Simple build; clean HTTP edge / async worker split | ✓ Good — Spring Modulith verifies |
| Name "Zero Mail" placeholder | Final brand pre-launch | — Pending rename before public launch |
| Single bundled Google OAuth registration | Phase 1.5 removed `google-gmail` leg; one consent flow | ✓ Good — Inbox Zero parity |
| Single-VPS deployment baseline | No GCP starter; one VPS hosts everything | ✓ Good — load-test 50 tenants PASS |
| Billing config under `ZeroMailCoreProperties` | Single core-owned properties root, no per-domain configuration classes | ✓ Good — survived Phase 2B review |
| Vietnamese default + English secondary i18n | Target market is Vietnam beta first | ✓ Good — Phase 1.1 shipped; CI parity gate |
| Postgres-backed queue (`SKIP LOCKED`), no Kafka/RabbitMQ | Pub/Sub already retries; one less moving piece | ✓ Good — Phase 4 ShedLock + retry green |
| Server-issued cookie session, not JWT | Simpler revoke; HttpOnly+SameSite+Secure | ✓ Good — AUTH-04 shipped |
| Modulith JDBC event spine for cross-module commands | Avoids tight coupling without Kafka | ✓ Good — `MailMessageObserved` → triage works |
| Phase 1.4 closed without ship; superseded by Phase 1.5 | Mismatched-account two-leg OAuth was wrong model | ✓ Good — Inbox Zero pivot saved cycles |
| Bundle 32 quick tasks + 12 SEEDs at v1.0 close | Closure hygiene; SEEDs are dormant by design | ⚠ Revisit at v1.1 — drop seeds that age out |
| OAuth Testing mode at launch | CASA production verification 4–12 weeks external; ship Testing mode now | — Pending — gated by SEED-012 |

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
*Last updated: 2026-05-17 — v1.1 milestone started (Email assistant chat + Settings page)*

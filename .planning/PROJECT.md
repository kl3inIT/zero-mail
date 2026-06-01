# Zero Mail (placeholder name)

## What This Is

Zero Mail is a multi-tenant SaaS that helps busy professionals and founders reach inbox zero in Gmail by using AI to auto-triage, categorize, archive, and draft replies to incoming email based on user-defined natural-language rules. **As of v1.0 (2026-05-15) it ships:** Java 25 / Spring Boot 4 backend (`backend/core` + `backend/api` + `backend/worker`), Spring AI 2.0.0-M6 LLM gateway with BYOK + per-tenant credit ledger, deterministic rules engine, hero triage orchestrator with audit/undo/shadow-mode/sender safety net, AI draft replies, analytics + daily digest, Next.js 16 / React 19 / shadcn frontend with Vietnamese-default i18n, and a CASA-ready safety posture.

## Current State

**Shipped:**
- v1.0 MVP — `v1.0.0-rc1` tagged 2026-05-15.
- v1.1 Email assistant chat — `v1.1` tagged 2026-05-19 (Phase 7 only).
- v1.2 Admin Console + User Settings UI — `v1.2` tagged 2026-06-01 (Phases 8, 08.1, 9, + bonus 08-bulk-unsubscribe campaign; 70/73 requirements complete, 3 deferred to v1.3; no GA tag this milestone).

- **Backend:** ~21 phases (v1.0 + Phase 7 + v1.2's 8/08.1/9/08-bulk-unsubscribe), Java 25 + Spring Boot 4.0.6 + Spring Modulith + Hibernate 7 + Liquibase 5 + Spring AI 2.0.0-M6.
- **Admin:** NEW separate `apps/admin` Vite + React 19 SPA on `admin.zeromail.com` — WebAuthn passkey auth (dedicated `@Order(1)` SecurityFilterChain), HMAC-chained append-only audit, master-key management for 6 LLM providers, curated catalog with Sync-from-`/models`, and metadata-only tenant/queue/spend dashboards.
- **Frontend:** Next.js 16.2.4 + React 19.2.5 + Tailwind 4 + shadcn/ui + TanStack Query + typed OpenAPI client; Vietnamese-default with English secondary. Single `/ai` settings surface (voice, behavior, updates, safety net, BYOK) on the admin-curated catalog. Brand palette shifted teal `#0E5E5A` → purple `#867AEB` in PR #40 — user-page visual refresh deferred to v1.3.
- **Infra:** Single VPS — Postgres 17 + Redis 7 + NPM reverse proxy + 9Router sidecar + api + worker + web + admin on one host. No GCP / Kafka / vector DB.
- **Trust posture:** v1.2 Phase 08.1 replaced the v1.0/v1.1 hard ban on rule-triggered outbound sends with one default-ON global `Auto-send rules` setting, safety/rate/idempotency gates, draft fallback, audit, and a single ArchUnit-locked outbound gateway boundary (chat + rules runtime both route through it). No long-term storage of raw email bodies, email-content LLM prompts/completions, or embeddings (rule-builder assistant chat excluded — see Privacy scope); per-tenant Scoped Values + multi-tenant leak test green; @Sensitive Logback scrub end-to-end verified; chat_message body-ban enforced 3-layer; admin surfaces guarded by ArchUnit + `AdminResponseBodyBanFilter`.
- **Launch state:** OAuth Testing mode (production CASA verification deferred to dormant SEED-012). v1.2 ships **without** a GA tag — hostile-corpus eval, Grafana dashboards, CASA refresh, visual refresh, and LAUNCH-GO-NOGO all deferred to v1.3+.

## Last Shipped Milestone: v1.2 — Admin Console + User Settings UI ✅ (2026-06-01)

**Delivered:** Admin console foundation (Phase 8), Inbox Zero-style examples/actions with user-enabled outbound automation (Phase 08.1), the user Settings UI on the admin-curated catalog (Phase 9), and a bonus bulk-unsubscribe campaign phase. 70/73 requirements complete. No GA tag this milestone — visual refresh, hostile-corpus eval, Grafana, CASA refresh, LAUNCH-GO-NOGO, and the formal GA tag deferred to v1.3+.

**Next milestone:** Not yet defined — run `/gsd-new-milestone` to scope v1.3. Likely v1.3 candidates: the 3 deferred settings reqs (SET-BEHV-05, SET-SAFE-02/03), VISUAL-REFRESH-01..06 (purple palette alignment), EVAL-01..05 (hostile-corpus aiEval), OPS-DASH-01..04 (Grafana), CASA-01, and a formal GA tag.

<details>
<summary>v1.2 target features (shipped)</summary>

**Target features:**

1. **Admin foundation (Phase 8)** — `/admin/*` routes + `ROLE_ADMIN` RBAC + admin action audit logs + per-provider/per-feature LLM catalog curation UI with Sync-from-`/models` flow + AES-GCM-encrypted master key management for OpenAI/Anthropic/Google/DeepSeek + test-connection + tenant read-only views + worker queue health (read-only) + promoted global LLM spend dashboard. Activates **SEED-011** (admin-support-and-compliance-console) and **OPS-02** (deferred from v1.1).
2. **Inbox Zero-style actions/examples (Phase 08.1)** — rule creation entry points (`Create rules`, `Choose from examples`, `Add manually`), copied Inbox Zero examples/personas as seed content, admin-managed example/action catalog, Available Actions panel, user settings for outbound automation, and gateway-bound architecture tests for `send_reply`, `forward_email`, and `send_email`.
3. **Settings UI on curated catalog (Phase 9)** — 4 tabs (Personalization, Behavior, Safety Net, AI Provider/Model) via shadcn `<Tabs>` query-param-driven; carries forward the 19 deferred v1.1 reqs (SET-AI-01..04, SET-VOICE-01..06, SET-BEHV-01..05, SET-SAFE-01..04). AI Provider/Model tab depends on the admin-curated catalog from Phase 8.

**Seeds activating in v1.2:**

- `SEED-011` — admin-support-and-compliance-console (promoted to v1.2 Phase 8)

**Explicitly deferred to v1.3+:**

- Visual refresh of user pages (purple brand palette alignment from PR #40)
- Hostile-corpus `aiEval` suite (15 hostile emails + 10 hostile personal_instructions + VIP send refusal + VI/EN fidelity)
- Grafana ops dashboards (lease residuals, audit-vs-state mismatch, ordering violations, leak counters, BUDGET_EXHAUSTED rate)
- CASA evidence refresh for chat surface
- LAUNCH-GO-NOGO checklist
- Formal **v1.2 GA tag**
- Provider expansion (Bedrock/Azure/Groq/Perplexity/native OpenRouter/OpenAI-compatible/Vertex), waitlist OAuth provisioning, learned patterns, multi-rule selection, browser extension sync, image attachments in chat, CASA production verification (SEED-012).

</details>

## Core Value

**AI auto-triage that users trust with their real inbox.** The current trust posture is explicit user control for risky automation, no stored email bodies/prompts/completions, undo/review where possible, and auditable gateway boundaries for any outbound send.

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
- ✓ Real-time balance + action-level cost in UI; insufficient-credit blocks billable actions (BILL-05..06, v1.0)
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

**Email assistant chat** *(v1.1)*
- ✓ Streaming chat assistant with 24-tool catalog, source-aware persistence, tenant-safe read tools, user-confirmed send/reply/forward via preview card (CHAT-*, ARCH-01..07, v1.1)

**Admin console & operator tooling** *(v1.2)*
- ✓ WebAuthn passkey admin auth, two-chain isolation, append-only HMAC-chained audit, separate `apps/admin` SPA (ADMIN-01..10, ARCH-08..12, OPS-INFRA-01..03, v1.2)
- ✓ Master-key management for 6 providers — AES-GCM, masked-only, test/rotate + cache eviction, 9Router dual-mode (MKEY-01..08, v1.2)
- ✓ Curated LLM catalog with 3-step Sync-from-`/models`, per-feature binding, Anthropic seed (CAT-01..07, v1.2)
- ✓ Metadata-only tenant inspection, queue health, and LLM spend dashboards with leak failsafes (OPS-TENANT-01..05, OPS-QUEUE-01..02, OPS-SPEND-01..02, v1.2)

**Inbox Zero-style rule actions** *(v1.2)*
- ✓ Examples/personas catalog (admin-managed), expanded When/Then action schema, user-enabled outbound automation behind one Auto-send setting + safety gates + single outbound gateway with fallback-to-draft (RACT-01..12, v1.2)

**User settings UI** *(v1.2)*
- ✓ Single `/ai` surface — voice, behavior, updates, safety net, BYOK on curated catalog; generate-from-Sent with in-memory-only privacy gates (SET-VOICE-01..07, SET-BEHV-01/03/04, SET-SAFE-01/04, SET-AI-01..04, v1.2)

**Bulk unsubscribe** *(v1.2 bonus)*
- ✓ RFC 8058 one-click + RFC 6068 mailto send-as-self gateways, throttled SKIP LOCKED dispatch, full REST surface (UNS-01..07, v1.2)

### Active

**v1.3 — not yet defined.** Run `/gsd-new-milestone` to scope. Carry-forward candidates: SET-BEHV-05 (shadow-mode toggle), SET-SAFE-02 (paste-import), SET-SAFE-03 (protect/escalate mode), VISUAL-REFRESH-01..06 (purple palette alignment), EVAL-01..05 (hostile-corpus aiEval), OPS-DASH-01..04 (Grafana), CASA-01, and a formal GA tag. Live outbound-send UAT (08.1 test #6) still needs a controlled Gmail tenant.

<details>
<summary>Prior Active note (pre-v1.2-close)</summary>

<!-- Next milestone scope. Define via `/gsd:new-milestone`. -->

**v1.2 in progress** (started 2026-05-19). Scope: Admin console foundation + Inbox Zero-style rule actions/examples + Settings UI on curated catalog (see "Current Milestone" section above). Requirements: `.planning/REQUIREMENTS.md`. Roadmap: `.planning/ROADMAP.md`.

*Deferred from v1.1 candidate list:* CASA production verification (SEED-012), live Resend deliverability + payment-provider smoke tests, Outlook/Microsoft 365, ~~bulk unsubscribe~~ (shipped in v1.2 as UNS-01..07), cold-email blocker, reply-tracker, attachment auto-filing, team/seat plans, waitlist OAuth provisioning.

</details>

### Out of Scope

<!-- Explicit boundaries. Reasoning included so we don't silently re-add them. -->

- **Ungated outbound automation** — rule-triggered send/reply/forward is allowed only behind Phase 08.1 global auto-send setting, sender-risk guard, safety net, cap/rate-limit, idempotency, OAuth, tenant, and audit gates; blocked sends fall back to Gmail draft.
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

**Safety posture.** App has write access to people's primary email. Every autonomous action leaves an audit trail; label/archive/draft remain reversible, and outbound sends require the global auto-send setting plus safety/rate/idempotency gates and one ArchUnit-enforced outbound gateway.

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
- **Write actions allowed**: (1) **Rules engine** (auto-triggered by incoming mail): label, archive (skip inbox), save Gmail draft, mark read/unread, star/unstar, add to digest, mark spam, and user-enabled outbound actions `send_reply`, `forward_email`, and `send_email`. Outbound rule actions require the global `Auto-send rules` setting (default ON), safety-net checks, low-trust sender guards, rate/daily caps, idempotency, and append-only audit; if any gate fails or the global setting is OFF, downgrade to Gmail draft instead of sending. (2) **Chat assistant** (user-initiated): same action set plus user-confirmed send/reply/forward through a preview card. All Gmail send execution must go through the shared outbound gateway/send executor so architectural tests can enforce the boundary; direct Gmail send call sites outside that gateway are forbidden.
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
| User-enabled outbound rule automation in Phase 08.1 | Users expect a rule engine to send replies/forward/send email when explicitly configured; safety moves from hard ban to opt-in gates + audited gateway boundary | ✓ Good — RACT-01..12 shipped v1.2; single ArchUnit-locked outbound gateway + fallback-to-draft |
| WebAuthn passkey admin auth, not Google OAuth (v1.2 pivot) | Decouple admin identity from Google IdP; hardware-bound passkey + separate `admin_users` table; user-side RBAC removed entirely | ✓ Good — ADMIN-01..10 shipped; two-chain isolation ArchUnit-enforced |
| Separate `apps/admin` Vite SPA on `admin.zeromail.com`, not a Next.js route group | Admin needs no SEO/SSR; DNS subdomain is the cognitive cue; admin schema types stay out of the public bundle | ✓ Good — shipped v1.2; `zeromail.com/admin` returns 404 |
| Admin-curated LLM catalog with manual-confirm Sync-from-`/models` | Auto-apply is an anti-feature (providers ship preview/deprecated models); admin reviews diff before confirm | ✓ Good — CAT-01..07 shipped v1.2 |
| No GA tag at v1.2 close | Hostile-corpus eval, visual refresh, Grafana, CASA refresh, LAUNCH-GO-NOGO not yet done; ship the capability, defer the GA gate | — Pending — v1.3 owns the GA tag |
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
*Last updated: 2026-06-01 after v1.2 milestone (Admin Console + User Settings UI shipped; 70/73 requirements; next milestone v1.3 not yet defined)*

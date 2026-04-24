# Roadmap: Zero Mail

## Overview

Zero Mail is an AI Gmail triage SaaS where trust is the product. This roadmap walks from a safety-first foundation (Scoped Values, log scrubbers, OAuth, CASA kickoff) through three parallel infrastructure tracks (mail ingestion, billing ledger, LLM gateway) that converge on the rules engine and then the hero triage orchestrator. After triage lands, the user-facing surface (drafts, analytics, web UI) ships together, followed by a polish + CASA-verified launch phase. Phase 2C (LLM Gateway) is hard-gated by Phase 1 safety infrastructure, and Phase 4 (Triage) is hard-gated by Phase 2C. CASA restricted-scope verification (4–12 weeks, external) is kicked off in parallel at Phase 1 OAuth wiring and completes before Phase 6 launch.

## Phases

**Phase Numbering:**
- Integer phases (1, 3, 4, 6): Planned milestone work
- Sub-phases (2A, 2B, 2C): Parallel tracks that must all complete before Phase 3 — executable concurrently post-Phase 1
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED) — none yet

- [ ] **Phase 1: Foundation & Safety Infrastructure** - Scoped Values, `@Sensitive`, Logback scrub, ArchUnit bans, multi-tenant leak test, Google OAuth, skeleton OpenAPI, CASA kickoff
- [ ] **Phase 2A: Mail Ingestion** - Gmail `users.watch` + Pub/Sub push + OIDC verification + idempotent history processing + global pause
- [ ] **Phase 2B: Billing (Prepaid Credits)** - Double-entry Postgres ledger, reserve/settle/release, credit-hold watchdog, balance UI hooks
- [ ] **Phase 2C: LLM Gateway** - Spring AI 2.0.0-M4 `LlmGateway` with sanitize → Unicode strip → structured tool-call + allow-list → BYOK per-request options → daily spend cap → drift detection
- [ ] **Phase 3: Rules Engine** - NL → structured matcher AST via Spring AI tool-call, deterministic evaluator, live preview, CRUD + reorder, template gallery
- [ ] **Phase 4: Triage Convergence (Hero)** - Orchestrator, safety policy layer, audit + undo, shadow mode for new tenants, sender safety net
- [ ] **Phase 5: User Surface (Drafts, Analytics, Web UI)** - AI-drafted replies, metadata-only analytics + daily digest, Next.js 16 / React 19 frontend covering all flows
- [ ] **Phase 6: Polish & CASA-Verified Launch** - End-to-end integration hardening, CASA Tier verification sign-off, launch readiness

## Phase Details

### Phase 1: Foundation & Safety Infrastructure
**Goal**: Ship the tenant-isolation and log-scrubbing infrastructure that makes every later phase safe, wire Google OAuth, publish the skeleton OpenAPI contract, and kick off the external CASA restricted-scope verification clock.
**Depends on**: Nothing (first phase)
**Requirements**: FND-01, FND-02, FND-03, FND-04, FND-05, FND-06, FND-07, AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):
  1. A new user can sign in with Google, connect one Gmail account, complete the guided onboarding through the template-rule step, and revoke access + delete all data from a settings screen.
  2. A concurrent multi-tenant integration test using virtual threads proves that tenant context never leaks across requests (Scoped Values only, ArchUnit fails any `ThreadLocal` reference in request/worker paths).
  3. Attempting to log an email body, LLM prompt, or LLM completion is blocked at build time by ArchUnit and at runtime by the Logback scrub filter — a reviewer can grep application logs during a synthetic traffic run and find zero body/prompt/completion content.
  4. A tenant whose OAuth grant is revoked externally is surfaced as `DISCONNECTED` in the UI with a reconnect prompt on the next request.
  5. The skeleton OpenAPI spec is published from `backend/api` and the `apps/web` module successfully generates its typed client via `openapi-typescript`, and a CASA restricted-scope verification submission has been filed with the external lab.
**Plans**: 9 plans
- [ ] 01-01-PLAN.md — Gradle multi-project scaffold, buildSrc conventions, runnable Spring Boot shells
- [ ] 01-02-PLAN.md — Tenant isolation primitives (ScopedValue, Hibernate resolver, TenantAwareTaskScope, Modulith packages, ArchUnit ThreadLocal/virtual-thread/native-SQL bans)
- [ ] 01-03-PLAN.md — Log safety contract (Sensitive<T> wrapper, Jackson module, Logback scrub filter, ArchUnit rules d + e)
- [ ] 01-04-PLAN.md — Liquibase YAML baseline + JPA entities + Testcontainers schema push (BLOCKING)
- [ ] 01-05-PLAN.md — Spring Security OAuth2 dual registration, Spring Session Redis, TenantBindingFilter, invalid_grant detection, FND-05 leak test
- [ ] 01-06-PLAN.md — AES-GCM refresh-token envelope cipher + GCP Secret Manager wiring
- [ ] 01-07-PLAN.md — backend/api skeleton OpenAPI + Phase 1 controllers + delete-cascade + onboarding state machine
- [ ] 01-08-PLAN.md — apps/web Next.js 16 scaffold + typed client codegen + /login, /onboarding, /settings routes
- [ ] 01-09-PLAN.md — FND-03 log-scrub synthetic-traffic test + CASA submission package + actuator probes
**UI hint**: yes

### Phase 2A: Mail Ingestion
**Goal**: Receive Gmail push notifications reliably, keep `users.watch` alive, and process every history delivery idempotently with a tenant-visible global pause.
**Depends on**: Phase 1
**Requirements**: MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06
**Success Criteria** (what must be TRUE):
  1. When a new message arrives in a connected Gmail account, the system logs a corresponding `MessageObserved` event within seconds, attributable to the correct tenant.
  2. `users.watch` is renewed daily before its 7-day expiry, and a per-tenant health alert fires if any renewal fails.
  3. Replaying the same Pub/Sub delivery (same `historyId` + `messageId`) a second time produces no duplicate downstream effects — verifiable via audit trail in Phase 4.
  4. A Pub/Sub push request with a missing, expired, or wrong-audience Google OIDC token is rejected with 401 and never reaches business logic.
  5. A user can flip a "pause all automated triage" toggle and observe that new-message events are still received but no write actions are queued; after a history-404, the user sees a visible reconnect prompt instead of a full mailbox rescan.
**Plans**: TBD
**Research flag**: This phase should run through `/gsd-research-phase` before planning — Gmail `watch`/history edge cases and OIDC push-token verification need current-library lookup (see SUMMARY.md research flags).

### Phase 2B: Billing (Prepaid Credits)
**Goal**: Stand up a double-entry Postgres credit ledger with reserve/settle/release semantics and a watchdog, so that every billable action in later phases can charge credits safely under concurrency.
**Depends on**: Phase 1
**Requirements**: BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07
**Success Criteria** (what must be TRUE):
  1. A user can purchase prepaid credits via the selected payment provider (Stripe or LemonSqueezy) and see the new balance reflected in real time in the UI.
  2. Running the billable-action flow concurrently for the same tenant never double-charges and never loses credits — the ledger always reconciles.
  3. Orphaned credit holds (reserved but neither settled nor released) are swept back to the available balance by a scheduled watchdog within its interval.
  4. When a tenant's balance is insufficient, any billable action is blocked at the gateway with a clear UI prompt to top up — no partial debit.
  5. Actions performed under a BYOK key do not consume platform credits, and both balance and per-action cost are visible in the UI.
**Plans**: TBD
**UI hint**: yes

### Phase 2C: LLM Gateway
**Goal**: Ship the single `LlmGateway` abstraction on Spring AI 2.0.0-M4 that all LLM traffic must traverse, with full prompt-injection hardening, BYOK routing, metadata-only observability, per-tenant spend caps, and drift detection — the contract that Phase 4 triage will be built on.
**Depends on**: Phase 1 (hard gate — safety infrastructure must ship first)
**Requirements**: LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, LLM-06, LLM-07, LLM-08, LLM-09, LLM-10, LLM-11
**Success Criteria** (what must be TRUE):
  1. Every LLM call in the codebase goes through `LlmGateway`; an ArchUnit test fails any direct `ChatClient`/vendor SDK usage outside the gateway.
  2. A message body containing HTML, hidden Unicode tag characters (U+E0000–U+E007F), or an injected "ignore previous instructions" payload is sanitized (Jsoup), NFC-normalized, tag-stripped, wrapped in the structured tool-call schema, and truncated to ≤4k tokens before any model call — verifiable via a prompt-injection test corpus.
  3. A tool call returning an action outside the per-action allow-list is rejected before it can be executed.
  4. A user-provided BYOK key (OpenAI, Anthropic, OpenRouter) is used for that user's calls without any server-side persistence of the key beyond the request scope, and BYOK calls bypass platform credit deduction.
  5. Per-tenant daily LLM spend cap blocks further billable calls when exceeded, no raw body/prompt/completion is persisted beyond the short-lived in-memory cache, and the golden-set drift job flags any regression on the fixed sample on schedule.
**Plans**: TBD
**Research flag**: This phase should run through `/gsd-research-phase` before planning — Spring AI 2.0.0-M4 per-request BYOK builder API and tokenizer choice must be verified in code, not from memory (see SUMMARY.md research flags).

### Phase 3: Rules Engine
**Goal**: Let users author, preview, and manage natural-language rules that compile to a deterministic matcher AST — with `SEMANTIC_INTENT` matchers deferred to Phase 4 for batched LLM evaluation.
**Depends on**: Phase 2C (needs `LlmGateway` for compilation), Phase 2A (preview runs against recent messages), Phase 2B (compile + preview are billable)
**Requirements**: RULE-01, RULE-02, RULE-03, RULE-04, RULE-05, RULE-06, RULE-07
**Success Criteria** (what must be TRUE):
  1. A user types "Archive receipts from Stripe and label them Finance" and the system compiles it into a structured matcher AST via a Spring AI tool-call — no free-form LLM output reaches runtime.
  2. The evaluator runs deterministic matchers against messages with no LLM call involved, and rules containing `SEMANTIC_INTENT` clauses are correctly deferred with a clear marker for batched evaluation.
  3. A user can preview a rule against the last N recent messages and see exactly which would match, before enabling the rule.
  4. A user can enable, disable, reorder, edit, and delete rules, and the changes take effect on the next message processed.
  5. A new user sees a template gallery of common v1 rules (receipts, newsletters, calendar invites) and can enable one with a single click.
**Plans**: TBD
**UI hint**: yes

### Phase 4: Triage Convergence (Hero)
**Goal**: The product. Orchestrate per-message triage: evaluate matchers in order, apply the safety policy layer, execute only allow-listed Gmail writes (label / archive / save-draft — never send), and leave an immutable audit trail with user-visible undo, shadow mode, and sender safety net.
**Depends on**: Phase 3 (rules), Phase 2C (LLM gateway — hard gate; no triage without sanitization + allow-list)
**Requirements**: TRG-01, TRG-02, TRG-03, TRG-04, TRG-05, TRG-06, TRG-07, TRG-08
**Success Criteria** (what must be TRUE):
  1. When a new message arrives in a connected Gmail account with active rules, the orchestrator evaluates matchers in rule order, applies at most the resulting allow-listed actions to Gmail, and the message visibly changes (label / archive / draft saved) within a few seconds.
  2. No code path can send mail on behalf of the user — an attempt to return a `SEND` action from any layer is rejected at the gateway and logged as a safety violation.
  3. Every automated action has an audit entry (message reference, rule, action, reason, timestamp) visible to the user, and the user can undo any action within the retention window and see the inverse Gmail change.
  4. A brand-new tenant's first N triage decisions are logged as would-apply but never written to Gmail until the user explicitly exits shadow mode.
  5. Messages from senders identified as frequent/important are not auto-acted on until the user opts that sender into automation, visible in a sender-safety-net UI.
**Plans**: TBD
**UI hint**: yes

### Phase 5: User Surface — Drafts, Analytics, Web UI
**Goal**: Deliver the complete user-facing surface: AI-drafted replies in Gmail, metadata-only analytics with a daily digest, and the Next.js 16 / React 19 frontend that ties every flow (onboarding, rules, audit, drafts, analytics, billing, privacy) together.
**Depends on**: Phase 4 (audit log + triage to surface), Phase 2B (billing UI), Phase 2A (pause toggle + connection health), Phase 1 (OpenAPI contract — note that `apps/web` scaffolding can begin in parallel right after Phase 1 once the OpenAPI stub is stable)
**Requirements**: DRFT-01, DRFT-02, DRFT-03, DRFT-04, ANL-01, ANL-02, ANL-03, WEB-01, WEB-02, WEB-03, WEB-04
**Success Criteria** (what must be TRUE):
  1. A user can request an AI draft reply for a thread, find it in Gmail as a normal draft with correct `In-Reply-To` and `References` headers, recognizable tone-matched phrasing, and must review before sending — no code path auto-sends.
  2. The analytics screen shows volume triaged, estimated time saved, top senders, and rule hits over a user-selectable window, derived from per-message metadata only (no bodies, prompts, or completions stored or queried).
  3. Each day, a connected tenant receives a digest email summarizing triage activity for the prior day.
  4. The `apps/web` Next.js 16 / React 19 frontend covers onboarding, rule CRUD with live preview, triage audit log with undo, draft review, analytics, billing, and an in-product privacy page explaining no-stored-bodies, no-auto-send, and the BYOK option.
  5. A persistent UI region (header or sidebar) surfaces the global pause toggle, real-time credit balance, and tenant connection health on every authenticated screen.
**Plans**: TBD
**UI hint**: yes

### Phase 6: Polish & CASA-Verified Launch
**Goal**: Harden end-to-end flows, close CASA restricted-scope verification (initiated in Phase 1), and produce a launch-ready build. No new REQ-IDs — this phase validates everything prior.
**Depends on**: Phase 5, plus external CASA lab sign-off
**Requirements**: _(none — integration, hardening, external verification close-out)_
**Success Criteria** (what must be TRUE):
  1. A fresh end-to-end run (sign up → connect Gmail → enable template rule → receive message → triage → undo → draft → analytics) completes without errors on a clean environment.
  2. Load/concurrency tests with N concurrent tenants sustain triage throughput without cross-tenant leakage, log bleed, or ledger inconsistency.
  3. CASA restricted-scope verification for Gmail scopes is completed (Tier decision logged), and the Google OAuth consent screen is moved from Testing to Production.
  4. Prompt-injection regression suite, ArchUnit suite, and golden-set drift check all pass on the release candidate commit.
  5. Production runbook exists (on-call, Pub/Sub backlog recovery, `users.watch` renewal incidents, ledger reconciliation) and a launch go/no-go has been signed off against the trust story (never auto-sends, no stored bodies, undoable actions).
**Plans**: TBD

## External Track (not a phase)

**CASA restricted-scope verification** is a 4–12 week external dependency executed in parallel with Phases 1 → 6. It is initiated during Phase 1 OAuth wiring (FND-07) and must be closed before Phase 6 launch go/no-go. It is tracked as a project risk, not as a phase, because no engineering work is gated on it until launch.

## Progress

**Execution Order:**
Phase 1 → {Phase 2A ∥ Phase 2B ∥ Phase 2C} → Phase 3 → Phase 4 → Phase 5 → Phase 6

Parallelization: Phases 2A, 2B, and 2C can run concurrently once Phase 1 completes. Phase 5 starts after Phase 4, but `apps/web` scaffolding may start immediately after Phase 1 once the OpenAPI stub is stable.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Safety Infrastructure | 0/TBD | Not started | - |
| 2A. Mail Ingestion | 0/TBD | Not started | - |
| 2B. Billing (Prepaid Credits) | 0/TBD | Not started | - |
| 2C. LLM Gateway | 0/TBD | Not started | - |
| 3. Rules Engine | 0/TBD | Not started | - |
| 4. Triage Convergence (Hero) | 0/TBD | Not started | - |
| 5. User Surface — Drafts, Analytics, Web UI | 0/TBD | Not started | - |
| 6. Polish & CASA-Verified Launch | 0/TBD | Not started | - |

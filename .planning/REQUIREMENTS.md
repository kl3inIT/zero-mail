# Requirements: Zero Mail

**Defined:** 2026-04-24
**Core Value:** AI auto-triage that users trust with their real Gmail inbox — triage quality, safety (no destructive or silently-sent actions), and reliability are non-negotiable.

## v1 Requirements

Requirements for initial release. Each maps to exactly one roadmap phase.

### Auth & Onboarding

- [x] **AUTH-01**: User can sign up and sign in via Google OAuth with Gmail scopes
- [x] **AUTH-02**: User can connect exactly one Gmail / Google Workspace account
- [ ] **AUTH-03**: User can revoke Gmail access and delete their account + all stored data
- [ ] **AUTH-04**: User session persists via cookie-based session (not JWT)
- [ ] **AUTH-05**: Disconnected tenants (`invalid_grant`) enter a DISCONNECTED state with user-visible recovery prompt
- [ ] **AUTH-06**: User completes guided onboarding (connect Gmail → enable 1 template rule → see first triage preview)

### Foundation & Safety Infrastructure

- [ ] **FND-01**: Every request runs in a tenant-scoped context using Scoped Values (never ThreadLocal)
- [ ] **FND-02**: ArchUnit test fails any new code that references `ThreadLocal` in request/worker paths
- [ ] **FND-03**: `@Sensitive` wrapper and Logback scrub filter prevent email bodies, prompts, or completions from reaching logs
- [ ] **FND-04**: ArchUnit test fails any code that references email body / LLM prompt / completion fields in log statements
- [ ] **FND-05**: Concurrent multi-tenant integration test confirms no cross-tenant leakage on virtual threads
- [ ] **FND-06**: Skeleton OpenAPI spec is published and consumed by the frontend module via `openapi-typescript`
- [ ] **FND-07**: CASA restricted-scope verification is initiated at OAuth wiring (not deferred to launch)

### Mail Ingestion

- [x] **MAIL-01**: System registers `users.watch` on Gmail connect and processes Pub/Sub push notifications
- [x] **MAIL-02**: Daily scheduled job renews `users.watch` before its 7-day expiry with per-tenant health alerting
- [x] **MAIL-03**: Pub/Sub push receiver verifies Google OIDC tokens on every request
- [x] **MAIL-04**: Message processing is idempotent per `(tenantId, historyId, messageId)` — duplicate deliveries are safe
- [x] **MAIL-05**: History-404 recovery is bounded (no full mailbox rescan) and surfaces a user-visible reconnect prompt
- [x] **MAIL-06**: User can globally pause all automated triage actions from the UI

### LLM Gateway

- [x] **LLM-01**: All LLM traffic flows through a `LlmGateway` abstraction built on Spring AI 2.0.0-M6
- [x] **LLM-02**: Default traffic routes to OpenRouter; model pin is configurable per call site
- [x] **LLM-03**: User can provide BYOK API keys (OpenAI, Anthropic, OpenRouter) via per-request Spring AI options
- [x] **LLM-04**: BYOK keys are stored encrypted-at-rest only (AES-GCM via RefreshTokenCipher); ciphertext is decrypted into a per-call byte[] that lives only on the call stack and is zeroed via Arrays.fill on completion. Plaintext is never logged, never returned to clients, and never persisted in plaintext form. BYOK usage bypasses platform LLM billing (user pays their provider directly)
- [x] **LLM-05**: All email content is HTML-sanitized (Jsoup) before reaching any LLM
- [x] **LLM-06**: All content is NFC-normalized and Unicode tag characters (U+E0000–U+E007F) are stripped
- [x] **LLM-07**: Prompt-injection hardening wraps untrusted content in a structured tool-call schema with a per-action allow-list
- [x] **LLM-08**: Email content is truncated to a safe token budget (≤4k tokens) before any LLM call
- [x] **LLM-09**: No raw email body, LLM prompt, or LLM completion is persisted beyond a short-lived in-memory cache
- [x] **LLM-10**: Per-tenant daily LLM spend cap blocks further billable calls when exceeded
- [x] **LLM-11**: Golden-set drift detection runs on a fixed sample to catch silent model regressions

### Billing (Prepaid Credits)

- [x] **BILL-01**: User can purchase prepaid credits via SePay/VietQR for the Vietnam beta; global Merchant-of-Record/card provider is deferred
- [x] **BILL-02**: Each billable action (triage, draft, preview) deducts credits via a double-entry Postgres ledger
- [x] **BILL-03**: Credit reserve/settle/release flow prevents double-charge and lost credits under concurrency
- [x] **BILL-04**: A scheduled watchdog sweeps stale credit holds and releases them back to the balance
- [x] **BILL-05**: User sees real-time credit balance and per-action cost in the UI
- [x] **BILL-06**: System blocks billable actions when credit balance is insufficient with a clear UI prompt
- [x] **BILL-07**: BYOK-only actions do not consume platform credits

### Rules Engine

- [x] **RULE-01**: User writes rules in plain English (e.g., "Archive receipts from Stripe and label them Finance")
- [x] **RULE-02**: Spring AI tool-call compiles NL rules into a structured matcher AST (no free-form LLM output at runtime)
- [x] **RULE-03**: The evaluator runs deterministic matchers without an LLM call
- [x] **RULE-04**: Matchers marked `SEMANTIC_INTENT` are deferred to the triage convergence phase for LLM evaluation (batched)
- [x] **RULE-05**: User can preview a rule against the last N recent messages before enabling it
- [x] **RULE-06**: User can enable, disable, reorder, edit, and delete rules
- [x] **RULE-07**: A template rule gallery ships with common v1 rules (receipts, newsletters, calendar invites, etc.)

### Triage Convergence (Hero)

- [x] **TRG-01**: On each new message, the orchestrator runs matchers in rule order and collects candidate actions
- [x] **TRG-02**: A safety policy layer rejects any action outside the allow-list (label / archive / save-draft)
- [x] **TRG-03**: Auto-send is blocked at the gateway layer — no code path can send mail on behalf of the user
- [x] **TRG-04**: Applied actions are Gmail write calls: label, archive (skip inbox), or save draft
- [x] **TRG-05**: Every triage action writes an immutable audit entry (message ref, rule, action, reason, timestamp)
- [x] **TRG-06**: User can undo any automated action from the audit log within retention window
- [x] **TRG-07**: Tenant-wide shadow mode can be toggled on to log would-apply decisions without Gmail writes; default is OFF
- [x] **TRG-08**: Sender safety net suppresses automated actions on messages from the user's frequent/important senders until user opts in

### Draft Replies

- [x] **DRFT-01**: User can request an AI-generated draft reply for a thread
- [x] **DRFT-02**: Draft is saved in Gmail as a normal draft with correct `In-Reply-To` and `References` headers
- [x] **DRFT-03**: Draft tone is matched from recent sent mail via lightweight in-request features (no persisted embeddings)
- [x] **DRFT-04**: Draft generation never auto-sends and always requires user review in Gmail

### Analytics

- [ ] **ANL-01**: User sees volume triaged, estimated time saved, top senders, and rule hits over a selectable window
- [ ] **ANL-02**: All metrics derive from minimal per-message metadata only (no email bodies, prompts, or completions)
- [ ] **ANL-03**: Daily digest email summarizes triage activity for the prior day

### Web UI

- [x] **WEB-01**: Next.js 16 / React 19 frontend lives in `apps/web` and consumes the backend OpenAPI via typed client
- [ ] **WEB-02**: UI covers onboarding, rule CRUD with live preview, triage audit log with undo, draft review, analytics, and billing — 5A portion done (onboarding, rules+live-preview, triage audit log+undo, billing); 5B draft-review done and `GET /api/triage/audit` built; analytics remains → 5C
- [x] **WEB-03**: In-product privacy page explains data handling (no stored bodies, no auto-send, BYOK option)
- [x] **WEB-04**: UI surfaces the global pause toggle, credit balance, and tenant connection health in a persistent location

## v2 Requirements

Acknowledged but deferred. Not in current roadmap.

### Advanced Triage

- **V2-01**: Auto-send for narrow opt-in rules (e.g., "acknowledge receipt") with per-rule approval flow
- **V2-02**: First-class bulk unsubscribe feature
- **V2-03**: First-class cold-email blocker
- **V2-04**: Reply-tracker / follow-up nudges
- **V2-05**: Attachment analysis and auto-filing

### Platform Expansion

- **V2-06**: Outlook / Microsoft 365 support
- **V2-07**: Team / seat-based plans
- **V2-08**: Native mobile apps
- **V2-09**: Enterprise features (SSO, SCIM, audit exports, DPA-grade compliance)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep and silent re-addition.

| Feature | Reason |
|---------|--------|
| Auto-send replies without human review | Trust-killing blast radius; opt-in auto-send deferred to v2 |
| Outlook / Microsoft 365 | Doubles provider surface area; Gmail-only in v1 to ship focused |
| Generic IMAP / SMTP | Different auth, push, and label model; not on target-user provider mix |
| Self-hosted / open-source distribution | Separate strategic decision; Cloud SaaS only in v1 |
| Team / seat-based plans | v1 targets individual prosumers; team features wait for SMB signal |
| Long-term storage of email bodies, LLM prompts, completions, or embeddings | Privacy constraint — permanent, not a deferred feature |
| RAG over user mail bodies | Requires persistent derived features; incompatible with privacy stance |
| Full in-app mail client UI | Gmail remains the primary client; we augment, not replace |
| Vector DB in v1 infra | No embedding persistence → no vector DB need in v1 |
| Enterprise SSO / SCIM / DPA | Target buyer is busy pro / founder, not enterprise procurement in v1 |

## Traceability

Each v1 requirement maps to exactly one phase.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Complete |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Pending |
| AUTH-05 | Phase 1 | Pending |
| AUTH-06 | Phase 1 | Pending |
| FND-01 | Phase 1 | Pending |
| FND-02 | Phase 1 | Pending |
| FND-03 | Phase 1 | Pending |
| FND-04 | Phase 1 | Pending |
| FND-05 | Phase 1 | Pending |
| FND-06 | Phase 1 | Pending |
| FND-07 | Phase 1 | Pending |
| MAIL-01 | Phase 2A | Complete |
| MAIL-02 | Phase 2A | Complete |
| MAIL-03 | Phase 2A | Complete |
| MAIL-04 | Phase 2A | Complete |
| MAIL-05 | Phase 2A | Complete |
| MAIL-06 | Phase 2A | Complete |
| BILL-01 | Phase 2B | Complete |
| BILL-02 | Phase 2B | Complete |
| BILL-03 | Phase 2B | Complete |
| BILL-04 | Phase 2B | Complete |
| BILL-05 | Phase 2B | Complete |
| BILL-06 | Phase 2B | Complete |
| BILL-07 | Phase 2B | Complete |
| LLM-01 | Phase 2C | Complete |
| LLM-02 | Phase 2C | Complete |
| LLM-03 | Phase 2C | Complete |
| LLM-04 | Phase 2C | Complete |
| LLM-05 | Phase 2C | Complete |
| LLM-06 | Phase 2C | Complete |
| LLM-07 | Phase 2C | Complete |
| LLM-08 | Phase 2C | Complete |
| LLM-09 | Phase 2C | Complete |
| LLM-10 | Phase 2C | Complete |
| LLM-11 | Phase 2C | Complete |
| RULE-01 | Phase 3 | Complete |
| RULE-02 | Phase 3 | Complete |
| RULE-03 | Phase 3 | Complete |
| RULE-04 | Phase 3 | Complete |
| RULE-05 | Phase 3 | Complete |
| RULE-06 | Phase 3 | Complete |
| RULE-07 | Phase 3 | Complete |
| TRG-01 | Phase 4 | Complete |
| TRG-02 | Phase 4 | Complete |
| TRG-03 | Phase 4 | Complete |
| TRG-04 | Phase 4 | Complete |
| TRG-05 | Phase 4 | Complete |
| TRG-06 | Phase 4 | Complete |
| TRG-07 | Phase 4 | Complete |
| TRG-08 | Phase 4 | Complete |
| DRFT-01 | Phase 5B | Complete |
| DRFT-02 | Phase 5B | Complete |
| DRFT-03 | Phase 5B | Complete |
| DRFT-04 | Phase 5B | Complete |
| ANL-01 | Phase 5C | Pending |
| ANL-02 | Phase 5C | Pending |
| ANL-03 | Phase 5C | Pending |
| WEB-01 | Phase 5A | Complete |
| WEB-02 | Phase 5A / 5B / 5C | 5A portion done (onboarding, rules+live-preview, triage audit log+undo, billing); 5B draft-review done and `GET /api/triage/audit` built; analytics remains → 5C |
| WEB-03 | Phase 5A | Complete |
| WEB-04 | Phase 5A | Complete |

**Coverage:**
- v1 requirements: 61 total
- Mapped to phases: 61
- Unmapped: 0

---
*Requirements defined: 2026-04-24*
*Last updated: 2026-05-13 — Phase 5B closure verified DRFT-01..DRFT-04 as Complete; WEB-02 now has 5A + 5B portions done, analytics remains Phase 5C; traceability remains 61/61 mapped*

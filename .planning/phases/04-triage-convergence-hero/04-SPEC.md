# Phase 4: Triage Convergence (Hero) — Specification

**Created:** 2026-05-11
**Ambiguity score:** 0.11 (gate: ≤ 0.20)
**Requirements:** 8 locked

## Goal

When a Gmail message is observed for a connected tenant with active rules, the triage orchestrator evaluates matchers in rule order, applies only allow-listed Gmail write actions (label / archive / save-draft) through the LlmGateway-protected safety policy, writes an immutable audit entry, and supports user undo within 30 days — never sending mail on behalf of the user, p95 end-to-end latency ≤ 10s.

## Background

Phase 3 (Rules Engine) shipped the deterministic matcher AST and tri-state `RuleEvaluator` (`MATCHED / NOT_MATCHED / DEFERRED`). `SemanticIntentMatcher` is intentionally deferred to Phase 4 for LLM evaluation. `ActionProposal` + `ActionProposalMerger` produce candidate actions per matched rule. No code path currently consumes these proposals — `RuleEvaluator` is exercised only by Phase 3 preview today.

Phase 2A wired Gmail push notifications: `GmailDeliveryProcessingService.processDelivery` calls `MailMessageObservedRepository.insertObservedIfAbsent` for every new INBOX message, but does not fire any downstream trigger. CLAUDE.md Convention #6 locks the integration shape: `message-observed → triage` happens via Spring Modulith after-commit event in-process inside `backend/worker`.

Phase 2C (LLM Gateway) shipped `LlmGateway` + `ActionValidator` allow-list enforcement and `CreditLedger` reserve/settle/release via `CallSite` enum — Phase 4 reuses both. Phase 2A also shipped `tenants.triage_paused` (Liquibase 013) as a global kill-switch.

Today: no orchestrator, no audit table, no undo, no shadow mode, no sender safety net. Phase 4 builds the convergence point that wires Phase 2A → Phase 3 → Phase 2C into a single safe, auditable write path; Phase 5 will ship the UI on top.

## Requirements

1. **Triage orchestrator**: Triage runs once per observed message inside `backend/worker`.
   - Current: `MailMessageObservedEntity` rows are written by `GmailDeliveryProcessingService` but no consumer exists. `RuleEvaluator` is only invoked from Phase 3 preview.
   - Target: A Spring Modulith after-commit event from `core.gmail` (e.g., `MailMessageObserved`) is consumed by a new `core.triage` orchestrator that loads the tenant's enabled rules in `display_order`, evaluates matchers against a `RuleEvaluationInput` built from Gmail metadata-only fetch (`format=metadata`, `fields=id,threadId,labelIds,internalDate,payload/headers`), and produces a merged list of `ActionProposal` instances per message.
   - Acceptance: Integration test where a tenant has 2 enabled rules and a new `MailMessageObservedEntity` is committed → orchestrator observes the event, produces deterministic action proposals matching expected rules, and persists exactly one audit entry per applied action.

2. **Safety policy layer**: Every proposed action passes through an allow-list gate before any Gmail write.
   - Current: `ActionValidator` (Phase 2C) enforces the allow-list on LLM tool-call output; no equivalent exists on the rule-engine path.
   - Target: A `TriageSafetyPolicy` component in `core.triage` rejects any `ActionProposal` whose `RuleActionType` is not in `{LABEL, ARCHIVE_SKIP_INBOX, SAVE_DRAFT}`. Rejected proposals are logged with `event=triage_safety_violation tenantId={} rule_id={} action_type={}` and never reach Gmail.
   - Acceptance: A unit test that crafts a hypothetical `ActionProposal` with `RuleActionType.SEND` (or any non-allow-listed type) results in `SafetyViolationException` and zero Gmail API calls. ArchUnit test forbids any Gmail write call site outside the `TriageGmailWriter` class.

3. **Auto-send blocked at the gateway layer**: No code path in `backend/core` or `backend/worker` can send mail on behalf of the user.
   - Current: Phase 2C `ActionValidator` blocks `SEND` returned from LLM tool calls. `RuleActionType` enum does not include `SEND`, but there is no architectural guard preventing future addition.
   - Target: ArchUnit test asserts that `RuleActionType.SEND` does not exist (or, if added later for v2, is excluded from any Phase 4 allow-list constant). `TriageGmailWriter` only exposes methods for `applyLabel`, `archiveSkipInbox`, `saveDraft` — there is no `sendDraft` / `sendMessage` method anywhere in the triage code path.
   - Acceptance: A grep for `users.messages().send` and `users.drafts().send` across `backend/` returns zero matches; ArchUnit `NoGmailSendAllowedTest` passes; a code review checklist item is added to PHASE-4 closure.

4. **Allow-listed Gmail writes execute exactly once per accepted action**: Approved proposals translate to Gmail API calls with idempotency.
   - Current: `GmailApiClientFactory` builds the authenticated Gmail client; no triage write path exists.
   - Target: `TriageGmailWriter` performs the three allow-listed Gmail writes (`users.messages.modify` for label add/remove, `users.messages.modify` with `removeLabelIds=["INBOX"]` for archive, `users.drafts.create` for save-draft). Each call is wrapped with: (a) per-message+per-action idempotency check against the audit table (no duplicate apply on retry), (b) a `CreditLedger.reserve → execute → settle/release` pair using a new `CallSite.TRIAGE_*` enum value, (c) structured logging with no body/snippet/subject content.
   - Acceptance: Replaying the same `MailMessageObservedEntity` twice (or a manual orchestrator re-run for the same `(tenantId, messageId)`) produces exactly one audit entry per action and at most one Gmail API write per action.

5. **Immutable audit entry per applied action**: Every action writes a tamper-evident row with full provenance.
   - Current: No `triage_audit` table exists.
   - Target: A new `triage_audit` table (Liquibase changelog ≥ 020) stores: `id (UUID)`, `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `rule_id`, `rule_name_snapshot`, `action_type`, `action_args_json`, `reason` (matcher node id + evidence), `decision` (`APPLIED | SHADOW_LOGGED | REJECTED_BY_SAFETY_NET | REJECTED_BY_SAFETY_POLICY | REVERTED`), `gmail_change_token` (label state snapshot for undo), `applied_at`, `reverted_at` (nullable). Rows are tenant-scoped via `@TenantId`. No UPDATE on `decision` other than `APPLIED → REVERTED` and `applied_at` / `reverted_at` timestamps; all other writes are INSERT-only.
   - Acceptance: Integration test asserts row presence + tenant scoping + JSON schema validity. ArchUnit test bans any repository method named `delete*` or `update*` on the audit entity other than the explicit `markReverted` operation.

6. **Undo applied actions within 30 days**: User can revert any `APPLIED` action through a REST endpoint.
   - Current: No undo endpoint exists. Gmail's own undo (within seconds in the Gmail UI) is unrelated.
   - Target: `POST /api/triage/audit/{auditId}/undo` in `backend/api` calls a `TriageUndoService` that: (a) checks the audit row belongs to the requesting tenant, (b) is in `APPLIED` decision state, (c) `applied_at ≥ now - 30 days`, (d) computes the inverse Gmail call (`removeLabel` for `applyLabel`, `addLabel="INBOX"` for archive, `deleteDraft` for save-draft using the stored draft id from `action_args_json`), (e) executes the inverse call, (f) flips `decision` to `REVERTED` and sets `reverted_at`. Outside the window or already-reverted → `409 Conflict` with `ErrorCodes.TRIAGE_UNDO_EXPIRED` / `TRIAGE_UNDO_ALREADY_DONE`. Reverted entries remain in the table forever (no row deletion); only the `decision` flips. A daily worker job purges audit rows where `applied_at < now - 30 days` AND `decision IN (APPLIED, REVERTED)` to honor the retention bound.
   - Acceptance: E2E test labels a message via orchestrator → calls undo endpoint → verifies Gmail label removed + audit row `decision=REVERTED`. Second undo on same audit row → `409`. Mocked clock test confirms undo at 30d+1s → `409 TRIAGE_UNDO_EXPIRED`.

7. **Shadow mode is an opt-in tenant-wide preview toggle**: When ON, the orchestrator logs would-apply decisions but never calls Gmail.
   - Current: No shadow mode column exists. Behavior originally implied "mandatory shadow for first N decisions" is **explicitly reframed** (interview round 1, 2026-05-11): shadow mode is user-selectable, default OFF. Auto-triage starts immediately once rules are enabled.
   - Target: Add `tenants.triage_shadow_mode BOOLEAN NOT NULL DEFAULT FALSE` (Liquibase ≥ 021). When TRUE for a tenant, `TriageGmailWriter` is skipped and the audit row is written with `decision=SHADOW_LOGGED` instead of `APPLIED`, with `gmail_change_token = NULL` (no real Gmail state change occurred). A `PATCH /api/tenant/triage/shadow-mode {"enabled": boolean}` endpoint flips the flag. Shadow mode is independent of and orthogonal to `triage_paused` (Phase 2A): paused stops orchestrator entirely; shadow stops only the Gmail write step.
   - Acceptance: Integration test flips `triage_shadow_mode=true` → orchestrator runs → audit row `decision=SHADOW_LOGGED` exists, zero Gmail API calls. Flip back to false → next message produces `decision=APPLIED` and a Gmail write. Both states distinguishable in the audit table via `decision` column without inspecting Gmail.

8. **Sender safety net via sent-history heuristic**: Auto-actions on messages from frequent correspondents are suppressed by default until the user opts that sender into automation.
   - Current: No sender-importance signal exists in the codebase. Phase 1 has no list of "protected senders." Phase 5 plans an analytics screen showing top senders but no policy hook.
   - Target: At triage time, the orchestrator checks `SenderSafetyNetService.isProtected(tenantId, senderEmail)`. A sender is **protected** when, at the time of triage, the tenant has sent ≥ 3 emails to that sender's email address within the trailing 90 days. The check reads Gmail SENT label metadata only (`format=metadata`, query `in:sent to:<senderEmail> newer_than:90d`, max 3 results — early-exit), never message bodies/snippets. Per-tenant per-sender results are cached in Redis for 24h with key `triage:sender-protect:{tenantId}:{lower(senderEmail)}`. Protected senders cause the orchestrator to: (a) write audit row with `decision=REJECTED_BY_SAFETY_NET`, (b) skip Gmail writes, (c) include sender in a per-tenant "protected senders" list surfaced via `GET /api/triage/sender-safety-net`. A `POST /api/triage/sender-safety-net/{senderEmail}/opt-in` endpoint persists a row in a new `tenant_sender_opt_in` table that overrides the heuristic — opted-in senders are no longer protected and auto-triage proceeds. Phase 4 backend ships the heuristic + opt-in endpoint; Phase 5 surfaces the UI.
   - Acceptance: Integration test with seeded Gmail SENT fixture (3 messages to `boss@example.com` in last 90d) → triage on incoming message from `boss@example.com` → audit row `decision=REJECTED_BY_SAFETY_NET`. After `POST /opt-in` → next message from same sender → `decision=APPLIED` with Gmail write. Heuristic cache hit on second call to the same sender within 24h verified via Redis key inspection.

## Boundaries

**In scope:**
- New Spring Modulith package `core.triage` (`application/`, `domain/`, `persistence/`, `service/`, `exception/`)
- `MailMessageObserved` after-commit event published from `core.gmail` and consumed in `backend/worker`
- `TriageOrchestratorService` (in-worker) that loads rules, evaluates matchers (including inline `SemanticIntentMatcher` resolution via `LlmGateway`), merges action proposals, and dispatches to `TriageGmailWriter` or shadow logger
- `TriageSafetyPolicy` allow-list gate (label / archive / save-draft only) with `SafetyViolationException`
- `TriageGmailWriter` (Gmail API write adapter; the ONLY class allowed to call `users.messages.modify` / `users.drafts.create` from Phase 4 code)
- `triage_audit` table + `TriageAuditRepository` + JSON action-args
- `TriageUndoService` + `POST /api/triage/audit/{auditId}/undo` controller in `backend/api`
- 30-day audit retention worker in `backend/worker` (purges expired rows)
- `tenants.triage_shadow_mode` column + `PATCH /api/tenant/triage/shadow-mode` controller
- `SenderSafetyNetService` (Gmail SENT metadata-only lookup, Redis-cached) + `tenant_sender_opt_in` table + 2 REST endpoints
- `CallSite.TRIAGE_PLATFORM_LLM`, `CallSite.TRIAGE_DETERMINISTIC` enum additions for credit ledger
- New `RuleActionType` values **only** within the allow-list (e.g., `APPLY_LABEL`, `ARCHIVE_SKIP_INBOX`, `SAVE_DRAFT`); adding `RuleActionType.SEND` is forbidden
- `triage_audit_*` ArchUnit rules (no UPDATE/DELETE outside revert), `NoGmailSendAllowedTest`, `TriageBoundaryArchTests` (only `TriageGmailWriter` calls Gmail write APIs)
- i18n message keys for new error codes (vi + en)
- Backend integration + unit tests for orchestrator, safety policy, undo, shadow mode, sender net
- Spring Modulith `allowedDependencies` updates for `core.triage` (depends on `rules`, `gmail`, `llm`, `billing`, `tenant`, `shared.*`)

**Out of scope:**
- Audit log UI, undo button, shadow-mode toggle UI, sender-safety-net management UI — all deferred to Phase 5 (matches ROADMAP Phase 5 success criterion #1 + interview round 1 boundary decision)
- AI-drafted reply *content generation* — deferred to Phase 5 DRFT-01..DRFT-04; Phase 4 `SAVE_DRAFT` action accepts a draft body **payload from the rule's `ActionIntent`**, not from an LLM call
- Daily digest email — Phase 5 ANL-03
- Analytics screens (volume, top senders, rule hits) — Phase 5 ANL-01
- Auto-send for any rule type — permanently out of v1 (REQUIREMENTS Out-of-Scope list)
- Cold-email blocker / bulk unsubscribe / reply tracker — v2 (V2-02, V2-03, V2-04)
- Cross-message batched LLM evaluation for `SEMANTIC_INTENT` — interview round 2 locked **per-message inline** via LlmGateway; cross-message batching is a v2 cost optimization
- Persistent training of sender heuristic via embeddings — privacy constraint forbids embeddings (CLAUDE.md hard-do-not-use list)
- Re-running triage against historical messages (backfill) — Phase 4 only triages messages observed after the orchestrator is deployed
- CASA verification work for triage scopes — that remains the parallel external track from Phase 1

## Constraints

- **Privacy invariant (locked from Phase 1 / Phase 2C)**: No raw email body, snippet, subject (beyond the sanitized excerpt already used by Phase 3 `RuleEvaluationInput`), prompt, completion, or sender display name reaches logs or the audit table. `triage_audit.reason` may contain matcher node id + structured evidence ids only — never raw text. Logback scrub filter and ArchUnit log-bans from Phase 1 must still pass.
- **LLM call routing**: `SemanticIntentMatcher` evaluation MUST go through `LlmGateway` only; direct `ChatClient` / vendor SDK usage in `core.triage` fails the existing Phase 2C ArchUnit boundary.
- **Credit accounting**: Triage decisions that invoke the LLM (any rule with at least one `SEMANTIC_INTENT` matcher reached during evaluation) consume platform credits via `CreditLedger` under a new `CallSite.TRIAGE_PLATFORM_LLM`. Pure-deterministic triage decisions consume `CallSite.TRIAGE_DETERMINISTIC` with a near-zero unit (configurable; Phase 2B `BillingProperties` extension). BYOK paths bypass credits (Phase 2C convention).
- **Latency target (TRG-01)**: p95 end-to-end from `MailMessageObserved` event consumption to last Gmail write call ≤ 10 seconds; p99 ≤ 30 seconds, measured in production via Micrometer histograms tagged `triage.duration`. SLO breach > 30 min → alert (alerting wiring out of Phase 4 scope, recorded as a Phase 6 hardening item).
- **Idempotency**: Replay of the same `(tenantId, gmailMessageId)` MUST NOT cause duplicate Gmail writes or duplicate audit rows. Enforced by a unique index `triage_audit (tenant_id, gmail_message_id, rule_id, action_type)`.
- **Tenant isolation**: Orchestrator binds `TenantContext` ScopedValue before any tenant-scoped repository or LlmGateway call; Phase 1 FND-05 leak test must still pass after Phase 4 additions.
- **Auto-send is forbidden architecturally**: enforced by ArchUnit + the existence-test on `RuleActionType.SEND`, not by runtime checks alone.
- **Spring Boot 4 / Jackson 3 compatibility**: All new Jackson annotations (e.g., on `action_args_json` (de)serializers) follow CLAUDE.md guidance — `jackson-annotations` stays in `com.fasterxml.jackson.annotation.*`, core/databind in `tools.jackson.*`.
- **Schema migrations**: Liquibase YAML changesets only; no destructive operations, additive only.
- **Backend domain layout**: new package `core.triage` follows the locked structure (`application/`, `domain/`, `persistence/`, `service/`, `exception/`); `backend/api/controllers/triage/` for HTTP entry points (CONVENTIONS #2).

## Acceptance Criteria

- [ ] An after-commit `MailMessageObserved` event is published from `core.gmail` and consumed by `core.triage`; integration test in `backend/worker` proves wiring.
- [ ] Orchestrator evaluates rules in `display_order`, resolves `SemanticIntentMatcher` inline via `LlmGateway`, and produces the same `ActionProposal` set as a hand-rolled control run.
- [ ] `TriageSafetyPolicy` rejects any action whose `RuleActionType` is outside `{APPLY_LABEL, ARCHIVE_SKIP_INBOX, SAVE_DRAFT}`; rejection is logged + recorded as audit `decision=REJECTED_BY_SAFETY_POLICY`.
- [ ] ArchUnit test `NoGmailSendAllowedTest` fails the build if any `users.messages.send` or `users.drafts.send` call site exists in `backend/`.
- [ ] ArchUnit test `TriageGmailWriteBoundaryTest` fails the build if any non-`TriageGmailWriter` class invokes Gmail write APIs from triage code.
- [ ] `triage_audit` table exists via Liquibase changelog ≥ 020 with the documented columns and unique index; rows are written for every orchestrator decision, including `SHADOW_LOGGED`, `REJECTED_BY_SAFETY_NET`, `REJECTED_BY_SAFETY_POLICY`.
- [ ] `POST /api/triage/audit/{auditId}/undo` succeeds within 30d window, returns `409 TRIAGE_UNDO_EXPIRED` outside window, and returns `409 TRIAGE_UNDO_ALREADY_DONE` on second call.
- [ ] Daily worker purges `triage_audit` rows older than 30 days; verified by Testcontainers test with mocked clock.
- [ ] `tenants.triage_shadow_mode` column exists (Liquibase ≥ 021) defaulting to FALSE; flipping via `PATCH /api/tenant/triage/shadow-mode` changes orchestrator behavior from `APPLIED` to `SHADOW_LOGGED` without invoking Gmail writes.
- [ ] `SenderSafetyNetService` returns `protected=true` when Gmail SENT history has ≥ 3 messages to the sender in the trailing 90 days; cached in Redis with 24h TTL; opt-in row in `tenant_sender_opt_in` overrides protection.
- [ ] No raw email body / snippet / display name / prompt / completion appears in `triage_audit`, application logs, or Micrometer tags (verified by sweep test analogous to FND-03).
- [ ] Phase 1 FND-05 multi-tenant leak test still passes after Phase 4 additions.
- [ ] `./gradlew clean check` is GREEN; new ArchUnit rules + integration tests pass.

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                  |
|--------------------|-------|------|--------|------------------------------------------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | OK     | Hero phase outcome + p95 latency + per-message inline SEMANTIC_INTENT  |
| Boundary Clarity   | 0.90  | 0.70 | OK     | Phase 4 = backend-only; UI in Phase 5; explicit out-of-scope list      |
| Constraint Clarity | 0.85  | 0.65 | OK     | Privacy, allow-list, credit routing, idempotency, latency all locked   |
| Acceptance Criteria| 0.88  | 0.70 | OK     | 13 pass/fail checks tied to specific files/tables/columns/endpoints    |
| **Ambiguity**      | 0.11  | <=0.20 | OK   | Gate cleared after 2 rounds                                            |

## Interview Log

| Round | Perspective       | Question summary                                              | Decision locked                                                                                       |
|-------|-------------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| 1     | Researcher        | Shadow mode N (TRG-07)                                       | **Reframed** by user: shadow is opt-in tenant-wide toggle, default OFF; no count-based auto-unlock     |
| 1     | Boundary Keeper   | Undo retention window (TRG-06)                               | 30 days; daily worker purges expired audit rows                                                       |
| 1     | Boundary Keeper   | Sender safety net mechanism (TRG-08)                         | Heuristic: >=3 sent emails to sender in trailing 90 days, Gmail SENT metadata-only, Redis 24h cache    |
| 1     | Boundary Keeper   | Phase 4 vs Phase 5 scope                                     | Phase 4 = backend + REST endpoints only; all UI deferred to Phase 5                                   |
| 2     | Failure Analyst   | TRG-01 latency SLO                                           | Auto-selected by Claude per user delegation: p95 <= 10s, p99 <= 30s (Micrometer `triage.duration`)     |
| 2     | Failure Analyst   | SEMANTIC_INTENT resolution                                   | Per-message inline LlmGateway call; "batched" = batched matchers within one message, not cross-message |

Notes on auto-selection: TRG-01 latency was delegated to Claude with "performance càng tốt càng ok." The 10s/30s pair balances ROADMAP success-criterion #1 ("within a few seconds") against realistic Gmail metadata fetch (~300-800 ms) + matcher eval (deterministic << 100 ms; LLM ~1-4 s) + Gmail write (~300-700 ms) + retry budget.

---

*Phase: 04-triage-convergence-hero*
*Spec created: 2026-05-11*
*Next step: /gsd-discuss-phase 4 — implementation decisions (event-vs-direct call shape, audit JSON schema, RuleActionType allow-list enum naming, retention-purge ShedLock lock-key, idempotency unique-index choice, sender-cache invalidation on opt-in)*